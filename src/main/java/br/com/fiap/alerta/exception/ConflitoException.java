package br.com.fiap.alerta.exception;

/** Operação válida em formato, mas incompatível com o estado atual do recurso (HTTP 409). */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
