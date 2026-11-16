package br.com.fiap.alerta.model;

/** Quem disparou o alerta: nome e apartamento ficam visíveis para todo o prédio. */
public record Autor(String moradorId, String nome, String apartamento) {

    public static Autor de(Morador morador) {
        return new Autor(morador.getId(), morador.getNome(), morador.getApartamento());
    }
}
