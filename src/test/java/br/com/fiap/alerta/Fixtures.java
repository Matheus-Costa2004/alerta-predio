package br.com.fiap.alerta;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Autor;
import br.com.fiap.alerta.model.ClassificacaoIa;
import br.com.fiap.alerta.model.Consentimento;
import br.com.fiap.alerta.model.InscricaoPush;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.NecessidadeEspecial;
import br.com.fiap.alerta.model.Papel;
import br.com.fiap.alerta.model.StatusAlerta;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.model.TipoAlerta;

/** Dados de teste compartilhados pelos testes unitários e de controller. */
public final class Fixtures {

    public static final String PREDIO = "predio-1";
    public static final String OUTRO_PREDIO = "predio-2";
    public static final Instant AGORA = Instant.parse("2026-11-14T15:00:00Z");
    public static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    private Fixtures() {
    }

    public static Morador morador(String id, String predioId, String nome, String apartamento) {
        Morador m = new Morador();
        m.setId(id);
        m.setPredioId(predioId);
        m.setNome(nome);
        m.setApartamento(apartamento);
        m.setTelefone("(11) 90000-" + apartamento);
        m.setPapel(Papel.MORADOR);
        m.setStatus(StatusMorador.ATIVO);
        return m;
    }

    public static Morador sindica() {
        Morador m = morador("carla", PREDIO, "Carla Mendes", "101");
        m.setPapel(Papel.SINDICO);
        return m;
    }

    public static Morador ana() {
        return comNecessidade(morador("ana", PREDIO, "Ana Souza", "302"), "Cadeirante");
    }

    public static Morador joao() {
        return morador("joao", PREDIO, "João Lima", "204");
    }

    public static Morador deOutroPredio() {
        return morador("pedro", OUTRO_PREDIO, "Pedro Alves", "11");
    }

    public static Morador comNecessidade(Morador morador, String descricao) {
        morador.setNecessidadeEspecial(new NecessidadeEspecial(descricao, new Consentimento(true, AGORA, "1.0")));
        return morador;
    }

    public static Morador comStatus(Morador morador, StatusMorador status, int advertencias) {
        morador.setStatus(status);
        morador.setQtdAdvertencias(advertencias);
        return morador;
    }

    public static Alerta alerta(String id, Morador autor, TipoAlerta tipo) {
        Alerta a = new Alerta();
        a.setId(id);
        a.setPredioId(autor.getPredioId());
        a.setAutor(Autor.de(autor));
        a.setTipo(tipo);
        a.setDescricao("Descrição de teste");
        a.setStatus(StatusAlerta.ATIVO);
        a.setClassificacao(ClassificacaoIa.pendente());
        a.setDataHora(AGORA);
        return a;
    }

    public static InscricaoPush inscricao(String sufixo) {
        return new InscricaoPush("https://fcm.googleapis.com/fcm/send/" + sufixo, "p256dh-" + sufixo,
                "auth-" + sufixo, AGORA);
    }
}
