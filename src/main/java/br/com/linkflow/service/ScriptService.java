package br.com.linkflow.service;

import br.com.linkflow.client.ClaudeClient;
import br.com.linkflow.dto.request.ScriptRequest;
import br.com.linkflow.dto.response.ScriptResponse;
import br.com.linkflow.entity.Product;
import br.com.linkflow.entity.Script;
import br.com.linkflow.entity.User;
import br.com.linkflow.exception.BusinessException;
import br.com.linkflow.repository.ProductRepository;
import br.com.linkflow.repository.ScriptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final ProductRepository productRepository;
    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;

    // Limites por plano
    private static final int LIMITE_FREE    = 5;
    private static final int LIMITE_CREATOR = 30;

    // ── Geração ───────────────────────────────────────────────────────────

    @Transactional
    public ScriptResponse gerar(ScriptRequest request, User user) {
        validarLimiteMensal(user);

        // Busca produto do Radar se informado
        Product product = null;
        if (request.productId() != null) {
            product = productRepository.findById(UUID.fromString(request.productId()))
                .orElse(null);
        }

        // Monta e envia prompt para o Claude
        String prompt = buildPrompt(request);
        log.info("Gerando roteiro para '{}' — usuário {}", request.productName(), user.getEmail());
        String jsonResposta = claudeClient.completar(prompt);

        // Parseia resposta estruturada
        RoteiroIA roteiro = parsearResposta(jsonResposta);

        // Persiste o roteiro vinculado ao produto
        Script script = Script.builder()
            .user(user)
            .product(product)
            .productName(request.productName())
            .platform(request.platform())
            .format(request.format())
            .tone(request.tone())
            .title(roteiro.titulo_sugerido())
            .hook(roteiro.gancho_abertura())
            .topics(roteiro.topicos())
            .cta(roteiro.cta_afiliado())
            .caption(roteiro.legenda_instagram())
            .hashtags(roteiro.hashtags())
            .stories(roteiro.stories())
            .build();

        scriptRepository.save(script);
        log.info("Roteiro salvo: id={}", script.getId());

        return ScriptResponse.from(script);
    }

    // ── Histórico ─────────────────────────────────────────────────────────

    public Page<ScriptResponse> listar(User user, int page) {
        return scriptRepository
            .findByUserOrderByCreatedAtDesc(user, PageRequest.of(page, 10))
            .map(ScriptResponse::from);
    }

    public ScriptResponse buscarPorId(UUID id, User user) {
        return scriptRepository.findById(id)
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .map(ScriptResponse::from)
            .orElseThrow(() -> new BusinessException("Roteiro não encontrado."));
    }

    public Page<ScriptResponse> listarPorProduto(UUID productId, User user, int page) {
        return scriptRepository
            .findByUserAndProductIdOrderByCreatedAtDesc(user, productId, PageRequest.of(page, 10))
            .map(ScriptResponse::from);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void validarLimiteMensal(User user) {
        long usados = scriptRepository.countByUserThisMonth(user);
        int limite = switch (user.getPlan()) {
            case FREE    -> LIMITE_FREE;
            case CREATOR -> LIMITE_CREATOR;
            case PRO     -> Integer.MAX_VALUE;
        };
        if (usados >= limite) {
            throw new BusinessException(
                "Limite de %d roteiros/mês atingido. Faça upgrade do plano.".formatted(limite)
            );
        }
    }

    private String buildPrompt(ScriptRequest req) {
        return """
            Você é um especialista em marketing de afiliados e criação de conteúdo digital no Brasil.

            Gere um roteiro completo para um criador-afiliado iniciante divulgar o seguinte produto:

            Produto: %s
            Destaques: %s
            Plataforma: %s
            Formato: %s
            Tom de voz: %s
            Duração alvo: %s

            Retorne SOMENTE um objeto JSON válido, sem markdown, sem texto antes ou depois.
            Estrutura obrigatória:
            {
              "titulo_sugerido": "título chamativo para o vídeo",
              "gancho_abertura": "primeiros 20 segundos que prendem atenção imediatamente",
              "topicos": ["tópico 1", "tópico 2", "tópico 3", "tópico 4", "tópico 5"],
              "cta_afiliado": "chamada para ação natural mencionando o link na descrição",
              "legenda_instagram": "legenda completa com emojis e storytelling",
              "hashtags": ["hashtag1", "hashtag2", "hashtag3", "hashtag4", "hashtag5", "hashtag6", "hashtag7", "hashtag8"],
              "stories": ["slide 1", "slide 2", "slide 3", "slide 4", "slide 5"]
            }
            """.formatted(
                req.productName(),
                req.productHighlights() != null ? req.productHighlights() : "não informado",
                req.platform().name().toLowerCase(),
                req.format().toPortugues(),
                req.tone(),
                req.duration()
            );
    }

    private RoteiroIA parsearResposta(String json) {
        try {
            // Remove possíveis blocos de markdown
            String clean = json.replaceAll("```json|```", "").trim();

            // Extrai JSON se vier com texto antes/depois
            int inicio = clean.indexOf('{');
            int fim    = clean.lastIndexOf('}');
            if (inicio >= 0 && fim > inicio) {
                clean = clean.substring(inicio, fim + 1);
            }

            return objectMapper.readValue(clean, RoteiroIA.class);
        } catch (Exception e) {
            log.error("Erro ao parsear resposta do Claude: {}", json);
            throw new BusinessException("Erro ao processar roteiro gerado. Tente novamente.");
        }
    }

    // Record para deserializar o JSON do Claude
    record RoteiroIA(
        String titulo_sugerido,
        String gancho_abertura,
        List<String> topicos,
        String cta_afiliado,
        String legenda_instagram,
        List<String> hashtags,
        List<String> stories
    ) {}
}
