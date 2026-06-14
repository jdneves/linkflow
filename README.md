# LinkFlow — Backend

Plataforma para criadores-afiliados: do produto ao vídeo publicado.

O LinkFlow automatiza o pipeline completo — encontra produtos em alta, gera roteiros com IA, produz narração e vídeo, encurta links de afiliado e entrega analytics de cliques.

## Stack

- **Java 21** + **Spring Boot 3.3**
- **PostgreSQL** + Flyway (migrations)
- **Redis** (cache)
- **JWT** (access token + refresh token stateless)
- **Claude API** — geração de roteiros com IA
- **ElevenLabs** — narração por texto-para-voz
- **HeyGen** — geração de vídeo com avatar
- **Cloudflare R2** — armazenamento de mídia

## Módulos

| Módulo | Prefixo | Descrição |
|--------|---------|-----------|
| Auth | `/api/auth` | Cadastro, login, refresh e logout |
| Radar de Produtos | `/api/radar` | Produtos em alta e busca por categoria |
| Studio | `/api/studio` | Geração de roteiros com Claude AI |
| Vídeos | `/api/videos` | Jobs de geração de vídeo via HeyGen |
| Links Afiliados | `/api/links` | Encurtamento e rastreamento de cliques |
| Analytics | `/api/analytics` | Dashboard e relatório semanal |
| Onboarding | `/api/onboarding` | Progresso de configuração do creator |
| Usuários | `/api/users` | Gerenciamento de perfis |

## Rodando localmente

### Pré-requisitos

- Java 21
- Docker e Docker Compose

### 1. Sobe o banco e o Redis

```bash
docker-compose up -d
```

### 2. Configure as variáveis de ambiente

Copie o arquivo de exemplo e preencha com seus valores:

```bash
cp .env.example .env
```

```env
# Banco de dados
DATABASE_URL=jdbc:postgresql://localhost:5432/linkflow
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT (mínimo 32 caracteres)
JWT_SECRET=sua-chave-super-secreta-com-32-ou-mais-chars

# Claude API
ANTHROPIC_API_KEY=sk-ant-...

# ElevenLabs
ELEVENLABS_API_KEY=...
ELEVENLABS_VOICE_ID=21m00Tcm4TlvDq8ikWAM

# HeyGen
HEYGEN_API_KEY=...
HEYGEN_AVATAR_ID=...

# Cloudflare R2
R2_ENDPOINT=https://SEU_ID.r2.cloudflarestorage.com
R2_ACCESS_KEY=...
R2_SECRET_KEY=...
R2_BUCKET=linkflow-media
R2_PUBLIC_URL=https://pub-SEU_ID.r2.dev
```

### 3. Rode a aplicação

```bash
./gradlew bootRun
```

A API estará disponível em `http://localhost:8080`.

## Endpoints

### Autenticação

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| POST | `/api/auth/register` | — | Cadastro |
| POST | `/api/auth/login` | — | Login |
| POST | `/api/auth/refresh` | — | Renovar access token |
| POST | `/api/auth/logout` | Bearer | Logout |
| GET | `/api/auth/me` | Bearer | Dados do usuário logado |

### Radar de Produtos

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| GET | `/api/radar` | Bearer | Listar produtos (paginado, filtros) |
| GET | `/api/radar/trending` | Bearer | Produtos em alta |
| GET | `/api/radar/{id}` | Bearer | Detalhe do produto |
| GET | `/api/radar/categorias` | Bearer | Listar categorias disponíveis |

### Studio (Roteiros)

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| POST | `/api/studio/roteiro` | Bearer | Gerar roteiro com IA |
| GET | `/api/studio/roteiros` | Bearer | Listar roteiros do usuário |
| GET | `/api/studio/roteiros/{id}` | Bearer | Detalhe do roteiro |
| GET | `/api/studio/roteiros/produto/{productId}` | Bearer | Roteiros por produto |

### Vídeos

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| POST | `/api/videos` | Bearer | Iniciar geração de vídeo (FACELESS ou AVATAR) |
| GET | `/api/videos/{id}` | Bearer | Status do job de vídeo |
| GET | `/api/videos` | Bearer | Listar vídeos do usuário |

**Request body (POST /api/videos):**
```json
{
  "scriptId": "uuid-do-roteiro",
  "mode": "FACELESS",  // ou "AVATAR"
  "avatarId": "...",   // opcional, obrigatório se mode=AVATAR
  "voiceId": "..."     // opcional
}
```

### Links Afiliados

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| POST | `/api/links` | Bearer | Criar link encurtado |
| GET | `/api/links` | Bearer | Listar links do usuário |
| GET | `/api/links/{id}` | Bearer | Detalhe do link |
| DELETE | `/api/links/{id}` | Bearer | Remover link |
| GET | `/r/{username}/{slug}` | — | Redirecionar (rastreia clique) |

### Analytics

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| GET | `/api/analytics/dashboard` | Bearer | Métricas consolidadas |
| POST | `/api/analytics/relatorio` | Bearer | Relatório semanal |

### Onboarding

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| GET | `/api/onboarding` | Bearer | Progresso de onboarding |

### Usuários

| Método | Rota | Auth | Descrição |
|--------|------|------|-----------|
| GET | `/api/users/me` | Bearer | Perfil do usuário autenticado |
| GET | `/api/users/{id}` | Bearer | Buscar por ID |
| GET | `/api/users` | Bearer | Listar usuários (paginado) |
| GET | `/api/users/search` | Bearer | Buscar por nome/email |
| GET | `/api/users/all` | Bearer | Todos os usuários |
| GET | `/api/users/plan/{plan}` | Bearer | Usuários por plano |

## Exemplo rápido

```bash
# 1. Cadastrar
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"João Silva","email":"joao@email.com","password":"Senha@123"}'

# 2. Login → copie o accessToken da resposta
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"joao@email.com","password":"Senha@123"}'

# 3. Produtos em alta
curl http://localhost:8080/api/radar/trending \
  -H "Authorization: Bearer SEU_ACCESS_TOKEN"

# 4. Gerar roteiro
curl -X POST http://localhost:8080/api/studio/roteiro \
  -H "Authorization: Bearer SEU_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":"uuid-do-produto","tone":"INFORMAL","duration":60}'

# 5. Iniciar vídeo FACELESS
curl -X POST http://localhost:8080/api/videos \
  -H "Authorization: Bearer SEU_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"scriptId":"uuid-do-roteiro","mode":"FACELESS"}'
```

Uma coleção Postman completa está disponível em [`postman/LinkFlow.postman_collection.json`](postman/LinkFlow.postman_collection.json).

## Testes

```bash
# Rodar todos os testes
./gradlew test

# Com relatório de cobertura
./gradlew test jacocoTestReport

# Classe específica
./gradlew test --tests AuthServiceTest
```

Os testes usam o perfil `test` com banco H2 em memória — não é necessário PostgreSQL rodando.

## CI/CD

O pipeline no GitHub Actions (`.github/workflows/ci-cd.yml`) executa em cada push/PR para `main`:

1. **Build e testes** — sobe PostgreSQL como service, roda `./gradlew test`
2. **Deploy automático** — aciona o deploy hook do Render após os testes passarem

## Deploy no Render

1. Suba o projeto no GitHub
2. Crie um novo **Web Service** no [render.com](https://render.com) apontando para o repositório
3. O Render detecta o `Dockerfile` automaticamente
4. Configure todas as variáveis de ambiente no painel do Render
5. Crie um **PostgreSQL** no Render e copie a `DATABASE_URL` gerada
6. (Opcional) Adicione o `RENDER_DEPLOY_HOOK` como secret no GitHub para deploy automático via CI

## Estrutura do projeto

```
src/main/java/br/com/linkflow/
├── LinkFlowApplication.java
├── client/                  # Clientes HTTP externos (Claude, ElevenLabs, HeyGen, R2)
├── config/                  # SecurityConfig, CacheConfig
├── controller/              # REST endpoints
├── dto/
│   ├── request/             # DTOs de entrada com Bean Validation
│   └── response/            # DTOs de saída
├── entity/                  # Entidades JPA
├── exception/               # BusinessException + GlobalExceptionHandler
├── mock/                    # Dados mock para desenvolvimento
├── repository/              # Spring Data JPA repositories
├── scheduler/               # Jobs agendados (limpeza de tokens, alertas de preço)
├── security/                # JwtService + JwtAuthenticationFilter
└── service/                 # Lógica de negócio

src/main/resources/
├── application.properties
└── db/migration/            # Scripts Flyway (V1__, V2__, ...)
```

## Planos e Limites

O LinkFlow oferece quatro planos com cotas mensais diferenciadas:

| Plano | Roteiros | Vídeos FACELESS | Vídeos AVATAR | Links |
|-------|----------|----------------|---------------|-------|
| **FREE** | 5 | 3 | 🚫 Bloqueado | 10 |
| **INICIANTE** | 20 | 15 | 🚫 Bloqueado | 30 |
| **PRO** | 50 | 20 | 10 | 100 |
| **MASTER** | ∞ Ilimitado | 40 | 20 | ∞ Ilimitado |

### Modos de vídeo

- **FACELESS**: Vídeos sem apresentador (áudio + imagens/clipes do produto)
- **AVATAR**: Vídeos com apresentador virtual via HeyGen (recurso premium)

Os limites são resetados mensalmente. Vídeos com avatar são exclusivos dos planos PRO e MASTER.

## Licença

MIT
