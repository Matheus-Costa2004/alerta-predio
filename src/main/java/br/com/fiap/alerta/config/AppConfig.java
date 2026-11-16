package br.com.fiap.alerta.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * A triagem da IA e o envio de push rodam em segundo plano (@Async), depois que o alerta já foi gravado.
 * Fica fora da classe principal para não afetar os testes de fatia (@WebMvcTest).
 */
@Configuration
@EnableAsync
public class AppConfig {

    /** Relógio injetável: os testes fixam a data para validar a suspensão de 30 dias. */
    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
