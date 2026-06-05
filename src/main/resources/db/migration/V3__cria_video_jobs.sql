-- ============================================================
-- V3 — Tabela de jobs de geração de vídeo (ElevenLabs + HeyGen)
-- ============================================================

CREATE TABLE video_jobs (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    script_id        UUID         REFERENCES scripts(id) ON DELETE SET NULL,
    heygen_video_id  VARCHAR(200),
    audio_url        VARCHAR(1000),
    video_url        VARCHAR(1000),
    avatar_id        VARCHAR(200),
    voice_id         VARCHAR(200),
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    error_message    VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMP
);

CREATE INDEX idx_video_jobs_user_id ON video_jobs(user_id);
CREATE INDEX idx_video_jobs_status  ON video_jobs(status);