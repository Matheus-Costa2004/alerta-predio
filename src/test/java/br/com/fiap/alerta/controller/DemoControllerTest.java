package br.com.fiap.alerta.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.fiap.alerta.auth.AutenticacaoService;

@WebMvcTest(controllers = DemoController.class, properties = "alerta.seed.enabled=true")
class DemoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AutenticacaoService autenticacao;

    @Test
    void comSeedLigadoListaAsContasDeDemonstracao() throws Exception {
        mvc.perform(get("/api/demo/contas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].papel").value("SINDICO"))
                .andExpect(jsonPath("$[0].token").value("demo-sindica"))
                .andExpect(jsonPath("$[1].nome").value("Ana Souza"));
    }
}
