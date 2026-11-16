package br.com.fiap.alerta.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Resposta do morador a um alerta; um por morador por alerta (pode ser atualizado). */
@Document("checkins")
@CompoundIndex(name = "alerta_morador", def = "{'alertaId': 1, 'moradorId': 1}", unique = true)
public class Checkin {

    @Id
    private String id;

    private String alertaId;

    private String moradorId;

    private SituacaoCheckin situacao;

    private Instant dataHora;

    public Checkin() {
    }

    public Checkin(String alertaId, String moradorId, SituacaoCheckin situacao, Instant dataHora) {
        this.alertaId = alertaId;
        this.moradorId = moradorId;
        this.situacao = situacao;
        this.dataHora = dataHora;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlertaId() {
        return alertaId;
    }

    public void setAlertaId(String alertaId) {
        this.alertaId = alertaId;
    }

    public String getMoradorId() {
        return moradorId;
    }

    public void setMoradorId(String moradorId) {
        this.moradorId = moradorId;
    }

    public SituacaoCheckin getSituacao() {
        return situacao;
    }

    public void setSituacao(SituacaoCheckin situacao) {
        this.situacao = situacao;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public void setDataHora(Instant dataHora) {
        this.dataHora = dataHora;
    }
}
