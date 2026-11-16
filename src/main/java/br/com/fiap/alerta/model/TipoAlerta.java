package br.com.fiap.alerta.model;

public enum TipoAlerta {
    INCENDIO("Incêndio", "🔥"),
    GAS("Vazamento de gás", "💨"),
    ALAGAMENTO("Alagamento", "🌊"),
    SOCORRO_MEDICO("Socorro médico", "🚑"),
    ELEVADOR("Pessoa presa no elevador", "🛗");

    private final String rotulo;
    private final String icone;

    TipoAlerta(String rotulo, String icone) {
        this.rotulo = rotulo;
        this.icone = icone;
    }

    public String rotulo() {
        return rotulo;
    }

    public String icone() {
        return icone;
    }
}
