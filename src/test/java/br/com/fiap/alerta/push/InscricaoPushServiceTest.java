package br.com.fiap.alerta.push;

import static br.com.fiap.alerta.Fixtures.AGORA;
import static br.com.fiap.alerta.Fixtures.RELOGIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.dto.InscricaoPushRequest;
import br.com.fiap.alerta.exception.AcessoNegadoException;
import br.com.fiap.alerta.exception.RequisicaoInvalidaException;
import br.com.fiap.alerta.model.InscricaoPush;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.service.AuditoriaService;
import br.com.fiap.alerta.service.ControleAcesso;

@ExtendWith(MockitoExtension.class)
class InscricaoPushServiceTest {

    @Mock
    private MoradorRepository moradores;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private EnviadorPush enviador;

    private InscricaoPushService service;

    @BeforeEach
    void setUp() {
        service = new InscricaoPushService(moradores, new ControleAcesso(auditoria), enviador, RELOGIO);
    }

    @Test
    void inscricaoTiraOAparelhoDeOutrosMoradoresEAssociaAoAtual() {
        String endpoint = "https://fcm.googleapis.com/fcm/send/abc";

        service.inscrever(pedido(endpoint), Fixtures.ana());

        InOrder ordem = inOrder(moradores);
        ordem.verify(moradores).removerEndpointDeTodos(endpoint);
        ordem.verify(moradores).adicionarInscricao("ana", new InscricaoPush(endpoint, "p256", "auth", AGORA));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://web.push.apple.com/abc", "https://updates.push.services.mozilla.com/wpush/v2/x",
            "https://wns2-bl2p.notify.windows.com/w/?token=x" })
    void aceitaOsPushServicesDosNavegadores(String endpoint) {
        service.inscrever(pedido(endpoint), Fixtures.ana());

        verify(moradores).adicionarInscricao(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://169.254.169.254/latest/meta-data", "https://googleapis.com.atacante.net/x",
            "https://atacante.net/fcm.googleapis.com" })
    void recusaEndpointsQueNaoSaoPushServices(String endpoint) {
        assertThatThrownBy(() -> service.inscrever(pedido(endpoint), Fixtures.ana()))
                .isInstanceOf(RequisicaoInvalidaException.class);
        verify(moradores, never()).adicionarInscricao(anyString(), any());
    }

    @Test
    void moradorRemovidoNaoSeInscreve() {
        Morador removido = Fixtures.comStatus(Fixtures.joao(), StatusMorador.REMOVIDO, 3);

        assertThatThrownBy(() -> service.inscrever(pedido("https://fcm.googleapis.com/fcm/send/x"), removido))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void cancelarRemoveSoDoMoradorAtual() {
        service.cancelar("https://fcm.googleapis.com/fcm/send/abc", Fixtures.ana());

        verify(moradores).removerInscricao("ana", "https://fcm.googleapis.com/fcm/send/abc");
    }

    @Test
    void chavePublicaRefleteOEnviador() {
        when(enviador.habilitado()).thenReturn(true);
        when(enviador.chavePublica()).thenReturn("BPub");

        assertThat(service.chavePublica().habilitado()).isTrue();
        assertThat(service.chavePublica().chavePublica()).isEqualTo("BPub");
    }

    private static InscricaoPushRequest pedido(String endpoint) {
        return new InscricaoPushRequest(endpoint, new InscricaoPushRequest.Chaves("p256", "auth"));
    }
}
