package br.com.fiap.alerta.dto;

import java.time.Instant;

import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Autor;
import br.com.fiap.alerta.model.ClassificacaoIa;
import br.com.fiap.alerta.model.Encerramento;
import br.com.fiap.alerta.model.StatusAlerta;
import br.com.fiap.alerta.model.TipoAlerta;

/** Alerta sem a foto: é o que as listas e o painel consultam a cada poucos segundos. */
public record AlertaResumo(String id, String predioId, TipoAlerta tipo, StatusAlerta status, Autor autor,
        String descricao, boolean temFoto, int totalConfirmacoes, ClassificacaoIa classificacao,
        Encerramento encerramento, Integer notificados, Instant dataHora) {

    public static AlertaResumo de(Alerta alerta) {
        return new AlertaResumo(alerta.getId(), alerta.getPredioId(), alerta.getTipo(), alerta.getStatus(),
                alerta.getAutor(), alerta.getDescricao(), alerta.getFotoUrl() != null,
                alerta.getConfirmacoes().size(), alerta.getClassificacao(), alerta.getEncerramento(),
                alerta.getNotificados(), alerta.getDataHora());
    }
}
