package br.com.fiap.alerta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.alerta.ia.ClassificadorAlerta;
import br.com.fiap.alerta.ia.ResultadoClassificacao;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Gravidade;
import br.com.fiap.alerta.model.StatusAlerta;
import br.com.fiap.alerta.model.StatusClassificacao;
import br.com.fiap.alerta.model.TipoAlerta;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.repository.PredioRepository;

/** (d) O alerta nunca depende da IA: nem quando ela falha, nem quando ela demora. */
@SpringBootTest(properties = { "spring.data.mongodb.database=alerta_it_triagem", "alerta.seed.enabled=false" })
@AutoConfigureMockMvc
class TriagemIaIT {

    private static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private AlertaRepository alertas;
    @Autowired
    private PredioRepository predios;
    @Autowired
    private MoradorRepository moradores;

    @MockitoBean
    private ClassificadorAlerta classificador;

    private String predioId;
    private String token;

    @BeforeEach
    void criarPredio() throws Exception {
        predios.deleteAll();
        moradores.deleteAll();
        alertas.deleteAll();
        JsonNode acesso = json.readTree(mvc.perform(post("/api/predios").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nome": "Residencial Acácias", "endereco": "Rua das Acácias, 120",
                         "sindico": {"nome": "Carla", "apartamento": "101", "telefone": "(11) 98888-1001"}}
                        """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        predioId = acesso.at("/predio/id").asText();
        token = acesso.get("token").asText();
    }

    @Test
    void falhaDaIaNaoImpedeOAlerta() throws Exception {
        when(classificador.classificar(any())).thenThrow(new IllegalStateException("API de visão fora do ar"));

        String alertaId = disparar()
                .get("id").asText();

        await().atMost(ESPERA).until(() -> classificacao(alertaId) == StatusClassificacao.INDISPONIVEL);
        Alerta alerta = alertas.findById(alertaId).orElseThrow();
        assertThat(alerta.getStatus()).isEqualTo(StatusAlerta.ATIVO);
        assertThat(alerta.getClassificacao().erro()).isEqualTo("Erro inesperado na IA (IllegalStateException)");
        mvc.perform(put("/api/alertas/{id}/checkin", alertaId).header("X-Morador-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"situacao\": \"SEGURO\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/alertas/{id}/painel", alertaId).header("X-Morador-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerta.classificacao.status").value("INDISPONIVEL"))
                .andExpect(jsonPath("$.totais.seguros").value(1));
    }

    @Test
    void disparoRespondeSemEsperarAIa() throws Exception {
        CountDownLatch liberarIa = new CountDownLatch(1);
        when(classificador.classificar(any())).thenAnswer(chamada -> {
            liberarIa.await(ESPERA.toSeconds(), TimeUnit.SECONDS);
            return new ResultadoClassificacao(TipoAlerta.INCENDIO, Gravidade.ALTA, 0.9, "fumaça na foto", "teste");
        });

        long inicio = System.nanoTime();
        String alertaId = disparar().get("id").asText();
        Duration tempoDoDisparo = Duration.ofNanos(System.nanoTime() - inicio);

        // A IA já foi chamada e está travada, mas o alerta já respondeu 201 e está gravado como PENDENTE
        verify(classificador, timeout(ESPERA.toMillis())).classificar(any());
        assertThat(tempoDoDisparo).isLessThan(Duration.ofSeconds(3));
        assertThat(classificacao(alertaId)).isEqualTo(StatusClassificacao.PENDENTE);

        liberarIa.countDown();
        await().atMost(ESPERA).until(() -> classificacao(alertaId) == StatusClassificacao.CONCLUIDA);
        assertThat(alertas.findById(alertaId).orElseThrow().getClassificacao().confianca()).isEqualTo(0.9);
    }

    private JsonNode disparar() throws Exception {
        return json.readTree(mvc.perform(post("/api/predios/{id}/alertas", predioId).header("X-Morador-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\": \"INCENDIO\", \"descricao\": \"Fumaça no corredor\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.classificacao.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString());
    }

    private StatusClassificacao classificacao(String alertaId) {
        return alertas.findById(alertaId).orElseThrow().getClassificacao().status();
    }
}
