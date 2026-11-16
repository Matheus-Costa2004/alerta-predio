package br.com.fiap.alerta.controller;

import static br.com.fiap.alerta.Fixtures.PREDIO;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.auth.AutenticacaoService;
import br.com.fiap.alerta.dto.AlertaResumo;
import br.com.fiap.alerta.dto.DisparoAlertaRequest;
import br.com.fiap.alerta.dto.EncerramentoRequest;
import br.com.fiap.alerta.dto.PainelAlerta;
import br.com.fiap.alerta.exception.AcessoNegadoException;
import br.com.fiap.alerta.exception.ConflitoException;
import br.com.fiap.alerta.exception.NaoAutenticadoException;
import br.com.fiap.alerta.exception.RecursoNaoEncontradoException;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Checkin;
import br.com.fiap.alerta.model.Encerramento;
import br.com.fiap.alerta.model.MotivoEncerramento;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.Penalidade;
import br.com.fiap.alerta.model.SituacaoCheckin;
import br.com.fiap.alerta.model.StatusAlerta;
import br.com.fiap.alerta.model.TipoAlerta;
import br.com.fiap.alerta.service.AlertaService;
import br.com.fiap.alerta.service.PainelService;

@WebMvcTest(AlertaController.class)
class AlertaControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AlertaService alertas;
    @MockitoBean
    private PainelService painel;
    @MockitoBean
    private AutenticacaoService autenticacao;

    private final Morador joao = Fixtures.joao();
    private final Morador carla = Fixtures.sindica();
    private final Morador pedro = Fixtures.deOutroPredio();

    @BeforeEach
    void tokens() {
        when(autenticacao.autenticar("tk-joao")).thenReturn(joao);
        when(autenticacao.autenticar("tk-carla")).thenReturn(carla);
        when(autenticacao.autenticar("tk-pedro")).thenReturn(pedro);
        when(autenticacao.autenticar("tk-invalido")).thenThrow(new NaoAutenticadoException("Token de morador inválido."));
    }

    @Test
    void disparoResponde201ComLocationEClassificacaoPendente() throws Exception {
        when(alertas.disparar(eq(PREDIO), any(DisparoAlertaRequest.class), eq(joao)))
                .thenReturn(Fixtures.alerta("a1", joao, TipoAlerta.INCENDIO));

        mvc.perform(disparo("tk-joao", "{\"tipo\": \"INCENDIO\", \"descricao\": \"Fumaça no 304\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/alertas/a1")))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.classificacao.status").value("PENDENTE"))
                .andExpect(jsonPath("$.autor.nome").value("João Lima"))
                .andExpect(jsonPath("$.autor.apartamento").value("204"));
        verify(alertas).disparar(PREDIO, new DisparoAlertaRequest(TipoAlerta.INCENDIO, "Fumaça no 304", null), joao);
    }

    @Test
    void semTokenOuComTokenInvalidoResponde401() throws Exception {
        mvc.perform(disparo(null, "{\"tipo\": \"INCENDIO\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.detail").value("Envie o token do morador no header X-Morador-Token."));
        mvc.perform(disparo("tk-invalido", "{\"tipo\": \"INCENDIO\"}")).andExpect(status().isUnauthorized());
        verify(alertas, never()).disparar(any(), any(), any());
    }

    // (b) obrigatório, na camada HTTP
    @Test
    void moradorDeOutroPredioResponde403() throws Exception {
        when(alertas.disparar(eq(PREDIO), any(), eq(pedro)))
                .thenThrow(new AcessoNegadoException("Você não é morador deste prédio."));
        when(alertas.buscar("a1", pedro)).thenThrow(new AcessoNegadoException("Você não é morador deste prédio."));

        mvc.perform(disparo("tk-pedro", "{\"tipo\": \"GAS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"))
                .andExpect(jsonPath("$.detail").value("Você não é morador deste prédio."));
        mvc.perform(get("/api/alertas/a1").header("X-Morador-Token", "tk-pedro")).andExpect(status().isForbidden());
    }

    @Test
    void suspensoResponde403AoDisparar() throws Exception {
        when(alertas.disparar(eq(PREDIO), any(), eq(joao)))
                .thenThrow(new AcessoNegadoException("Seu botão de alerta está suspenso até 14/12/2026"));

        mvc.perform(disparo("tk-joao", "{\"tipo\": \"GAS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Seu botão de alerta está suspenso até 14/12/2026"));
    }

    @Test
    void disparoInvalidoResponde400SemChamarOServico() throws Exception {
        mvc.perform(disparo("tk-joao", "{\"descricao\": \"sem tipo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.tipo").exists());
        mvc.perform(disparo("tk-joao", "{\"tipo\": \"TERREMOTO\"}")).andExpect(status().isBadRequest());
        mvc.perform(disparo("tk-joao", "{\"tipo\": \"GAS\", \"fotoUrl\": \"ftp://servidor/foto.jpg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.fotoUrl").exists());
        mvc.perform(disparo("tk-joao", "{\"tipo\": \"GAS\", \"descricao\": \"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(alertas, never()).disparar(any(), any(), any());
    }

    @Test
    void fotoComoDataUrlEhAceita() throws Exception {
        when(alertas.disparar(eq(PREDIO), any(), eq(joao))).thenReturn(Fixtures.alerta("a1", joao, TipoAlerta.GAS));

        mvc.perform(disparo("tk-joao", "{\"tipo\": \"GAS\", \"fotoUrl\": \"data:image/jpeg;base64,/9j/4AAQSkZJRg==\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void listarFiltraPorStatusEStatusInvalidoResponde400() throws Exception {
        when(alertas.listar(PREDIO, StatusAlerta.ATIVO, joao))
                .thenReturn(List.of(AlertaResumo.de(Fixtures.alerta("a1", joao, TipoAlerta.GAS))));

        mvc.perform(get("/api/predios/{id}/alertas", PREDIO).param("status", "ATIVO").header("X-Morador-Token", "tk-joao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("a1"))
                .andExpect(jsonPath("$[0].temFoto").value(false));
        mvc.perform(get("/api/predios/{id}/alertas", PREDIO).param("status", "QUALQUER")
                .header("X-Morador-Token", "tk-joao"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alertaInexistenteResponde404() throws Exception {
        when(alertas.buscar("x", joao)).thenThrow(new RecursoNaoEncontradoException("Alerta", "x"));

        mvc.perform(get("/api/alertas/x").header("X-Morador-Token", "tk-joao"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Alerta não encontrado(a): x"));
    }

    @Test
    void checkinResponde200EValidaASituacao() throws Exception {
        when(alertas.checkin("a1", SituacaoCheckin.PRECISA_AJUDA, joao))
                .thenReturn(new Checkin("a1", "joao", SituacaoCheckin.PRECISA_AJUDA, Fixtures.AGORA));

        mvc.perform(checkin("{\"situacao\": \"PRECISA_AJUDA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PRECISA_AJUDA"))
                .andExpect(jsonPath("$.moradorId").value("joao"));
        mvc.perform(checkin("{}")).andExpect(status().isBadRequest());
        mvc.perform(checkin("{\"situacao\": \"TALVEZ\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void checkinEmAlertaEncerradoResponde409() throws Exception {
        when(alertas.checkin("a1", SituacaoCheckin.SEGURO, joao))
                .thenThrow(new ConflitoException("Este alerta já foi encerrado."));

        mvc.perform(checkin("{\"situacao\": \"SEGURO\"}")).andExpect(status().isConflict());
    }

    @Test
    void confirmarDuasVezesResponde409() throws Exception {
        when(alertas.confirmar("a1", joao)).thenThrow(new ConflitoException("Você já confirmou este alerta."));

        mvc.perform(post("/api/alertas/a1/confirmacoes").header("X-Morador-Token", "tk-joao"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Você já confirmou este alerta."));
    }

    @Test
    void painelResponde200() throws Exception {
        PainelAlerta.Entrada ana = new PainelAlerta.Entrada("ana", "Ana Souza", "302", null, true, "Cadeirante", null,
                null);
        when(painel.montar("a1", joao)).thenReturn(new PainelAlerta(
                AlertaResumo.de(Fixtures.alerta("a1", joao, TipoAlerta.GAS)), new PainelAlerta.Totais(3, 0, 1, 2),
                List.of(), List.of(ana), List.of()));

        mvc.perform(get("/api/alertas/a1/painel").header("X-Morador-Token", "tk-joao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totais.semResposta").value(1))
                .andExpect(jsonPath("$.semResposta[0].vulneravel").value(true))
                .andExpect(jsonPath("$.semResposta[0].telefone").doesNotExist());
    }

    @Test
    void encerramentoPeloSindicoResponde200ComPenalidade() throws Exception {
        Alerta encerrado = Fixtures.alerta("a1", joao, TipoAlerta.ELEVADOR);
        encerrado.setStatus(StatusAlerta.ENCERRADO);
        encerrado.setEncerramento(new Encerramento(MotivoEncerramento.FALSO_MA_FE, null, "carla", "Carla Mendes",
                Penalidade.SUSPENSAO, Fixtures.AGORA));
        when(alertas.encerrar("a1", new EncerramentoRequest(MotivoEncerramento.FALSO_MA_FE, null), carla))
                .thenReturn(encerrado);

        mvc.perform(encerramento("tk-carla", "{\"motivo\": \"FALSO_MA_FE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCERRADO"))
                .andExpect(jsonPath("$.encerramento.penalidadeAplicada").value("SUSPENSAO"));
    }

    @Test
    void encerramentoInvalidoNaoAutorizadoOuRepetido() throws Exception {
        when(alertas.encerrar("a1", new EncerramentoRequest(MotivoEncerramento.REAL, null), joao))
                .thenThrow(new AcessoNegadoException("Somente o síndico pode encerrar alertas."));
        when(alertas.encerrar("a1", new EncerramentoRequest(MotivoEncerramento.REAL, null), carla))
                .thenThrow(new ConflitoException("Este alerta já foi encerrado."));

        mvc.perform(encerramento("tk-carla", "{}")).andExpect(status().isBadRequest());
        mvc.perform(encerramento("tk-joao", "{\"motivo\": \"REAL\"}")).andExpect(status().isForbidden());
        mvc.perform(encerramento("tk-carla", "{\"motivo\": \"REAL\"}")).andExpect(status().isConflict());
    }

    private static MockHttpServletRequestBuilder disparo(String token, String corpo) {
        MockHttpServletRequestBuilder requisicao = post("/api/predios/{id}/alertas", PREDIO)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
        return token == null ? requisicao : requisicao.header("X-Morador-Token", token);
    }

    private static MockHttpServletRequestBuilder checkin(String corpo) {
        return put("/api/alertas/a1/checkin").header("X-Morador-Token", "tk-joao")
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static MockHttpServletRequestBuilder encerramento(String token, String corpo) {
        return post("/api/alertas/a1/encerramento").header("X-Morador-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }
}
