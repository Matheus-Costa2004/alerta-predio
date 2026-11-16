package br.com.fiap.alerta.model;

/** Dado sensível (LGPD): só é gravado junto com o consentimento do morador. */
public record NecessidadeEspecial(String descricao, Consentimento consentimento) {
}
