package br.com.fiap.alerta.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.alerta.config.IaProperties;

class IaConfigTest {

    private final IaConfig config = new IaConfig();
    private final IaProperties.Gemini gemini = new IaProperties.Gemini("", "gemini-3.5-flash-lite",
            "https://generativelanguage.googleapis.com/v1beta", Duration.ofSeconds(15));

    @Test
    void provedorFakeUsaOClassificadorLocal() {
        assertThat(config.classificadorAlerta(new IaProperties("fake", gemini), RestClient.builder(),
                new ObjectMapper())).isInstanceOf(ClassificadorAlertaFake.class);
    }

    @Test
    void provedorGeminiUsaAApiMesmoSemChave() {
        assertThat(config.classificadorAlerta(new IaProperties("GEMINI", gemini), RestClient.builder(),
                new ObjectMapper())).isInstanceOf(ClassificadorGemini.class);
    }
}
