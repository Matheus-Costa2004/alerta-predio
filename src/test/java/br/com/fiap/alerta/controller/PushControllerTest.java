package br.com.fiap.alerta.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.auth.AutenticacaoService;
import br.com.fiap.alerta.dto.ChavePublicaResponse;
import br.com.fiap.alerta.dto.InscricaoPushRequest;
import br.com.fiap.alerta.exception.RequisicaoInvalidaException;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.push.InscricaoPushService;

@WebMvcTest(PushController.class)
class PushControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InscricaoPushService service;
    @MockitoBean
    private AutenticacaoService autenticacao;

    private final Morador ana = Fixtures.ana();

    @BeforeEach
    void tokens() {
        when(autenticacao.autenticar("tk-ana")).thenReturn(ana);
    }

    @Test
    void chavePublicaEhPublica() throws Exception {
        when(service.chavePublica()).thenReturn(new ChavePublicaResponse(true, "BPublica"));

        mvc.perform(get("/api/push/chave-publica"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.chavePublica").value("BPublica"));
    }

    @Test
    void inscricaoNoFormatoDoNavegadorResponde204() throws Exception {
        mvc.perform(inscricao("""
                {"endpoint": "https://fcm.googleapis.com/fcm/send/abc", "expirationTime": null,
                 "keys": {"p256dh": "BPub", "auth": "segredo"}}"""))
                .andExpect(status().isNoContent());

        verify(service).inscrever(new InscricaoPushRequest("https://fcm.googleapis.com/fcm/send/abc",
                new InscricaoPushRequest.Chaves("BPub", "segredo")), ana);
    }

    @Test
    void inscricaoInvalidaResponde400() throws Exception {
        mvc.perform(inscricao("{\"endpoint\": \"http://fcm.googleapis.com/x\", \"keys\": {\"p256dh\": \"a\", \"auth\": \"b\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.endpoint").value("endpoint deve ser https"));
        mvc.perform(inscricao("{\"endpoint\": \"https://fcm.googleapis.com/x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.keys").exists());
        verify(service, never()).inscrever(any(), any());
    }

    @Test
    void pushServiceDesconhecidoResponde400() throws Exception {
        doThrow(new RequisicaoInvalidaException("Serviço de push não reconhecido: atacante.net"))
                .when(service).inscrever(any(), any());

        mvc.perform(inscricao("{\"endpoint\": \"https://atacante.net/x\", \"keys\": {\"p256dh\": \"a\", \"auth\": \"b\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    @Test
    void cancelarInscricaoResponde204EExigeOEndpoint() throws Exception {
        mvc.perform(delete("/api/push/inscricoes").param("endpoint", "https://fcm.googleapis.com/fcm/send/abc")
                .header("X-Morador-Token", "tk-ana"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/push/inscricoes").header("X-Morador-Token", "tk-ana"))
                .andExpect(status().isBadRequest());

        verify(service).cancelar("https://fcm.googleapis.com/fcm/send/abc", ana);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder inscricao(String corpo) {
        return post("/api/push/inscricoes").header("X-Morador-Token", "tk-ana")
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }
}
