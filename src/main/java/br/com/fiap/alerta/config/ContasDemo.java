package br.com.fiap.alerta.config;

import java.util.List;

import br.com.fiap.alerta.dto.ContaDemo;

/** Tokens fixos do prédio de demonstração (o banco guarda só o hash, como para qualquer morador). */
public final class ContasDemo {

    public static final String TOKEN_SINDICA = "demo-sindica";
    public static final String TOKEN_ANA = "demo-ana";
    public static final String TOKEN_JOAO = "demo-joao";
    public static final String TOKEN_BEATRIZ = "demo-beatriz";
    public static final String TOKEN_RAFAEL = "demo-rafael";

    public static final List<ContaDemo> CONTAS = List.of(
            new ContaDemo("Carla Mendes", "101", "SINDICO", "Síndica: encerra alertas e vê o painel completo",
                    TOKEN_SINDICA),
            new ContaDemo("Ana Souza", "302", "MORADOR", "Moradora cadeirante (necessidade especial com consentimento)",
                    TOKEN_ANA),
            new ContaDemo("João Lima", "204", "MORADOR", "Morador que disparou o alerta de alagamento", TOKEN_JOAO));

    private ContasDemo() {
    }
}
