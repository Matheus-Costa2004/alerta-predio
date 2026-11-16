package br.com.fiap.alerta.model;

/** Suspenso ainda recebe alertas e faz check-in; só o botão de disparo fica bloqueado. */
public enum StatusMorador {
    ATIVO,
    ADVERTIDO,
    SUSPENSO,
    REMOVIDO
}
