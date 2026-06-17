#!/usr/bin/env bash
#
# Troca um authorization_code do Mercado Livre por access_token + refresh_token
# e grava ML_CLIENT_ID / ML_CLIENT_SECRET / ML_REFRESH_TOKEN no .env.
#
# As credenciais NÃO ficam no script. Forneça-as por ambiente (ou pelo .env, que
# é carregado automaticamente se existir):
#
#   ML_CLIENT_ID=...  ML_CLIENT_SECRET=...  ./scripts/ml-exchange-token.sh TG-xxxx
#
# Variáveis aceitas:
#   ML_CLIENT_ID      (obrigatória)
#   ML_CLIENT_SECRET  (obrigatória)
#   ML_REDIRECT_URI   (opcional; default abaixo)
#
# O authorization_code é de USO ÚNICO e expira em ~10 min. Gere um novo pela
# URL de autorização sempre que for rodar.

set -euo pipefail

ENV_FILE="$(dirname "$0")/../.env"

# Carrega o .env (se existir) para popular ML_CLIENT_ID/ML_CLIENT_SECRET/etc.
# O ambiente do shell tem prioridade sobre o .env.
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

CLIENT_ID="${ML_CLIENT_ID:-}"
CLIENT_SECRET="${ML_CLIENT_SECRET:-}"
REDIRECT_URI="${ML_REDIRECT_URI:-https://linkflow-api-vjqp.onrender.com/api/integrations/mercadolivre/callback}"

CODE="${1:-}"
if [[ -z "$CODE" ]]; then
  echo "Erro: informe o authorization_code. Ex.: $0 TG-xxxx-xxxx" >&2
  exit 1
fi
if [[ -z "$CLIENT_ID" || -z "$CLIENT_SECRET" ]]; then
  echo "Erro: defina ML_CLIENT_ID e ML_CLIENT_SECRET (no ambiente ou no .env)." >&2
  exit 1
fi

echo "==> Trocando code por tokens no Mercado Livre..."
RESP="$(curl -s -X POST https://api.mercadolibre.com/oauth/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'Accept: application/json' \
  -d 'grant_type=authorization_code' \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "code=${CODE}" \
  -d "redirect_uri=${REDIRECT_URI}")"

ACCESS_TOKEN="$(printf '%s' "$RESP" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)"
REFRESH_TOKEN="$(printf '%s' "$RESP" | grep -o '"refresh_token":"[^"]*"' | cut -d'"' -f4)"

if [[ -z "$REFRESH_TOKEN" ]]; then
  echo "==> Falha ao obter refresh_token. Resposta da API:" >&2
  printf '%s\n' "$RESP" >&2
  exit 1
fi

echo "==> Tokens obtidos com sucesso."
echo "    access_token  (expira em ~6h): ${ACCESS_TOKEN:0:12}..."
echo "    refresh_token (durável):       ${REFRESH_TOKEN:0:12}..."

# Atualiza/insere as chaves no .env (idempotente)
touch "$ENV_FILE"
upsert() {
  local key="$1" val="$2"
  if grep -qE "^${key}=" "$ENV_FILE"; then
    # remove a linha antiga e adiciona a nova no fim
    grep -vE "^${key}=" "$ENV_FILE" > "${ENV_FILE}.tmp" && mv "${ENV_FILE}.tmp" "$ENV_FILE"
  fi
  printf '%s=%s\n' "$key" "$val" >> "$ENV_FILE"
}

upsert "ML_ENABLED" "true"
upsert "ML_CLIENT_ID" "$CLIENT_ID"
upsert "ML_CLIENT_SECRET" "$CLIENT_SECRET"
upsert "ML_REFRESH_TOKEN" "$REFRESH_TOKEN"

echo "==> .env atualizado: ML_ENABLED, ML_CLIENT_ID, ML_CLIENT_SECRET, ML_REFRESH_TOKEN"
echo "    (o access_token NÃO é salvo — o TokenManager o renova via refresh_token)"
