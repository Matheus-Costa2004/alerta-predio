package br.com.fiap.alerta.service;

import static br.com.fiap.alerta.service.AuditoriaService.detalhes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.fiap.alerta.exception.AcessoNegadoException;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.MotivoEncerramento;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.Penalidade;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.model.TipoEventoAuditoria;
import br.com.fiap.alerta.repository.MoradorRepository;

/**
 * Regra de advertência: só alerta encerrado como FALSO_MA_FE conta.
 * 1ª = advertência; 2ª = botão de alerta suspenso por 30 dias; 3ª = remoção do prédio.
 * O suspenso continua recebendo alertas, confirmando e fazendo check-in.
 */
@Service
public class PenalidadeService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final MoradorRepository moradores;
    private final AuditoriaService auditoria;
    private final Clock relogio;
    private final Duration suspensao;

    public PenalidadeService(MoradorRepository moradores, AuditoriaService auditoria, Clock relogio,
            @Value("${alerta.suspensao-dias:30}") int diasSuspensao) {
        this.moradores = moradores;
        this.auditoria = auditoria;
        this.relogio = relogio;
        this.suspensao = Duration.ofDays(diasSuspensao);
    }

    /** Bloqueia o disparo de quem está suspenso; suspensão vencida volta para ADVERTIDO. */
    public void exigirBotaoLiberado(Morador morador) {
        if (morador.getStatus() != StatusMorador.SUSPENSO) {
            return;
        }
        Instant fim = morador.getSuspensoAte();
        if (fim != null && !Instant.now(relogio).isBefore(fim)) {
            morador.setStatus(StatusMorador.ADVERTIDO);
            morador.setSuspensoAte(null);
            moradores.save(morador);
            return;
        }
        auditoria.registrar(TipoEventoAuditoria.DISPARO_BLOQUEADO, morador.getPredioId(), morador,
                "Morador suspenso tentou disparar um alerta", detalhes("suspensoAte", fim));
        throw new AcessoNegadoException("Seu botão de alerta está suspenso"
                + (fim == null ? "" : " até " + DATA.format(fim))
                + " por alertas falsos de má-fé. Você continua recebendo alertas e pode fazer check-in.");
    }

    /** Devolve a penalidade aplicada ao autor, ou null quando o motivo não gera advertência. */
    public Penalidade avaliarEncerramento(Alerta alerta, MotivoEncerramento motivo, Morador sindico) {
        if (motivo != MotivoEncerramento.FALSO_MA_FE) {
            return null;
        }
        Morador autor = moradores.findById(alerta.getAutor().moradorId()).orElse(null);
        if (autor == null || autor.getStatus() == StatusMorador.REMOVIDO) {
            return null;
        }
        return aplicarAdvertencia(autor, alerta, sindico);
    }

    private Penalidade aplicarAdvertencia(Morador autor, Alerta alerta, Morador sindico) {
        int advertencias = autor.getQtdAdvertencias() + 1;
        autor.setQtdAdvertencias(advertencias);

        Penalidade penalidade;
        TipoEventoAuditoria tipo;
        String descricao;
        if (advertencias == 1) {
            autor.setStatus(StatusMorador.ADVERTIDO);
            penalidade = Penalidade.ADVERTENCIA;
            tipo = TipoEventoAuditoria.ADVERTENCIA;
            descricao = "1ª advertência por alerta falso de má-fé";
        } else if (advertencias == 2) {
            autor.setStatus(StatusMorador.SUSPENSO);
            autor.setSuspensoAte(Instant.now(relogio).plus(suspensao));
            penalidade = Penalidade.SUSPENSAO;
            tipo = TipoEventoAuditoria.SUSPENSAO;
            descricao = "2ª advertência: botão de alerta suspenso por " + suspensao.toDays() + " dias";
        } else {
            autor.setStatus(StatusMorador.REMOVIDO);
            autor.setSuspensoAte(null);
            autor.setInscricoesPush(new ArrayList<>());
            penalidade = Penalidade.REMOCAO;
            tipo = TipoEventoAuditoria.REMOCAO;
            descricao = "3ª advertência: morador removido do prédio";
        }
        moradores.save(autor);
        auditoria.registrar(tipo, autor.getPredioId(), sindico, descricao,
                detalhes("alertaId", alerta.getId(), "moradorPenalizadoId", autor.getId(),
                        "moradorPenalizado", autor.getNome() + " (apto " + autor.getApartamento() + ")",
                        "advertencias", advertencias, "suspensoAte", autor.getSuspensoAte()));
        return penalidade;
    }
}
