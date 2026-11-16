package br.com.fiap.alerta.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** alerta.ia.*: qual classificador usar e como falar com o Gemini. */
@ConfigurationProperties("alerta.ia")
public record IaProperties(@DefaultValue("fake") String provedor, @DefaultValue Gemini gemini) {

    public record Gemini(String apiKey,
            @DefaultValue("gemini-3.5-flash-lite") String modelo,
            @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String url,
            @DefaultValue("15s") Duration timeout) {

        public boolean temChave() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
