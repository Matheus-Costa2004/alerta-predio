package br.com.fiap.alerta.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Trilha append-only de ações sensíveis: a API só lê, nunca altera nem apaga. */
@Document("auditoria")
public class EventoAuditoria {

    @Id
    private String id;

    private TipoEventoAuditoria tipo;

    @Indexed
    private String predioId;

    /** Quem executou a ação (nulo quando é o sistema). */
    private String moradorId;

    private String moradorNome;

    private String descricao;

    private Map<String, Object> detalhes = new LinkedHashMap<>();

    private Instant dataHora;

    public EventoAuditoria() {
    }

    public EventoAuditoria(TipoEventoAuditoria tipo, String predioId, String moradorId, String moradorNome,
            String descricao, Map<String, Object> detalhes, Instant dataHora) {
        this.tipo = tipo;
        this.predioId = predioId;
        this.moradorId = moradorId;
        this.moradorNome = moradorNome;
        this.descricao = descricao;
        this.detalhes = new LinkedHashMap<>(detalhes);
        this.dataHora = dataHora;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TipoEventoAuditoria getTipo() {
        return tipo;
    }

    public void setTipo(TipoEventoAuditoria tipo) {
        this.tipo = tipo;
    }

    public String getPredioId() {
        return predioId;
    }

    public void setPredioId(String predioId) {
        this.predioId = predioId;
    }

    public String getMoradorId() {
        return moradorId;
    }

    public void setMoradorId(String moradorId) {
        this.moradorId = moradorId;
    }

    public String getMoradorNome() {
        return moradorNome;
    }

    public void setMoradorNome(String moradorNome) {
        this.moradorNome = moradorNome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Map<String, Object> getDetalhes() {
        return detalhes;
    }

    public void setDetalhes(Map<String, Object> detalhes) {
        this.detalhes = detalhes;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public void setDataHora(Instant dataHora) {
        this.dataHora = dataHora;
    }
}
