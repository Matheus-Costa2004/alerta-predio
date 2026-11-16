package br.com.fiap.alerta.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.fiap.alerta.model.EventoAuditoria;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.TipoEventoAuditoria;
import br.com.fiap.alerta.repository.EventoAuditoriaRepository;

/** Trilha append-only: só existe inserção e consulta. */
@Service
public class AuditoriaService {

    private final EventoAuditoriaRepository repository;
    private final Clock relogio;

    public AuditoriaService(EventoAuditoriaRepository repository, Clock relogio) {
        this.repository = repository;
        this.relogio = relogio;
    }

    /** O horário é sempre o do servidor; {@code ator} nulo indica uma ação do sistema. */
    public EventoAuditoria registrar(TipoEventoAuditoria tipo, String predioId, Morador ator, String descricao,
            Map<String, Object> detalhes) {
        return repository.save(new EventoAuditoria(tipo, predioId, ator == null ? null : ator.getId(),
                ator == null ? null : ator.getNome(), descricao, detalhes, Instant.now(relogio)));
    }

    public List<EventoAuditoria> doPredio(String predioId) {
        return repository.findByPredioIdOrderByDataHoraDesc(predioId);
    }

    /** Map ordenado a partir de pares chave/valor, ignorando valores nulos (Map.of não aceita nulo). */
    public static Map<String, Object> detalhes(Object... chavesEValores) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            if (chavesEValores[i + 1] != null) {
                mapa.put((String) chavesEValores[i], chavesEValores[i + 1]);
            }
        }
        return mapa;
    }
}
