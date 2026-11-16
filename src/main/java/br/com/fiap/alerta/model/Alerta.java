package br.com.fiap.alerta.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("alertas")
@CompoundIndex(name = "predio_data", def = "{'predioId': 1, 'dataHora': -1}")
public class Alerta {

    @Id
    private String id;

    private String predioId;

    private Autor autor;

    private TipoAlerta tipo;

    private String descricao;

    /** URL http(s) ou data URL (foto tirada no PWA, já reduzida no navegador). */
    private String fotoUrl;

    private StatusAlerta status = StatusAlerta.ATIVO;

    private List<Confirmacao> confirmacoes = new ArrayList<>();

    private ClassificacaoIa classificacao = ClassificacaoIa.pendente();

    private Encerramento encerramento;

    /** Quantos moradores o disparo tentou notificar (preenchido pelo envio assíncrono). */
    private Integer notificados;

    private Instant dataHora;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPredioId() {
        return predioId;
    }

    public void setPredioId(String predioId) {
        this.predioId = predioId;
    }

    public Autor getAutor() {
        return autor;
    }

    public void setAutor(Autor autor) {
        this.autor = autor;
    }

    public TipoAlerta getTipo() {
        return tipo;
    }

    public void setTipo(TipoAlerta tipo) {
        this.tipo = tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public StatusAlerta getStatus() {
        return status;
    }

    public void setStatus(StatusAlerta status) {
        this.status = status;
    }

    public List<Confirmacao> getConfirmacoes() {
        return confirmacoes;
    }

    public void setConfirmacoes(List<Confirmacao> confirmacoes) {
        this.confirmacoes = confirmacoes == null ? new ArrayList<>() : confirmacoes;
    }

    public ClassificacaoIa getClassificacao() {
        return classificacao;
    }

    public void setClassificacao(ClassificacaoIa classificacao) {
        this.classificacao = classificacao;
    }

    public Encerramento getEncerramento() {
        return encerramento;
    }

    public void setEncerramento(Encerramento encerramento) {
        this.encerramento = encerramento;
    }

    public Integer getNotificados() {
        return notificados;
    }

    public void setNotificados(Integer notificados) {
        this.notificados = notificados;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public void setDataHora(Instant dataHora) {
        this.dataHora = dataHora;
    }
}
