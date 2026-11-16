package br.com.fiap.alerta.dto;

import br.com.fiap.alerta.model.Morador;

public record SessaoResponse(PredioResponse predio, Morador morador) {
}
