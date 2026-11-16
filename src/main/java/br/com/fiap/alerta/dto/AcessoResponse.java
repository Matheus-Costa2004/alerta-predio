package br.com.fiap.alerta.dto;

import br.com.fiap.alerta.model.Morador;
import io.swagger.v3.oas.annotations.media.Schema;

public record AcessoResponse(PredioResponse predio, Morador morador,
        @Schema(description = "Envie em X-Morador-Token. Só é mostrado agora; o servidor guarda apenas o hash")
        String token) {
}
