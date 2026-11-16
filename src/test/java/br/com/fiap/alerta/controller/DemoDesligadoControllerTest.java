package br.com.fiap.alerta.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.fiap.alerta.auth.AutenticacaoService;

/** Sem SEED_ENABLED os tokens de demonstração não são expostos. */
@WebMvcTest(controllers = DemoController.class, properties = "alerta.seed.enabled=false")
class DemoDesligadoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AutenticacaoService autenticacao;

    @Test
    void semSeedOEndpointNaoExiste() throws Exception {
        mvc.perform(get("/api/demo/contas")).andExpect(status().isNotFound());
    }
}
