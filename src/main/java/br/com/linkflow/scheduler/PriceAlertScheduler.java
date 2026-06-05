package br.com.linkflow.scheduler;

import br.com.linkflow.entity.AffiliateLink;
import br.com.linkflow.repository.AffiliateLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertScheduler {

    private final AffiliateLinkRepository linkRepository;
    private final JavaMailSender mailSender;

    // Roda a cada 12 horas
    @Scheduled(cron = "0 0 8,20 * * *")
    public void verificarMudancasDePreco() {
        log.info("Verificando mudanças de preço nos links ativos...");

        // Busca links vinculados a produtos
        linkRepository.findAll().stream()
            .filter(link -> link.getProduct() != null && link.getActive())
            .forEach(this::verificarLink);
    }

    private void verificarLink(AffiliateLink link) {
        try {
            var produto = link.getProduct();
            if (produto == null || produto.getPrice() == null) return;

            // Aqui, no futuro, você consulta a API real do ML/Shopee
            // Por enquanto simula: nenhuma mudança detectada no mock
            // Quando integrar a API real, compara o preço atual com produto.getPrice()

            // Exemplo de como será quando integrar:
            // BigDecimal precoAtual = mlApiClient.getPreco(produto.getExternalId());
            // if (precoSubiuMaisDe10Pct(produto.getPrice(), precoAtual)) {
            //     notificarUsuario(link, produto.getPrice(), precoAtual);
            // }

            log.debug("Link {} verificado — nenhuma mudança detectada (mock ativo)", link.getSlug());

        } catch (Exception e) {
            log.error("Erro ao verificar link {}: {}", link.getSlug(), e.getMessage());
        }
    }

    private boolean precoSubiuMaisDe10Pct(BigDecimal anterior, BigDecimal atual) {
        if (anterior.compareTo(BigDecimal.ZERO) == 0) return false;
        BigDecimal variacao = atual.subtract(anterior)
            .divide(anterior, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        return variacao.compareTo(BigDecimal.valueOf(10)) > 0;
    }

    void notificarUsuario(AffiliateLink link, BigDecimal precoAnterior, BigDecimal precoAtual) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(link.getUser().getEmail());
            message.setSubject("⚠️ Alerta de preço — " + link.getTitle());
            message.setText("""
                Olá, %s!

                O produto vinculado ao seu link subiu de preço:

                Produto: %s
                Preço anterior: R$ %s
                Preço atual:    R$ %s

                Recomendamos atualizar sua divulgação ou trocar o produto.

                Acesse: linkflow.app/links

                Equipe LinkFlow
                """.formatted(
                    link.getUser().getName(),
                    link.getTitle() != null ? link.getTitle() : link.getSlug(),
                    precoAnterior,
                    precoAtual
                )
            );
            mailSender.send(message);
            log.info("Alerta de preço enviado para {}", link.getUser().getEmail());
        } catch (Exception e) {
            log.error("Falha ao enviar alerta de preço: {}", e.getMessage());
        }
    }
}
