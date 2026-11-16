package br.com.fiap.alerta.dto;

import br.com.fiap.alerta.model.TipoAlerta;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DisparoAlertaRequest(
        @NotNull @Schema(example = "INCENDIO") TipoAlerta tipo,
        @Size(max = 500) @Schema(example = "Fumaça saindo do apartamento 304") String descricao,
        @Size(max = 2_800_000, message = "foto muito grande (máximo de cerca de 2 MB)")
        @Pattern(regexp = "^(https?://\\S+|data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+)$",
                message = "use uma URL http(s) ou uma data URL data:image/(jpeg|png|webp);base64,...")
        @Schema(description = "Opcional: URL da foto ou data URL (o PWA envia a foto já reduzida)")
        String fotoUrl) {
}
