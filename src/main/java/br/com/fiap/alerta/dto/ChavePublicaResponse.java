package br.com.fiap.alerta.dto;

/** Chave pública VAPID para o navegador assinar a inscrição; habilitado=false quando o push não está configurado. */
public record ChavePublicaResponse(boolean habilitado, String chavePublica) {
}
