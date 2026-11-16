package br.com.fiap.alerta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mesmo formato de PushSubscription.toJSON() no navegador. */
public record InscricaoPushRequest(
        @NotBlank @Size(max = 1000) @Pattern(regexp = "^https://\\S+$", message = "endpoint deve ser https")
        @Schema(example = "https://fcm.googleapis.com/fcm/send/abc123") String endpoint,
        @NotNull @Valid Chaves keys) {

    public record Chaves(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }
}
