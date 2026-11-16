package br.com.fiap.alerta.dto;

import br.com.fiap.alerta.model.MotivoEncerramento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EncerramentoRequest(
        @NotNull @Schema(description = "Só FALSO_MA_FE gera advertência para o autor", example = "REAL")
        MotivoEncerramento motivo,
        @Size(max = 300) @Schema(example = "Bombeiros controlaram o foco; sem feridos") String observacao) {
}
