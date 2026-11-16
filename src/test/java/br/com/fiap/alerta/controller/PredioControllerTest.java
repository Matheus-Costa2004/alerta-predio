package br.com.fiap.alerta.controller;

import static br.com.fiap.alerta.Fixtures.PREDIO;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.auth.AutenticacaoService;
import br.com.fiap.alerta.dto.AcessoResponse;
import br.com.fiap.alerta.dto.EntrarPredioRequest;
import br.com.fiap.alerta.dto.NovoPredioRequest;
import br.com.fiap.alerta.dto.PredioResponse;
import br.com.fiap.alerta.dto.SessaoResponse;
import br.com.fiap.alerta.exception.AcessoNegadoException;
import br.com.fiap.alerta.exception.RecursoNaoEncontradoException;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.service.PredioService;

@WebMvcTest({ PredioController.class, MoradorController.class })
class PredioControllerTest {

    private static final String SINDICO = """
            {"nome": "Carla Mendes", "apartamento": "101", "telefone": "(11) 98888-1001"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PredioService service;
    @MockitoBean
    private AutenticacaoService autenticacao;

    private final Morador carla = Fixtures.sindica();
    private final Morador joao = Fixtures.joao();
    private final PredioResponse predio = new PredioResponse(PREDIO, "Residencial Acácias", "Rua A, 1", "ACACIA",
            "carla", Fixtures.AGORA);

    @BeforeEach
    void tokens() {
        carla.setTokenHash("hash-que-nao-pode-vazar");
        carla.setInscricoesPush(List.of(Fixtures.inscricao("carla")));
        when(autenticacao.autenticar("tk-carla")).thenReturn(carla);
        when(autenticacao.autenticar("tk-joao")).thenReturn(joao);
        when(autenticacao.autenticar("tk-pedro")).thenReturn(Fixtures.deOutroPredio());
    }

    @Test
    void criarPredioResponde201ComTokenELocationSemVazarOHash() throws Exception {
        when(service.criar(any(NovoPredioRequest.class))).thenReturn(new AcessoResponse(predio, carla, "token-novo"));

        mvc.perform(post("/api/predios").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome": "Residencial Acácias", "endereco": "Rua A, 1", "sindico": %s}""".formatted(SINDICO)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/predios/" + PREDIO)))
                .andExpect(jsonPath("$.token").value("token-novo"))
                .andExpect(jsonPath("$.predio.codigoConvite").value("ACACIA"))
                .andExpect(jsonPath("$.morador.papel").value("SINDICO"))
                .andExpect(jsonPath("$.morador.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.morador.inscricoesPush").doesNotExist());
    }

    @Test
    void criarPredioInvalidoResponde400() throws Exception {
        mvc.perform(post("/api/predios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \"Residencial\", \"endereco\": \"Rua A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.sindico").exists());
        mvc.perform(post("/api/predios").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome": "Residencial", "endereco": "Rua A",
                 "sindico": {"nome": "Carla", "apartamento": "101", "telefone": "abc"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['sindico.telefone']").value("telefone inválido"));
        mvc.perform(post("/api/predios").contentType(MediaType.APPLICATION_JSON).content("{nome"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corpo da requisição inválido"));
        verify(service, never()).criar(any());
    }

    @Test
    void necessidadeEspecialSemConsentimentoResponde400() throws Exception {
        mvc.perform(post("/api/predios/entrar").contentType(MediaType.APPLICATION_JSON).content("""
                {"codigoConvite": "ACACIA", "morador": {"nome": "Ana", "apartamento": "302",
                 "telefone": "(11) 98888-3002", "necessidadeEspecial": "Cadeirante",
                 "consentimentoNecessidadeEspecial": false}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['morador.consentimentoValido']").exists());
        verify(service, never()).entrar(any());
    }

    @Test
    void entrarComCodigoValidoResponde201EInexistente404() throws Exception {
        String corpo = """
                {"codigoConvite": "%s", "morador": {"nome": "Ana", "apartamento": "302",
                 "telefone": "(11) 98888-3002", "necessidadeEspecial": "Cadeirante",
                 "consentimentoNecessidadeEspecial": true}}""";
        when(service.entrar(any(EntrarPredioRequest.class))).thenAnswer(i -> {
            EntrarPredioRequest pedido = i.getArgument(0);
            if (!pedido.codigoConvite().equals("ACACIA")) {
                throw new RecursoNaoEncontradoException("Prédio com o código de convite", pedido.codigoConvite());
            }
            return new AcessoResponse(PredioResponse.de(new br.com.fiap.alerta.model.Predio(), false), Fixtures.ana(),
                    "token-ana");
        });

        mvc.perform(post("/api/predios/entrar").contentType(MediaType.APPLICATION_JSON)
                .content(corpo.formatted("ACACIA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("token-ana"))
                .andExpect(jsonPath("$.morador.necessidadeEspecial.descricao").value("Cadeirante"));
        mvc.perform(post("/api/predios/entrar").contentType(MediaType.APPLICATION_JSON)
                .content(corpo.formatted("ERRADO")))
                .andExpect(status().isNotFound());
    }

    @Test
    void areaDoPredioExigeTokenEMembroEOSindicoParaListas() throws Exception {
        when(service.buscar(PREDIO, joao)).thenReturn(predio);
        when(service.moradores(PREDIO, joao))
                .thenThrow(new AcessoNegadoException("Somente o síndico pode ver a lista de moradores."));
        when(service.moradores(PREDIO, carla)).thenReturn(List.of(carla, joao));

        mvc.perform(get("/api/predios/{id}", PREDIO)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/predios/{id}", PREDIO).header("X-Morador-Token", "tk-joao")).andExpect(status().isOk());
        mvc.perform(get("/api/predios/{id}/moradores", PREDIO).header("X-Morador-Token", "tk-joao"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/predios/{id}/moradores", PREDIO).header("X-Morador-Token", "tk-carla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Carla Mendes"))
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist());
    }

    @Test
    void auditoriaSoParaSindico() throws Exception {
        when(service.auditoria(PREDIO, joao))
                .thenThrow(new AcessoNegadoException("Somente o síndico pode ver a auditoria do prédio."));
        when(service.auditoria(PREDIO, carla)).thenReturn(List.of());

        mvc.perform(get("/api/predios/{id}/auditoria", PREDIO).header("X-Morador-Token", "tk-joao"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/predios/{id}/auditoria", PREDIO).header("X-Morador-Token", "tk-carla"))
                .andExpect(status().isOk());
    }

    @Test
    void euDevolveOMoradorEOPredio() throws Exception {
        when(service.sessao(carla)).thenReturn(new SessaoResponse(predio, carla));

        mvc.perform(get("/api/moradores/eu").header("X-Morador-Token", "tk-carla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.morador.nome").value("Carla Mendes"))
                .andExpect(jsonPath("$.morador.status").value("ATIVO"))
                .andExpect(jsonPath("$.morador.sindico").doesNotExist())
                .andExpect(jsonPath("$.predio.nome").value("Residencial Acácias"));
    }
}
