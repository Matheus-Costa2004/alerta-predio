package br.com.fiap.alerta.service;

import static br.com.fiap.alerta.service.AuditoriaService.detalhes;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import br.com.fiap.alerta.dto.AlertaResumo;
import br.com.fiap.alerta.dto.DisparoAlertaRequest;
import br.com.fiap.alerta.dto.EncerramentoRequest;
import br.com.fiap.alerta.exception.ConflitoException;
import br.com.fiap.alerta.exception.RecursoNaoEncontradoException;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Autor;
import br.com.fiap.alerta.model.Checkin;
import br.com.fiap.alerta.model.ClassificacaoIa;
import br.com.fiap.alerta.model.Confirmacao;
import br.com.fiap.alerta.model.Encerramento;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.Penalidade;
import br.com.fiap.alerta.model.SituacaoCheckin;
import br.com.fiap.alerta.model.StatusAlerta;
import br.com.fiap.alerta.model.TipoEventoAuditoria;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.repository.CheckinRepository;

@Service
public class AlertaService {

    private final AlertaRepository alertas;
    private final CheckinRepository checkins;
    private final ControleAcesso acesso;
    private final PenalidadeService penalidades;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public AlertaService(AlertaRepository alertas, CheckinRepository checkins, ControleAcesso acesso,
            PenalidadeService penalidades, AuditoriaService auditoria, ApplicationEventPublisher eventos,
            Clock relogio) {
        this.alertas = alertas;
        this.checkins = checkins;
        this.acesso = acesso;
        this.penalidades = penalidades;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * Grava o alerta na hora e devolve. A triagem da IA e o push reagem ao evento em outra thread:
     * o disparo nunca espera nem depende da IA.
     */
    public Alerta disparar(String predioId, DisparoAlertaRequest dados, Morador autor) {
        acesso.exigirMembro(autor, predioId);
        penalidades.exigirBotaoLiberado(autor);

        Alerta alerta = new Alerta();
        alerta.setPredioId(predioId);
        alerta.setAutor(Autor.de(autor));
        alerta.setTipo(dados.tipo());
        alerta.setDescricao(dados.descricao() == null || dados.descricao().isBlank() ? null
                : dados.descricao().trim());
        alerta.setFotoUrl(dados.fotoUrl());
        alerta.setStatus(StatusAlerta.ATIVO);
        alerta.setClassificacao(ClassificacaoIa.pendente());
        alerta.setDataHora(Instant.now(relogio));
        Alerta salvo = alertas.save(alerta);

        auditoria.registrar(TipoEventoAuditoria.ALERTA_DISPARADO, predioId, autor,
                dados.tipo().rotulo() + " disparado pelo apto " + autor.getApartamento(),
                detalhes("alertaId", salvo.getId(), "tipo", dados.tipo().name(), "temFoto", dados.fotoUrl() != null));
        eventos.publishEvent(new AlertaDisparadoEvent(salvo.getId()));
        return salvo;
    }

    public List<AlertaResumo> listar(String predioId, StatusAlerta status, Morador morador) {
        acesso.exigirMembro(morador, predioId);
        List<Alerta> encontrados = status == null
                ? alertas.findByPredioIdOrderByDataHoraDesc(predioId)
                : alertas.findByPredioIdAndStatusOrderByDataHoraDesc(predioId, status);
        return encontrados.stream().map(AlertaResumo::de).toList();
    }

    /** Morador de outro prédio recebe 403 (e a tentativa vai para a auditoria do prédio do alerta). */
    public Alerta buscar(String alertaId, Morador morador) {
        Alerta alerta = carregar(alertaId);
        acesso.exigirMembro(morador, alerta.getPredioId());
        return alerta;
    }

    public Alerta confirmar(String alertaId, Morador morador) {
        Alerta alerta = buscar(alertaId, morador);
        if (Objects.equals(alerta.getAutor().moradorId(), morador.getId())) {
            throw new ConflitoException("Quem disparou o alerta não pode confirmá-lo.");
        }
        exigirAtivo(alerta);
        long alterados = alertas.adicionarConfirmacao(alertaId, morador.getId(),
                Confirmacao.de(morador, Instant.now(relogio)));
        if (alterados == 0) {
            throw new ConflitoException("Você já confirmou este alerta.");
        }
        return carregar(alertaId);
    }

    /** Check-in vale também para morador suspenso: a suspensão só bloqueia o botão de disparo. */
    public Checkin checkin(String alertaId, SituacaoCheckin situacao, Morador morador) {
        Alerta alerta = buscar(alertaId, morador);
        exigirAtivo(alerta);
        Instant agora = Instant.now(relogio);
        Checkin checkin = checkins.findByAlertaIdAndMoradorId(alertaId, morador.getId())
                .orElseGet(() -> new Checkin(alertaId, morador.getId(), situacao, agora));
        checkin.setSituacao(situacao);
        checkin.setDataHora(agora);
        return checkins.save(checkin);
    }

    /** Só o síndico encerra; FALSO_MA_FE aplica a escada de penalidades ao autor. */
    public Alerta encerrar(String alertaId, EncerramentoRequest dados, Morador sindico) {
        Alerta alerta = carregar(alertaId);
        acesso.exigirSindico(sindico, alerta.getPredioId(), "encerrar alertas");
        exigirAtivo(alerta);

        Encerramento encerramento = new Encerramento(dados.motivo(), dados.observacao(), sindico.getId(),
                sindico.getNome(), null, Instant.now(relogio));
        // Encerra primeiro (atômico): se dois síndicos clicarem juntos, só um aplica a penalidade
        if (alertas.encerrar(alertaId, encerramento) == 0) {
            throw new ConflitoException("Este alerta já foi encerrado.");
        }
        Penalidade penalidade = penalidades.avaliarEncerramento(alerta, dados.motivo(), sindico);
        if (penalidade != null) {
            alertas.registrarPenalidade(alertaId, penalidade);
        }
        auditoria.registrar(TipoEventoAuditoria.ALERTA_ENCERRADO, alerta.getPredioId(), sindico,
                "Alerta encerrado como " + dados.motivo(),
                detalhes("alertaId", alertaId, "motivo", dados.motivo().name(),
                        "penalidade", penalidade == null ? null : penalidade.name()));
        return carregar(alertaId);
    }

    private Alerta carregar(String alertaId) {
        return alertas.findById(alertaId).orElseThrow(() -> new RecursoNaoEncontradoException("Alerta", alertaId));
    }

    private static void exigirAtivo(Alerta alerta) {
        if (alerta.getStatus() == StatusAlerta.ENCERRADO) {
            throw new ConflitoException("Este alerta já foi encerrado.");
        }
    }
}
