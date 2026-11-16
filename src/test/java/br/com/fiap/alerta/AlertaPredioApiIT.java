package br.com.fiap.alerta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.alerta.model.InscricaoPush;
import br.com.fiap.alerta.model.StatusClassificacao;
import br.com.fiap.alerta.push.EnviadorPush;
import br.com.fiap.alerta.push.EnviadorPush.ResultadoEnvio;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.repository.CheckinRepository;
import br.com.fiap.alerta.repository.EventoAuditoriaRepository;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.repository.PredioRepository;

/**
 * Sobe a aplicação inteira contra um MongoDB real (database alerta_it). Só o envio de push para os
 * servidores do Google/Mozilla/Apple é substituído por um mock, para conferir quem foi notificado.
 */
@SpringBootTest(properties = { "spring.data.mongodb.database=alerta_it", "alerta.seed.enabled=false" })
@AutoConfigureMockMvc
class AlertaPredioApiIT {

    private static final Duration ESPERA = Duration.ofSeconds(10);
    private static final String TOKEN = "X-Morador-Token";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Autowired
    private PredioRepository predios;
    @Autowired
    private MoradorRepository moradores;
    @Autowired
    private AlertaRepository alertas;
    @Autowired
    private CheckinRepository checkins;
    @Autowired
    private EventoAuditoriaRepository auditoria;

    @MockitoBean
    private EnviadorPush enviador;

    private record Acesso(String predioId, String moradorId, String token, String codigoConvite) {
    }

    @BeforeEach
    void limpar() {
        // deleteAll (e não drop) para preservar os índices únicos
        predios.deleteAll();
        moradores.deleteAll();
        alertas.deleteAll();
        checkins.deleteAll();
        auditoria.deleteAll();
        when(enviador.habilitado()).thenReturn(true);
        when(enviador.enviar(any(), any())).thenReturn(ResultadoEnvio.ENVIADO);
    }

    // (a) obrigatório
    @Test
    void disparoDeAlertaNotificaTodosOsMoradoresDoPredio() throws Exception {
        Acesso carla = criarPredio("Residencial Acácias", "Carla", "101");
        Acesso ana = entrar(carla.codigoConvite(), "Ana", "302", "Cadeirante");
        Acesso joao = entrar(carla.codigoConvite(), "João", "204", null);
        Acesso bia = entrar(carla.codigoConvite(), "Beatriz", "501", null);
        Acesso pedro = criarPredio("Edifício Horizonte", "Pedro", "11");
        for (Acesso a : List.of(carla, ana, joao, bia, pedro)) {
            inscrever(a, endpoint(a));
        }

        String alertaId = disparar(carla.predioId(), joao, "INCENDIO", "Fumaça saindo do 304")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.autor.nome").value("João"))
                .andExpect(jsonPath("$.autor.apartamento").value("204"))
                .andReturn().getResponse().getContentAsString().transform(this::id);

        ArgumentCaptor<InscricaoPush> notificados = ArgumentCaptor.forClass(InscricaoPush.class);
        verify(enviador, timeout(ESPERA.toMillis()).times(3)).enviar(notificados.capture(), any());
        assertThat(notificados.getAllValues()).extracting(InscricaoPush::endpoint)
                .containsExactlyInAnyOrder(endpoint(carla), endpoint(ana), endpoint(bia));
        verify(enviador, after(300).never()).enviar(argThat(i -> i.endpoint().equals(endpoint(joao))), any());
        verify(enviador, never()).enviar(argThat(i -> i.endpoint().equals(endpoint(pedro))), any());
        await().atMost(ESPERA).until(() -> alertas.findById(alertaId).orElseThrow().getNotificados() != null);
        assertThat(alertas.findById(alertaId).orElseThrow().getNotificados()).isEqualTo(3);
    }

    // (b) obrigatório
    @Test
    void moradorDeOutroPredioRecebe403AoDispararOuVerAlertas() throws Exception {
        Acesso carla = criarPredio("Residencial Acácias", "Carla", "101");
        Acesso joao = entrar(carla.codigoConvite(), "João", "204", null);
        Acesso pedro = criarPredio("Edifício Horizonte", "Pedro", "11");
        String alertaId = id(disparar(carla.predioId(), joao, "GAS", "Cheiro forte de gás no hall")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        disparar(carla.predioId(), pedro, "INCENDIO", "tentativa de fora")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"));
        mvc.perform(get("/api/alertas/{id}", alertaId).header(TOKEN, pedro.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/alertas/{id}/painel", alertaId).header(TOKEN, pedro.token()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/predios/{id}/alertas", carla.predioId()).header(TOKEN, pedro.token()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/alertas/{id}/checkin", alertaId).header(TOKEN, pedro.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"situacao\": \"SEGURO\"}"))
                .andExpect(status().isForbidden());

        assertThat(alertas.findByPredioIdOrderByDataHoraDesc(carla.predioId())).hasSize(1);
        mvc.perform(get("/api/predios/{id}/auditoria", carla.predioId()).header(TOKEN, carla.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tipo == 'ACESSO_NEGADO')]", hasSize(5)))
                .andExpect(jsonPath("$[?(@.tipo == 'ACESSO_NEGADO')].moradorId", hasItem(pedro.moradorId())));
    }

    // (c) obrigatório
    @Test
    void segundaAdvertenciaPorMaFeSuspendeOBotaoMasMantemNotificacaoECheckin() throws Exception {
        Acesso carla = criarPredio("Residencial Acácias", "Carla", "101");
        Acesso joao = entrar(carla.codigoConvite(), "João", "204", null);
        Acesso ana = entrar(carla.codigoConvite(), "Ana", "302", null);
        inscrever(joao, endpoint(joao));

        String primeiro = id(disparar(carla.predioId(), joao, "ELEVADOR", "trote 1")
                .andReturn().getResponse().getContentAsString());
        encerrar(carla, primeiro, "FALSO_MA_FE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encerramento.penalidadeAplicada").value("ADVERTENCIA"));
        eu(joao).andExpect(jsonPath("$.morador.status").value("ADVERTIDO"));

        String segundo = id(disparar(carla.predioId(), joao, "ELEVADOR", "trote 2")
                .andReturn().getResponse().getContentAsString());
        encerrar(carla, segundo, "FALSO_MA_FE")
                .andExpect(jsonPath("$.encerramento.penalidadeAplicada").value("SUSPENSAO"));
        eu(joao)
                .andExpect(jsonPath("$.morador.status").value("SUSPENSO"))
                .andExpect(jsonPath("$.morador.qtdAdvertencias").value(2))
                .andExpect(jsonPath("$.morador.suspensoAte").exists());

        disparar(carla.predioId(), joao, "INCENDIO", "terceira tentativa")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("suspenso")));

        String alertaDaAna = id(disparar(carla.predioId(), ana, "GAS", "Vazamento de gás no 3º andar")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        verify(enviador, timeout(ESPERA.toMillis()))
                .enviar(argThat(i -> i.endpoint().equals(endpoint(joao))), any());
        mvc.perform(put("/api/alertas/{id}/checkin", alertaDaAna).header(TOKEN, joao.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"situacao\": \"SEGURO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("SEGURO"));
        mvc.perform(post("/api/alertas/{id}/confirmacoes", alertaDaAna).header(TOKEN, joao.token()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/alertas/{id}/painel", alertaDaAna).header(TOKEN, joao.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seguros[0].nome").value("João"));
    }

    @Test
    void painelPoeVulneraveisPrimeiroEEncerramentoRealNaoPenaliza() throws Exception {
        Acesso carla = criarPredio("Residencial Acácias", "Carla", "101");
        Acesso joao = entrar(carla.codigoConvite(), "João", "204", null);
        entrar(carla.codigoConvite(), "Ana", "302", "Cadeirante: precisa de ajuda na escada");
        Acesso bia = entrar(carla.codigoConvite(), "Beatriz", "105", null);
        String alertaId = id(disparar(carla.predioId(), joao, "ALAGAMENTO", "Água na garagem")
                .andReturn().getResponse().getContentAsString());
        checkin(joao, alertaId, "PRECISA_AJUDA").andExpect(status().isOk());
        checkin(bia, alertaId, "SEGURO").andExpect(status().isOk());
        checkin(bia, alertaId, "PRECISA_AJUDA").andExpect(status().isOk());

        mvc.perform(get("/api/alertas/{id}/painel", alertaId).header(TOKEN, carla.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totais.moradores").value(4))
                .andExpect(jsonPath("$.totais.precisamDeAjuda").value(2))
                .andExpect(jsonPath("$.precisamDeAjuda[0].apartamento").value("105"))
                .andExpect(jsonPath("$.precisamDeAjuda[1].apartamento").value("204"))
                .andExpect(jsonPath("$.semResposta[0].nome").value("Ana"))
                .andExpect(jsonPath("$.semResposta[0].vulneravel").value(true))
                .andExpect(jsonPath("$.semResposta[0].necessidadeEspecial").value("Cadeirante: precisa de ajuda na escada"))
                .andExpect(jsonPath("$.semResposta[0].telefone").exists());
        mvc.perform(get("/api/alertas/{id}/painel", alertaId).header(TOKEN, joao.token()))
                .andExpect(jsonPath("$.semResposta[0].telefone").doesNotExist());

        encerrar(carla, alertaId, "REAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCERRADO"))
                .andExpect(jsonPath("$.encerramento.motivo").value("REAL"))
                .andExpect(jsonPath("$.encerramento.penalidadeAplicada").doesNotExist());
        eu(joao).andExpect(jsonPath("$.morador.status").value("ATIVO"));
        encerrar(carla, alertaId, "REAL").andExpect(status().isConflict());
        checkin(bia, alertaId, "SEGURO").andExpect(status().isConflict());
        assertThat(checkins.findByAlertaId(alertaId)).hasSize(2);
    }

    @Test
    void soOSindicoEncerraEConfirmacaoEhUmaPorMorador() throws Exception {
        Acesso carla = criarPredio("Residencial Acácias", "Carla", "101");
        Acesso joao = entrar(carla.codigoConvite(), "João", "204", null);
        Acesso ana = entrar(carla.codigoConvite(), "Ana", "302", null);
        String alertaId = id(disparar(carla.predioId(), joao, "SOCORRO_MEDICO", "Vizinho desmaiou no hall")
                .andReturn().getResponse().getContentAsString());

        encerrar(ana, alertaId, "REAL").andExpect(status().isForbidden());
        mvc.perform(post("/api/alertas/{id}/confirmacoes", alertaId).header(TOKEN, ana.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmacoes", hasSize(1)))
                .andExpect(jsonPath("$.confirmacoes[0].nome").value("Ana"));
        mvc.perform(post("/api/alertas/{id}/confirmacoes", alertaId).header(TOKEN, ana.token()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/alertas/{id}/confirmacoes", alertaId).header(TOKEN, joao.token()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/predios/{id}/alertas", carla.predioId()).param("status", "ATIVO")
                .header(TOKEN, ana.token()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].totalConfirmacoes").value(1))
                .andExpect(jsonPath("$[0].fotoUrl").doesNotExist());
    }

    @Test
    void triagemFakeClassificaEmSegundoPlano() throws Exception {
        Acesso carla = criarPredio("Residencial Acácias", "Carla", "101");
        String alertaId = id(disparar(carla.predioId(), carla, "ALAGAMENTO", "Muita fumaça e fogo no 2º andar")
                .andExpect(jsonPath("$.classificacao.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString());

        await().atMost(ESPERA).until(() -> alertas.findById(alertaId).orElseThrow().getClassificacao().status()
                == StatusClassificacao.CONCLUIDA);
        mvc.perform(get("/api/alertas/{id}", alertaId).header(TOKEN, carla.token()))
                .andExpect(jsonPath("$.classificacao.tipoProvavel").value("INCENDIO"))
                .andExpect(jsonPath("$.classificacao.gravidade").value("CRITICA"))
                .andExpect(jsonPath("$.classificacao.modelo").value("fake-local"))
                .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    void inscricaoDePushMudaDeMoradorQuandoOAparelhoTrocaDeConta() throws Exception {
        Acesso carla = criarPredio("Residencial Acácias", "Carla", "101");
        Acesso ana = entrar(carla.codigoConvite(), "Ana", "302", null);
        String aparelho = "https://fcm.googleapis.com/fcm/send/aparelho-compartilhado";

        inscrever(carla, aparelho);
        inscrever(ana, aparelho);

        assertThat(moradores.findById(carla.moradorId()).orElseThrow().getInscricoesPush()).isEmpty();
        assertThat(moradores.findById(ana.moradorId()).orElseThrow().getInscricoesPush())
                .extracting(InscricaoPush::endpoint).containsExactly(aparelho);
        mvc.perform(post("/api/push/inscricoes").header(TOKEN, ana.token()).contentType(MediaType.APPLICATION_JSON)
                .content(inscricaoJson("https://servidor-qualquer.example.com/push")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void necessidadeEspecialExigeConsentimentoETokenNaoApareceNoJson() throws Exception {
        Acesso carla = criarPredio("Residencial Acácias", "Carla", "101");
        mvc.perform(post("/api/predios/entrar").contentType(MediaType.APPLICATION_JSON).content("""
                {"codigoConvite": "%s", "morador": {"nome": "Ana", "apartamento": "302",
                 "telefone": "(11) 98888-3002", "necessidadeEspecial": "Cadeirante"}}
                """.formatted(carla.codigoConvite())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['morador.consentimentoValido']").exists());

        Acesso ana = entrar(carla.codigoConvite().toLowerCase(), "Ana", "302", "Cadeirante");
        eu(ana)
                .andExpect(jsonPath("$.morador.necessidadeEspecial.consentimento.aceito").value(true))
                .andExpect(jsonPath("$.morador.necessidadeEspecial.consentimento.versaoTermo").value("1.0"))
                .andExpect(jsonPath("$.morador.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.predio.codigoConvite").doesNotExist());
        mvc.perform(get("/api/moradores/eu").header(TOKEN, "token-inventado")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/moradores/eu")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthEstaUpComOMongo() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.mongo.status").value("UP"));
        mvc.perform(get("/api/info")).andExpect(jsonPath("$.aplicacao").value("alerta-predio"));
    }

    private Acesso criarPredio(String nome, String sindico, String apartamento) throws Exception {
        String resposta = mvc.perform(post("/api/predios").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome": "%s", "endereco": "Rua das Acácias, 120",
                 "sindico": {"nome": "%s", "apartamento": "%s", "telefone": "(11) 98888-0000"}}
                """.formatted(nome, sindico, apartamento)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return acesso(resposta);
    }

    private Acesso entrar(String codigo, String nome, String apartamento, String necessidade) throws Exception {
        String extra = necessidade == null ? ""
                : ", \"necessidadeEspecial\": \"%s\", \"consentimentoNecessidadeEspecial\": true".formatted(necessidade);
        String resposta = mvc.perform(post("/api/predios/entrar").contentType(MediaType.APPLICATION_JSON).content("""
                {"codigoConvite": "%s", "morador": {"nome": "%s", "apartamento": "%s",
                 "telefone": "(11) 98888-1234"%s}}
                """.formatted(codigo, nome, apartamento, extra)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return acesso(resposta);
    }

    private void inscrever(Acesso acesso, String endpoint) throws Exception {
        mvc.perform(post("/api/push/inscricoes").header(TOKEN, acesso.token())
                .contentType(MediaType.APPLICATION_JSON).content(inscricaoJson(endpoint)))
                .andExpect(status().isNoContent());
    }

    private ResultActions disparar(String predioId, Acesso autor, String tipo, String descricao) throws Exception {
        return mvc.perform(post("/api/predios/{id}/alertas", predioId).header(TOKEN, autor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\": \"%s\", \"descricao\": \"%s\"}".formatted(tipo, descricao)));
    }

    private ResultActions encerrar(Acesso sindico, String alertaId, String motivo) throws Exception {
        return mvc.perform(post("/api/alertas/{id}/encerramento", alertaId).header(TOKEN, sindico.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"%s\"}".formatted(motivo)));
    }

    private ResultActions checkin(Acesso morador, String alertaId, String situacao) throws Exception {
        return mvc.perform(put("/api/alertas/{id}/checkin", alertaId).header(TOKEN, morador.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"situacao\": \"%s\"}".formatted(situacao)));
    }

    private ResultActions eu(Acesso morador) throws Exception {
        return mvc.perform(get("/api/moradores/eu").header(TOKEN, morador.token())).andExpect(status().isOk());
    }

    private static String inscricaoJson(String endpoint) {
        return """
                {"endpoint": "%s", "expirationTime": null, "keys": {"p256dh": "chave-p256dh", "auth": "segredo"}}
                """.formatted(endpoint);
    }

    private static String endpoint(Acesso acesso) {
        return "https://fcm.googleapis.com/fcm/send/" + acesso.moradorId();
    }

    private Acesso acesso(String resposta) throws Exception {
        JsonNode node = json.readTree(resposta);
        return new Acesso(node.at("/predio/id").asText(), node.at("/morador/id").asText(), node.get("token").asText(),
                node.at("/predio/codigoConvite").asText(null));
    }

    private String id(String resposta) {
        try {
            return json.readTree(resposta).get("id").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
