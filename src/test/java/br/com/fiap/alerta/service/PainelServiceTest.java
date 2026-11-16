package br.com.fiap.alerta.service;

import static br.com.fiap.alerta.Fixtures.AGORA;
import static br.com.fiap.alerta.Fixtures.PREDIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.dto.PainelAlerta;
import br.com.fiap.alerta.dto.PainelAlerta.Entrada;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Checkin;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.SituacaoCheckin;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.model.TipoAlerta;
import br.com.fiap.alerta.repository.CheckinRepository;
import br.com.fiap.alerta.repository.MoradorRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PainelServiceTest {

    @Mock
    private AlertaService alertas;
    @Mock
    private MoradorRepository moradores;
    @Mock
    private CheckinRepository checkins;

    private PainelService service;
    private Morador carla;
    private Morador joao;

    @BeforeEach
    void setUp() {
        service = new PainelService(alertas, moradores, checkins);
        carla = Fixtures.sindica();
        joao = Fixtures.joao();
        Morador ana = Fixtures.ana();
        Morador bia = Fixtures.morador("bia", PREDIO, "Beatriz", "1201");
        Morador seuJose = Fixtures.comNecessidade(Fixtures.morador("jose", PREDIO, "Seu José", "403"),
                "Idoso, usa aparelho auditivo");
        Morador rafa = Fixtures.morador("rafa", PREDIO, "Rafael", "105");
        Alerta alerta = Fixtures.alerta("a1", joao, TipoAlerta.INCENDIO);

        when(alertas.buscar("a1", carla)).thenReturn(alerta);
        when(alertas.buscar("a1", joao)).thenReturn(alerta);
        when(moradores.findByPredioIdAndStatusNot(PREDIO, StatusMorador.REMOVIDO))
                .thenReturn(List.of(carla, joao, ana, bia, seuJose, rafa));
        when(checkins.findByAlertaId("a1")).thenReturn(List.of(
                new Checkin("a1", "joao", SituacaoCheckin.PRECISA_AJUDA, AGORA),
                new Checkin("a1", "ana", SituacaoCheckin.PRECISA_AJUDA, AGORA),
                new Checkin("a1", "carla", SituacaoCheckin.SEGURO, AGORA)));
    }

    @Test
    void separaPorSituacaoComVulneraveisPrimeiroEApartamentosEmOrdemNumerica() {
        PainelAlerta painel = service.montar("a1", carla);

        assertThat(painel.totais()).isEqualTo(new PainelAlerta.Totais(6, 2, 3, 1));
        assertThat(painel.precisamDeAjuda()).extracting(Entrada::nome).containsExactly("Ana Souza", "João Lima");
        assertThat(painel.semResposta()).extracting(Entrada::nome).containsExactly("Seu José", "Rafael", "Beatriz");
        assertThat(painel.seguros()).extracting(Entrada::nome).containsExactly("Carla Mendes");
        assertThat(painel.precisamDeAjuda().get(0).vulneravel()).isTrue();
        assertThat(painel.precisamDeAjuda().get(0).necessidadeEspecial()).isEqualTo("Cadeirante");
        assertThat(painel.precisamDeAjuda().get(0).respondidoEm()).isEqualTo(AGORA);
        assertThat(painel.semResposta().get(0).situacao()).isNull();
        assertThat(painel.alerta().id()).isEqualTo("a1");
    }

    @Test
    void telefoneSoAparecePraSindico() {
        assertThat(service.montar("a1", carla).semResposta()).allSatisfy(e -> assertThat(e.telefone()).isNotNull());
        assertThat(service.montar("a1", joao).semResposta()).allSatisfy(e -> assertThat(e.telefone()).isNull());
    }

    @Test
    void comparaApartamentosPeloNumero() {
        assertThat(PainelService.compararApartamentos("302", "1201")).isNegative();
        assertThat(PainelService.compararApartamentos("302", "302A")).isNegative();
        assertThat(PainelService.compararApartamentos("COB", "101")).isPositive();
        assertThat(PainelService.compararApartamentos("101", "101")).isZero();
    }
}
