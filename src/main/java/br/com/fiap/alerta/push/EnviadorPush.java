package br.com.fiap.alerta.push;

import br.com.fiap.alerta.model.InscricaoPush;

/** Entrega uma notificação a um navegador inscrito. Nunca lança exceção: devolve o resultado. */
public interface EnviadorPush {

    ResultadoEnvio enviar(InscricaoPush inscricao, byte[] payload);

    boolean habilitado();

    /** Chave pública VAPID em base64url, ou null quando o push está desligado. */
    String chavePublica();

    enum ResultadoEnvio {
        ENVIADO,
        /** 404/410 do push service: a inscrição deve ser apagada. */
        INSCRICAO_EXPIRADA,
        FALHA
    }
}
