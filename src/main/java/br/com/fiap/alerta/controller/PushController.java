package br.com.fiap.alerta.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.alerta.auth.MoradorAutenticado;
import br.com.fiap.alerta.dto.ChavePublicaResponse;
import br.com.fiap.alerta.dto.InscricaoPushRequest;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.push.InscricaoPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/push")
@Tag(name = "Notificações", description = "Web Push (VAPID) para avisar os moradores")
public class PushController {

    private final InscricaoPushService service;

    public PushController(InscricaoPushService service) {
        this.service = service;
    }

    @GetMapping("/chave-publica")
    @SecurityRequirements
    @Operation(summary = "Chave pública VAPID; habilitado=false quando o push não está configurado")
    public ChavePublicaResponse chavePublica() {
        return service.chavePublica();
    }

    @PostMapping("/inscricoes")
    @Operation(summary = "Registra o navegador do morador (PushSubscription.toJSON())")
    public ResponseEntity<Void> inscrever(@Valid @RequestBody InscricaoPushRequest dados,
            @MoradorAutenticado Morador morador) {
        service.inscrever(dados, morador);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/inscricoes")
    @Operation(summary = "Remove a inscrição de push deste navegador")
    public ResponseEntity<Void> cancelar(@RequestParam String endpoint, @MoradorAutenticado Morador morador) {
        service.cancelar(endpoint, morador);
        return ResponseEntity.noContent().build();
    }
}
