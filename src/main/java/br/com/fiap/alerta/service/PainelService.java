package br.com.fiap.alerta.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.fiap.alerta.dto.AlertaResumo;
import br.com.fiap.alerta.dto.PainelAlerta;
import br.com.fiap.alerta.dto.PainelAlerta.Entrada;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Checkin;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.SituacaoCheckin;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.repository.CheckinRepository;
import br.com.fiap.alerta.repository.MoradorRepository;

@Service
public class PainelService {

    /** Vulneráveis primeiro; depois pela ordem dos apartamentos (101, 204, 302, 1201...). */
    static final Comparator<Entrada> ORDEM = Comparator.comparing((Entrada e) -> !e.vulneravel())
            .thenComparing(Entrada::apartamento, PainelService::compararApartamentos);

    private final AlertaService alertas;
    private final MoradorRepository moradores;
    private final CheckinRepository checkins;

    public PainelService(AlertaService alertas, MoradorRepository moradores, CheckinRepository checkins) {
        this.alertas = alertas;
        this.moradores = moradores;
        this.checkins = checkins;
    }

    public PainelAlerta montar(String alertaId, Morador quemConsulta) {
        Alerta alerta = alertas.buscar(alertaId, quemConsulta);
        Map<String, Checkin> respostas = checkins.findByAlertaId(alertaId).stream()
                .collect(Collectors.toMap(Checkin::getMoradorId, Function.identity(), (a, b) -> a));
        boolean sindico = quemConsulta.isSindico();

        List<Entrada> precisamDeAjuda = new ArrayList<>();
        List<Entrada> semResposta = new ArrayList<>();
        List<Entrada> seguros = new ArrayList<>();
        List<Morador> doPredio = moradores.findByPredioIdAndStatusNot(alerta.getPredioId(), StatusMorador.REMOVIDO);
        for (Morador morador : doPredio) {
            Checkin resposta = respostas.get(morador.getId());
            Entrada entrada = new Entrada(morador.getId(), morador.getNome(), morador.getApartamento(),
                    sindico ? morador.getTelefone() : null, morador.isVulneravel(),
                    morador.isVulneravel() ? morador.getNecessidadeEspecial().descricao() : null,
                    resposta == null ? null : resposta.getSituacao(),
                    resposta == null ? null : resposta.getDataHora());
            if (resposta == null) {
                semResposta.add(entrada);
            } else if (resposta.getSituacao() == SituacaoCheckin.PRECISA_AJUDA) {
                precisamDeAjuda.add(entrada);
            } else {
                seguros.add(entrada);
            }
        }
        precisamDeAjuda.sort(ORDEM);
        semResposta.sort(ORDEM);
        seguros.sort(ORDEM);

        return new PainelAlerta(AlertaResumo.de(alerta),
                new PainelAlerta.Totais(doPredio.size(), precisamDeAjuda.size(), semResposta.size(), seguros.size()),
                precisamDeAjuda, semResposta, seguros);
    }

    /** Compara pelo número inicial do apartamento ("302A" → 302) e depois pelo texto. */
    static int compararApartamentos(String a, String b) {
        int porNumero = Long.compare(numeroInicial(a), numeroInicial(b));
        return porNumero != 0 ? porNumero : a.compareTo(b);
    }

    private static long numeroInicial(String apartamento) {
        int fim = 0;
        while (fim < apartamento.length() && fim < 9 && Character.isDigit(apartamento.charAt(fim))) {
            fim++;
        }
        return fim == 0 ? Long.MAX_VALUE : Long.parseLong(apartamento.substring(0, fim));
    }
}
