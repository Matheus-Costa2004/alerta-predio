package br.com.fiap.alerta.dto;

import java.time.Instant;

import br.com.fiap.alerta.model.Predio;

/** O código de convite só vai para o síndico, que é quem decide quem entra. */
public record PredioResponse(String id, String nome, String endereco, String codigoConvite, String sindicoId,
        Instant dataCriacao) {

    public static PredioResponse de(Predio predio, boolean incluirCodigoConvite) {
        return new PredioResponse(predio.getId(), predio.getNome(), predio.getEndereco(),
                incluirCodigoConvite ? predio.getCodigoConvite() : null, predio.getSindicoId(),
                predio.getDataCriacao());
    }
}
