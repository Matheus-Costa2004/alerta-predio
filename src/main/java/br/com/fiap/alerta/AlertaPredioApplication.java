package br.com.fiap.alerta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlertaPredioApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertaPredioApplication.class, args);
    }
}
