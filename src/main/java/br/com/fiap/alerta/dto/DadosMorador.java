package br.com.fiap.alerta.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DadosMorador(
        @NotBlank @Size(max = 80) @Schema(example = "Ana Souza") String nome,
        @NotBlank @Size(max = 10) @Schema(example = "302") String apartamento,
        @NotBlank @Pattern(regexp = "[0-9()+\\-\\s]{8,20}", message = "telefone inválido")
        @Schema(example = "(11) 98888-3002") String telefone,
        @Size(max = 200)
        @Schema(description = "Opcional. Ex.: cadeirante, idoso que mora sozinho. Aparece no painel dos alertas",
                example = "Cadeirante: precisa de ajuda para descer as escadas")
        String necessidadeEspecial,
        @Schema(description = "LGPD: obrigatório (true) quando necessidadeEspecial é informada")
        Boolean consentimentoNecessidadeEspecial) {

    public boolean temNecessidadeEspecial() {
        return necessidadeEspecial != null && !necessidadeEspecial.isBlank();
    }

    /** Dado sensível só entra com consentimento explícito. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "informar necessidade especial exige consentimentoNecessidadeEspecial = true")
    public boolean isConsentimentoValido() {
        return !temNecessidadeEspecial() || Boolean.TRUE.equals(consentimentoNecessidadeEspecial);
    }
}
