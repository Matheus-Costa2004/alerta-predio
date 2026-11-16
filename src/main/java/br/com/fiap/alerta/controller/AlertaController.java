package br.com.fiap.alerta.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.alerta.auth.MoradorAutenticado;
import br.com.fiap.alerta.dto.AlertaResumo;
import br.com.fiap.alerta.dto.CheckinRequest;
import br.com.fiap.alerta.dto.DisparoAlertaRequest;
import br.com.fiap.alerta.dto.EncerramentoRequest;
import br.com.fiap.alerta.dto.PainelAlerta;
import br.com.fiap.alerta.model.Alerta;
import br.com.fiap.alerta.model.Checkin;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.StatusAlerta;
import br.com.fiap.alerta.service.AlertaService;
import br.com.fiap.alerta.service.PainelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Alertas", description = "Disparo, confirmação, check-in, painel e encerramento")
public class AlertaController {

    private final AlertaService alertas;
    private final PainelService painel;

    public AlertaController(AlertaService alertas, PainelService painel) {
        this.alertas = alertas;
        this.painel = painel;
    }

    @PostMapping("/api/predios/{predioId}/alertas")
    @Operation(summary = "Dispara um alerta: grava na hora e responde 201 sem esperar a IA nem o push",
            description = "403 para morador de outro prédio ou com o botão suspenso (2ª advertência por má-fé)")
    public ResponseEntity<Alerta> disparar(@PathVariable String predioId,
            @Valid @RequestBody DisparoAlertaRequest dados, @MoradorAutenticado Morador morador) {
        Alerta alerta = alertas.disparar(predioId, dados, morador);
        return ResponseEntity.created(Rotas.local("/api/alertas/{id}", alerta.getId())).body(alerta);
    }

    @GetMapping("/api/predios/{predioId}/alertas")
    @Operation(summary = "Alertas do prédio, mais recentes primeiro (sem a foto)")
    public List<AlertaResumo> listar(@PathVariable String predioId,
            @RequestParam(required = false) StatusAlerta status, @MoradorAutenticado Morador morador) {
        return alertas.listar(predioId, status, morador);
    }

    @GetMapping("/api/alertas/{id}")
    @Operation(summary = "Alerta completo, com foto e classificação da IA")
    public Alerta buscar(@PathVariable String id, @MoradorAutenticado Morador morador) {
        return alertas.buscar(id, morador);
    }

    @PostMapping("/api/alertas/{id}/confirmacoes")
    @Operation(summary = "Confirma que a emergência é real (outro morador, uma vez por alerta)")
    public Alerta confirmar(@PathVariable String id, @MoradorAutenticado Morador morador) {
        return alertas.confirmar(id, morador);
    }

    @PutMapping("/api/alertas/{id}/checkin")
    @Operation(summary = "Estou seguro / Preciso de ajuda (vale também para morador suspenso)")
    public Checkin checkin(@PathVariable String id, @Valid @RequestBody CheckinRequest dados,
            @MoradorAutenticado Morador morador) {
        return alertas.checkin(id, dados.situacao(), morador);
    }

    @GetMapping("/api/alertas/{id}/painel")
    @Operation(summary = "Quem está seguro, quem precisa de ajuda e quem não respondeu; vulneráveis primeiro")
    public PainelAlerta painel(@PathVariable String id, @MoradorAutenticado Morador morador) {
        return painel.montar(id, morador);
    }

    @PostMapping("/api/alertas/{id}/encerramento")
    @Operation(summary = "Encerra o alerta com o motivo (só síndico); FALSO_MA_FE gera advertência ao autor")
    public Alerta encerrar(@PathVariable String id, @Valid @RequestBody EncerramentoRequest dados,
            @MoradorAutenticado Morador morador) {
        return alertas.encerrar(id, dados, morador);
    }
}
