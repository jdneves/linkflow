# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LinkFlow is a platform for creator-affiliates that generates AI-powered video content from products. The backend is a Spring Boot 3.3 REST API using Java 21, PostgreSQL, Redis, and integrates with Claude API (for scripts), ElevenLabs (narration), HeyGen (video generation), and Cloudflare R2 (storage).

## Development Commands

### Running the Application

```bash
# Start PostgreSQL + Redis
docker-compose up -d

# Run the application
./gradlew bootRun

# Stop containers
docker-compose down
```

The API runs on `http://localhost:8080`.

### Testing

```bash
# Run all tests
./gradlew test

# Run tests with coverage report
./gradlew test jacocoTestReport

# Run specific test class
./gradlew test --tests AuthServiceTest

# Run specific test method
./gradlew test --tests AuthServiceTest.deveRegistrarUsuario
```

Tests use the `test` profile and H2 in-memory database (see `src/test/resources/application-test.properties` if exists).

### Building

```bash
# Build JAR
./gradlew build

# Clean build artifacts
./gradlew clean

# Build without tests
./gradlew build -x test
```

## Architecture

### Layered Structure

```
controller/  → REST endpoints, input validation (@Valid), ResponseEntity
service/     → Business logic, transactions (@Transactional)
repository/  → JPA repositories (Spring Data JPA)
entity/      → JPA entities with Lombok (@Entity, @Builder)
dto/         → Request/response records (immutable)
  ├── request/   → API input DTOs with Bean Validation
  └── response/  → API output DTOs
security/    → JWT filter & token service
config/      → Spring configurations (SecurityConfig, etc.)
exception/   → Custom exceptions + GlobalExceptionHandler
scheduler/   → Background jobs (@Scheduled)
```

### Authentication Flow

**Stateless JWT with refresh tokens:**

1. **Register/Login** → Returns `accessToken` (short-lived, 1 day) + `refreshToken` (long-lived, 7 days)
2. **Access protected endpoints** → Send `Authorization: Bearer <accessToken>`
3. **Access token expires** → POST `/api/auth/refresh` with `refreshToken` to get new tokens
4. **Logout** → POST `/api/auth/logout` (deletes refresh token from DB)

**Key classes:**
- `JwtService` - Generate/validate access tokens, extract claims
- `JwtAuthenticationFilter` - Intercepts requests, validates JWT, sets SecurityContext
- `AuthService` - Register, login, refresh, logout logic
- `RefreshToken` entity - Stored in DB with expiration, one-time use pattern

**Security notes:**
- Passwords hashed with BCrypt (12 rounds)
- Refresh tokens are UUID strings, not JWTs
- Tokens cleaned up daily at 3AM via `TokenCleanupScheduler`
- CSRF disabled (stateless API), CORS configured for all origins in dev

### Database Schema

**Core tables:**
- `users` - User accounts with plan (FREE/CREATOR/PRO), email (unique), password (BCrypt)
- `refresh_tokens` - JWT refresh tokens with expiration
- `products` - Product catalog from affiliate platforms (Mercado Livre, Shopee, Amazon)
- `scripts` - AI-generated video scripts (title, hook, topics, CTA)
- `affiliate_links` - Short links with tracking (`/r/{short_code}`)
- `link_clicks` - Click analytics (IP hash, user agent, geolocation)
- `videos` - Generated video jobs with status tracking
- `plan_usage` - Monthly limits per user plan

**Migrations:** Flyway at `src/main/resources/db/migration/V{n}__{description}.sql`

**Indexes:**
- `users.email`, `refresh_tokens.token` - Lookup performance
- `products.score`, `products.category` - Filtering/sorting
- `products.name` (GIN trigram) - Fuzzy text search
- `link_clicks.clicked_at` - Time-based analytics

### External Integrations

**Configuration via environment variables:**

```properties
# Claude API (script generation)
linkflow.claude.api-key=${ANTHROPIC_API_KEY}
linkflow.claude.model=claude-sonnet-4-20250514

# ElevenLabs (text-to-speech)
linkflow.elevenlabs.api-key=${ELEVENLABS_API_KEY}

# HeyGen (video generation)
linkflow.heygen.api-key=${HEYGEN_API_KEY}

# Cloudflare R2 (storage)
linkflow.storage.endpoint=${R2_ENDPOINT}
linkflow.storage.access-key=${R2_ACCESS_KEY}
linkflow.storage.secret-key=${R2_SECRET_KEY}
linkflow.storage.bucket=${R2_BUCKET}
```

**Implementation pattern:**
- Services will inject these via `@Value`
- Use timeouts for long-running operations (video generation can take minutes)
- Async processing enabled (`@EnableAsync` in main class)

### User Plans & Limits

Three tiers with different quotas:
- **FREE** - Limited scripts/videos per month
- **CREATOR** - Medium limits
- **PRO** - High limits

Limits tracked in `plan_usage` table, reset monthly. Enforce limits in service layer before expensive operations (AI calls, video generation).

### DTO Patterns

**Request DTOs** - Java records with Bean Validation:
```java
public record RegisterRequest(
    @NotBlank @Size(min = 2, max = 100) String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password
) {}
```

**Response DTOs** - Java records, built from entities:
```java
public record AuthResponse(String accessToken, String refreshToken, UserInfo user) {
    public record UserInfo(...) {
        public static UserInfo from(User entity) { ... }
    }
}
```

Never expose JPA entities directly in controllers.

### Exception Handling

`GlobalExceptionHandler` (@RestControllerAdvice) catches:
- `BusinessException` → 400 Bad Request (business rule violations)
- `BadCredentialsException` → 401 Unauthorized (wrong credentials)
- `MethodArgumentNotValidException` → 400 with field errors (validation failures)
- `Exception` → 500 Internal Server Error (unexpected errors, logged with stacktrace)

Custom business exceptions extend `BusinessException` with user-friendly messages.

### Dependency Injection

Constructor injection with Lombok:
```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    // ...
}
```

Never use `@Autowired` on fields.

## Code Review with /linkflow-review

A custom skill `/linkflow-review` is available for comprehensive code reviews focused on:
1. Security vulnerabilities (auth bypass, injection, XSS, exposed secrets)
2. Functional bugs (NPE, race conditions, resource leaks)
3. Spring Boot best practices
4. JPA/Database optimization
5. Code quality & SOLID principles

Priority: Security → Bugs → Quality → Style

## Important Implementation Notes

### JWT Secret Security
The `JWT_SECRET` environment variable MUST be set in production (at least 32 characters). The default in `application.properties` is for development only and should never be used in production.

### Refresh Token Security
Refresh tokens should be one-time use. After validating a refresh token, delete it from the database BEFORE generating new tokens to prevent replay attacks.

### Password Validation
Current validation only checks minimum length (8 chars). Consider adding complexity requirements (uppercase, lowercase, number, special character) for production.

### Rate Limiting
Login endpoint has no rate limiting. Implement rate limiting (Bucket4j, Redis) to prevent brute force attacks before production deployment.

### Database Connection Pool
Uses HikariCP defaults. Monitor connection pool usage under load and tune if needed.

### Async Processing
`@EnableAsync` is configured. Use `@Async` on methods for long-running operations (video generation, API calls) to avoid blocking HTTP threads.

### Scheduled Tasks
Scheduled jobs run only in a single instance. Use distributed locks (Redis) if deploying multiple instances to prevent duplicate execution.

### Logging
Uses SLF4J with Logback. Avoid logging sensitive data (passwords, tokens, PII). Structure logs with context (userId, requestId) for debugging.

## Environment Variables Required

**Database:**
- `DATABASE_URL` - PostgreSQL connection string
- `DATABASE_USER` - DB username
- `DATABASE_PASSWORD` - DB password

**Cache:**
- `REDIS_HOST` - Redis hostname
- `REDIS_PORT` - Redis port
- `REDIS_PASSWORD` - Redis password (optional)

**Security:**
- `JWT_SECRET` - JWT signing key (min 32 chars)

**External APIs:**
- `ANTHROPIC_API_KEY` - Claude API key
- `ELEVENLABS_API_KEY` - ElevenLabs API key
- `HEYGEN_API_KEY` - HeyGen API key
- `R2_ENDPOINT` - Cloudflare R2 endpoint URL
- `R2_ACCESS_KEY` - R2 access key
- `R2_SECRET_KEY` - R2 secret key
- `R2_BUCKET` - R2 bucket name

See `.env.example` or README.md for example values.

## Testing Strategy

**Integration tests** use `@SpringBootTest` with `@ActiveProfiles("test")` and H2 database. Tests are transactional (rolled back after each test).

**Test naming:** Portuguese method names describing behavior:
```java
@DisplayName("Deve registrar novo usuário com sucesso")
void deveRegistrarUsuario() { ... }
```

Use AssertJ for fluent assertions (`assertThat(...)`).

## Common Issues & Solutions

**"Refresh token inválido"** - Token already used (one-time use) or expired  
**"E-mail já cadastrado"** - User with email already exists  
**"E-mail ou senha incorretos"** - Bad credentials on login  
**Token validation fails** - Check JWT_SECRET matches between token generation and validation  
**Database connection fails** - Verify PostgreSQL is running and DATABASE_URL is correct  
**Redis connection fails** - Verify Redis is running and credentials are correct
