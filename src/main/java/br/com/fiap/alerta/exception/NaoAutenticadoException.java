package br.com.fiap.alerta.exception;

/** Header X-Morador-Token ausente ou inválido (HTTP 401). */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
