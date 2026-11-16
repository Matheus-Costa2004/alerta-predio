package br.com.fiap.alerta.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.ClassificacaoIa;
import br.com.fiap.alerta.model.Confirmacao;
import br.com.fiap.alerta.model.Encerramento;
import br.com.fiap.alerta.model.Penalidade;
import br.com.fiap.alerta.model.StatusAlerta;

/**
 * A triagem da IA, as confirmações e o encerramento podem acontecer ao mesmo tempo no mesmo alerta, então
 * cada um altera só o próprio campo, com operação atômica, em vez de regravar o documento inteiro.
 */
public interface AlertaRepository extends MongoRepository<Alerta, String> {

    List<Alerta> findByPredioIdOrderByDataHoraDesc(String predioId);

    List<Alerta> findByPredioIdAndStatusOrderByDataHoraDesc(String predioId, StatusAlerta status);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'classificacao': ?1 } }")
    long atualizarClassificacao(String id, ClassificacaoIa classificacao);

    /** Só grava se o alerta está ativo e o morador ainda não confirmou: devolve 0 caso contrário. */
    @Query("{ '_id': ?0, 'status': 'ATIVO', 'confirmacoes.moradorId': { '$ne': ?1 } }")
    @Update("{ '$push': { 'confirmacoes': ?2 } }")
    long adicionarConfirmacao(String id, String moradorId, Confirmacao confirmacao);

    /** Só encerra alerta ativo: devolve 0 se outro síndico encerrou antes. */
    @Query("{ '_id': ?0, 'status': 'ATIVO' }")
    @Update("{ '$set': { 'status': 'ENCERRADO', 'encerramento': ?1 } }")
    long encerrar(String id, Encerramento encerramento);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'encerramento.penalidadeAplicada': ?1 } }")
    long registrarPenalidade(String id, Penalidade penalidade);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'notificados': ?1 } }")
    long registrarNotificados(String id, int notificados);
}
