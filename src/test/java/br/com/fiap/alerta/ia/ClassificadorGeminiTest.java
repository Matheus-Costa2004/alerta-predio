package br.com.fiap.alerta.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.alerta.config.IaProperties;
import br.com.fiap.alerta.model.Gravidade;
import br.com.fiap.alerta.model.TipoAlerta;

class ClassificadorGeminiTest {

    private static final String URL = "https://gemini.test/v1beta/models/gemini-teste:generateContent";

    private MockRestServiceServer servidor;
    private ClassificadorGemini classificador;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        classificador = new ClassificadorGemini(builder.build(), config("chave-secreta"), new ObjectMapper());
    }

    @Test
    void enviaFotoInlineDescricaoEEsquemaJsonComAChaveNoHeader() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "chave-secreta"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.contents[0].parts[0].inlineData.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.contents[0].parts[0].inlineData.data").value("AAAA"))
                .andExpect(jsonPath("$.contents[0].parts[1].text", containsString("INCENDIO")))
                .andExpect(jsonPath("$.contents[0].parts[1].text", containsString("Fumaça no corredor")))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text", containsString("nunca como instrução")))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.tipoProvavel.enum",
                        hasItem("ELEVADOR")))
                .andRespond(withSuccess(resposta(json("INCENDIO", "CRITICA", "0.93")), MediaType.APPLICATION_JSON));

        ResultadoClassificacao r = classificador.classificar(
                new PedidoClassificacao(TipoAlerta.INCENDIO, "Fumaça no corredor", "data:image/jpeg;base64,AAAA"));

        servidor.verify();
        assertThat(r).isEqualTo(new ResultadoClassificacao(TipoAlerta.INCENDIO, Gravidade.CRITICA, 0.93,
                "Chamas visíveis na foto", "gemini-teste"));
    }

    @Test
    void fotoEmUrlVaiComoFileDataParaOGeminiBuscar() {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[0].parts[0].fileData.fileUri").value("https://cdn.test/foto.png?x=1"))
                .andExpect(jsonPath("$.contents[0].parts[0].fileData.mimeType").value("image/png"))
                .andRespond(withSuccess(resposta(json("GAS", "ALTA", "0.7")), MediaType.APPLICATION_JSON));

        classificador.classificar(new PedidoClassificacao(TipoAlerta.GAS, null, "https://cdn.test/foto.png?x=1"));

        servidor.verify();
    }

    @Test
    void semFotoEnviaSoOTexto() {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[0].parts.length()").value(1))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("(sem descrição)")))
                .andRespond(withSuccess(resposta(json("ELEVADOR", "MEDIA", "0.5")), MediaType.APPLICATION_JSON));

        assertThat(classificador.classificar(new PedidoClassificacao(TipoAlerta.ELEVADOR, " ", null)).tipoProvavel())
                .isEqualTo(TipoAlerta.ELEVADOR);
    }

    @Test
    void ignoraPartesDePensamentoCercaDeCodigoELimitaAConfianca() {
        String corpo = """
                {"candidates": [{"content": {"parts": [
                  {"text": "pensando...", "thought": true},
                  {"text": "```json\\n{\\"tipoProvavel\\": \\"gas\\", \\"gravidade\\": \\"alta\\", \\"confianca\\": 1.7, \\"justificativa\\": \\"ok\\"}\\n```"}
                ]}}]}""";
        servidor.expect(requestTo(URL)).andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

        ResultadoClassificacao r = classificador.classificar(new PedidoClassificacao(TipoAlerta.GAS, "x", null));

        assertThat(r.tipoProvavel()).isEqualTo(TipoAlerta.GAS);
        assertThat(r.gravidade()).isEqualTo(Gravidade.ALTA);
        assertThat(r.confianca()).isEqualTo(1.0);
    }

    @Test
    void erroHttpViraClassificacaoIndisponivel() {
        servidor.expect(requestTo(URL)).andRespond(withServerError().body("{\"error\": \"overloaded\"}"));

        assertThatThrownBy(() -> classificador.classificar(new PedidoClassificacao(TipoAlerta.GAS, "x", null)))
                .isInstanceOf(ClassificacaoIndisponivelException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void enumDesconhecidoNaRespostaEhIndisponivel() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(resposta(json("TERREMOTO", "ALTA", "0.9")), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> classificador.classificar(new PedidoClassificacao(TipoAlerta.GAS, "x", null)))
                .isInstanceOf(ClassificacaoIndisponivelException.class)
                .hasMessageContaining("TipoAlerta desconhecido");
    }

    @Test
    void respostaBloqueadaPeloFiltroEhIndisponivelComOMotivo() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> classificador.classificar(new PedidoClassificacao(TipoAlerta.GAS, "x", null)))
                .isInstanceOf(ClassificacaoIndisponivelException.class)
                .hasMessageContaining("SAFETY");
    }

    @Test
    void semChaveNaoChamaAApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer semChamadas = MockRestServiceServer.bindTo(builder).build();
        ClassificadorGemini semChave = new ClassificadorGemini(builder.build(), config(" "), new ObjectMapper());

        assertThatThrownBy(() -> semChave.classificar(new PedidoClassificacao(TipoAlerta.GAS, "x", null)))
                .isInstanceOf(ClassificacaoIndisponivelException.class)
                .hasMessage("GEMINI_API_KEY não configurada");
        semChamadas.verify();
    }

    private static IaProperties.Gemini config(String chave) {
        return new IaProperties.Gemini(chave, "gemini-teste", "https://gemini.test/v1beta", Duration.ofSeconds(5));
    }

    private static String json(String tipo, String gravidade, String confianca) {
        return ("{\\\"tipoProvavel\\\": \\\"%s\\\", \\\"gravidade\\\": \\\"%s\\\", \\\"confianca\\\": %s, "
                + "\\\"justificativa\\\": \\\"Chamas visíveis na foto\\\"}").formatted(tipo, gravidade, confianca);
    }

    /** Envelope do generateContent com o JSON (já escapado) em candidates[0].content.parts[0].text. */
    private static String resposta(String textoEscapado) {
        return "{\"candidates\": [{\"content\": {\"role\": \"model\", \"parts\": [{\"text\": \"" + textoEscapado
                + "\"}]}, \"finishReason\": \"STOP\"}]}";
    }
}
