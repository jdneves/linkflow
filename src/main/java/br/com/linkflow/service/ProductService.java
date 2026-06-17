package br.com.linkflow.service;

import br.com.linkflow.dto.response.ProductResponse;
import br.com.linkflow.dto.response.RestPage;
import br.com.linkflow.entity.Product;
import br.com.linkflow.entity.Product.Platform;
import br.com.linkflow.provider.MockProductProvider;
import br.com.linkflow.provider.ProductProvider;
import br.com.linkflow.provider.RawProduct;
import br.com.linkflow.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductScoringService scoringService;

    /** Providers reais (beans), indexados por plataforma. Só os habilitados são usados. */
    private final Map<Platform, ProductProvider> realProviders = new EnumMap<>(Platform.class);

    public ProductService(ProductRepository productRepository,
                          ProductScoringService scoringService,
                          List<ProductProvider> providers) {
        this.productRepository = productRepository;
        this.scoringService = scoringService;
        for (ProductProvider provider : providers) {
            realProviders.put(provider.platform(), provider);
        }
    }

    // ── Busca com filtros ──────────────────────────────────────────────────

    @Cacheable(value = "products", key = "#category + '_' + #platform + '_' + #search + '_' + #page")
    public Page<ProductResponse> buscar(String category, String platform, String search, int page) {
        Pageable pageable = PageRequest.of(page, 12);
        Platform plat = platform != null ? Platform.valueOf(platform.toUpperCase()) : null;
        String searchPattern = search != null ? "%" + search.toLowerCase() + "%" : null;
        return new RestPage<>(productRepository.findWithFilters(category, plat, searchPattern, pageable)
            .map(ProductResponse::from));
    }

    // ── Produtos em alta ───────────────────────────────────────────────────

    @Cacheable(value = "trending-products")
    public List<ProductResponse> buscarEmAlta() {
        return productRepository.findTrending(PageRequest.of(0, 6))
            .map(ProductResponse::from)
            .toList();
    }

    // ── Busca produto por ID ───────────────────────────────────────────────

    public ProductResponse buscarPorId(String id) {
        return productRepository.findById(java.util.UUID.fromString(id))
            .map(ProductResponse::from)
            .orElseThrow(() -> new br.com.linkflow.exception.BusinessException("Produto não encontrado."));
    }

    // ── Categorias disponíveis ─────────────────────────────────────────────

    public List<String> listarCategorias() {
        return List.of(
            "eletrodomesticos",
            "eletronicos",
            "beleza",
            "fitness",
            "casa",
            "games",
            "moda"
        );
    }

    // ── Sincronização via providers (real + fallback mock) ────────────────

    @Scheduled(cron = "0 0 */6 * * *") // a cada 6 horas
    @Transactional
    @CacheEvict(value = {"products", "trending-products"}, allEntries = true)
    public int sincronizarProdutos() {
        log.info("Sincronizando produtos do radar...");
        int total = 0;

        // Itera todas as plataformas; cada uma usa o provider real (se habilitado)
        // ou cai no mock daquela plataforma.
        for (Platform platform : Platform.values()) {
            List<RawProduct> raw = coletarCatalogo(platform);
            for (RawProduct rp : raw) {
                upsert(scoringService.toScoredProduct(rp));
            }
            total += raw.size();
        }

        log.info("Sincronização concluída: {} produtos processados.", total);
        return total;
    }

    /**
     * Coleta o catálogo de uma plataforma. Usa o provider real quando habilitado;
     * em qualquer falha (ou provider desabilitado) cai no {@link MockProductProvider}
     * da plataforma — o ciclo de sync nunca é derrubado.
     */
    private List<RawProduct> coletarCatalogo(Platform platform) {
        ProductProvider real = realProviders.get(platform);
        if (real != null && real.isEnabled()) {
            try {
                List<RawProduct> raw = real.fetchCatalog();
                if (!raw.isEmpty()) {
                    return raw;
                }
                log.warn("Provider real de {} não retornou produtos; usando mock.", platform);
            } catch (Exception e) {
                log.warn("Provider real de {} falhou ({}); usando mock.", platform, e.getMessage());
            }
        }
        return new MockProductProvider(platform).fetchCatalog();
    }

    /** Insere ou atualiza o produto pela chave (externalId, platform). */
    private void upsert(Product produto) {
        productRepository.findByExternalIdAndPlatform(produto.getExternalId(), produto.getPlatform())
            .ifPresentOrElse(
                existente -> {
                    existente.setName(produto.getName());
                    existente.setDescription(produto.getDescription());
                    existente.setPrice(produto.getPrice());
                    existente.setOriginalPrice(produto.getOriginalPrice());
                    existente.setCommissionPct(produto.getCommissionPct());
                    existente.setImageUrl(produto.getImageUrl());
                    existente.setProductUrl(produto.getProductUrl());
                    existente.setCategory(produto.getCategory());
                    existente.setScore(produto.getScore());
                    existente.setTrend(produto.getTrend());
                    productRepository.save(existente);
                },
                () -> productRepository.save(produto)
            );
    }

    // Roda na inicialização da aplicação (1 vez, 5s após startup)
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void sincronizarNaInicializacao() {
        if (productRepository.count() == 0) {
            log.info("Banco vazio — carregando produtos mock iniciais...");
            sincronizarProdutos();
        }
    }
}
