package br.com.fiap.alerta.service;

import static br.com.fiap.alerta.service.AuditoriaService.detalhes;

import java.util.Objects;

import org.springframework.stereotype.Component;

import br.com.fiap.alerta.exception.AcessoNegadoException;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.model.TipoEventoAuditoria;

/** Regras de quem pode ver e fazer o quê em cada prédio; toda negação fica na auditoria do prédio alvo. */
@Component
public class ControleAcesso {

    private final AuditoriaService auditoria;

    public ControleAcesso(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    /** Morador de outro prédio, ou removido deste, recebe 403. Suspenso continua sendo membro. */
    public void exigirMembro(Morador morador, String predioId) {
        if (!Objects.equals(morador.getPredioId(), predioId)) {
            auditoria.registrar(TipoEventoAuditoria.ACESSO_NEGADO, predioId, morador,
                    "Morador de outro prédio tentou acessar este prédio",
                    detalhes("predioDoMorador", morador.getPredioId()));
            throw new AcessoNegadoException("Você não é morador deste prédio.");
        }
        if (morador.getStatus() == StatusMorador.REMOVIDO) {
            auditoria.registrar(TipoEventoAuditoria.ACESSO_NEGADO, predioId, morador,
                    "Morador removido tentou acessar o prédio", detalhes());
            throw new AcessoNegadoException("Você foi removido deste prédio após 3 alertas falsos de má-fé.");
        }
    }

    public void exigirSindico(Morador morador, String predioId, String acao) {
        exigirMembro(morador, predioId);
        if (!morador.isSindico()) {
            auditoria.registrar(TipoEventoAuditoria.ACESSO_NEGADO, predioId, morador,
                    "Morador sem papel de síndico tentou " + acao, detalhes());
            throw new AcessoNegadoException("Somente o síndico pode " + acao + ".");
        }
    }
}
