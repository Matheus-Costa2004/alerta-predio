package br.com.fiap.alerta.ia;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.ClassificacaoIa;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.service.AlertaDisparadoEvent;

/**
 * Roda depois que o alerta já foi gravado, em outra thread. Qualquer falha da IA vira classificação
 * INDISPONIVEL; o alerta continua ATIVO e os moradores já foram avisados.
 */
@Service
public class TriagemService {

    private static final Logger log = LoggerFactory.getLogger(TriagemService.class);

    private final AlertaRepository alertas;
    private final ClassificadorAlerta classificador;
    private final Clock relogio;

    public TriagemService(AlertaRepository alertas, ClassificadorAlerta classificador, Clock relogio) {
        this.alertas = alertas;
        this.classificador = classificador;
        this.relogio = relogio;
    }

    @Async
    @EventListener
    public void aoDisparar(AlertaDisparadoEvent evento) {
        triar(evento.alertaId());
    }

    public ClassificacaoIa triar(String alertaId) {
        Alerta alerta = alertas.findById(alertaId).orElse(null);
        if (alerta == null) {
            log.warn("Triagem: alerta {} não encontrado", alertaId);
            return null;
        }
        ClassificacaoIa classificacao;
        try {
            ResultadoClassificacao resultado = classificador.classificar(
                    new PedidoClassificacao(alerta.getTipo(), alerta.getDescricao(), alerta.getFotoUrl()));
            if (resultado == null || resultado.tipoProvavel() == null || resultado.gravidade() == null) {
                throw new ClassificacaoIndisponivelException("A IA devolveu uma resposta incompleta");
            }
            classificacao = ClassificacaoIa.concluida(resultado.tipoProvavel(), resultado.gravidade(),
                    resultado.confianca(), resultado.justificativa(), resultado.modelo(), Instant.now(relogio));
        } catch (Exception e) {
            log.warn("Triagem indisponível para o alerta {}: {}", alertaId, e.getMessage());
            classificacao = ClassificacaoIa.indisponivel(motivo(e), Instant.now(relogio));
        }
        try {
            alertas.atualizarClassificacao(alertaId, classificacao);
        } catch (RuntimeException e) {
            log.error("Triagem: não foi possível gravar a classificação do alerta {}", alertaId, e);
        }
        return classificacao;
    }

    private static String motivo(Exception e) {
        return e instanceof ClassificacaoIndisponivelException
                ? e.getMessage()
                : "Erro inesperado na IA (" + e.getClass().getSimpleName() + ")";
    }
}
