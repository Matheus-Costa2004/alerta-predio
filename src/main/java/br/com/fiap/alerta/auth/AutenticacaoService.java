package br.com.fiap.alerta.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import br.com.fiap.alerta.exception.NaoAutenticadoException;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.repository.MoradorRepository;

/**
 * Token opaco por morador: 32 bytes aleatórios devolvidos uma única vez. O banco guarda só o SHA-256,
 * então um vazamento da collection não entrega tokens válidos.
 */
@Service
public class AutenticacaoService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final MoradorRepository moradores;

    public AutenticacaoService(MoradorRepository moradores) {
        this.moradores = moradores;
    }

    public String novoToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Morador autenticar(String token) {
        return moradores.findByTokenHash(hash(token))
                .orElseThrow(() -> new NaoAutenticadoException("Token de morador inválido."));
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
