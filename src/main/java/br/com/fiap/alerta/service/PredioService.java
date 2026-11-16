package br.com.fiap.alerta.service;

import static br.com.fiap.alerta.service.AuditoriaService.detalhes;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import br.com.fiap.alerta.auth.AutenticacaoService;
import br.com.fiap.alerta.dto.AcessoResponse;
import br.com.fiap.alerta.dto.DadosMorador;
import br.com.fiap.alerta.dto.EntrarPredioRequest;
import br.com.fiap.alerta.dto.NovoPredioRequest;
import br.com.fiap.alerta.dto.PredioResponse;
import br.com.fiap.alerta.dto.SessaoResponse;
import br.com.fiap.alerta.exception.RecursoNaoEncontradoException;
import br.com.fiap.alerta.model.Consentimento;
import br.com.fiap.alerta.model.EventoAuditoria;
import br.com.fiap.alerta.model.Morador;
import br.com.fiap.alerta.model.NecessidadeEspecial;
import br.com.fiap.alerta.model.Papel;
import br.com.fiap.alerta.model.Predio;
import br.com.fiap.alerta.model.StatusMorador;
import br.com.fiap.alerta.model.TipoEventoAuditoria;
import br.com.fiap.alerta.repository.MoradorRepository;
import br.com.fiap.alerta.repository.PredioRepository;

@Service
public class PredioService {

    public static final String VERSAO_TERMO = "1.0";
    public static final String FINALIDADE_NECESSIDADE =
            "Exibir a necessidade especial no painel dos alertas do prédio, para priorizar a ajuda";

    /** Sem 0/O e 1/I/L, que se confundem quando o código é ditado ou lido no celular. */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_CODIGO = 6;
    private static final int TENTATIVAS_CODIGO = 10;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final PredioRepository predios;
    private final MoradorRepository moradores;
    private final AutenticacaoService autenticacao;
    private final AuditoriaService auditoria;
    private final ControleAcesso acesso;
    private final Clock relogio;

    public PredioService(PredioRepository predios, MoradorRepository moradores, AutenticacaoService autenticacao,
            AuditoriaService auditoria, ControleAcesso acesso, Clock relogio) {
        this.predios = predios;
        this.moradores = moradores;
        this.autenticacao = autenticacao;
        this.auditoria = auditoria;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    /** Cria o prédio com um código de convite único; quem cria vira o síndico. */
    public AcessoResponse criar(NovoPredioRequest dados) {
        Instant agora = Instant.now(relogio);
        Predio predio = predios.save(new Predio(dados.nome().trim(), dados.endereco().trim(),
                gerarCodigoConvite(), agora));

        String token = autenticacao.novoToken();
        Morador sindico = moradores.save(novoMorador(predio.getId(), dados.sindico(), Papel.SINDICO, token, agora));
        predio.setSindicoId(sindico.getId());
        predio = predios.save(predio);

        auditoria.registrar(TipoEventoAuditoria.PREDIO_CRIADO, predio.getId(), sindico,
                "Prédio criado; quem criou é o síndico", detalhes("nome", predio.getNome()));
        registrarConsentimento(sindico);
        return new AcessoResponse(PredioResponse.de(predio, true), sindico, token);
    }

    public AcessoResponse entrar(EntrarPredioRequest dados) {
        String codigo = dados.codigoConvite().trim().toUpperCase(Locale.ROOT);
        Predio predio = predios.findByCodigoConvite(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Prédio com o código de convite", codigo));

        String token = autenticacao.novoToken();
        Morador morador = moradores.save(novoMorador(predio.getId(), dados.morador(), Papel.MORADOR, token,
                Instant.now(relogio)));

        auditoria.registrar(TipoEventoAuditoria.MORADOR_ENTROU, predio.getId(), morador,
                "Morador entrou com o código de convite", detalhes("apartamento", morador.getApartamento()));
        registrarConsentimento(morador);
        return new AcessoResponse(PredioResponse.de(predio, false), morador, token);
    }

    public SessaoResponse sessao(Morador morador) {
        return new SessaoResponse(PredioResponse.de(buscarPredio(morador.getPredioId()), morador.isSindico()),
                morador);
    }

    /** O acesso é checado antes da busca: quem é de fora recebe 403, sem saber se o id existe. */
    public PredioResponse buscar(String predioId, Morador morador) {
        acesso.exigirMembro(morador, predioId);
        return PredioResponse.de(buscarPredio(predioId), morador.isSindico());
    }

    public List<Morador> moradores(String predioId, Morador morador) {
        acesso.exigirSindico(morador, predioId, "ver a lista de moradores");
        return moradores.findByPredioIdOrderByApartamentoAsc(predioId);
    }

    public List<EventoAuditoria> auditoria(String predioId, Morador morador) {
        acesso.exigirSindico(morador, predioId, "ver a auditoria do prédio");
        return auditoria.doPredio(predioId);
    }

    String gerarCodigoConvite() {
        for (int i = 0; i < TENTATIVAS_CODIGO; i++) {
            StringBuilder codigo = new StringBuilder(TAMANHO_CODIGO);
            for (int j = 0; j < TAMANHO_CODIGO; j++) {
                codigo.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
            }
            if (!predios.existsByCodigoConvite(codigo.toString())) {
                return codigo.toString();
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código de convite único");
    }

    private Predio buscarPredio(String predioId) {
        return predios.findById(predioId).orElseThrow(() -> new RecursoNaoEncontradoException("Prédio", predioId));
    }

    private static Morador novoMorador(String predioId, DadosMorador dados, Papel papel, String token,
            Instant agora) {
        Morador morador = new Morador();
        morador.setPredioId(predioId);
        morador.setNome(dados.nome().trim());
        morador.setApartamento(dados.apartamento().trim().toUpperCase(Locale.ROOT));
        morador.setTelefone(dados.telefone().trim());
        morador.setPapel(papel);
        morador.setStatus(StatusMorador.ATIVO);
        morador.setDataEntrada(agora);
        morador.setTokenHash(AutenticacaoService.hash(token));
        // Dado sensível nunca é gravado sem o consentimento (a validação do DTO já exige, isto é defesa extra)
        if (dados.temNecessidadeEspecial() && Boolean.TRUE.equals(dados.consentimentoNecessidadeEspecial())) {
            morador.setNecessidadeEspecial(new NecessidadeEspecial(dados.necessidadeEspecial().trim(),
                    new Consentimento(true, agora, VERSAO_TERMO)));
        }
        return morador;
    }

    private void registrarConsentimento(Morador morador) {
        if (morador.getNecessidadeEspecial() != null) {
            auditoria.registrar(TipoEventoAuditoria.CONSENTIMENTO_REGISTRADO, morador.getPredioId(), morador,
                    "Consentimento para uso da necessidade especial nos alertas",
                    detalhes("versaoTermo", VERSAO_TERMO, "finalidade", FINALIDADE_NECESSIDADE));
        }
    }
}
