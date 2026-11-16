package br.com.fiap.alerta.push;

import static br.com.fiap.alerta.Fixtures.AGORA;
import static br.com.fiap.alerta.Fixtures.RELOGIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class VapidTest {

    private final KeyPair par = ChavesEc.gerarPar();
    private final String publica = ChavesEc.base64Url(ChavesEc.codificar((ECPublicKey) par.getPublic()));
    private final String privada = ChavesEc.base64Url(ChavesEc.codificar((ECPrivateKey) par.getPrivate()));

    @Test
    void jwtTemCabecalhoES256ClaimsCorretosEAssinaturaVerificavel() throws Exception {
        ChavesVapid chaves = ChavesVapid.de(publica, privada);
        String token = new Vapid(chaves, "mailto:sindico@example.com", RELOGIO).token("https://fcm.googleapis.com");

        String[] partes = token.split("\\.");
        assertThat(partes).hasSize(3);
        ObjectMapper json = new ObjectMapper();
        JsonNode cabecalho = json.readTree(Base64.getUrlDecoder().decode(partes[0]));
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(partes[1]));
        assertThat(cabecalho.get("alg").asText()).isEqualTo("ES256");
        assertThat(claims.get("aud").asText()).isEqualTo("https://fcm.googleapis.com");
        assertThat(claims.get("sub").asText()).isEqualTo("mailto:sindico@example.com");
        assertThat(claims.get("exp").asLong()).isEqualTo(AGORA.plus(Vapid.VALIDADE).getEpochSecond());

        byte[] assinatura = Base64.getUrlDecoder().decode(partes[2]);
        assertThat(assinatura).hasSize(64);
        Signature verificador = Signature.getInstance("SHA256withECDSAinP1363Format");
        verificador.initVerify(par.getPublic());
        verificador.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verificador.verify(assinatura)).isTrue();
    }

    @Test
    void autorizacaoUsaAOrigemDoEndpointEAChavePublica() {
        Vapid vapid = new Vapid(ChavesVapid.de(publica, privada), "mailto:a@b.c", RELOGIO);

        String autorizacao = vapid.autorizacao("https://updates.push.services.mozilla.com:443/wpush/v2/abc");

        assertThat(autorizacao).startsWith("vapid t=").endsWith(", k=" + publica);
        assertThat(Vapid.audiencia("https://updates.push.services.mozilla.com:443/wpush/v2/abc"))
                .isEqualTo("https://updates.push.services.mozilla.com:443");
        assertThat(Vapid.audiencia("https://fcm.googleapis.com/fcm/send/x")).isEqualTo("https://fcm.googleapis.com");
    }

    @Test
    void chavesQueNaoFormamParSaoRecusadas() {
        String outraPrivada = ChavesEc.base64Url(ChavesEc.codificar((ECPrivateKey) ChavesEc.gerarPar().getPrivate()));

        assertThatThrownBy(() -> ChavesVapid.de(publica, outraPrivada))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não formam um par");
        assertThatThrownBy(() -> ChavesVapid.de("não é base64!", privada)).isInstanceOf(IllegalArgumentException.class);
    }
}
