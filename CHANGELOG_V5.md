# Changelog v5 - Modos de Vídeo e Novos Planos

## Resumo

Introdução de **dois modos de geração de vídeo** (FACELESS e AVATAR) com **cotas separadas por plano**, e reformulação dos planos para **FREE / INICIANTE / PRO / MASTER**. O avatar passa a ser um recurso pago e medido (controle de custo).

## Mudanças Implementadas

### 1. Novos Planos de Usuário

**Planos renomeados/adicionados:**
- `CREATOR` → `INICIANTE`
- Novo plano: `MASTER`

**Grade de limites mensais:**

| Plano | Roteiros | Vídeos FACELESS | Vídeos AVATAR | Links |
|-------|----------|-----------------|---------------|-------|
| FREE | 5 | 3 | 0 (bloqueado) | 10 |
| INICIANTE | 20 | 15 | 0 (bloqueado) | 30 |
| PRO | 50 | 20 | 10 | 100 |
| MASTER | -1 (ilimitado) | 40 | 20 | -1 (ilimitado) |

### 2. Modos de Vídeo

**Novo enum `VideoMode`:**
- `FACELESS` - Vídeos sem apresentador (áudio + imagens/clipes do produto)
- `AVATAR` - Vídeos com apresentador virtual via HeyGen (recurso premium)

**Convenção de limites:**
- Para roteiros, vídeos faceless e links: `limite <= 0` = **ilimitado**
- Para vídeos avatar: `limite <= 0` = **não incluído no plano (bloqueado)**, nunca ilimitado

### 3. Alterações de Banco de Dados

**Migration V5:**
```sql
-- Renomear plano CREATOR para INICIANTE
UPDATE users SET plan = 'INICIANTE' WHERE plan = 'CREATOR';

-- Adicionar coluna mode em video_jobs
ALTER TABLE video_jobs ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'AVATAR';

-- Índices para performance
CREATE INDEX idx_video_jobs_mode ON video_jobs(mode);
CREATE INDEX idx_video_jobs_user_created ON video_jobs(user_id, created_at);
```

### 4. Mudanças de API

**Request body alterado (POST /api/videos):**

**Antes:**
```json
{
  "scriptId": "uuid",
  "avatarId": "...",
  "voiceId": "..."
}
```

**Depois:**
```json
{
  "scriptId": "uuid",
  "mode": "FACELESS",  // ou "AVATAR" (obrigatório)
  "avatarId": "...",   // opcional
  "voiceId": "..."     // opcional
}
```

**Response alterado (VideoJobResponse):**

**Campos adicionados/reordenados:**
- Campo `mode` adicionado (FACELESS ou AVATAR)
- Ordem dos campos ajustada para melhor UX

**Dashboard Analytics (GET /api/analytics/dashboard):**

**Campo `usoPlano` alterado:**

**Antes:**
```json
{
  "plano": "PRO",
  "rotelirosMes": 3,
  "limiteRoteiros": 30,
  "videosMes": 5,
  "limiteVideos": 20,
  "linksAtivos": 8,
  "limiteLinks": 50
}
```

**Depois:**
```json
{
  "plano": "PRO",
  "rotelirosMes": 3,      // mantido com typo proposital (frontend depende)
  "limiteRoteiros": 50,
  "facelessMes": 4,
  "limiteFaceless": 20,
  "avatarMes": 2,
  "limiteAvatar": 10,
  "linksAtivos": 8,
  "limiteLinks": 100
}
```

### 5. Validações de Cota

**Lógica implementada no `VideoService.validarLimitePorModo()`:**

- **Modo AVATAR:**
  - Se `limiteAvatar <= 0` → **bloqueado** (BusinessException: "Vídeos com avatar não estão disponíveis no seu plano...")
  - Senão, verifica se `avatarMes >= limiteAvatar`

- **Modo FACELESS:**
  - Se `limiteFaceless > 0` → verifica se `facelessMes >= limiteFaceless`
  - Se `limiteFaceless <= 0` → **ilimitado** (não bloqueia)

### 6. Pipeline de Geração

**Ramificação implementada em `VideoService.executarPipeline()`:**

- **AVATAR:** Pipeline atual (ElevenLabs → R2 → HeyGen)
- **FACELESS:** Novo caminho sem HeyGen (marcado com TODO)
  - Locução (ElevenLabs) ✓
  - Upload R2 ✓
  - Montagem do vídeo (Remotion/ffmpeg) - **TODO: FacelessVideoRenderer**
  - Atualmente lança `UnsupportedOperationException` com mensagem clara

### 7. Novas Classes

**`PlanLimits` (config/PlanLimits.java):**
- Centraliza limites por plano
- Métodos helper: `hasUnlimitedScripts()`, `hasUnlimitedFacelessVideos()`, `hasAvatarVideos()`, etc.

**`VideoMode` (entity/VideoMode.java):**
- Enum simples com FACELESS e AVATAR

**`VideoCreateRequest` (dto/request/VideoCreateRequest.java):**
- Record com validação Bean Validation
- Campos: `scriptId`, `mode`, `avatarId?`, `voiceId?`

### 8. Alterações em Services

**`VideoService`:**
- Novo método `validarLimitePorModo(User, VideoMode)`
- Ramificação no pipeline por modo
- Contagem separada de faceless/avatar via repository

**`ScriptService`:**
- Migrado para usar `PlanLimits` (antes tinha constantes hardcoded)
- Suporta limites ilimitados

**`LinkService`:**
- Migrado para usar `PlanLimits`
- Suporta limites ilimitados

**`AnalyticsService`:**
- Novo cálculo de `facelessMes` e `avatarMes`
- Campo `rotelirosMes` mantido (typo proposital para compatibilidade com frontend)

### 9. Repository

**`VideoJobRepository`:**
- Novo método: `countByUserAndModeThisMonth(User, VideoMode)`

### 10. Documentação Atualizada

- **README.md**: Tabela de planos, exemplo de request
- **CLAUDE.md**: Seção de planos e limites com convenções

## Checklist de Implementação

- [x] Enum Plan + migration de usuários CREATOR→INICIANTE
- [x] Grade de limites por plano (incl. faceless/avatar)
- [x] VideoMode enum + coluna `mode` em VideoJob + migration
- [x] `mode` obrigatório em VideoCreateRequest + em VideoJobResponse
- [x] Validação de cota por modo (com a exceção do avatar <= 0 = bloqueado)
- [x] Ramificação FACELESS vs AVATAR no VideoService
- [x] `usoPlano` com facelessMes/limiteFaceless/avatarMes/limiteAvatar
- [x] Preservar `rotelirosMes` (sic)
- [x] Atualizar README.md e CLAUDE.md
- [x] Compilação bem-sucedida

## Compatibilidade com Frontend

O frontend (LinkFlow-Web) JÁ foi alterado para este contrato. Os dois precisam ser deployados juntos — **não suba um sem o outro**.

## Pendências / TODO

1. **Implementar FacelessVideoRenderer** - O pipeline FACELESS está preparado mas a montagem de vídeo ainda não foi implementada. Atualmente retorna erro amigável solicitando uso do modo AVATAR.

2. **Testes unitários** - Adicionar testes para:
   - Validação de cotas por modo
   - Limites ilimitados
   - Bloqueio de avatar em planos FREE/INICIANTE
   - Contagem separada por modo

3. **Migração de dados existentes** - Vídeos criados antes da V5 terão `mode = 'AVATAR'` (valor default da migration). Isso está correto pois o HeyGen era o único modo disponível.

## Deploy

### 1. Backend (linkflow)
```bash
git add .
git commit -m "feat: modos de vídeo FACELESS/AVATAR e novos planos FREE/INICIANTE/PRO/MASTER"
git push origin main
```

### 2. Verificar Migration
A migration V5 será executada automaticamente no próximo deploy. Usuários com plano `CREATOR` serão migrados para `INICIANTE`.

### 3. Variáveis de Ambiente
Nenhuma nova variável necessária. Todas as configurações estão em código (PlanLimits).

## Breaking Changes

⚠️ **BREAKING CHANGE**: O endpoint `POST /api/videos` agora requer o campo `mode` obrigatório. Clientes antigos que não enviarem este campo receberão erro 400 de validação.

## Observações

- O typo `rotelirosMes` foi mantido propositalmente pois o frontend depende dele
- Limites ilimitados são representados por valores `<= 0` para scripts/faceless/links
- Avatar com limite `<= 0` significa **bloqueado**, não ilimitado
- A contagem de vídeos é separada por modo (faceless vs avatar)
- Vídeos FACELESS ainda não são renderizados (retorna erro amigável)
