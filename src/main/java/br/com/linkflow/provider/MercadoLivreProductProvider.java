package br.com.linkflow.provider;

import br.com.linkflow.client.MercadoLivreClient;
import br.com.linkflow.client.MercadoLivreClient.MlItem;
import br.com.linkflow.config.MercadoLivreProperties;
import br.com.linkflow.entity.Product.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider real do Mercado Livre. Itera as categorias do LinkFlow, busca os
 * itens da categoria raiz MLB correspondente e mapeia para {@link RawProduct}.
 *
 * <p>Comissão: a busca do ML não retorna comissão de afiliado, então derivamos
 * do mapa {@code ml.commission-by-category} (config). Resiliência: cada
 * categoria é buscada de forma isolada e uma falha não interrompe as demais.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MercadoLivreProductProvider implements ProductProvider {

    private final MercadoLivreClient client;
    private final MercadoLivreProperties props;
    private final MercadoLivreCategoryMapper categoryMapper;

    @Override
    public Platform platform() {
        return Platform.MERCADO_LIVRE;
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled();
    }

    @Override
    public List<RawProduct> fetchCatalog() {
        List<RawProduct> catalog = new ArrayList<>();

        for (String linkflowCategory : categoryMapper.linkflowCategories()) {
            String mlCategoryId = categoryMapper.mlCategoryId(linkflowCategory);
            if (mlCategoryId == null) continue;

            List<MlItem> items = client.buscarPorCategoria(mlCategoryId, props.getLimitPerCategory());
            for (MlItem item : items) {
                catalog.add(toRawProduct(item, linkflowCategory));
            }

            throttle();
        }

        log.info("Mercado Livre: {} produtos coletados.", catalog.size());
        return catalog;
    }

    private RawProduct toRawProduct(MlItem item, String searchedCategory) {
        // Preferimos a categoria buscada (autoritativa); como fallback, tentamos
        // mapear pelo category_id retornado pelo ML.
        String category = searchedCategory != null
            ? searchedCategory
            : categoryMapper.toLinkflowCategory(item.categoryId());

        // TODO: comissão real via programa de afiliados do Mercado Livre.
        BigDecimal commissionPct = props.getCommissionByCategory().get(category);

        return new RawProduct(
            item.id(),
            Platform.MERCADO_LIVRE,
            item.title(),
            item.description(), // de short_description.content do produto de catálogo
            item.price(),
            item.originalPrice(),
            commissionPct,
            item.thumbnail(),
            withAffiliateTag(item.permalink()),
            category
        );
    }

    /** Acrescenta a tag de afiliado ao link do produto, se configurada. */
    private String withAffiliateTag(String permalink) {
        if (permalink == null || permalink.isBlank()) return permalink;
        String tag = props.getAffiliateTag();
        if (tag == null || tag.isBlank()) return permalink;
        String sep = permalink.contains("?") ? "&" : "?";
        return permalink + sep + "matt_tool=" + tag;
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
