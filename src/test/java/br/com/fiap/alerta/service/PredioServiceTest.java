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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.auth.AutenticacaoService;
import br.com.fiap.alerta.dto.AcessoResponse;
import br.com.fiap.alerta.dto.DadosMorador;
import br.com.fiap.alerta.dto.EntrarPredioRequest;
import br.com.fiap.alerta.dto.NovoPredioRequest;
import br.com.fiap.alerta.exception.AcessoNegadoException;
import br.com.fiap.alerta.exception.RecursoNaoEncontradoException;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.Papel;
import br.com.fiap.alerta.model.Predio;
import br.com.fiap.alerta.model.TipoEventoAuditoria;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.repository.PredioRepository;

@ExtendWith(MockitoExtension.class)
class PredioServiceTest {

    @Mock
    private PredioRepository predios;
    @Mock
    private MoradorRepository moradores;
    @Mock
    private AuditoriaService auditoria;

    private PredioService service;

    @BeforeEach
    void setUp() {
        service = new PredioService(predios, moradores, new AutenticacaoService(moradores), auditoria,
                new ControleAcesso(auditoria), RELOGIO);
    }

    @Test
    void criarGeraCodigoUnicoTornaOCriadorSindicoEDevolveOTokenSoUmaVez() {
        when(predios.existsByCodigoConvite(anyString())).thenReturn(false);
        when(predios.save(any(Predio.class))).thenAnswer(i -> {
            Predio p = i.getArgument(0);
            p.setId("p1");
            return p;
        });
        when(moradores.save(any(Morador.class))).thenAnswer(i -> {
            Morador m = i.getArgument(0);
            m.setId("m1");
            return m;
        });

        AcessoResponse acesso = service.criar(new NovoPredioRequest(" Residencial Acácias ", "Rua A, 1",
                dados(null, null)));

        assertThat(acesso.predio().codigoConvite()).matches("[A-HJ-NP-Z2-9]{6}");
        assertThat(acesso.predio().sindicoId()).isEqualTo("m1");
        assertThat(acesso.predio().nome()).isEqualTo("Residencial Acácias");
        assertThat(acesso.morador().getPapel()).isEqualTo(Papel.SINDICO);
        assertThat(acesso.morador().getDataEntrada()).isEqualTo(AGORA);
        assertThat(acesso.token()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(acesso.morador().getTokenHash()).isEqualTo(AutenticacaoService.hash(acesso.token()))
                .isNotEqualTo(acesso.token());
        verify(auditoria).registrar(eq(TipoEventoAuditoria.PREDIO_CRIADO), eq("p1"), any(Morador.class),
                anyString(), anyMap());
    }

    @Test
    void codigoDeConviteRepetidoGeraOutro() {
        when(predios.existsByCodigoConvite(anyString())).thenReturn(true, true, false);

        String codigo = service.gerarCodigoConvite();

        assertThat(codigo).hasSize(6);
        verify(predios, times(3)).existsByCodigoConvite(anyString());
    }

    @Test
    void desisteDepoisDeDezColisoes() {
        when(predios.existsByCodigoConvite(anyString())).thenReturn(true);

        assertThatThrownBy(service::gerarCodigoConvite).isInstanceOf(IllegalStateException.class);
        verify(predios, times(10)).existsByCodigoConvite(anyString());
    }

    @Test
    void entrarComCodigoNormalizadoCriaMoradorComumSemExporOCodigo() {
        Predio predio = predio();
        when(predios.findByCodigoConvite("ACACIA")).thenReturn(Optional.of(predio));
        when(moradores.save(any(Morador.class))).thenAnswer(i -> i.getArgument(0));

        AcessoResponse acesso = service.entrar(new EntrarPredioRequest(" acacia ", dados(null, null)));

        assertThat(acesso.morador().getPapel()).isEqualTo(Papel.MORADOR);
        assertThat(acesso.morador().getPredioId()).isEqualTo("p1");
        assertThat(acesso.morador().getApartamento()).isEqualTo("302A");
        assertThat(acesso.morador().getNecessidadeEspecial()).isNull();
        assertThat(acesso.predio().codigoConvite()).isNull();
        verify(auditoria).registrar(eq(TipoEventoAuditoria.MORADOR_ENTROU), eq("p1"), any(Morador.class),
                anyString(), anyMap());
        verify(auditoria, never()).registrar(eq(TipoEventoAuditoria.CONSENTIMENTO_REGISTRADO), any(), any(),
                any(), anyMap());
    }

    @Test
    void codigoInexistenteEh404() {
        when(predios.findByCodigoConvite("NAOHA1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar(new EntrarPredioRequest("NAOHA1", dados(null, null))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(moradores, never()).save(any());
    }

    @Test
    void necessidadeEspecialComConsentimentoFicaRegistradaComDataEVersaoDoTermo() {
        when(predios.findByCodigoConvite("ACACIA")).thenReturn(Optional.of(predio()));
        when(moradores.save(any(Morador.class))).thenAnswer(i -> i.getArgument(0));

        AcessoResponse acesso = service.entrar(new EntrarPredioRequest("ACACIA", dados(" Cadeirante ", true)));

        assertThat(acesso.morador().getNecessidadeEspecial().descricao()).isEqualTo("Cadeirante");
        assertThat(acesso.morador().getNecessidadeEspecial().consentimento().aceito()).isTrue();
        assertThat(acesso.morador().getNecessidadeEspecial().consentimento().dataHora()).isEqualTo(AGORA);
        assertThat(acesso.morador().getNecessidadeEspecial().consentimento().versaoTermo())
                .isEqualTo(PredioService.VERSAO_TERMO);
        verify(auditoria).registrar(eq(TipoEventoAuditoria.CONSENTIMENTO_REGISTRADO), eq("p1"), any(Morador.class),
                anyString(), anyMap());
    }

    @Test
    void necessidadeEspecialSemConsentimentoNuncaEhGravada() {
        when(predios.findByCodigoConvite("ACACIA")).thenReturn(Optional.of(predio()));
        when(moradores.save(any(Morador.class))).thenAnswer(i -> i.getArgument(0));

        AcessoResponse acesso = service.entrar(new EntrarPredioRequest("ACACIA", dados("Cadeirante", false)));

        assertThat(acesso.morador().getNecessidadeEspecial()).isNull();
    }

    @Test
    void consentimentoEhObrigatorioNaValidacaoDoDto() {
        assertThat(dados("Cadeirante", null).isConsentimentoValido()).isFalse();
        assertThat(dados("Cadeirante", false).isConsentimentoValido()).isFalse();
        assertThat(dados("Cadeirante", true).isConsentimentoValido()).isTrue();
        assertThat(dados("  ", null).isConsentimentoValido()).isTrue();
        assertThat(dados(null, null).isConsentimentoValido()).isTrue();
    }

    @Test
    void codigoDeConviteSoAparecePraSindico() {
        when(predios.findById(Fixtures.PREDIO)).thenReturn(Optional.of(predio()));

        assertThat(service.buscar(Fixtures.PREDIO, Fixtures.sindica()).codigoConvite()).isEqualTo("ACACIA");
        assertThat(service.buscar(Fixtures.PREDIO, Fixtures.joao()).codigoConvite()).isNull();
        assertThat(service.sessao(Fixtures.joao()).predio().codigoConvite()).isNull();
    }

    @Test
    void outroPredioNaoVeOPredioEMoradorComumNaoVeListaNemAuditoria() {
        assertThatThrownBy(() -> service.buscar(Fixtures.PREDIO, Fixtures.deOutroPredio()))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.moradores(Fixtures.PREDIO, Fixtures.joao()))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.auditoria(Fixtures.PREDIO, Fixtures.joao()))
                .isInstanceOf(AcessoNegadoException.class);
        verify(predios, never()).findById(any());
    }

    @Test
    void sindicoVeMoradoresEAuditoria() {
        when(moradores.findByPredioIdOrderByApartamentoAsc(Fixtures.PREDIO)).thenReturn(List.of(Fixtures.ana()));
        when(auditoria.doPredio(Fixtures.PREDIO)).thenReturn(List.of());

        assertThat(service.moradores(Fixtures.PREDIO, Fixtures.sindica())).hasSize(1);
        assertThat(service.auditoria(Fixtures.PREDIO, Fixtures.sindica())).isEmpty();
    }

    private static Predio predio() {
        Predio p = new Predio("Residencial Acácias", "Rua A, 1", "ACACIA", AGORA);
        p.setId("p1");
        return p;
    }

    private static DadosMorador dados(String necessidade, Boolean consentimento) {
        return new DadosMorador("Ana Souza", "302a", "(11) 98888-3002", necessidade, consentimento);
    }
}
