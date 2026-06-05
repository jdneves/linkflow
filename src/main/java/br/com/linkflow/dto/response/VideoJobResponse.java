package br.com.linkflow.dto.response;

import br.com.linkflow.entity.VideoJob;

import java.time.LocalDateTime;
import java.util.UUID;

public record VideoJobResponse(
    UUID id,
    String status,
    String audioUrl,
    String videoUrl,
    String scriptId,
    String productName,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {
    public static VideoJobResponse from(VideoJob job) {
        return new VideoJobResponse(
            job.getId(),
            job.getStatus().name(),
            job.getAudioUrl(),
            job.getVideoUrl(),
            job.getScript() != null ? job.getScript().getId().toString() : null,
            job.getScript() != null ? job.getScript().getProductName() : null,
            job.getErrorMessage(),
            job.getCreatedAt(),
            job.getCompletedAt()
        );
    }
}
