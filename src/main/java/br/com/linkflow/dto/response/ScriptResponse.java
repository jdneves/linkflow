package br.com.linkflow.dto.response;

import br.com.linkflow.entity.Script;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ScriptResponse(
    UUID id,
    String productName,
    String productId,
    String platform,
    String format,
    String tone,
    String title,
    String hook,
    List<String> topics,
    String cta,
    String caption,
    List<String> hashtags,
    List<String> stories,
    LocalDateTime createdAt
) {
    public static ScriptResponse from(Script s) {
        return new ScriptResponse(
            s.getId(),
            s.getProductName(),
            s.getProduct() != null ? s.getProduct().getId().toString() : null,
            s.getPlatform().name(),
            s.getFormat().name(),
            s.getTone(),
            s.getTitle(),
            s.getHook(),
            s.getTopics(),
            s.getCta(),
            s.getCaption(),
            s.getHashtags(),
            s.getStories(),
            s.getCreatedAt()
        );
    }
}
