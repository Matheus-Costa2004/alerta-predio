package br.com.fiap.alerta.push;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.InscricaoPush;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.push.EnviadorPush.ResultadoEnvio;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.service.AlertaDisparadoEvent;

/**
 * Avisa todos os moradores do prédio, menos o autor e os removidos. Suspensos recebem normalmente.
 * Roda em outra thread, depois que o alerta foi gravado.
 */
@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final AlertaRepository alertas;
    private final MoradorRepository moradores;
    private final EnviadorPush enviador;
    private final ObjectMapper json;

    public NotificacaoService(AlertaRepository alertas, MoradorRepository moradores, EnviadorPush enviador,
            ObjectMapper json) {
        this.alertas = alertas;
        this.moradores = moradores;
        this.enviador = enviador;
        this.json = json;
    }

    @Async
    @EventListener
    public void aoDisparar(AlertaDisparadoEvent evento) {
        notificarNovoAlerta(evento.alertaId());
    }

    /** Devolve quantos moradores foram avisados (os sem inscrição de push veem o alerta pelo polling). */
    public int notificarNovoAlerta(String alertaId) {
        Alerta alerta = alertas.findById(alertaId).orElse(null);
        if (alerta == null) {
            log.warn("Notificação: alerta {} não encontrado", alertaId);
            return 0;
        }
        List<Morador> destinatarios = moradores
                .findByPredioIdAndStatusNot(alerta.getPredioId(), StatusMorador.REMOVIDO).stream()
                .filter(m -> !Objects.equals(m.getId(), alerta.getAutor().moradorId()))
                .toList();

        if (enviador.habilitado()) {
            byte[] payload = payload(alerta);
            int enviados = 0;
            for (Morador morador : destinatarios) {
                for (InscricaoPush inscricao : List.copyOf(morador.getInscricoesPush())) {
                    ResultadoEnvio resultado = enviarComSeguranca(inscricao, payload);
                    if (resultado == ResultadoEnvio.ENVIADO) {
                        enviados++;
                    } else if (resultado == ResultadoEnvio.INSCRICAO_EXPIRADA) {
                        moradores.removerInscricao(morador.getId(), inscricao.endpoint());
                    }
                }
            }
            log.info("Alerta {}: {} push(es) entregue(s) para {} morador(es)", alertaId, enviados,
                    destinatarios.size());
        }
        alertas.registrarNotificados(alertaId, destinatarios.size());
        return destinatarios.size();
    }

    private ResultadoEnvio enviarComSeguranca(InscricaoPush inscricao, byte[] payload) {
        try {
            return enviador.enviar(inscricao, payload);
        } catch (RuntimeException e) {
            log.warn("Falha inesperada no envio de push: {}", e.getMessage());
            return ResultadoEnvio.FALHA;
        }
    }

    byte[] payload(Alerta alerta) {
        String descricao = alerta.getDescricao() == null ? ""
                : ": " + (alerta.getDescricao().length() > 120
                        ? alerta.getDescricao().substring(0, 120) + "…" : alerta.getDescricao());
        Map<String, Object> mensagem = new LinkedHashMap<>();
        mensagem.put("alertaId", alerta.getId());
        mensagem.put("tipo", alerta.getTipo().name());
        mensagem.put("titulo", alerta.getTipo().icone() + " " + alerta.getTipo().rotulo() + " · Apto "
                + alerta.getAutor().apartamento());
        mensagem.put("corpo", alerta.getAutor().nome() + descricao + " Toque para responder.");
        mensagem.put("url", "/#/alerta/" + alerta.getId());
        try {
            return json.writeValueAsBytes(mensagem);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o payload do push", e);
        }
    }
}
