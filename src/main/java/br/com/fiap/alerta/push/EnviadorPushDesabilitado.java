package br.com.fiap.alerta.push;

import br.com.fiap.alerta.model.InscricaoPush;

/** Sem chaves VAPID: nada é enviado e o PWA continua avisando por polling. */
public class EnviadorPushDesabilitado implements EnviadorPush {

    @Override
    public ResultadoEnvio enviar(InscricaoPush inscricao, byte[] payload) {
        return ResultadoEnvio.FALHA;
    }

    @Override
    public boolean habilitado() {
        return false;
    }

    @Override
    public String chavePublica() {
        return null;
    }
}
