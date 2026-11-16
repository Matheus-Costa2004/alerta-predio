package br.com.fiap.alerta.auth;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AutenticacaoService autenticacao;

    public WebConfig(AutenticacaoService autenticacao) {
        this.autenticacao = autenticacao;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new MoradorAutenticadoResolver(autenticacao));
    }
}
