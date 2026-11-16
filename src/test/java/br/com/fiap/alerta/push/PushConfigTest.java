package br.com.fiap.alerta.push;

import static br.com.fiap.alerta.Fixtures.RELOGIO;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import org.junit.jupiter.api.Test;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.config.PushProperties;

class PushConfigTest {

    private final PushConfig config = new PushConfig();

    @Test
    void semChavesOPushFicaDesligado() {
        EnviadorPush enviador = config.enviadorPush(new PushProperties("", null, "mailto:a@b.c"), RELOGIO);

        assertThat(enviador.habilitado()).isFalse();
        assertThat(enviador.chavePublica()).isNull();
        assertThat(enviador.enviar(Fixtures.inscricao("x"), new byte[0])).isEqualTo(EnviadorPush.ResultadoEnvio.FALHA);
    }

    @Test
    void comChavesValidasOPushFicaLigadoEExpoeAChavePublica() {
        KeyPair par = ChavesEc.gerarPar();
        String publica = ChavesEc.base64Url(ChavesEc.codificar((ECPublicKey) par.getPublic()));
        String privada = ChavesEc.base64Url(ChavesEc.codificar((ECPrivateKey) par.getPrivate()));

        EnviadorPush enviador = config.enviadorPush(new PushProperties(publica, privada, "mailto:a@b.c"), RELOGIO);

        assertThat(enviador).isInstanceOf(EnviadorWebPush.class);
        assertThat(enviador.habilitado()).isTrue();
        assertThat(enviador.chavePublica()).isEqualTo(publica);
    }

    @Test
    void chavesInvalidasNaoDerrubamAAplicacao() {
        EnviadorPush enviador = config.enviadorPush(new PushProperties("abc", "def", "mailto:a@b.c"), RELOGIO);

        assertThat(enviador.habilitado()).isFalse();
    }
}
