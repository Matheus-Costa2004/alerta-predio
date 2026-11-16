package br.com.fiap.alerta.ia;

/**
 * Triagem do alerta por IA a partir da foto e da descrição. Pode lançar qualquer exceção: quem chama
 * (TriagemService) transforma a falha em classificação "indisponível" sem afetar o alerta.
 */
public interface ClassificadorAlerta {

    ResultadoClassificacao classificar(PedidoClassificacao pedido);
}
