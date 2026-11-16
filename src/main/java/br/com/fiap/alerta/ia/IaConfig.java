package br.com.fiap.alerta.ia;

import java.net.http.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.alerta.config.IaProperties;

/** alerta.ia.provedor=gemini usa a API do Google; qualquer outro valor (padrão: fake) usa o classificador local. */
@Configuration
public class IaConfig {

    private static final Logger log = LoggerFactory.getLogger(IaConfig.class);

    @Bean
    ClassificadorAlerta classificadorAlerta(IaProperties propriedades, RestClient.Builder restClient,
            ObjectMapper json) {
        if (!"gemini".equalsIgnoreCase(propriedades.provedor())) {
            log.info("Triagem por IA: classificador fake (local, sem chamadas externas)");
            return new ClassificadorAlertaFake();
        }
        IaProperties.Gemini gemini = propriedades.gemini();
        if (!gemini.temChave()) {
            log.warn("Triagem por IA: GEMINI_API_KEY ausente; os alertas seguirão com classificação indisponível");
        } else {
            log.info("Triagem por IA: Gemini, modelo {}", gemini.modelo());
        }
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(gemini.timeout()).build());
        fabrica.setReadTimeout(gemini.timeout());
        return new ClassificadorGemini(restClient.requestFactory(fabrica).build(), gemini, json);
    }
}
