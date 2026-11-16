package br.com.fiap.alerta.model;

public enum StatusClassificacao {
    /** O alerta acabou de ser gravado; a triagem roda em segundo plano. */
    PENDENTE,
    CONCLUIDA,
    /** A IA falhou ou não está configurada; o alerta segue normalmente. */
    INDISPONIVEL
}
