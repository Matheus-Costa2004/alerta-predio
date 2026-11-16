package br.com.fiap.alerta.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.alerta.auth.MoradorAutenticado;
import br.com.fiap.alerta.dto.SessaoResponse;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.service.PredioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Prédios")
public class MoradorController {

    private final PredioService service;

    public MoradorController(PredioService service) {
        this.service = service;
    }

    @GetMapping("/api/moradores/eu")
    @Operation(summary = "Morador dono do token e o prédio dele (status, suspensão, papel)")
    public SessaoResponse eu(@MoradorAutenticado Morador morador) {
        return service.sessao(morador);
    }
}
