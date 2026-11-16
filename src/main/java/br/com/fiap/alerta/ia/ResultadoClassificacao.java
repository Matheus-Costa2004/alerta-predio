package br.com.fiap.alerta.ia;

import br.com.fiap.alerta.model.Gravidade;
import br.com.fiap.alerta.model.TipoAlerta;

/** Tipo provável, gravidade e confiança (0 a 1) estimados pela IA. */
public record ResultadoClassificacao(TipoAlerta tipoProvavel, Gravidade gravidade, double confianca,
        String justificativa, String modelo) {
}
