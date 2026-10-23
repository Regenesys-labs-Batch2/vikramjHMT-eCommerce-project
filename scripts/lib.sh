#!/usr/bin/env bash
# Shared helpers for the end-to-end scripts. Source this file; do not run it.
IDENTITY=${IDENTITY_URL:-http://localhost:8081}
CATALOG=${CATALOG_URL:-http://localhost:8082}
INVENTORY=${INVENTORY_URL:-http://localhost:8083}
CART=${CART_URL:-http://localhost:8084}
ORDER=${ORDER_URL:-http://localhost:8085}
ADMIN_PASSWORD=${SEED_ADMIN_PASSWORD:-Admin#12345}

PASS=0
FAIL=0
BODY_FILE=$(mktemp)

# req METHOD URL TOKEN [BODY] [EXTRA_HEADER]  ->  sets CODE and leaves the response body in $BODY_FILE
req() {
  local method=$1 url=$2 token=$3 body=${4:-} extra=${5:-}
  local args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$url"
    -H 'Content-Type: application/json' -H "X-Correlation-Id: smoke-$RANDOM")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$extra" ] && args+=(-H "$extra")
  [ -n "$body" ] && args+=(-d "$body")
  CODE=$(curl "${args[@]}")
}

field() { jq -r "$1" "$BODY_FILE"; }

check() { # NAME EXPECTED ACTUAL
  if [ "$2" = "$3" ]; then
    echo "PASS $1"
    PASS=$((PASS + 1))
  else
    echo "FAIL $1 (expected '$2', got '$3')"
    FAIL=$((FAIL + 1))
  fi
}

uuid() { cat /proc/sys/kernel/random/uuid; }

admin_login() {
  req POST "$IDENTITY/api/auth/login" "" "{\"email\":\"admin@ecom.test\",\"password\":\"$ADMIN_PASSWORD\"}"
  ADMIN_TOKEN=$(field .accessToken)
}

# Registers and logs in a fresh customer; sets NC_EMAIL, NC_TOKEN and NC_ID.
new_customer() {
  NC_EMAIL="smoke-$(date +%s%N)-$RANDOM@ecom.test"
  req POST "$IDENTITY/api/users/register" "" "{\"email\":\"$NC_EMAIL\",\"password\":\"Passw0rd!23\",\"fullName\":\"Smoke Test\"}"
  req POST "$IDENTITY/api/auth/login" "" "{\"email\":\"$NC_EMAIL\",\"password\":\"Passw0rd!23\"}"
  NC_TOKEN=$(field .accessToken)
  NC_ID=$(field .userId)
}

# new_product PRICE STOCK -> creates a category and product (and stock when STOCK > 0); needs ADMIN_TOKEN.
new_product() {
  local sku
  sku="$(tr -dc 'A-Z' </dev/urandom | head -c3)-$(printf '%03d' $((RANDOM % 1000)))"
  req POST "$CATALOG/api/categories" "$ADMIN_TOKEN" "{\"name\":\"Smoke-$RANDOM-$RANDOM\",\"description\":\"smoke\"}"
  CATEGORY_ID=$(field .id)
  req POST "$CATALOG/api/products" "$ADMIN_TOKEN" \
    "{\"sku\":\"$sku\",\"name\":\"Smoke Product $sku\",\"description\":\"d\",\"categoryId\":\"$CATEGORY_ID\",\"price\":{\"amount\":$1,\"currency\":\"INR\"},\"active\":true}"
  PRODUCT_ID=$(field .id)
  PRODUCT_SKU=$sku
  if [ "${2:-0}" -gt 0 ]; then
    req POST "$INVENTORY/api/inventory/$PRODUCT_ID/adjustments" "$ADMIN_TOKEN" "{\"delta\":$2,\"reason\":\"smoke restock\"}"
  fi
}

# add_to_cart TOKEN PRODUCT_ID QUANTITY
add_to_cart() {
  req POST "$CART/api/carts/items" "$1" "{\"productId\":\"$2\",\"quantity\":$3}"
}

# checkout TOKEN [BODY] [KEY] -> sets CODE; KEY defaults to a fresh UUID and is left in LAST_KEY
checkout() {
  local body=${2:-}
  [ -z "$body" ] && body='{}'
  LAST_KEY=${3:-$(uuid)}
  req POST "$ORDER/api/orders" "$1" "$body" "Idempotency-Key: $LAST_KEY"
}

stock_field() { # PRODUCT_ID JQ_FIELD
  req GET "$INVENTORY/api/inventory/$1" "$ADMIN_TOKEN"
  field "$2"
}
