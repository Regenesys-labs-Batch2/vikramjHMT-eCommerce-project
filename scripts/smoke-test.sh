#!/usr/bin/env bash
# End-to-end business scenarios (Reference O) against the six running services.
# Usage: smoke-test.sh [all|timeout]   ("timeout" expects Inventory to be stopped)
set -u
cd "$(dirname "$0")" && source ./lib.sh
MODE=${1:-all}

truthy() { [ -n "$1" ] && [ "$1" != null ] && echo true || echo false; }

admin_login
check "admin can log in" true "$(truthy "$ADMIN_TOKEN")"

if [ "$MODE" = timeout ]; then
  new_customer
  new_product 1000.00 0
  add_to_cart "$NC_TOKEN" "$PRODUCT_ID" 1
  checkout "$NC_TOKEN"
  check "inventory down returns 503" 503 "$CODE"
  check "inventory down error code" DEPENDENCY_UNAVAILABLE "$(field .code)"
  req GET "$ORDER/api/orders/customer/$NC_ID" "$NC_TOKEN"
  check "order is CANCELLED, never CONFIRMED" CANCELLED "$(field '.content[0].status')"
  check "order failure reason" INVENTORY_UNAVAILABLE "$(field '.content[0].failureReason')"
  echo "RESULT pass=$PASS fail=$FAIL"
  [ "$FAIL" -eq 0 ]
  exit $?
fi

# --- registration, authentication, profile ---------------------------------------------------------
new_customer
TOKEN=$NC_TOKEN
CUSTOMER_ID=$NC_ID
EMAIL=$NC_EMAIL
check "customer registered and authenticated" true "$(truthy "$TOKEN")"
req POST "$IDENTITY/api/users/register" "" "{\"email\":\"$EMAIL\",\"password\":\"Passw0rd!23\",\"fullName\":\"Dup\"}"
check "duplicate registration is rejected" 409 "$CODE"
req POST "$IDENTITY/api/auth/login" "" "{\"email\":\"$EMAIL\",\"password\":\"wrong-password\"}"
check "wrong password returns 401" 401 "$CODE"
req GET "$IDENTITY/api/users/me" "$TOKEN"
check "profile is readable" 200 "$CODE"
check "profile email" "$EMAIL" "$(field .email)"
req GET "$IDENTITY/api/users/$CUSTOMER_ID" "$TOKEN"
check "customer cannot use the admin user lookup" 403 "$CODE"
req GET "$IDENTITY/api/users/me" ""
check "missing token returns 401" 401 "$CODE"

# --- catalog -----------------------------------------------------------------------------------------
new_product 1200.00 5
check "admin created a product" true "$(truthy "$PRODUCT_ID")"
req GET "$CATALOG/api/products/$PRODUCT_ID" ""
check "product is publicly readable" 200 "$CODE"
req GET "$CATALOG/api/products/search?q=smoke%20product&minPrice=1000" ""
check "search finds the product" true "$([ "$(field .totalElements)" -ge 1 ] && echo true || echo false)"
req POST "$CATALOG/api/products" "$TOKEN" "{\"sku\":\"ZZZ-999\",\"name\":\"x\",\"categoryId\":\"$CATEGORY_ID\",\"price\":{\"amount\":10,\"currency\":\"INR\"}}"
check "customer cannot create products" 403 "$CODE"

# --- inventory ---------------------------------------------------------------------------------------
check "admin restock is visible" 5 "$(stock_field "$PRODUCT_ID" .availableQuantity)"
req POST "$INVENTORY/api/inventory/$PRODUCT_ID/adjustments" "$TOKEN" '{"delta":5,"reason":"nope"}'
check "customer cannot adjust stock" 403 "$CODE"

# --- successful checkout and idempotency -------------------------------------------------------------
add_to_cart "$TOKEN" "$PRODUCT_ID" 2
check "item added to the cart" 201 "$CODE"
checkout "$TOKEN"
check "checkout returns 201" 201 "$CODE"
check "order is CONFIRMED" CONFIRMED "$(field .status)"
ORDER1=$(field .id)
KEY1=$LAST_KEY
check "stock was reduced" 3 "$(stock_field "$PRODUCT_ID" .availableQuantity)"
checkout "$TOKEN" '{}' "$KEY1"
check "duplicate request returns 200" 200 "$CODE"
check "duplicate request returns the same order" "$ORDER1" "$(field .id)"
checkout "$TOKEN" '{"simulatedPaymentOutcome":"DECLINED"}' "$KEY1"
check "same key with a different body returns 422" 422 "$CODE"

# --- insufficient stock ------------------------------------------------------------------------------
add_to_cart "$TOKEN" "$PRODUCT_ID" 50
checkout "$TOKEN"
check "insufficient stock returns 409" 409 "$CODE"
check "insufficient stock code" INSUFFICIENT_STOCK "$(field .code)"
req GET "$CART/api/carts/current" "$TOKEN"
ITEM_ID=$(field '.items[0].id')
req DELETE "$CART/api/carts/items/$ITEM_ID" "$TOKEN"
check "cart item removed" 204 "$CODE"

# --- payment failure and compensation ----------------------------------------------------------------
add_to_cart "$TOKEN" "$PRODUCT_ID" 1
checkout "$TOKEN" '{"simulatedPaymentOutcome":"DECLINED"}'
check "declined payment still creates the order resource" 201 "$CODE"
check "declined order is CANCELLED" CANCELLED "$(field .status)"
check "declined order failure reason" CARD_DECLINED "$(field .failureReason)"
check "stock is restored after the declined payment" 3 "$(stock_field "$PRODUCT_ID" .availableQuantity)"

# --- payment timeout reconciliation ------------------------------------------------------------------
checkout "$TOKEN" '{"simulatedPaymentOutcome":"TIMEOUT"}'
check "payment timeout returns 503" 503 "$CODE"
req GET "$ORDER/api/orders/customer/$CUSTOMER_ID?size=50" "$TOKEN"
PENDING_ID=$(field '.content[] | select(.status=="PAYMENT_PENDING") | .id' | head -1)
check "timed-out order waits in PAYMENT_PENDING" true "$(truthy "$PENDING_ID")"
req POST "$ORDER/api/orders/$PENDING_ID/recover" "$ADMIN_TOKEN"
check "recover returns 200" 200 "$CODE"
check "recovered order is CONFIRMED" CONFIRMED "$(field .status)"
check "stock after the recovered order" 2 "$(stock_field "$PRODUCT_ID" .availableQuantity)"

# --- cancellation ------------------------------------------------------------------------------------
req POST "$ORDER/api/orders/$ORDER1/cancel" "$TOKEN"
check "cancelling a CONFIRMED order returns 200" 200 "$CODE"
check "cancelled order status" CANCELLED "$(field .status)"
check "stock restored by the cancellation" 4 "$(stock_field "$PRODUCT_ID" .availableQuantity)"
req POST "$ORDER/api/orders/$ORDER1/cancel" "$TOKEN"
check "cancelling twice returns 409" 409 "$CODE"

# --- access control ----------------------------------------------------------------------------------
req GET "$ORDER/api/orders/$ORDER1" ""
check "orders require a token" 401 "$CODE"
req GET "$ORDER/api/orders/customer/$CUSTOMER_ID" "$ADMIN_TOKEN"
check "admin can read a customer's orders" 200 "$CODE"

echo "RESULT pass=$PASS fail=$FAIL"
[ "$FAIL" -eq 0 ]
