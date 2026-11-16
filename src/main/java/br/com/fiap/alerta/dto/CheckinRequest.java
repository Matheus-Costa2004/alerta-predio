package br.com.fiap.alerta.dto;

import br.com.fiap.alerta.model.SituacaoCheckin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CheckinRequest(@NotNull @Schema(example = "SEGURO") SituacaoCheckin situacao) {
}
