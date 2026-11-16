package br.com.fiap.alerta.exception;

/** Dado bem formado, mas recusado por uma regra da aplicação (HTTP 400). */
public class RequisicaoInvalidaException extends RuntimeException {

    public RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
