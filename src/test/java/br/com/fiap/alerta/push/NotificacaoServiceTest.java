package br.com.fiap.alerta.push;

import static br.com.fiap.alerta.Fixtures.PREDIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.InscricaoPush;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.model.TipoAlerta;
import br.com.fiap.alerta.push.EnviadorPush.ResultadoEnvio;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.service.AlertaDisparadoEvent;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacaoServiceTest {

    @Mock
    private AlertaRepository alertas;
    @Mock
    private MoradorRepository moradores;
    @Mock
    private EnviadorPush enviador;

    private NotificacaoService service;
    private Morador joao;
    private Morador carla;
    private Morador ana;
    private Morador suspenso;
    private Alerta alerta;

    @BeforeEach
    void setUp() {
        service = new NotificacaoService(alertas, moradores, enviador, new ObjectMapper());
        joao = comInscricao(Fixtures.joao(), "joao");
        carla = comInscricao(Fixtures.sindica(), "carla");
        ana = comInscricao(Fixtures.ana(), "ana-celular", "ana-notebook");
        suspenso = comInscricao(Fixtures.comStatus(Fixtures.morador("rafa", PREDIO, "Rafael", "403"),
                StatusMorador.SUSPENSO, 2), "rafa");
        alerta = Fixtures.alerta("a1", joao, TipoAlerta.INCENDIO);
        when(alertas.findById("a1")).thenReturn(Optional.of(alerta));
        when(moradores.findByPredioIdAndStatusNot(PREDIO, StatusMorador.REMOVIDO))
                .thenReturn(List.of(joao, carla, ana, suspenso));
    }

    // (a) obrigatório
    @Test
    void notificaTodosOsMoradoresDoPredioMenosOAutorInclusiveOSuspenso() {
        when(enviador.habilitado()).thenReturn(true);
        when(enviador.enviar(any(), any())).thenReturn(ResultadoEnvio.ENVIADO);

        service.aoDisparar(new AlertaDisparadoEvent("a1"));

        ArgumentCaptor<InscricaoPush> enviados = ArgumentCaptor.forClass(InscricaoPush.class);
        verify(enviador, times(4)).enviar(enviados.capture(), any());
        assertThat(enviados.getAllValues()).extracting(InscricaoPush::endpoint)
                .containsExactlyInAnyOrder(endpoint("carla"), endpoint("ana-celular"), endpoint("ana-notebook"),
                        endpoint("rafa"))
                .doesNotContain(endpoint("joao"));
        verify(alertas).registrarNotificados("a1", 3);
    }

    @Test
    void inscricaoExpiradaEhApagadaEOsDemaisContinuamRecebendo() {
        when(enviador.habilitado()).thenReturn(true);
        when(enviador.enviar(any(), any())).thenAnswer(i -> {
            InscricaoPush inscricao = i.getArgument(0);
            if (inscricao.endpoint().endsWith("ana-notebook")) {
                return ResultadoEnvio.INSCRICAO_EXPIRADA;
            }
            if (inscricao.endpoint().endsWith("carla")) {
                throw new IllegalStateException("erro inesperado");
            }
            return ResultadoEnvio.ENVIADO;
        });

        assertThat(service.notificarNovoAlerta("a1")).isEqualTo(3);

        verify(moradores).removerInscricao("ana", endpoint("ana-notebook"));
        verify(enviador).enviar(Fixtures.inscricao("rafa"), service.payload(alerta));
    }

    @Test
    void comPushDesligadoNadaEhEnviadoMasOAlertaRegistraQuemDeveriaSaber() {
        when(enviador.habilitado()).thenReturn(false);

        assertThat(service.notificarNovoAlerta("a1")).isEqualTo(3);

        verify(enviador, never()).enviar(any(), any());
        verify(alertas).registrarNotificados("a1", 3);
    }

    @Test
    void payloadTemTipoAptoELinkDoAlerta() throws Exception {
        JsonNode payload = new ObjectMapper().readTree(service.payload(alerta));

        assertThat(payload.get("titulo").asText()).isEqualTo("🔥 Incêndio · Apto 204");
        assertThat(payload.get("corpo").asText()).startsWith("João Lima: Descrição de teste");
        assertThat(payload.get("url").asText()).isEqualTo("/#/alerta/a1");
        assertThat(payload.get("tipo").asText()).isEqualTo("INCENDIO");
    }

    private static Morador comInscricao(Morador morador, String... sufixos) {
        List<InscricaoPush> inscricoes = new ArrayList<>();
        for (String sufixo : sufixos) {
            inscricoes.add(Fixtures.inscricao(sufixo));
        }
        morador.setInscricoesPush(inscricoes);
        return morador;
    }

    private static String endpoint(String sufixo) {
        return Fixtures.inscricao(sufixo).endpoint();
    }
}
