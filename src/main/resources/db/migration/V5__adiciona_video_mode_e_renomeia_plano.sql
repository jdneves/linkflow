-- ============================================================
-- V5 — Adiciona modo de vídeo (FACELESS/AVATAR) e novos planos
-- ============================================================

-- 1. Renomear plano CREATOR para INICIANTE
UPDATE users SET plan = 'INICIANTE' WHERE plan = 'CREATOR';

-- 2. Adicionar coluna mode em video_jobs
ALTER TABLE video_jobs ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'AVATAR';

-- 3. Criar índice para facilitar contagem por modo
CREATE INDEX idx_video_jobs_mode ON video_jobs(mode);
CREATE INDEX idx_video_jobs_user_created ON video_jobs(user_id, created_at);
