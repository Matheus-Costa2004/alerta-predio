package br.com.fiap.alerta.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.fiap.alerta.model.EventoAuditoria;
import br.com.fiap.alerta.model.TipoEventoAuditoria;

public interface EventoAuditoriaRepository extends MongoRepository<EventoAuditoria, String> {

    List<EventoAuditoria> findByPredioIdOrderByDataHoraDesc(String predioId);

    List<EventoAuditoria> findByPredioIdAndTipo(String predioId, TipoEventoAuditoria tipo);
}
