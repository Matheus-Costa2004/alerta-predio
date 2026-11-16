package br.com.fiap.alerta.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Identificação do servidor no push service (RFC 8292): JWT ES256 com aud = origem do endpoint,
 * exp curto e sub = contato do responsável. Vai no header {@code Authorization: vapid t=..., k=...}.
 */
public class Vapid {

    static final Duration VALIDADE = Duration.ofHours(12);
    private static final String CABECALHO_JWT = ChavesEc.base64Url(
            "{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));

    private final ChavesVapid chaves;
    private final String assunto;
    private final Clock relogio;

    public Vapid(ChavesVapid chaves, String assunto, Clock relogio) {
        this.chaves = chaves;
        this.assunto = assunto;
        this.relogio = relogio;
    }

    public String autorizacao(String endpoint) {
        return "vapid t=" + token(audiencia(endpoint)) + ", k=" + chaves.publicaBase64Url();
    }

    String token(String audiencia) {
        long expiracao = Instant.now(relogio).plus(VALIDADE).getEpochSecond();
        String claims = "{\"aud\":\"" + escapar(audiencia) + "\",\"exp\":" + expiracao
                + ",\"sub\":\"" + escapar(assunto) + "\"}";
        String assinavel = CABECALHO_JWT + "." + ChavesEc.base64Url(claims.getBytes(StandardCharsets.UTF_8));
        try {
            // Formato P1363 (r||s, 64 bytes) é o que o JWS ES256 exige, não o DER padrão do Java
            Signature assinatura = Signature.getInstance("SHA256withECDSAinP1363Format");
            assinatura.initSign(chaves.privada());
            assinatura.update(assinavel.getBytes(StandardCharsets.US_ASCII));
            return assinavel + "." + ChavesEc.base64Url(assinatura.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar o JWT VAPID", e);
        }
    }

    static String audiencia(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
