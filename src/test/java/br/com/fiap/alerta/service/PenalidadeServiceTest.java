package br.com.fiap.alerta.service;

import static br.com.fiap.alerta.Fixtures.AGORA;
import static br.com.fiap.alerta.Fixtures.RELOGIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.exception.AcessoNegadoException;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.MotivoEncerramento;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.Penalidade;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.model.TipoAlerta;
import br.com.fiap.alerta.model.TipoEventoAuditoria;
import br.com.fiap.alerta.repository.MoradorRepository;

@ExtendWith(MockitoExtension.class)
class PenalidadeServiceTest {

    @Mock
    private MoradorRepository moradores;
    @Mock
    private AuditoriaService auditoria;

    private PenalidadeService service;
    private Morador joao;
    private Morador carla;
    private Alerta alerta;

    @BeforeEach
    void setUp() {
        service = new PenalidadeService(moradores, auditoria, RELOGIO, 30);
        joao = Fixtures.joao();
        carla = Fixtures.sindica();
        alerta = Fixtures.alerta("a1", joao, TipoAlerta.ELEVADOR);
    }

    @ParameterizedTest
    @EnumSource(value = MotivoEncerramento.class, names = { "REAL", "FALSO_ENGANO" })
    void soMaFeContaComoAdvertencia(MotivoEncerramento motivo) {
        assertThat(service.avaliarEncerramento(alerta, motivo, carla)).isNull();

        verify(moradores, never()).findById(any());
        verify(moradores, never()).save(any());
    }

    @Test
    void primeiraMaFeEhAdvertencia() {
        when(moradores.findById("joao")).thenReturn(Optional.of(joao));

        Penalidade penalidade = service.avaliarEncerramento(alerta, MotivoEncerramento.FALSO_MA_FE, carla);

        assertThat(penalidade).isEqualTo(Penalidade.ADVERTENCIA);
        assertThat(joao.getStatus()).isEqualTo(StatusMorador.ADVERTIDO);
        assertThat(joao.getQtdAdvertencias()).isEqualTo(1);
        assertThat(joao.getSuspensoAte()).isNull();
        verify(moradores).save(joao);
        verify(auditoria).registrar(eq(TipoEventoAuditoria.ADVERTENCIA), eq(Fixtures.PREDIO), eq(carla), anyString(),
                anyMap());
    }

    @Test
    void segundaMaFeSuspendeOBotaoPorTrintaDias() {
        Fixtures.comStatus(joao, StatusMorador.ADVERTIDO, 1);
        when(moradores.findById("joao")).thenReturn(Optional.of(joao));

        Penalidade penalidade = service.avaliarEncerramento(alerta, MotivoEncerramento.FALSO_MA_FE, carla);

        assertThat(penalidade).isEqualTo(Penalidade.SUSPENSAO);
        assertThat(joao.getStatus()).isEqualTo(StatusMorador.SUSPENSO);
        assertThat(joao.getQtdAdvertencias()).isEqualTo(2);
        assertThat(joao.getSuspensoAte()).isEqualTo(AGORA.plus(Duration.ofDays(30)));
        verify(auditoria).registrar(eq(TipoEventoAuditoria.SUSPENSAO), eq(Fixtures.PREDIO), eq(carla), anyString(),
                anyMap());
    }

    @Test
    void terceiraMaFeRemoveOMoradorEApagaAsInscricoesDePush() {
        Fixtures.comStatus(joao, StatusMorador.ADVERTIDO, 2);
        joao.setInscricoesPush(new ArrayList<>(List.of(Fixtures.inscricao("joao"))));
        when(moradores.findById("joao")).thenReturn(Optional.of(joao));

        Penalidade penalidade = service.avaliarEncerramento(alerta, MotivoEncerramento.FALSO_MA_FE, carla);

        assertThat(penalidade).isEqualTo(Penalidade.REMOCAO);
        assertThat(joao.getStatus()).isEqualTo(StatusMorador.REMOVIDO);
        assertThat(joao.getQtdAdvertencias()).isEqualTo(3);
        assertThat(joao.getInscricoesPush()).isEmpty();
        verify(auditoria).registrar(eq(TipoEventoAuditoria.REMOCAO), eq(Fixtures.PREDIO), eq(carla), anyString(),
                anyMap());
    }

    @Test
    void autorJaRemovidoOuInexistenteNaoRecebeNovaPenalidade() {
        Fixtures.comStatus(joao, StatusMorador.REMOVIDO, 3);
        when(moradores.findById("joao")).thenReturn(Optional.of(joao), Optional.empty());

        assertThat(service.avaliarEncerramento(alerta, MotivoEncerramento.FALSO_MA_FE, carla)).isNull();
        assertThat(service.avaliarEncerramento(alerta, MotivoEncerramento.FALSO_MA_FE, carla)).isNull();
        assertThat(joao.getQtdAdvertencias()).isEqualTo(3);
        verify(moradores, never()).save(any());
    }

    @Test
    void moradorAtivoOuAdvertidoPodeDisparar() {
        service.exigirBotaoLiberado(joao);
        service.exigirBotaoLiberado(Fixtures.comStatus(Fixtures.ana(), StatusMorador.ADVERTIDO, 1));

        verify(moradores, never()).save(any());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
    }

    @Test
    void suspensoNaoDisparaEAMensagemMostraAteQuando() {
        Fixtures.comStatus(joao, StatusMorador.SUSPENSO, 2);
        joao.setSuspensoAte(AGORA.plus(Duration.ofDays(10)));

        assertThatThrownBy(() -> service.exigirBotaoLiberado(joao))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("suspenso até 24/11/2026")
                .hasMessageContaining("continua recebendo alertas");
        verify(auditoria).registrar(eq(TipoEventoAuditoria.DISPARO_BLOQUEADO), eq(Fixtures.PREDIO), eq(joao),
                anyString(), anyMap());
        assertThat(joao.getStatus()).isEqualTo(StatusMorador.SUSPENSO);
    }

    @Test
    void suspensaoVencidaVoltaParaAdvertidoELiberaOBotao() {
        Fixtures.comStatus(joao, StatusMorador.SUSPENSO, 2);
        joao.setSuspensoAte(AGORA.minusSeconds(1));

        service.exigirBotaoLiberado(joao);

        assertThat(joao.getStatus()).isEqualTo(StatusMorador.ADVERTIDO);
        assertThat(joao.getSuspensoAte()).isNull();
        assertThat(joao.getQtdAdvertencias()).isEqualTo(2);
        verify(moradores).save(joao);
    }
}
