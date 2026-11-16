package br.com.fiap.alerta.model;

import java.time.Instant;

/** PushSubscription do navegador: endpoint do push service e as chaves para criptografar o payload. */
public record InscricaoPush(String endpoint, String p256dh, String auth, Instant dataHora) {
}
