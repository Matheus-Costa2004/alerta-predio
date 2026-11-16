package br.com.fiap.alerta.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("predios")
public class Predio {

    @Id
    private String id;

    private String nome;

    private String endereco;

    /** Código curto que o síndico compartilha com os vizinhos para eles entrarem no prédio. */
    @Indexed(unique = true)
    private String codigoConvite;

    private String sindicoId;

    private Instant dataCriacao;

    public Predio() {
    }

    public Predio(String nome, String endereco, String codigoConvite, Instant dataCriacao) {
        this.nome = nome;
        this.endereco = endereco;
        this.codigoConvite = codigoConvite;
        this.dataCriacao = dataCriacao;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getCodigoConvite() {
        return codigoConvite;
    }

    public void setCodigoConvite(String codigoConvite) {
        this.codigoConvite = codigoConvite;
    }

    public String getSindicoId() {
        return sindicoId;
    }

    public void setSindicoId(String sindicoId) {
        this.sindicoId = sindicoId;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(Instant dataCriacao) {
        this.dataCriacao = dataCriacao;
    }
}
