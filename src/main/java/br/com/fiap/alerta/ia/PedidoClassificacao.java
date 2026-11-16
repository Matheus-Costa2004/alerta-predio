package br.com.fiap.alerta.ia;

import br.com.fiap.alerta.model.TipoAlerta;

/** O que a IA recebe: o tipo escolhido pelo morador, a descrição e a foto (URL ou data URL), ambas opcionais. */
public record PedidoClassificacao(TipoAlerta tipoInformado, String descricao, String fotoUrl) {
}
