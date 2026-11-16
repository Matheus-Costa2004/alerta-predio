import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Gera o par de chaves VAPID (P-256) para o Web Push do AlertaPrédio, sem dependências.
 *
 * Uso (Java 21+, na raiz do projeto): java scripts/GerarChavesVapid.java
 *
 * Copie as duas linhas para o .env (docker compose) ou para as Environment Variables do Render.
 * Use um par por ambiente e não troque depois: os navegadores já inscritos param de receber push.
 */
public class GerarChavesVapid {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
        gerador.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair par = gerador.generateKeyPair();

        ECPublicKey publica = (ECPublicKey) par.getPublic();
        byte[] naoComprimida = new byte[65];
        naoComprimida[0] = 0x04;
        System.arraycopy(bytes32(publica.getW().getAffineX()), 0, naoComprimida, 1, 32);
        System.arraycopy(bytes32(publica.getW().getAffineY()), 0, naoComprimida, 33, 32);
        byte[] privada = bytes32(((ECPrivateKey) par.getPrivate()).getS());

        Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();
        System.out.println("VAPID_PUBLIC_KEY=" + base64Url.encodeToString(naoComprimida));
        System.out.println("VAPID_PRIVATE_KEY=" + base64Url.encodeToString(privada));
    }

    private static byte[] bytes32(BigInteger valor) {
        byte[] bruto = valor.toByteArray();
        byte[] saida = new byte[32];
        if (bruto.length > 32) {
            System.arraycopy(bruto, bruto.length - 32, saida, 0, 32);
        } else {
            System.arraycopy(bruto, 0, saida, 32 - bruto.length, bruto.length);
        }
        return saida;
    }
}
