package br.com.fiap.alerta.push;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import br.com.fiap.alerta.dto.ChavePublicaResponse;
import br.com.fiap.alerta.dto.InscricaoPushRequest;
import br.com.fiap.alerta.exception.RequisicaoInvalidaException;
import br.com.fiap.alerta.model.InscricaoPush;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.service.ControleAcesso;

@Service
public class InscricaoPushService {

    /**
     * O servidor faz POST no endpoint informado pelo navegador; aceitar só push services conhecidos impede
     * que alguém use o envio de push para fazer o servidor chamar endereços arbitrários (SSRF).
     */
    static final List<String> SERVICOS_DE_PUSH = List.of("googleapis.com", "push.services.mozilla.com",
            "notify.windows.com", "push.apple.com");

    private final MoradorRepository moradores;
    private final ControleAcesso acesso;
    private final EnviadorPush enviador;
    private final Clock relogio;

    public InscricaoPushService(MoradorRepository moradores, ControleAcesso acesso, EnviadorPush enviador,
            Clock relogio) {
        this.moradores = moradores;
        this.acesso = acesso;
        this.enviador = enviador;
        this.relogio = relogio;
    }

    public ChavePublicaResponse chavePublica() {
        return new ChavePublicaResponse(enviador.habilitado(), enviador.chavePublica());
    }

    /** Um navegador pertence a um morador só: trocar de conta no mesmo aparelho move a inscrição. */
    public void inscrever(InscricaoPushRequest dados, Morador morador) {
        acesso.exigirMembro(morador, morador.getPredioId());
        exigirServicoConhecido(dados.endpoint());
        moradores.removerEndpointDeTodos(dados.endpoint());
        moradores.adicionarInscricao(morador.getId(), new InscricaoPush(dados.endpoint(), dados.keys().p256dh(),
                dados.keys().auth(), Instant.now(relogio)));
    }

    public void cancelar(String endpoint, Morador morador) {
        moradores.removerInscricao(morador.getId(), endpoint);
    }

    static void exigirServicoConhecido(String endpoint) {
        String host;
        try {
            host = URI.create(endpoint).getHost();
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("Endpoint de push inválido.");
        }
        String hostMinusculo = host == null ? "" : host.toLowerCase(Locale.ROOT);
        boolean conhecido = SERVICOS_DE_PUSH.stream()
                .anyMatch(s -> hostMinusculo.equals(s) || hostMinusculo.endsWith("." + s));
        if (!conhecido) {
            throw new RequisicaoInvalidaException("Serviço de push não reconhecido: " + hostMinusculo);
        }
    }
}
