package br.com.fiap.alerta.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.alerta.config.ContasDemo;
import br.com.fiap.alerta.dto.ContaDemo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Só existe com SEED_ENABLED=true: entrega os tokens das contas do prédio de demonstração. */
@RestController
@ConditionalOnProperty(name = "alerta.seed.enabled", havingValue = "true")
@Tag(name = "Demonstração")
public class DemoController {

    @GetMapping("/api/demo/contas")
    @SecurityRequirements
    @Operation(summary = "Contas do prédio de demonstração (seed) para entrar sem cadastro")
    public List<ContaDemo> contas() {
        return ContasDemo.CONTAS;
    }
}
