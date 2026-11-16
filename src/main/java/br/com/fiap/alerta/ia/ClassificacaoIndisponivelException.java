package br.com.fiap.alerta.ia;

/** A IA não pôde classificar (sem chave, timeout, erro HTTP, resposta inválida). A mensagem vai para o alerta. */
public class ClassificacaoIndisponivelException extends RuntimeException {

    public ClassificacaoIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public ClassificacaoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
