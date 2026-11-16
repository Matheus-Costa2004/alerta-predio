package br.com.fiap.alerta.ia;

import static br.com.fiap.alerta.Fixtures.AGORA;
import static br.com.fiap.alerta.Fixtures.RELOGIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.config.IaProperties;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.ClassificacaoIa;
import br.com.fiap.alerta.model.Gravidade;
import br.com.fiap.alerta.model.StatusClassificacao;
import br.com.fiap.alerta.model.TipoAlerta;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.service.AlertaDisparadoEvent;

@ExtendWith(MockitoExtension.class)
class TriagemServiceTest {

    @Mock
    private AlertaRepository alertas;
    @Mock
    private ClassificadorAlerta classificador;

    private TriagemService service;
    private Alerta alerta;

    @BeforeEach
    void setUp() {
        service = new TriagemService(alertas, classificador, RELOGIO);
        alerta = Fixtures.alerta("a1", Fixtures.joao(), TipoAlerta.INCENDIO);
        alerta.setFotoUrl("data:image/jpeg;base64,AAAA");
    }

    @Test
    void classificacaoBemSucedidaEhGravadaComoConcluida() {
        when(alertas.findById("a1")).thenReturn(Optional.of(alerta));
        when(classificador.classificar(new PedidoClassificacao(TipoAlerta.INCENDIO, "Descrição de teste",
                "data:image/jpeg;base64,AAAA")))
                .thenReturn(new ResultadoClassificacao(TipoAlerta.INCENDIO, Gravidade.ALTA, 0.92, "chamas", "gemini"));

        service.aoDisparar(new AlertaDisparadoEvent("a1"));

        ClassificacaoIa gravada = classificacaoGravada();
        assertThat(gravada.status()).isEqualTo(StatusClassificacao.CONCLUIDA);
        assertThat(gravada.tipoProvavel()).isEqualTo(TipoAlerta.INCENDIO);
        assertThat(gravada.gravidade()).isEqualTo(Gravidade.ALTA);
        assertThat(gravada.confianca()).isEqualTo(0.92);
        assertThat(gravada.modelo()).isEqualTo("gemini");
        assertThat(gravada.dataHora()).isEqualTo(AGORA);
    }

    // (d) obrigatório: a falha vira "indisponível" e nada é propagado para quem disparou
    @Test
    void falhaDaIaViraClassificacaoIndisponivel() {
        when(alertas.findById("a1")).thenReturn(Optional.of(alerta));
        when(classificador.classificar(any())).thenThrow(new ClassificacaoIndisponivelException("Gemini HTTP 503"));

        ClassificacaoIa resultado = service.triar("a1");

        assertThat(resultado.status()).isEqualTo(StatusClassificacao.INDISPONIVEL);
        assertThat(resultado.erro()).isEqualTo("Gemini HTTP 503");
        assertThat(classificacaoGravada()).isEqualTo(resultado);
    }

    @Test
    void erroInesperadoNaoExpoeDetalhesInternos() {
        when(alertas.findById("a1")).thenReturn(Optional.of(alerta));
        when(classificador.classificar(any())).thenThrow(new IllegalStateException("stack interna com segredo"));

        assertThat(service.triar("a1").erro()).isEqualTo("Erro inesperado na IA (IllegalStateException)");
    }

    @Test
    void semChaveDoGeminiOAlertaFicaComClassificacaoIndisponivel() {
        IaProperties.Gemini semChave = new IaProperties.Gemini("", "gemini-3.5-flash-lite",
                "https://gemini.invalid/v1beta", Duration.ofSeconds(1));
        TriagemService comGemini = new TriagemService(alertas,
                new ClassificadorGemini(RestClient.create(), semChave, new ObjectMapper()), RELOGIO);
        when(alertas.findById("a1")).thenReturn(Optional.of(alerta));

        ClassificacaoIa resultado = comGemini.triar("a1");

        assertThat(resultado.status()).isEqualTo(StatusClassificacao.INDISPONIVEL);
        assertThat(resultado.erro()).isEqualTo("GEMINI_API_KEY não configurada");
    }

    @Test
    void respostaIncompletaDaIaEhIndisponivel() {
        when(alertas.findById("a1")).thenReturn(Optional.of(alerta));
        when(classificador.classificar(any())).thenReturn(null);

        assertThat(service.triar("a1").status()).isEqualTo(StatusClassificacao.INDISPONIVEL);
    }

    @Test
    void falhaAoGravarAClassificacaoNaoPropaga() {
        when(alertas.findById("a1")).thenReturn(Optional.of(alerta));
        when(classificador.classificar(any()))
                .thenReturn(new ResultadoClassificacao(TipoAlerta.GAS, Gravidade.ALTA, 0.7, "", "fake"));
        when(alertas.atualizarClassificacao(eq("a1"), any())).thenThrow(new IllegalStateException("Mongo fora"));

        assertThat(service.triar("a1").status()).isEqualTo(StatusClassificacao.CONCLUIDA);
    }

    @Test
    void alertaInexistenteNaoChamaAIa() {
        when(alertas.findById("x")).thenReturn(Optional.empty());

        assertThat(service.triar("x")).isNull();
        verifyNoInteractions(classificador);
        verify(alertas, never()).atualizarClassificacao(any(), any());
    }

    private ClassificacaoIa classificacaoGravada() {
        ArgumentCaptor<ClassificacaoIa> captor = ArgumentCaptor.forClass(ClassificacaoIa.class);
        verify(alertas).atualizarClassificacao(eq("a1"), captor.capture());
        return captor.getValue();
    }
}
