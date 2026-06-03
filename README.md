# LinkFlow — Backend

Plataforma para criadores-afiliados: do produto ao vídeo publicado.

## Stack

- Java 21 + Spring Boot 3
- PostgreSQL + Flyway
- Redis (cache)
- JWT (access + refresh token)
- Claude API (roteiros com IA)
- ElevenLabs (narração)
- HeyGen (geração de vídeo)
- Cloudflare R2 (storage)

## Rodando localmente

### 1. Pré-requisitos
- Java 21
- Docker e Docker Compose

### 2. Sobe o banco e Redis
```bash
docker-compose up -d
```

### 3. Configure as variáveis de ambiente
Crie um arquivo `.env` na raiz (nunca suba no git):
```env
DATABASE_URL=jdbc:postgresql://localhost:5432/linkflow
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=sua-chave-secreta-com-pelo-menos-32-caracteres
ANTHROPIC_API_KEY=sk-ant-...
ELEVENLABS_API_KEY=...
HEYGEN_API_KEY=...
R2_ENDPOINT=https://SEU_ID.r2.cloudflarestorage.com
R2_ACCESS_KEY=...
R2_SECRET_KEY=...
R2_BUCKET=linkflow-media
```

### 4. Rode a aplicação
```bash
./gradlew bootRun
```

A API estará disponível em `http://localhost:8080`.

## Endpoints de autenticação

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | `/api/auth/register` | Cadastro |
| POST | `/api/auth/login` | Login |
| POST | `/api/auth/refresh` | Renovar token |
| POST | `/api/auth/logout` | Logout |
| GET  | `/api/auth/me` | Dados do usuário logado |

### Exemplo de cadastro
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"João Silva","email":"joao@email.com","password":"Senha@123"}'
```

### Exemplo de login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"joao@email.com","password":"Senha@123"}'
```

### Usando o token
```bash
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer SEU_ACCESS_TOKEN"
```

## Rodando os testes
```bash
./gradlew test
```

## Deploy no Render

1. Suba o projeto no GitHub
2. Crie um novo "Web Service" no render.com apontando para o repositório
3. O Render detecta o `Dockerfile` automaticamente
4. Configure as variáveis de ambiente no painel do Render
5. Crie um PostgreSQL no Render e copie a `DATABASE_URL` gerada

## Estrutura do projeto

```
src/main/java/br/com/linkflow/
├── LinkFlowApplication.java
├── config/
│   └── SecurityConfig.java
├── controller/
│   └── AuthController.java
├── dto/
│   ├── request/
│   │   ├── LoginRequest.java
│   │   └── RegisterRequest.java
│   └── response/
│       └── AuthResponse.java
├── entity/
│   ├── User.java
│   └── RefreshToken.java
├── exception/
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
├── repository/
│   ├── UserRepository.java
│   └── RefreshTokenRepository.java
├── scheduler/
│   └── TokenCleanupScheduler.java
├── security/
│   ├── JwtAuthenticationFilter.java
│   └── JwtService.java
└── service/
    └── AuthService.java
```
