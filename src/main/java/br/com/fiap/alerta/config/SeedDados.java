package br.com.fiap.alerta.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.fiap.alerta.auth.AutenticacaoService;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Autor;
import br.com.fiap.alerta.model.Checkin;
import br.com.fiap.alerta.model.ClassificacaoIa;
import br.com.fiap.alerta.model.Confirmacao;
import br.com.fiap.alerta.model.Consentimento;
import br.com.fiap.alerta.model.Encerramento;
import br.com.fiap.alerta.model.EventoAuditoria;
import br.com.fiap.alerta.model.Gravidade;
import br.com.fiap.alerta.model.MotivoEncerramento;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.NecessidadeEspecial;
import br.com.fiap.alerta.model.Papel;
import br.com.fiap.alerta.model.Predio;
import br.com.fiap.alerta.model.SituacaoCheckin;
import br.com.fiap.alerta.model.StatusAlerta;
import br.com.fiap.alerta.model.TipoAlerta;
import br.com.fiap.alerta.model.TipoEventoAuditoria;
import br.com.fiap.alerta.repository.AlertaRepository;
import br.com.fiap.alerta.repository.CheckinRepository;
import br.com.fiap.alerta.repository.EventoAuditoriaRepository;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.repository.PredioRepository;
import br.com.fiap.alerta.service.PredioService;

/**
 * Prédio de demonstração (SEED_ENABLED): síndica, quatro moradores (uma com necessidade especial) e um
 * alerta de alagamento já encerrado. Só roda com a collection de prédios vazia, então pode ficar ligado.
 */
@Component
@ConditionalOnProperty(name = "alerta.seed.enabled", havingValue = "true")
public class SeedDados implements CommandLineRunner {

    public static final String PREDIO_ID = "predio-demo";
    public static final String CODIGO_CONVITE = "ACACIA";
    public static final String ALERTA_ID = "alerta-demo-alagamento";

    private static final Logger log = LoggerFactory.getLogger(SeedDados.class);

    private final PredioRepository predios;
    private final MoradorRepository moradores;
    private final AlertaRepository alertas;
    private final CheckinRepository checkins;
    private final EventoAuditoriaRepository auditoria;

    public SeedDados(PredioRepository predios, MoradorRepository moradores, AlertaRepository alertas,
            CheckinRepository checkins, EventoAuditoriaRepository auditoria) {
        this.predios = predios;
        this.moradores = moradores;
        this.alertas = alertas;
        this.checkins = checkins;
        this.auditoria = auditoria;
    }

    @Override
    public void run(String... args) {
        if (predios.count() > 0) {
            log.info("Seed: já existem prédios, nada inserido");
            return;
        }
        Predio predio = new Predio("Residencial Jardim das Acácias", "Rua das Acácias, 120 - Vila Mariana, São Paulo/SP",
                CODIGO_CONVITE, instante("2026-10-01T12:00:00Z"));
        predio.setId(PREDIO_ID);
        predio.setSindicoId("morador-carla");
        predios.save(predio);

        Morador carla = morador("morador-carla", "Carla Mendes", "101", "(11) 98888-1001", Papel.SINDICO,
                ContasDemo.TOKEN_SINDICA, "2026-10-01T12:00:00Z");
        Morador ana = morador("morador-ana", "Ana Souza", "302", "(11) 98888-3002", Papel.MORADOR,
                ContasDemo.TOKEN_ANA, "2026-10-02T14:30:00Z");
        ana.setNecessidadeEspecial(new NecessidadeEspecial("Cadeirante: precisa de ajuda para descer as escadas",
                new Consentimento(true, instante("2026-10-02T14:30:00Z"), PredioService.VERSAO_TERMO)));
        Morador joao = morador("morador-joao", "João Lima", "204", "(11) 98888-2004", Papel.MORADOR,
                ContasDemo.TOKEN_JOAO, "2026-10-03T09:10:00Z");
        Morador beatriz = morador("morador-beatriz", "Beatriz Rocha", "501", "(11) 98888-5001", Papel.MORADOR,
                ContasDemo.TOKEN_BEATRIZ, "2026-10-04T19:45:00Z");
        Morador rafael = morador("morador-rafael", "Rafael Nunes", "403", "(11) 98888-4003", Papel.MORADOR,
                ContasDemo.TOKEN_RAFAEL, "2026-10-05T08:20:00Z");
        moradores.saveAll(List.of(carla, ana, joao, beatriz, rafael));

        alertas.save(alertaEncerrado(joao, carla, rafael));
        checkins.saveAll(List.of(
                checkin(ana, SituacaoCheckin.PRECISA_AJUDA, "2026-11-02T22:17:00Z"),
                checkin(carla, SituacaoCheckin.SEGURO, "2026-11-02T22:16:00Z"),
                checkin(joao, SituacaoCheckin.SEGURO, "2026-11-02T22:16:30Z"),
                checkin(beatriz, SituacaoCheckin.SEGURO, "2026-11-02T22:19:00Z")));

        auditoria.saveAll(List.of(
                evento(TipoEventoAuditoria.PREDIO_CRIADO, carla, "Prédio criado; quem criou é o síndico",
                        Map.of("nome", predio.getNome()), "2026-10-01T12:00:00Z"),
                evento(TipoEventoAuditoria.MORADOR_ENTROU, ana, "Morador entrou com o código de convite",
                        Map.of("apartamento", "302"), "2026-10-02T14:30:00Z"),
                evento(TipoEventoAuditoria.CONSENTIMENTO_REGISTRADO, ana,
                        "Consentimento para uso da necessidade especial nos alertas",
                        Map.of("versaoTermo", PredioService.VERSAO_TERMO,
                                "finalidade", PredioService.FINALIDADE_NECESSIDADE), "2026-10-02T14:30:00Z"),
                evento(TipoEventoAuditoria.ALERTA_DISPARADO, joao, "Alagamento disparado pelo apto 204",
                        Map.of("alertaId", ALERTA_ID, "tipo", "ALAGAMENTO", "temFoto", false), "2026-11-02T22:15:00Z"),
                evento(TipoEventoAuditoria.ALERTA_ENCERRADO, carla, "Alerta encerrado como REAL",
                        Map.of("alertaId", ALERTA_ID, "motivo", "REAL"), "2026-11-02T23:05:00Z")));

        log.info("Seed: prédio de demonstração criado (código {}), 5 moradores, 1 alerta encerrado", CODIGO_CONVITE);
    }

    private static Morador morador(String id, String nome, String apartamento, String telefone, Papel papel,
            String token, String entrada) {
        Morador m = new Morador();
        m.setId(id);
        m.setPredioId(PREDIO_ID);
        m.setNome(nome);
        m.setApartamento(apartamento);
        m.setTelefone(telefone);
        m.setPapel(papel);
        m.setTokenHash(AutenticacaoService.hash(token));
        m.setDataEntrada(instante(entrada));
        return m;
    }

    private static Alerta alertaEncerrado(Morador autor, Morador sindica, Morador confirmou) {
        Alerta a = new Alerta();
        a.setId(ALERTA_ID);
        a.setPredioId(PREDIO_ID);
        a.setAutor(Autor.de(autor));
        a.setTipo(TipoAlerta.ALAGAMENTO);
        a.setDescricao("Água entrando pela rampa da garagem; o subsolo está alagando rápido");
        a.setStatus(StatusAlerta.ENCERRADO);
        a.setDataHora(instante("2026-11-02T22:15:00Z"));
        a.setConfirmacoes(List.of(
                Confirmacao.de(sindica, instante("2026-11-02T22:16:00Z")),
                Confirmacao.de(confirmou, instante("2026-11-02T22:18:00Z"))));
        a.setClassificacao(ClassificacaoIa.concluida(TipoAlerta.ALAGAMENTO, Gravidade.MEDIA, 0.82,
                "Descrição indica água acumulando no subsolo (classificação simulada).", "fake-local",
                instante("2026-11-02T22:15:02Z")));
        a.setNotificados(4);
        a.setEncerramento(new Encerramento(MotivoEncerramento.REAL,
                "Zeladoria desligou a bomba e os bombeiros escoaram a água; a moradora do 302 foi ajudada",
                sindica.getId(), sindica.getNome(), null, instante("2026-11-02T23:05:00Z")));
        return a;
    }

    private static Checkin checkin(Morador morador, SituacaoCheckin situacao, String quando) {
        return new Checkin(ALERTA_ID, morador.getId(), situacao, instante(quando));
    }

    private static EventoAuditoria evento(TipoEventoAuditoria tipo, Morador ator, String descricao,
            Map<String, Object> detalhes, String quando) {
        return new EventoAuditoria(tipo, PREDIO_ID, ator.getId(), ator.getNome(), descricao, detalhes,
                instante(quando));
    }

    private static Instant instante(String iso) {
        return Instant.parse(iso);
    }
}
