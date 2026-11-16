package br.com.fiap.alerta.auth;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import br.com.fiap.alerta.exception.NaoAutenticadoException;
import br.com.fiap.alerta.model.Morador;

/** Resolve parâmetros {@code @MoradorAutenticado Morador} a partir do header X-Morador-Token. */
public class MoradorAutenticadoResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-Morador-Token";

    private final AutenticacaoService autenticacao;

    public MoradorAutenticadoResolver(AutenticacaoService autenticacao) {
        this.autenticacao = autenticacao;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(MoradorAutenticado.class)
                && Morador.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Morador resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            throw new NaoAutenticadoException("Envie o token do morador no header " + HEADER + ".");
        }
        return autenticacao.autenticar(token.trim());
    }
}
