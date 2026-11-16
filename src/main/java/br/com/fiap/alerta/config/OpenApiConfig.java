package br.com.fiap.alerta.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.fiap.alerta.auth.MoradorAutenticado;
import br.com.fiap.alerta.auth.MoradorAutenticadoResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "morador";

    static {
        // O morador vem do header X-Morador-Token, não de um parâmetro da requisição
        SpringDocUtils.getConfig().addAnnotationsToIgnore(MoradorAutenticado.class);
    }

    @Bean
    OpenAPI alertaOpenApi(@Value("${alerta.versao}") String versao, @Value("${alerta.ambiente}") String ambiente) {
        return new OpenAPI()
                .info(new Info()
                        .title("AlertaPrédio API")
                        .version(versao)
                        .description("Alertas de emergência entre moradores de um mesmo prédio (FIAP CitizenTech). "
                                + "Crie um prédio ou entre com o código de convite, copie o token devolvido e use o "
                                + "botão Authorize. Ambiente: " + ambiente))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(MoradorAutenticadoResolver.HEADER)
                        .description("Token devolvido ao criar o prédio ou ao entrar com o código de convite")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
