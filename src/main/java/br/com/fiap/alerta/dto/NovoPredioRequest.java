package br.com.fiap.alerta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NovoPredioRequest(
        @NotBlank @Size(max = 120) @Schema(example = "Residencial Jardim das Acácias") String nome,
        @NotBlank @Size(max = 200) @Schema(example = "Rua das Acácias, 120 - São Paulo/SP") String endereco,
        @NotNull @Valid @Schema(description = "Quem cria o prédio vira o síndico") DadosMorador sindico) {
}
