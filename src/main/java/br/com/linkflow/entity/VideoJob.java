package br.com.linkflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_jobs", indexes = {
    @Index(name = "idx_video_jobs_user_id", columnList = "user_id"),
    @Index(name = "idx_video_jobs_status",  columnList = "status"),
    @Index(name = "idx_video_jobs_mode", columnList = "mode"),
    @Index(name = "idx_video_jobs_user_created", columnList = "user_id, created_at")
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class VideoJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id")
    private Script script;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VideoMode mode = VideoMode.AVATAR;

    // IDs externos das APIs
    @Column(name = "heygen_video_id", length = 200)
    private String heygenVideoId;

    @Column(name = "audio_url", length = 1000)
    private String audioUrl;

    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    @Column(name = "avatar_id", length = 200)
    private String avatarId;

    @Column(name = "voice_id", length = 200)
    private String voiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum Status {
        PENDING,      // aguardando início
        GENERATING_AUDIO,  // ElevenLabs processando
        GENERATING_VIDEO,  // HeyGen processando
        COMPLETED,    // vídeo pronto
        FAILED        // erro em alguma etapa
    }
}
