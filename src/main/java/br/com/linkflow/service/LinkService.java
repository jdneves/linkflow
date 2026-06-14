package br.com.linkflow.service;

import br.com.linkflow.config.PlanLimits;
import br.com.linkflow.dto.request.LinkRequest;
import br.com.linkflow.dto.response.LinkResponse;
import br.com.linkflow.entity.AffiliateLink;
import br.com.linkflow.entity.Click;
import br.com.linkflow.entity.Click.Device;
import br.com.linkflow.entity.Product;
import br.com.linkflow.entity.Script;
import br.com.linkflow.entity.User;
import br.com.linkflow.exception.BusinessException;
import br.com.linkflow.repository.AffiliateLinkRepository;
import br.com.linkflow.repository.ClickRepository;
import br.com.linkflow.repository.ProductRepository;
import br.com.linkflow.repository.ScriptRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkService {

    private final AffiliateLinkRepository linkRepository;
    private final ClickRepository clickRepository;
    private final ProductRepository productRepository;
    private final ScriptRepository scriptRepository;
    private final OnboardingService onboardingService;

    @Value("${linkflow.base-url}")
    private String baseUrl;

    // ── Criação ───────────────────────────────────────────────────────────

    @Transactional
    public LinkResponse criar(LinkRequest request, User user) {
        validarLimite(user);

        String slug = resolverSlug(request, user);

        Product product = request.productId() != null
            ? productRepository.findById(UUID.fromString(request.productId())).orElse(null)
            : null;

        Script script = request.scriptId() != null
            ? scriptRepository.findById(UUID.fromString(request.scriptId())).orElse(null)
            : null;

        AffiliateLink link = AffiliateLink.builder()
            .user(user)
            .product(product)
            .script(script)
            .slug(slug)
            .destinationUrl(request.destinationUrl())
            .title(request.title())
            .campaign(request.campaign())
            .build();

        linkRepository.save(link);
        onboardingService.concluirPasso(user, OnboardingService.PASSO_LINK);
        log.info("Link criado: slug={} usuário={}", slug, user.getEmail());

        return LinkResponse.from(link, baseUrl);
    }

    // ── Redirect + Rastreamento ───────────────────────────────────────────

    @Transactional
    public String redirecionar(String slug, HttpServletRequest httpRequest) {
        AffiliateLink link = linkRepository.findBySlugAndActiveTrue(slug)
            .orElseThrow(() -> new BusinessException("Link não encontrado ou inativo."));

        // Registra o clique de forma assíncrona (não atrasa o redirect)
        registrarClick(link, httpRequest);

        return link.getDestinationUrl();
    }

    @Async
    @Transactional
    public void registrarClick(AffiliateLink link, HttpServletRequest request) {
        try {
            String userAgent = request.getHeader("User-Agent");
            String referer   = request.getHeader("Referer");
            String ip        = obterIp(request);

            Click click = Click.builder()
                .link(link)
                .referer(referer)
                .device(detectarDevice(userAgent))
                .ipHash(anonimizarIp(ip))
                .build();

            clickRepository.save(click);

            // Atualiza contador e timestamp no link
            link.setClicks(link.getClicks() + 1);
            link.setLastClickAt(LocalDateTime.now());
            linkRepository.save(link);

        } catch (Exception e) {
            log.error("Erro ao registrar clique no link {}: {}", link.getSlug(), e.getMessage());
        }
    }

    // ── Listagem e gestão ─────────────────────────────────────────────────

    public Page<LinkResponse> listar(User user, int page) {
        return linkRepository
            .findByUserAndActiveTrueOrderByCreatedAtDesc(user, PageRequest.of(page, 20))
            .map(l -> LinkResponse.from(l, baseUrl));
    }

    public LinkResponse buscarPorId(UUID id, User user) {
        return linkRepository.findById(id)
            .filter(l -> l.getUser().getId().equals(user.getId()))
            .map(l -> LinkResponse.from(l, baseUrl))
            .orElseThrow(() -> new BusinessException("Link não encontrado."));
    }

    @Transactional
    public void desativar(UUID id, User user) {
        AffiliateLink link = linkRepository.findById(id)
            .filter(l -> l.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new BusinessException("Link não encontrado."));
        link.setActive(false);
        linkRepository.save(link);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void validarLimite(User user) {
        PlanLimits limits = PlanLimits.of(user.getPlan());

        if (!limits.hasUnlimitedLinks()) {
            long ativos = linkRepository.countActiveByUser(user);
            if (ativos >= limits.getLinks()) {
                throw new BusinessException(
                    "Limite de %d links ativos atingido. Faça upgrade do plano.".formatted(limits.getLinks())
                );
            }
        }
    }

    private String resolverSlug(LinkRequest request, User user) {
        if (request.customSlug() != null && !request.customSlug().isBlank()) {
            String slug = user.getUsername().split("@")[0] + "/" + request.customSlug();
            if (linkRepository.existsBySlug(slug)) {
                throw new BusinessException("Esse slug já está em uso. Escolha outro.");
            }
            return slug;
        }
        // Gera slug automático: prefixo-do-email + 6 chars aleatórios
        String prefix = user.getEmail().split("@")[0].replaceAll("[^a-z0-9]", "");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return prefix + "/" + suffix;
    }

    private Device detectarDevice(String userAgent) {
        if (userAgent == null) return Device.UNKNOWN;
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return Device.MOBILE;
        if (ua.contains("tablet") || ua.contains("ipad")) return Device.TABLET;
        if (ua.contains("mozilla") || ua.contains("chrome") || ua.contains("safari")) return Device.DESKTOP;
        return Device.UNKNOWN;
    }

    private String obterIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private String anonimizarIp(String ip) {
        if (ip == null) return "unknown";
        // Guarda apenas os 3 primeiros octetos: 192.168.1.xxx
        String[] parts = ip.split("\\.");
        if (parts.length == 4) return parts[0] + "." + parts[1] + "." + parts[2] + ".xxx";
        return "unknown";
    }
}
