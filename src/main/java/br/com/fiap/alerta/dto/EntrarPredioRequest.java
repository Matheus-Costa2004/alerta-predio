package br.com.fiap.alerta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EntrarPredioRequest(
        @NotBlank @Size(max = 12) @Schema(example = "ACACIA") String codigoConvite,
        @NotNull @Valid DadosMorador morador) {
}
