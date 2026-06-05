package br.com.linkflow.dto.response;

import br.com.linkflow.entity.AffiliateLink;

import java.time.LocalDateTime;
import java.util.UUID;

public record LinkResponse(
    UUID id,
    String slug,
    String shortUrl,
    String destinationUrl,
    String title,
    String campaign,
    Long clicks,
    String productId,
    String scriptId,
    String qrCodeUrl,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime lastClickAt
) {
    public static LinkResponse from(AffiliateLink link, String baseUrl) {
        String shortUrl = baseUrl + "/r/" + link.getSlug();
        return new LinkResponse(
            link.getId(),
            link.getSlug(),
            shortUrl,
            link.getDestinationUrl(),
            link.getTitle(),
            link.getCampaign(),
            link.getClicks(),
            link.getProduct() != null ? link.getProduct().getId().toString() : null,
            link.getScript() != null ? link.getScript().getId().toString() : null,
            // QR Code via API pública gratuita
            "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=" + shortUrl,
            link.getActive(),
            link.getCreatedAt(),
            link.getLastClickAt()
        );
    }
}
