package br.com.linkflow.provider;

import br.com.linkflow.client.ShopeeClient;
import br.com.linkflow.client.ShopeeClient.ShopeeItem;
import br.com.linkflow.config.ShopeeProperties;
import br.com.linkflow.entity.Product.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider real da Shopee. Para cada categoria do LinkFlow faz uma busca por
 * palavra-chave na Affiliate Open API e mapeia os resultados para
 * {@link RawProduct}.
 *
 * <p>Comissão e link de afiliado vêm prontos da própria API ({@code commissionRate}
 * e {@code offerLink}), então não há tabela de comissão por categoria como no
 * Mercado Livre. Resiliência: cada categoria é buscada de forma isolada e uma
 * falha não interrompe as demais.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopeeProductProvider implements ProductProvider {

    private final ShopeeClient client;
    private final ShopeeProperties props;

    @Override
    public Platform platform() {
        return Platform.SHOPEE;
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled();
    }

    @Override
    public List<RawProduct> fetchCatalog() {
        List<RawProduct> catalog = new ArrayList<>();
        Map<String, Integer> porCategoria = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : props.getKeywordByCategory().entrySet()) {
            String linkflowCategory = entry.getKey();
            String keyword = entry.getValue();
            if (keyword == null || keyword.isBlank()) continue;

            List<ShopeeItem> items = client.buscarPorKeyword(keyword, props.getLimitPerCategory());
            for (ShopeeItem item : items) {
                catalog.add(toRawProduct(item, linkflowCategory));
            }
            porCategoria.put(linkflowCategory, items.size());

            if (items.isEmpty()) {
                log.warn("Shopee: categoria '{}' (keyword '{}') não retornou produtos — "
                    + "revise a keyword ou o status do programa de afiliados.",
                    linkflowCategory, keyword);
            }

            throttle();
        }

        log.info("Shopee: {} produtos coletados. Itens por categoria: {}",
            catalog.size(), porCategoria);
        return catalog;
    }

    private RawProduct toRawProduct(ShopeeItem item, String category) {
        return new RawProduct(
            item.id(),
            Platform.SHOPEE,
            item.name(),
            null, // productOfferV2 não traz descrição do produto
            item.price(),
            item.originalPrice(),
            item.commissionPct(), // comissão real da API (já em %)
            item.imageUrl(),
            item.offerLink(),     // link de afiliado pronto, com tracking
            category
        );
    }

    private void throttle() {
        long pause = props.getThrottleMs();
        if (pause <= 0) return;
        try {
            Thread.sleep(pause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
