package br.com.fiap.alerta.model;

import java.time.Instant;

/** Outro morador confirmando que a emergência é real. */
public record Confirmacao(String moradorId, String nome, String apartamento, Instant dataHora) {

    public static Confirmacao de(Morador morador, Instant dataHora) {
        return new Confirmacao(morador.getId(), morador.getNome(), morador.getApartamento(), dataHora);
    }
}
