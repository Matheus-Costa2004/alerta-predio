package br.com.fiap.alerta.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.fiap.alerta.Fixtures;
import br.com.fiap.alerta.exception.NaoAutenticadoException;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.repository.MoradorRepository;

@ExtendWith(MockitoExtension.class)
class AutenticacaoServiceTest {

    @Mock
    private MoradorRepository moradores;

    @InjectMocks
    private AutenticacaoService service;

    @Test
    void tokensSaoAleatoriosEOHashEhSha256Hex() {
        String token = service.novoToken();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(service.novoToken());
        assertThat(AutenticacaoService.hash("demo-ana")).hasSize(64).isEqualTo(AutenticacaoService.hash("demo-ana"))
                .isNotEqualTo(AutenticacaoService.hash("demo-joao"));
    }

    @Test
    void autenticaPeloHashDoToken() {
        Morador ana = Fixtures.ana();
        when(moradores.findByTokenHash(AutenticacaoService.hash("token-da-ana"))).thenReturn(Optional.of(ana));

        assertThat(service.autenticar("token-da-ana")).isSameAs(ana);
    }

    @Test
    void tokenDesconhecidoEh401() {
        when(moradores.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.autenticar("inventado")).isInstanceOf(NaoAutenticadoException.class);
    }
}
