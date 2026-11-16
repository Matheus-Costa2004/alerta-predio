package br.com.fiap.alerta.exception;

public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String recurso, String id) {
        super(recurso + " não encontrado(a): " + id);
    }
}
