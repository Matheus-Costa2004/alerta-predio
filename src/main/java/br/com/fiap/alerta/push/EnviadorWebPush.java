package br.com.fiap.alerta.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.fiap.alerta.model.InscricaoPush;

/** Envia o push direto ao push service do navegador (FCM, Mozilla, Apple, Windows) com o JDK HttpClient. */
public class EnviadorWebPush implements EnviadorPush {

    private static final Logger log = LoggerFactory.getLogger(EnviadorWebPush.class);
    /** Emergência perde o sentido depois de 1 hora: o push service descarta se não entregar até lá. */
    private static final String TTL_SEGUNDOS = "3600";

    private final ChavesVapid chaves;
    private final Vapid vapid;
    private final HttpClient http;

    public EnviadorWebPush(ChavesVapid chaves, Vapid vapid, HttpClient http) {
        this.chaves = chaves;
        this.vapid = vapid;
        this.http = http;
    }

    @Override
    public ResultadoEnvio enviar(InscricaoPush inscricao, byte[] payload) {
        try {
            byte[] corpo = CriptografiaWebPush.criptografar(payload,
                    ChavesEc.decodificarBase64Url(inscricao.p256dh()), ChavesEc.decodificarBase64Url(inscricao.auth()));
            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(inscricao.endpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("TTL", TTL_SEGUNDOS)
                    .header("Urgency", "high")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", vapid.autorizacao(inscricao.endpoint()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                    .build();
            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
            int status = resposta.statusCode();
            if (status >= 200 && status < 300) {
                return ResultadoEnvio.ENVIADO;
            }
            if (status == 404 || status == 410) {
                return ResultadoEnvio.INSCRICAO_EXPIRADA;
            }
            log.warn("Push recusado por {}: HTTP {} {}", Vapid.audiencia(inscricao.endpoint()), status,
                    resposta.body().length() > 200 ? resposta.body().substring(0, 200) : resposta.body());
            return ResultadoEnvio.FALHA;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultadoEnvio.FALHA;
        } catch (Exception e) {
            log.warn("Falha ao enviar push: {}", e.getMessage());
            return ResultadoEnvio.FALHA;
        }
    }

    @Override
    public boolean habilitado() {
        return true;
    }

    @Override
    public String chavePublica() {
        return chaves.publicaBase64Url();
    }
}
