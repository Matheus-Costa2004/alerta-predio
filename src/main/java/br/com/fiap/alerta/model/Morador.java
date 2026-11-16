package br.com.fiap.alerta.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Document("moradores")
public class Morador {

    @Id
    private String id;

    @Indexed
    private String predioId;

    private String nome;

    private String apartamento;

    private String telefone;

    private Papel papel = Papel.MORADOR;

    private StatusMorador status = StatusMorador.ATIVO;

    /** Só alertas encerrados como FALSO_MA_FE contam. */
    private int qtdAdvertencias;

    /** Fim da suspensão do botão de alerta (2ª advertência). */
    private Instant suspensoAte;

    private NecessidadeEspecial necessidadeEspecial;

    private Instant dataEntrada;

    /** SHA-256 do token de acesso; o token em si só é devolvido uma vez, na criação. */
    @JsonIgnore
    @Indexed(unique = true, sparse = true)
    private String tokenHash;

    @JsonIgnore
    private List<InscricaoPush> inscricoesPush = new ArrayList<>();

    @JsonIgnore
    public boolean isSindico() {
        return papel == Papel.SINDICO;
    }

    @JsonIgnore
    public boolean isVulneravel() {
        return necessidadeEspecial != null;
    }

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

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getApartamento() {
        return apartamento;
    }

    public void setApartamento(String apartamento) {
        this.apartamento = apartamento;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public Papel getPapel() {
        return papel;
    }

    public void setPapel(Papel papel) {
        this.papel = papel;
    }

    public StatusMorador getStatus() {
        return status;
    }

    public void setStatus(StatusMorador status) {
        this.status = status;
    }

    public int getQtdAdvertencias() {
        return qtdAdvertencias;
    }

    public void setQtdAdvertencias(int qtdAdvertencias) {
        this.qtdAdvertencias = qtdAdvertencias;
    }

    public Instant getSuspensoAte() {
        return suspensoAte;
    }

    public void setSuspensoAte(Instant suspensoAte) {
        this.suspensoAte = suspensoAte;
    }

    public NecessidadeEspecial getNecessidadeEspecial() {
        return necessidadeEspecial;
    }

    public void setNecessidadeEspecial(NecessidadeEspecial necessidadeEspecial) {
        this.necessidadeEspecial = necessidadeEspecial;
    }

    public Instant getDataEntrada() {
        return dataEntrada;
    }

    public void setDataEntrada(Instant dataEntrada) {
        this.dataEntrada = dataEntrada;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public List<InscricaoPush> getInscricoesPush() {
        return inscricoesPush;
    }

    public void setInscricoesPush(List<InscricaoPush> inscricoesPush) {
        this.inscricoesPush = inscricoesPush == null ? new ArrayList<>() : inscricoesPush;
    }
}
