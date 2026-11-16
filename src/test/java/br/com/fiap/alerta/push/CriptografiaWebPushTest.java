package br.com.fiap.alerta.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class CriptografiaWebPushTest {

    // RFC 8291, seção 5 e Apêndice A
    private static final String MENSAGEM = "When I grow up, I want to be a watermelon";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String UA_PUBLICA =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String UA_PRIVADA = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String AS_PUBLICA =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVADA = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String CORPO_ESPERADO = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
            + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
            + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void reproduzOExemploDoRfc8291ByteAByte() {
        KeyPair servidor = new KeyPair(ChavesEc.publica(b64(AS_PUBLICA)), ChavesEc.privada(b64(AS_PRIVADA)));

        byte[] corpo = CriptografiaWebPush.criptografar(MENSAGEM.getBytes(StandardCharsets.US_ASCII),
                ChavesEc.publica(b64(UA_PUBLICA)), b64(AUTH), servidor, b64(SALT));

        assertThat(ChavesEc.base64Url(corpo)).isEqualTo(CORPO_ESPERADO);
        // cabeçalho (salt 16 + rs 4 + idlen 1 + chave 65) + mensagem + delimitador + tag GCM
        // (o "Content-Length: 145" do exemplo HTTP do RFC é um erro conhecido; o corpo tem 144 bytes)
        assertThat(corpo).hasSize(86 + MENSAGEM.length() + 1 + 16);
    }

    @Test
    void mensagemComChavesAleatoriasEhDecifradaPeloNavegador() throws Exception {
        KeyPair navegador = ChavesEc.gerarPar();
        byte[] auth = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] mensagem = "{\"titulo\":\"🔥 Incêndio · Apto 302\"}".getBytes(StandardCharsets.UTF_8);

        byte[] corpo = CriptografiaWebPush.criptografar(mensagem,
                ChavesEc.codificar((ECPublicKey) navegador.getPublic()), auth);

        assertThat(decifrarComoNavegador(corpo, navegador, auth)).isEqualTo(mensagem);
    }

    @Test
    void duasMensagensIguaisGeramCorposDiferentes() {
        KeyPair navegador = ChavesEc.gerarPar();
        byte[] publica = ChavesEc.codificar((ECPublicKey) navegador.getPublic());
        byte[] auth = new byte[16];

        assertThat(CriptografiaWebPush.criptografar(new byte[] {1}, publica, auth))
                .isNotEqualTo(CriptografiaWebPush.criptografar(new byte[] {1}, publica, auth));
    }

    @Test
    void mensagemAcimaDoLimiteDoPushServiceEhRecusada() {
        byte[] publica = ChavesEc.codificar((ECPublicKey) ChavesEc.gerarPar().getPublic());

        assertThatThrownBy(() -> CriptografiaWebPush.criptografar(
                new byte[CriptografiaWebPush.TAMANHO_MAXIMO_MENSAGEM + 1], publica, new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chavePublicaForaDaCurvaEhRecusada() {
        byte[] invalida = b64(UA_PUBLICA);
        invalida[64] ^= 0x01;

        assertThatThrownBy(() -> ChavesEc.publica(invalida))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora da curva");
    }

    @Test
    void chavePublicaComTamanhoErradoEhRecusada() {
        assertThatThrownBy(() -> ChavesEc.publica(new byte[33])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChavesEc.privada(new byte[31])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codificacaoDaChaveIdaEVolta() {
        KeyPair par = ChavesEc.gerarPar();
        byte[] publica = ChavesEc.codificar((ECPublicKey) par.getPublic());
        byte[] privada = ChavesEc.codificar((ECPrivateKey) par.getPrivate());

        assertThat(ChavesEc.codificar(ChavesEc.publica(publica))).isEqualTo(publica);
        assertThat(ChavesEc.codificar(ChavesEc.privada(privada))).isEqualTo(privada);
        assertThat(ChavesEc.decodificarBase64Url(ChavesEc.base64Url(publica))).isEqualTo(publica);
    }

    /** Lado do navegador (RFC 8291), escrito à parte para conferir a implementação do servidor. */
    private static byte[] decifrarComoNavegador(byte[] corpo, KeyPair navegador, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(corpo);
        byte[] salt = new byte[16];
        buffer.get(salt);
        assertThat(buffer.getInt()).isEqualTo(4096);
        byte[] publicaServidor = new byte[buffer.get()];
        buffer.get(publicaServidor);
        byte[] cifrado = new byte[buffer.remaining()];
        buffer.get(cifrado);

        KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
        ecdh.init(navegador.getPrivate());
        ecdh.doPhase(ChavesEc.publica(publicaServidor), true);
        byte[] segredo = ecdh.generateSecret();
        byte[] publicaNavegador = ChavesEc.codificar((ECPublicKey) navegador.getPublic());

        byte[] prkChave = hmac(auth, segredo);
        byte[] info = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), publicaNavegador,
                publicaServidor, new byte[] {1});
        byte[] prk = hmac(salt, hmac(prkChave, info));
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(), new byte[] {1})), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] comDelimitador = aes.doFinal(cifrado);
        assertThat(comDelimitador[comDelimitador.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(comDelimitador, comDelimitador.length - 1);
    }

    private static byte[] hmac(byte[] chave, byte[] dados) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(dados);
    }

    private static byte[] concat(byte[]... partes) {
        ByteBuffer buffer = ByteBuffer.allocate(Arrays.stream(partes).mapToInt(p -> p.length).sum());
        Arrays.stream(partes).forEach(buffer::put);
        return buffer.array();
    }

    private static byte[] b64(String valor) {
        return ChavesEc.decodificarBase64Url(valor);
    }
}
