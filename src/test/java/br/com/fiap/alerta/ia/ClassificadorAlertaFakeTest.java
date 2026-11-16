package br.com.fiap.alerta.ia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.fiap.alerta.model.Gravidade;
import br.com.fiap.alerta.model.TipoAlerta;

class ClassificadorAlertaFakeTest {

    private final ClassificadorAlertaFake classificador = new ClassificadorAlertaFake();

    @Test
    void semDescricaoMantemOTipoInformadoComConfiancaBaixa() {
        ResultadoClassificacao r = classificador.classificar(new PedidoClassificacao(TipoAlerta.ELEVADOR, null, null));

        assertThat(r.tipoProvavel()).isEqualTo(TipoAlerta.ELEVADOR);
        assertThat(r.gravidade()).isEqualTo(Gravidade.MEDIA);
        assertThat(r.confianca()).isEqualTo(0.5);
        assertThat(r.modelo()).isEqualTo(ClassificadorAlertaFake.MODELO);
    }

    @Test
    void palavrasChaveConfirmamOTipoInformado() {
        ResultadoClassificacao r = classificador.classificar(
                new PedidoClassificacao(TipoAlerta.GAS, "Cheiro de gás forte perto do botijão", null));

        assertThat(r.tipoProvavel()).isEqualTo(TipoAlerta.GAS);
        assertThat(r.gravidade()).isEqualTo(Gravidade.ALTA);
        assertThat(r.confianca()).isEqualTo(0.8);
    }

    @Test
    void palavrasChaveDeOutroTipoSugeremOTipoProvavelEIgnoramAcentos() {
        ResultadoClassificacao r = classificador.classificar(
                new PedidoClassificacao(TipoAlerta.ALAGAMENTO, "FUMAÇA e Fogo saindo do 304", null));

        assertThat(r.tipoProvavel()).isEqualTo(TipoAlerta.INCENDIO);
        assertThat(r.confianca()).isEqualTo(0.6);
    }

    @Test
    void sinaisCriticosElevamAGravidade() {
        ResultadoClassificacao r = classificador.classificar(
                new PedidoClassificacao(TipoAlerta.SOCORRO_MEDICO, "Criança inconsciente no playground", null));

        assertThat(r.tipoProvavel()).isEqualTo(TipoAlerta.SOCORRO_MEDICO);
        assertThat(r.gravidade()).isEqualTo(Gravidade.CRITICA);
    }
}
