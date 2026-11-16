package br.com.fiap.alerta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import br.com.fiap.alerta.config.ContasDemo;
import br.com.fiap.alerta.config.SeedDados;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.repository.CheckinRepository;
import br.com.fiap.alerta.repository.EventoAuditoriaRepository;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.repository.PredioRepository;

/** O seed roda na inicialização, num database novo, não duplica dados e as contas de demonstração funcionam. */
@SpringBootTest(properties = "alerta.seed.enabled=true")
@AutoConfigureMockMvc
class SeedDadosIT {

    private static final String DATABASE = "alerta_it_seed_" + UUID.randomUUID().toString().substring(0, 8);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @Autowired
    private SeedDados seed;
    @Autowired
    private MockMvc mvc;
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

    @AfterAll
    static void apagarDatabase(@Autowired MongoTemplate mongo) {
        mongo.getDb().drop();
    }

    @Test
    void seedPopulaAsCincoCollectionsUmaVezSo() {
        assertContagens();

        seed.run();

        assertContagens();
    }

    @Test
    void contasDeDemonstracaoEntramNoPredio() throws Exception {
        mvc.perform(get("/api/demo/contas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/moradores/eu").header("X-Morador-Token", ContasDemo.TOKEN_ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.morador.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.morador.necessidadeEspecial.consentimento.aceito").value(true))
                .andExpect(jsonPath("$.predio.codigoConvite").doesNotExist());
        mvc.perform(get("/api/moradores/eu").header("X-Morador-Token", ContasDemo.TOKEN_SINDICA))
                .andExpect(jsonPath("$.morador.papel").value("SINDICO"))
                .andExpect(jsonPath("$.predio.codigoConvite").value(SeedDados.CODIGO_CONVITE));
    }

    @Test
    void painelDoAlertaEncerradoMostraQuemPrecisouDeAjuda() throws Exception {
        mvc.perform(get("/api/alertas/{id}/painel", SeedDados.ALERTA_ID)
                .header("X-Morador-Token", ContasDemo.TOKEN_SINDICA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerta.status").value("ENCERRADO"))
                .andExpect(jsonPath("$.alerta.encerramento.motivo").value("REAL"))
                .andExpect(jsonPath("$.totais.moradores").value(5))
                .andExpect(jsonPath("$.precisamDeAjuda[0].nome").value("Ana Souza"))
                .andExpect(jsonPath("$.semResposta[0].nome").value("Rafael Nunes"))
                .andExpect(jsonPath("$.totais.seguros").value(3));
    }

    private void assertContagens() {
        assertThat(predios.count()).isEqualTo(1);
        assertThat(moradores.count()).isEqualTo(5);
        assertThat(alertas.count()).isEqualTo(1);
        assertThat(checkins.count()).isEqualTo(4);
        assertThat(auditoria.count()).isEqualTo(5);
    }
}
