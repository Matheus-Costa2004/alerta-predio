package br.com.fiap.alerta.push;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/** Par VAPID do servidor, lido das variáveis VAPID_PUBLIC_KEY e VAPID_PRIVATE_KEY (base64url). */
public record ChavesVapid(ECPublicKey publica, ECPrivateKey privada, String publicaBase64Url) {

    /** Falha logo na inicialização se as chaves estiverem malformadas ou não formarem um par. */
    public static ChavesVapid de(String publicaBase64Url, String privadaBase64Url) {
        ECPublicKey publica = ChavesEc.publica(ChavesEc.decodificarBase64Url(publicaBase64Url));
        ECPrivateKey privada = ChavesEc.privada(ChavesEc.decodificarBase64Url(privadaBase64Url));
        if (!formamPar(publica, privada)) {
            throw new IllegalArgumentException("VAPID_PUBLIC_KEY e VAPID_PRIVATE_KEY não formam um par");
        }
        return new ChavesVapid(publica, privada, ChavesEc.base64Url(ChavesEc.codificar(publica)));
    }

    private static boolean formamPar(ECPublicKey publica, ECPrivateKey privada) {
        try {
            byte[] teste = "alerta-predio".getBytes(StandardCharsets.US_ASCII);
            Signature assinatura = Signature.getInstance("SHA256withECDSA");
            assinatura.initSign(privada);
            assinatura.update(teste);
            byte[] assinado = assinatura.sign();
            assinatura.initVerify(publica);
            assinatura.update(teste);
            return assinatura.verify(assinado);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
