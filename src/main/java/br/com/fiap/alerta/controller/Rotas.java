package br.com.fiap.alerta.controller;

import java.net.URI;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Monta o header Location das respostas 201. */
final class Rotas {

    private Rotas() {
    }

    static URI local(String caminho, Object... variaveis) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(caminho).buildAndExpand(variaveis).toUri();
    }
}
