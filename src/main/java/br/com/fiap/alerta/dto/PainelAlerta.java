package br.com.fiap.alerta.dto;

import java.time.Instant;
import java.util.List;

import br.com.fiap.alerta.model.SituacaoCheckin;

/** Quem está seguro, quem precisa de ajuda e quem não respondeu; em cada lista, vulneráveis primeiro. */
public record PainelAlerta(AlertaResumo alerta, Totais totais, List<Entrada> precisamDeAjuda,
        List<Entrada> semResposta, List<Entrada> seguros) {

    public record Totais(int moradores, int precisamDeAjuda, int semResposta, int seguros) {
    }

    /** O telefone só é preenchido para o síndico (minimização de dados, LGPD). */
    public record Entrada(String moradorId, String nome, String apartamento, String telefone, boolean vulneravel,
            String necessidadeEspecial, SituacaoCheckin situacao, Instant respondidoEm) {
    }
}
