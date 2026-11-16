package br.com.fiap.alerta.model;

import java.time.Instant;

/** Resultado da triagem da IA. É só informativo: nunca altera o status do alerta. */
public record ClassificacaoIa(StatusClassificacao status, TipoAlerta tipoProvavel, Gravidade gravidade,
        Double confianca, String justificativa, String modelo, String erro, Instant dataHora) {

    public static ClassificacaoIa pendente() {
        return new ClassificacaoIa(StatusClassificacao.PENDENTE, null, null, null, null, null, null, null);
    }

    public static ClassificacaoIa concluida(TipoAlerta tipoProvavel, Gravidade gravidade, double confianca,
            String justificativa, String modelo, Instant dataHora) {
        return new ClassificacaoIa(StatusClassificacao.CONCLUIDA, tipoProvavel, gravidade, confianca, justificativa,
                modelo, null, dataHora);
    }

    public static ClassificacaoIa indisponivel(String erro, Instant dataHora) {
        return new ClassificacaoIa(StatusClassificacao.INDISPONIVEL, null, null, null, null, null, erro, dataHora);
    }
}
