package br.com.fiap.alerta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.fiap.alerta.model.Checkin;

public interface CheckinRepository extends MongoRepository<Checkin, String> {

    Optional<Checkin> findByAlertaIdAndMoradorId(String alertaId, String moradorId);

    List<Checkin> findByAlertaId(String alertaId);
}
