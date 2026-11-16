package br.com.fiap.alerta.service;

import static br.com.fiap.alerta.Fixtures.AGORA;
import static br.com.fiap.alerta.Fixtures.PREDIO;
import static br.com.fiap.alerta.Fixtures.RELOGIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.dto.DisparoAlertaRequest;
import br.com.fiap.alerta.dto.EncerramentoRequest;
import br.com.fiap.alerta.exception.AcessoNegadoException;
import br.com.fiap.alerta.exception.ConflitoException;
import br.com.fiap.alerta.exception.RecursoNaoEncontradoException;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Checkin;
import br.com.fiap.alerta.model.Confirmacao;
import br.com.fiap.alerta.model.Encerramento;
import br.com.fiap.alerta.model.MotivoEncerramento;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.Penalidade;
import br.com.fiap.alerta.model.SituacaoCheckin;
import br.com.fiap.alerta.model.StatusAlerta;
import br.com.fiap.alerta.model.StatusClassificacao;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.model.TipoAlerta;
import br.com.fiap.alerta.model.TipoEventoAuditoria;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.repository.CheckinRepository;
import br.com.fiap.alerta.repository.MoradorRepository;

/** Usa ControleAcesso e PenalidadeService reais: só os repositórios, a auditoria e os eventos são mocks. */
@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    @Mock
    private AlertaRepository alertas;
    @Mock
    private CheckinRepository checkins;
    @Mock
    private MoradorRepository moradores;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private ApplicationEventPublisher eventos;

    private AlertaService service;
    private Morador carla;
    private Morador joao;
    private Morador ana;

    @BeforeEach
    void setUp() {
        ControleAcesso acesso = new ControleAcesso(auditoria);
        PenalidadeService penalidades = new PenalidadeService(moradores, auditoria, RELOGIO, 30);
        service = new AlertaService(alertas, checkins, acesso, penalidades, auditoria, eventos, RELOGIO);
        carla = Fixtures.sindica();
        joao = Fixtures.joao();
        ana = Fixtures.ana();
    }

    @Test
    void disparoGravaNaHoraComoAtivoPendenteEPublicaEventoSemChamarAIa() {
        when(alertas.save(any(Alerta.class))).thenAnswer(i -> {
            Alerta a = i.getArgument(0);
            a.setId("a1");
            return a;
        });

        Alerta alerta = service.disparar(PREDIO, new DisparoAlertaRequest(TipoAlerta.INCENDIO, "  Fumaça no 304  ",
                null), joao);

        assertThat(alerta.getId()).isEqualTo("a1");
        assertThat(alerta.getStatus()).isEqualTo(StatusAlerta.ATIVO);
        assertThat(alerta.getClassificacao().status()).isEqualTo(StatusClassificacao.PENDENTE);
        assertThat(alerta.getAutor().nome()).isEqualTo("João Lima");
        assertThat(alerta.getAutor().apartamento()).isEqualTo("204");
        assertThat(alerta.getDescricao()).isEqualTo("Fumaça no 304");
        assertThat(alerta.getDataHora()).isEqualTo(AGORA);
        // A IA e o push ficam com quem escuta o evento, em outra thread
        verify(eventos).publishEvent(new AlertaDisparadoEvent("a1"));
        verify(auditoria).registrar(eq(TipoEventoAuditoria.ALERTA_DISPARADO), eq(PREDIO), eq(joao), anyString(),
                anyMap());
    }

    @Test
    void moradorDeOutroPredioNaoDisparaENadaEhGravado() {
        Morador pedro = Fixtures.deOutroPredio();

        assertThatThrownBy(() -> service.disparar(PREDIO, disparo(), pedro))
                .isInstanceOf(AcessoNegadoException.class);

        verify(alertas, never()).save(any());
        verifyNoInteractions(eventos);
        verify(auditoria).registrar(eq(TipoEventoAuditoria.ACESSO_NEGADO), eq(PREDIO), eq(pedro), anyString(),
                anyMap());
    }

    @Test
    void moradorDeOutroPredioNaoVeOAlerta() {
        when(alertas.findById("a1")).thenReturn(Optional.of(Fixtures.alerta("a1", joao, TipoAlerta.GAS)));

        assertThatThrownBy(() -> service.buscar("a1", Fixtures.deOutroPredio()))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.checkin("a1", SituacaoCheckin.SEGURO, Fixtures.deOutroPredio()))
                .isInstanceOf(AcessoNegadoException.class);
        verify(checkins, never()).save(any());
    }

    @Test
    void alertaInexistenteEh404() {
        when(alertas.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar("x", joao)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void suspensoNaoDisparaMasContinuaFazendoCheckinEConfirmando() {
        Fixtures.comStatus(joao, StatusMorador.SUSPENSO, 2);
        joao.setSuspensoAte(AGORA.plus(Duration.ofDays(20)));
        Alerta daAna = Fixtures.alerta("a2", ana, TipoAlerta.GAS);
        when(alertas.findById("a2")).thenReturn(Optional.of(daAna));
        when(checkins.findByAlertaIdAndMoradorId("a2", "joao")).thenReturn(Optional.empty());
        when(checkins.save(any(Checkin.class))).thenAnswer(i -> i.getArgument(0));
        when(alertas.adicionarConfirmacao(eq("a2"), eq("joao"), any(Confirmacao.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.disparar(PREDIO, disparo(), joao))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("suspenso");
        Checkin checkin = service.checkin("a2", SituacaoCheckin.PRECISA_AJUDA, joao);
        service.confirmar("a2", joao);

        assertThat(checkin.getSituacao()).isEqualTo(SituacaoCheckin.PRECISA_AJUDA);
        assertThat(checkin.getMoradorId()).isEqualTo("joao");
        verify(alertas, never()).save(any());
    }

    @Test
    void removidoNaoDisparaNemFazCheckin() {
        Fixtures.comStatus(joao, StatusMorador.REMOVIDO, 3);
        when(alertas.findById("a2")).thenReturn(Optional.of(Fixtures.alerta("a2", ana, TipoAlerta.GAS)));

        assertThatThrownBy(() -> service.disparar(PREDIO, disparo(), joao)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.checkin("a2", SituacaoCheckin.SEGURO, joao))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("removido");
    }

    @Test
    void checkinRepetidoAtualizaASituacaoDoMesmoRegistro() {
        when(alertas.findById("a1")).thenReturn(Optional.of(Fixtures.alerta("a1", joao, TipoAlerta.GAS)));
        Checkin existente = new Checkin("a1", "ana", SituacaoCheckin.SEGURO, AGORA.minusSeconds(60));
        existente.setId("c1");
        when(checkins.findByAlertaIdAndMoradorId("a1", "ana")).thenReturn(Optional.of(existente));
        when(checkins.save(any(Checkin.class))).thenAnswer(i -> i.getArgument(0));

        Checkin atualizado = service.checkin("a1", SituacaoCheckin.PRECISA_AJUDA, ana);

        assertThat(atualizado.getId()).isEqualTo("c1");
        assertThat(atualizado.getSituacao()).isEqualTo(SituacaoCheckin.PRECISA_AJUDA);
        assertThat(atualizado.getDataHora()).isEqualTo(AGORA);
    }

    @Test
    void checkinEmAlertaEncerradoEhConflito() {
        Alerta encerrado = Fixtures.alerta("a1", joao, TipoAlerta.GAS);
        encerrado.setStatus(StatusAlerta.ENCERRADO);
        when(alertas.findById("a1")).thenReturn(Optional.of(encerrado));

        assertThatThrownBy(() -> service.checkin("a1", SituacaoCheckin.SEGURO, ana))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void autorNaoConfirmaOProprioAlertaENinguemConfirmaDuasVezes() {
        when(alertas.findById("a1")).thenReturn(Optional.of(Fixtures.alerta("a1", joao, TipoAlerta.GAS)));
        when(alertas.adicionarConfirmacao(eq("a1"), eq("ana"), any(Confirmacao.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.confirmar("a1", joao))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("Quem disparou");
        assertThatThrownBy(() -> service.confirmar("a1", ana))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("já confirmou");
    }

    @Test
    void soOSindicoEncerra() {
        when(alertas.findById("a1")).thenReturn(Optional.of(Fixtures.alerta("a1", joao, TipoAlerta.GAS)));

        assertThatThrownBy(() -> service.encerrar("a1", new EncerramentoRequest(MotivoEncerramento.REAL, null), ana))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("síndico");
        verify(alertas, never()).encerrar(any(), any());
    }

    @Test
    void encerrarComoMaFeAplicaERegistraAPenalidadeDoAutor() {
        Alerta alerta = Fixtures.alerta("a1", joao, TipoAlerta.ELEVADOR);
        when(alertas.findById("a1")).thenReturn(Optional.of(alerta));
        when(alertas.encerrar(eq("a1"), any(Encerramento.class))).thenReturn(1L);
        when(moradores.findById("joao")).thenReturn(Optional.of(joao));

        service.encerrar("a1", new EncerramentoRequest(MotivoEncerramento.FALSO_MA_FE, "trote"), carla);

        ArgumentCaptor<Encerramento> encerramento = ArgumentCaptor.forClass(Encerramento.class);
        verify(alertas).encerrar(eq("a1"), encerramento.capture());
        assertThat(encerramento.getValue().motivo()).isEqualTo(MotivoEncerramento.FALSO_MA_FE);
        assertThat(encerramento.getValue().porNome()).isEqualTo("Carla Mendes");
        verify(alertas).registrarPenalidade("a1", Penalidade.ADVERTENCIA);
        assertThat(joao.getStatus()).isEqualTo(StatusMorador.ADVERTIDO);
        verify(auditoria).registrar(eq(TipoEventoAuditoria.ALERTA_ENCERRADO), eq(PREDIO), eq(carla), anyString(),
                anyMap());
    }

    @Test
    void encerrarComoRealNaoPenaliza() {
        when(alertas.findById("a1")).thenReturn(Optional.of(Fixtures.alerta("a1", joao, TipoAlerta.ELEVADOR)));
        when(alertas.encerrar(eq("a1"), any(Encerramento.class))).thenReturn(1L);

        service.encerrar("a1", new EncerramentoRequest(MotivoEncerramento.REAL, null), carla);

        verify(alertas, never()).registrarPenalidade(any(), any());
        verify(moradores, never()).save(any());
    }

    @Test
    void encerrarAlertaJaEncerradoEhConflitoSemPenalidadeDupla() {
        Alerta encerrado = Fixtures.alerta("a1", joao, TipoAlerta.ELEVADOR);
        encerrado.setStatus(StatusAlerta.ENCERRADO);
        when(alertas.findById("a1")).thenReturn(Optional.of(encerrado), Optional.of(Fixtures.alerta("a1", joao,
                TipoAlerta.ELEVADOR)));
        when(alertas.encerrar(eq("a1"), any(Encerramento.class))).thenReturn(0L);
        EncerramentoRequest maFe = new EncerramentoRequest(MotivoEncerramento.FALSO_MA_FE, null);

        // já encerrado na leitura, e encerrado por outro síndico entre a leitura e a gravação
        assertThatThrownBy(() -> service.encerrar("a1", maFe, carla)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> service.encerrar("a1", maFe, carla)).isInstanceOf(ConflitoException.class);

        verify(moradores, never()).findById(any());
        verify(alertas, never()).registrarPenalidade(any(), any());
    }

    @Test
    void listarFiltraPorStatusETiraAFoto() {
        Alerta comFoto = Fixtures.alerta("a1", joao, TipoAlerta.INCENDIO);
        comFoto.setFotoUrl("data:image/jpeg;base64,AAAA");
        when(alertas.findByPredioIdAndStatusOrderByDataHoraDesc(PREDIO, StatusAlerta.ATIVO))
                .thenReturn(List.of(comFoto));
        when(alertas.findByPredioIdOrderByDataHoraDesc(PREDIO)).thenReturn(List.of());

        assertThat(service.listar(PREDIO, StatusAlerta.ATIVO, ana))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.temFoto()).isTrue();
                    assertThat(r.id()).isEqualTo("a1");
                });
        assertThat(service.listar(PREDIO, null, ana)).isEmpty();
    }

    private static DisparoAlertaRequest disparo() {
        return new DisparoAlertaRequest(TipoAlerta.INCENDIO, "Fumaça", null);
    }
}
