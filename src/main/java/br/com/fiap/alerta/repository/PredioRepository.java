package br.com.fiap.alerta.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.fiap.alerta.model.Predio;

public interface PredioRepository extends MongoRepository<Predio, String> {

    Optional<Predio> findByCodigoConvite(String codigoConvite);

    boolean existsByCodigoConvite(String codigoConvite);
}
