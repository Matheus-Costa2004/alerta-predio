package br.com.fiap.alerta.exception;

/** O morador está identificado, mas não pode fazer esta ação (HTTP 403). */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
