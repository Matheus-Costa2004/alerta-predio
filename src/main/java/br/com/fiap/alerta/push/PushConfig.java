package br.com.fiap.alerta.push;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.fiap.alerta.config.PushProperties;

@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /** Sem chaves (ou com chaves inválidas) o app sobe normalmente, só que sem push. */
    @Bean
    EnviadorPush enviadorPush(PushProperties propriedades, Clock relogio) {
        if (!propriedades.configurado()) {
            log.warn("Web Push desligado: defina VAPID_PUBLIC_KEY e VAPID_PRIVATE_KEY (java scripts/GerarChavesVapid.java)");
            return new EnviadorPushDesabilitado();
        }
        try {
            ChavesVapid chaves = ChavesVapid.de(propriedades.vapidChavePublica(), propriedades.vapidChavePrivada());
            log.info("Web Push habilitado (VAPID)");
            return new EnviadorWebPush(chaves, new Vapid(chaves, propriedades.vapidAssunto(), relogio),
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        } catch (IllegalArgumentException e) {
            log.error("Chaves VAPID inválidas ({}): Web Push desligado", e.getMessage());
            return new EnviadorPushDesabilitado();
        }
    }
}
