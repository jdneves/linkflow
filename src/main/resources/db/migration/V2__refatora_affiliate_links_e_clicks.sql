-- ============================================================
-- V2 — Refatora affiliate_links e cria tabela clicks
--
-- A V1 usava short_code + original_url + tabela link_clicks.
-- O módulo de links atual usa slug + destination_url + tabela clicks.
-- ============================================================

-- Remove tabela de cliques antiga (FK para affiliate_links)
DROP TABLE IF EXISTS link_clicks;

-- Remove tabela de links antiga e recria com o schema atual
DROP TABLE IF EXISTS affiliate_links;

CREATE TABLE affiliate_links (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id      UUID        REFERENCES products(id) ON DELETE SET NULL,
    script_id       UUID        REFERENCES scripts(id)  ON DELETE SET NULL,
    slug            VARCHAR(200) NOT NULL UNIQUE,
    destination_url VARCHAR(1000) NOT NULL,
    title           VARCHAR(200),
    campaign        VARCHAR(100),
    clicks          BIGINT      NOT NULL DEFAULT 0,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_click_at   TIMESTAMP
);

CREATE INDEX idx_links_slug    ON affiliate_links(slug);
CREATE INDEX idx_links_user_id ON affiliate_links(user_id);

CREATE TABLE clicks (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    link_id     UUID        NOT NULL REFERENCES affiliate_links(id) ON DELETE CASCADE,
    referer     VARCHAR(500),
    device      VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ip_hash     VARCHAR(50),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clicks_link_id    ON clicks(link_id);
CREATE INDEX idx_clicks_created_at ON clicks(created_at);
