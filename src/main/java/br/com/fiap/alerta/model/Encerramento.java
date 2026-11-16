package br.com.fiap.alerta.model;

import java.time.Instant;

public record Encerramento(MotivoEncerramento motivo, String observacao, String porMoradorId, String porNome,
        Penalidade penalidadeAplicada, Instant dataHora) {
}
