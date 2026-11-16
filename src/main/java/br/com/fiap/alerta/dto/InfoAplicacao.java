package br.com.fiap.alerta.dto;

import java.time.Instant;

public record InfoAplicacao(String aplicacao, String versao, String ambiente, Instant timestamp) {
}
