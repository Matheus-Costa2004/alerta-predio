package br.com.fiap.alerta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import br.com.fiap.alerta.model.InscricaoPush;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.StatusMorador;

public interface MoradorRepository extends MongoRepository<Morador, String> {

    Optional<Morador> findByTokenHash(String tokenHash);

    List<Morador> findByPredioIdOrderByApartamentoAsc(String predioId);

    List<Morador> findByPredioIdAndStatusNot(String predioId, StatusMorador status);

    @Query("{ '_id': ?0 }")
    @Update("{ '$push': { 'inscricoesPush': ?1 } }")
    long adicionarInscricao(String moradorId, InscricaoPush inscricao);

    @Query("{ '_id': ?0 }")
    @Update("{ '$pull': { 'inscricoesPush': { 'endpoint': ?1 } } }")
    long removerInscricao(String moradorId, String endpoint);

    /** Um navegador pertence a um morador só: tira o endpoint de quem o tinha antes. */
    @Query("{ 'inscricoesPush.endpoint': ?0 }")
    @Update("{ '$pull': { 'inscricoesPush': { 'endpoint': ?0 } } }")
    long removerEndpointDeTodos(String endpoint);
}
