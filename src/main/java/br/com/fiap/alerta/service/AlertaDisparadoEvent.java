package br.com.fiap.alerta.service;

/** Publicado depois que o alerta já está gravado; a triagem da IA e o push reagem em segundo plano. */
public record AlertaDisparadoEvent(String alertaId) {
}
