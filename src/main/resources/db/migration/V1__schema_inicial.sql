-- ============================================================
-- LinkFlow - V1 - Schema inicial completo
-- ============================================================

-- Extensões
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- busca por texto

-- ==========================
-- USUÁRIOS
-- ==========================
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    plan        VARCHAR(20)  NOT NULL DEFAULT 'FREE', -- FREE, CREATOR, PRO
    avatar_url  VARCHAR(500),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ==========================
-- REFRESH TOKENS
-- ==========================
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(500) NOT NULL UNIQUE,
    expires_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user  ON refresh_tokens(user_id);

-- ==========================
-- PRODUTOS (Radar)
-- ==========================
CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_id     VARCHAR(100) NOT NULL,
    platform        VARCHAR(20)  NOT NULL, -- MERCADO_LIVRE, SHOPEE, AMAZON
    name            VARCHAR(500) NOT NULL,
    description     TEXT,
    price           NUMERIC(12,2),
    original_price  NUMERIC(12,2),
    commission_pct  NUMERIC(5,2),
    image_url       VARCHAR(500),
    product_url     VARCHAR(1000),
    category        VARCHAR(100),
    score           INTEGER DEFAULT 0,    -- score de oportunidade 0-100
    trend           VARCHAR(10) DEFAULT 'STABLE', -- RISING, STABLE, FALLING
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(external_id, platform)
);

CREATE INDEX idx_products_platform  ON products(platform);
CREATE INDEX idx_products_score     ON products(score DESC);
CREATE INDEX idx_products_category  ON products(category);
CREATE INDEX idx_products_name      ON products USING gin(name gin_trgm_ops);

-- ==========================
-- ROTEIROS
-- ==========================
CREATE TABLE scripts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id      UUID REFERENCES products(id) ON DELETE SET NULL,
    product_name    VARCHAR(500) NOT NULL,
    platform        VARCHAR(20)  NOT NULL, -- YOUTUBE, INSTAGRAM, TIKTOK
    format          VARCHAR(50)  NOT NULL, -- REVIEW, UNBOXING, VALE_A_PENA, etc.
    tone            VARCHAR(50)  NOT NULL,
    title           VARCHAR(500),
    hook            TEXT,
    topics          JSONB,                 -- ["tópico1", "tópico2", ...]
    cta             TEXT,
    caption         TEXT,
    hashtags        JSONB,                 -- ["hashtag1", ...]
    stories         JSONB,                 -- ["slide1", "slide2", ...]
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scripts_user    ON scripts(user_id);
CREATE INDEX idx_scripts_product ON scripts(product_id);

-- ==========================
-- LINKS DE AFILIADO
-- ==========================
CREATE TABLE affiliate_links (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    script_id       UUID REFERENCES scripts(id) ON DELETE SET NULL,
    product_id      UUID REFERENCES products(id) ON DELETE SET NULL,
    original_url    VARCHAR(1000) NOT NULL,
    short_code      VARCHAR(30)   NOT NULL UNIQUE, -- ex: airfryer-4l
    platform        VARCHAR(20)   NOT NULL,
    campaign        VARCHAR(100),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    last_price      NUMERIC(12,2),
    price_alert     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_links_user       ON affiliate_links(user_id);
CREATE INDEX idx_links_short_code ON affiliate_links(short_code);
CREATE INDEX idx_links_product    ON affiliate_links(product_id);

-- ==========================
-- CLIQUES NOS LINKS
-- ==========================
CREATE TABLE link_clicks (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    link_id     UUID NOT NULL REFERENCES affiliate_links(id) ON DELETE CASCADE,
    ip_hash     VARCHAR(64),   -- hash do IP por privacidade
    user_agent  VARCHAR(300),
    referer     VARCHAR(500),
    country     VARCHAR(2),
    clicked_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clicks_link    ON link_clicks(link_id);
CREATE INDEX idx_clicks_date    ON link_clicks(clicked_at);

-- ==========================
-- VÍDEOS GERADOS
-- ==========================
CREATE TABLE videos (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    script_id       UUID REFERENCES scripts(id) ON DELETE SET NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING, GENERATING_AUDIO, GENERATING_VIDEO, COMPLETED, FAILED
    audio_url       VARCHAR(1000),
    video_url       VARCHAR(1000),
    heygen_job_id   VARCHAR(100),
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP
);

CREATE INDEX idx_videos_user   ON videos(user_id);
CREATE INDEX idx_videos_status ON videos(status);

-- ==========================
-- LIMITES DE PLANO
-- ==========================
CREATE TABLE plan_usage (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    scripts_month   INTEGER NOT NULL DEFAULT 0,
    videos_month    INTEGER NOT NULL DEFAULT 0,
    links_total     INTEGER NOT NULL DEFAULT 0,
    reset_at        TIMESTAMP NOT NULL DEFAULT DATE_TRUNC('month', NOW()) + INTERVAL '1 month',
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ==========================
-- DADOS INICIAIS
-- ==========================
INSERT INTO users (id, name, email, password, plan)
VALUES (
    uuid_generate_v4(),
    'Admin',
    'admin@linkflow.com.br',
    -- senha: Admin@123 (BCrypt) — trocar antes de produção
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeAiZ5dkP8kVJ6dMi',
    'PRO'
);
