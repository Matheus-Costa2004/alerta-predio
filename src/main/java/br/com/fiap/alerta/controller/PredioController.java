package br.com.fiap.alerta.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.alerta.auth.MoradorAutenticado;
import br.com.fiap.alerta.dto.AcessoResponse;
import br.com.fiap.alerta.dto.EntrarPredioRequest;
import br.com.fiap.alerta.dto.NovoPredioRequest;
import br.com.fiap.alerta.dto.PredioResponse;
import br.com.fiap.alerta.model.EventoAuditoria;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.service.PredioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/predios")
@Tag(name = "Prédios", description = "Criar prédio, entrar com código de convite e área do síndico")
public class PredioController {

    private final PredioService service;

    public PredioController(PredioService service) {
        this.service = service;
    }

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Cria o prédio; quem cria vira o síndico e recebe o token de acesso")
    public ResponseEntity<AcessoResponse> criar(@Valid @RequestBody NovoPredioRequest dados) {
        AcessoResponse acesso = service.criar(dados);
        return ResponseEntity.created(Rotas.local("/api/predios/{id}", acesso.predio().id())).body(acesso);
    }

    @PostMapping("/entrar")
    @SecurityRequirements
    @Operation(summary = "Entra no prédio com o código de convite e recebe o token de acesso",
            description = "Necessidade especial é opcional e exige consentimentoNecessidadeEspecial = true (LGPD)")
    public ResponseEntity<AcessoResponse> entrar(@Valid @RequestBody EntrarPredioRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.entrar(dados));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Dados do prédio (o código de convite só aparece para o síndico)")
    public PredioResponse buscar(@PathVariable String id, @MoradorAutenticado Morador morador) {
        return service.buscar(id, morador);
    }

    @GetMapping("/{id}/moradores")
    @Operation(summary = "Moradores com status e advertências (só síndico)")
    public List<Morador> moradores(@PathVariable String id, @MoradorAutenticado Morador morador) {
        return service.moradores(id, morador);
    }

    @GetMapping("/{id}/auditoria")
    @Operation(summary = "Trilha de auditoria do prédio, mais recente primeiro (só síndico)")
    public List<EventoAuditoria> auditoria(@PathVariable String id, @MoradorAutenticado Morador morador) {
        return service.auditoria(id, morador);
    }
}
