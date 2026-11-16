package br.com.fiap.alerta.ia;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.com.fiap.alerta.model.Gravidade;
import br.com.fiap.alerta.model.TipoAlerta;

/**
 * Classificador determinístico para o ambiente local e os testes: não chama nenhuma API.
 * Procura palavras-chave na descrição; sem pista, confirma o tipo informado com confiança baixa.
 */
public class ClassificadorAlertaFake implements ClassificadorAlerta {

    public static final String MODELO = "fake-local";

    private static final Map<TipoAlerta, List<String>> PALAVRAS = new EnumMap<>(Map.of(
            TipoAlerta.INCENDIO, List.of("fogo", "fumaca", "chama", "incendio", "queimad", "faisca"),
            TipoAlerta.GAS, List.of("gas", "botijao", "cheiro de gas"),
            TipoAlerta.ALAGAMENTO, List.of("agua", "alag", "inund", "infiltra", "cano estourado"),
            TipoAlerta.SOCORRO_MEDICO, List.of("desmai", "infarto", "sangr", "ferid", "caiu", "convuls",
                    "respira", "inconsciente", "passando mal"),
            TipoAlerta.ELEVADOR, List.of("elevador", "presa", "preso")));

    private static final List<String> SINAIS_CRITICOS = List.of("crianca", "inconsciente", "nao respira",
            "muita fumaca", "chamas", "explos", "urgente");

    private static final Map<TipoAlerta, Gravidade> GRAVIDADE_PADRAO = new EnumMap<>(Map.of(
            TipoAlerta.INCENDIO, Gravidade.ALTA,
            TipoAlerta.GAS, Gravidade.ALTA,
            TipoAlerta.SOCORRO_MEDICO, Gravidade.ALTA,
            TipoAlerta.ALAGAMENTO, Gravidade.MEDIA,
            TipoAlerta.ELEVADOR, Gravidade.MEDIA));

    @Override
    public ResultadoClassificacao classificar(PedidoClassificacao pedido) {
        String texto = normalizar(pedido.descricao());

        TipoAlerta tipo = pedido.tipoInformado();
        int melhor = contar(texto, tipo);
        for (TipoAlerta candidato : TipoAlerta.values()) {
            int pontos = contar(texto, candidato);
            if (pontos > melhor) {
                tipo = candidato;
                melhor = pontos;
            }
        }

        Gravidade gravidade = SINAIS_CRITICOS.stream().anyMatch(texto::contains)
                ? Gravidade.CRITICA
                : GRAVIDADE_PADRAO.get(tipo);
        double confianca = melhor == 0 ? 0.5 : tipo == pedido.tipoInformado() ? 0.8 : 0.6;
        String justificativa = melhor == 0
                ? "Sem pistas na descrição; mantido o tipo informado pelo morador (classificação simulada)."
                : "Palavras-chave da descrição indicam " + tipo.rotulo().toLowerCase(Locale.ROOT)
                        + " (classificação simulada).";
        return new ResultadoClassificacao(tipo, gravidade, confianca, justificativa, MODELO);
    }

    private static int contar(String texto, TipoAlerta tipo) {
        return (int) PALAVRAS.get(tipo).stream().filter(texto::contains).count();
    }

    /** Minúsculas e sem acento, para "Fumaça" casar com "fumaca". */
    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
