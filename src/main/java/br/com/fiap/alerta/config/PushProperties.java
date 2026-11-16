package br.com.fiap.alerta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** alerta.push.*: chaves VAPID (base64url) do servidor de aplicação para o Web Push. */
@ConfigurationProperties("alerta.push")
public record PushProperties(String vapidChavePublica, String vapidChavePrivada,
        @DefaultValue("mailto:alerta-predio@example.com") String vapidAssunto) {

    public boolean configurado() {
        return vapidChavePublica != null && !vapidChavePublica.isBlank()
                && vapidChavePrivada != null && !vapidChavePrivada.isBlank();
    }
}
