# Guia de Endpoints GET - LinkFlow

## 📚 Tipos de Busca Implementados

### 1. **GET com Path Variable** (ID na URL)
Busca um único recurso por identificador único.

```bash
# Sintaxe
GET /api/users/{id}

# Exemplo
curl -X GET "http://localhost:8080/api/users/35f06d6b-78c6-488b-8d75-ac4b1429e390" \
  -H "Authorization: Bearer SEU_TOKEN"
```

**No Controller:**
```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
    var user = userService.findById(id);
    return ResponseEntity.ok(UserResponse.from(user));
}
```

---

### 2. **GET com Query Parameter** (Filtro na URL)
Busca recursos com filtros opcionais após `?`.

```bash
# Sintaxe
GET /api/users?email=teste@email.com

# Exemplo
curl -X GET "http://localhost:8080/api/users?email=test@example.com" \
  -H "Authorization: Bearer SEU_TOKEN"
```

**No Controller:**
```java
@GetMapping
public ResponseEntity<UserResponse> getUserByEmail(@RequestParam String email) {
    var user = userService.findByEmail(email);
    return ResponseEntity.ok(UserResponse.from(user));
}
```

---

### 3. **GET com Múltiplos Query Parameters** (Busca com filtros)
Permite combinar vários filtros opcionais.

```bash
# Sintaxe
GET /api/users/search?plan=PRO&active=true

# Exemplos
# Buscar por plano
curl -X GET "http://localhost:8080/api/users/search?plan=PRO" \
  -H "Authorization: Bearer SEU_TOKEN"

# Buscar por ativo
curl -X GET "http://localhost:8080/api/users/search?active=true" \
  -H "Authorization: Bearer SEU_TOKEN"

# Combinar filtros
curl -X GET "http://localhost:8080/api/users/search?plan=FREE&active=true" \
  -H "Authorization: Bearer SEU_TOKEN"
```

**No Controller:**
```java
@GetMapping("/search")
public ResponseEntity<List<UserResponse>> searchUsers(
    @RequestParam(required = false) String plan,
    @RequestParam(required = false) Boolean active
) {
    var users = userService.search(plan, active);
    return ResponseEntity.ok(users.stream().map(UserResponse::from).toList());
}
```

---

### 4. **GET com Paginação**
Lista recursos com controle de páginas e tamanho.

```bash
# Sintaxe
GET /api/users/all?page=0&size=10

# Exemplos
# Primeira página, 5 itens
curl -X GET "http://localhost:8080/api/users/all?page=0&size=5" \
  -H "Authorization: Bearer SEU_TOKEN"

# Segunda página, 10 itens
curl -X GET "http://localhost:8080/api/users/all?page=1&size=10" \
  -H "Authorization: Bearer SEU_TOKEN"
```

**No Controller:**
```java
@GetMapping("/all")
public ResponseEntity<Page<UserResponse>> listUsers(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size
) {
    var users = userService.findAll(PageRequest.of(page, size));
    return ResponseEntity.ok(users.map(UserResponse::from));
}
```

**Resposta de Paginação:**
```json
{
  "content": [...],         // Array com os itens
  "totalElements": 50,      // Total de registros
  "totalPages": 5,          // Total de páginas
  "size": 10,               // Tamanho da página
  "number": 0,              // Página atual (0-indexed)
  "first": true,            // É a primeira página?
  "last": false,            // É a última página?
  "numberOfElements": 10    // Quantidade de itens nesta página
}
```

---

### 5. **GET com Path Variable + Path Segment**
Busca por categoria/tipo na URL.

```bash
# Sintaxe
GET /api/users/plan/{plan}

# Exemplo
curl -X GET "http://localhost:8080/api/users/plan/PRO" \
  -H "Authorization: Bearer SEU_TOKEN"
```

**No Controller:**
```java
@GetMapping("/plan/{plan}")
public ResponseEntity<List<UserResponse>> getUsersByPlan(@PathVariable String plan) {
    var users = userService.findByPlan(plan);
    return ResponseEntity.ok(users.stream().map(UserResponse::from).toList());
}
```

---

## 🔐 Segurança e Autenticação

Todos os endpoints (exceto `/api/users/me`) requerem role `PRO`:

```java
@PreAuthorize("hasRole('PRO')")  // Apenas usuários PRO
```

**Para testar sem autenticação:**
```java
// Remover a anotação @PreAuthorize
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
    // ...
}
```

---

## 📝 Padrões Implementados

### Service Layer
```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    
    @Transactional(readOnly = true)  // Sempre read-only em consultas
    public User findById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Usuário não encontrado."));
    }
}
```

### Repository (Spring Data JPA)
```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    // Métodos de busca gerados automaticamente pelo Spring
    List<User> findByPlan(User.Plan plan);
    List<User> findByActive(Boolean active);
    List<User> findByPlanAndActive(User.Plan plan, Boolean active);
}
```

**Convenções de nomenclatura:**
- `findBy{Campo}` - Busca por campo específico
- `findBy{Campo}And{Campo2}` - Busca com múltiplos campos (AND)
- `findBy{Campo}Or{Campo2}` - Busca com múltiplos campos (OR)
- `findBy{Campo}OrderBy{Campo2}Asc` - Busca com ordenação

---

## 🧪 Testando Endpoints

### 1. Obter Token de Acesso
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test@12345"}' | jq -r '.accessToken')
```

### 2. Usar o Token nos Requests
```bash
curl -X GET "http://localhost:8080/api/users/all" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 🎯 Resumo Rápido

| Tipo | Sintaxe | Exemplo |
|------|---------|---------|
| **Path Variable** | `/{id}` | `/users/123` |
| **Query Parameter** | `?param=valor` | `/users?email=teste@email.com` |
| **Múltiplos Queries** | `?param1=x&param2=y` | `/users/search?plan=PRO&active=true` |
| **Paginação** | `?page=0&size=10` | `/users/all?page=0&size=10` |
| **Path Segment** | `/categoria/{valor}` | `/users/plan/PRO` |

---

## 🚀 Próximos Passos

Para adicionar novos endpoints GET em outros recursos (produtos, scripts, vídeos):

1. Criar o **DTO Response**
2. Adicionar métodos no **Repository** (Spring Data JPA gera automaticamente)
3. Criar o **Service** com lógica de busca
4. Criar o **Controller** com os endpoints
5. Testar com curl/Postman

**Exemplo rápido:**
```java
// ProductRepository.java
List<Product> findByPlatform(String platform);
List<Product> findByScoreGreaterThan(Integer score);

// ProductController.java
@GetMapping("/platform/{platform}")
public ResponseEntity<List<ProductResponse>> getByPlatform(@PathVariable String platform) {
    return ResponseEntity.ok(productService.findByPlatform(platform));
}
```

Agora você tem todos os padrões de busca GET implementados e funcionando! 🎉
