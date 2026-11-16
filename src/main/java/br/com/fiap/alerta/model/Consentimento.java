package br.com.fiap.alerta.model;

import java.time.Instant;

/** LGPD: registro de que o titular aceitou o uso do dado sensível, quando e em qual versão do termo. */
public record Consentimento(boolean aceito, Instant dataHora, String versaoTermo) {
}
