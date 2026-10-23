#!/usr/bin/env bash
# Ten customers try to buy the last unit in parallel: exactly one order may be CONFIRMED.
set -u
cd "$(dirname "$0")" && source ./lib.sh
CUSTOMERS=${CUSTOMERS:-10}

admin_login
new_product 2500.00 1

TOKENS=()
for i in $(seq 1 "$CUSTOMERS"); do
  new_customer
  add_to_cart "$NC_TOKEN" "$PRODUCT_ID" 1
  TOKENS+=("$NC_TOKEN")
done

RESULTS=$(mktemp -d)
for i in "${!TOKENS[@]}"; do
  (
    curl -s -o /dev/null -w '%{http_code}' -X POST "$ORDER/api/orders" \
      -H 'Content-Type: application/json' -H "Authorization: Bearer ${TOKENS[$i]}" \
      -H "Idempotency-Key: $(uuid)" -H "X-Correlation-Id: concurrency-$i" -d '{}' >"$RESULTS/$i"
  ) &
done
wait

CODES=$(for f in "$RESULTS"/*; do cat "$f"; echo; done)
CREATED=$(echo "$CODES" | grep -c '^201$')
CONFLICTS=$(echo "$CODES" | grep -c '^409$')
check "exactly one purchase of the last unit succeeds" 1 "$CREATED"
check "all other customers receive 409" $((CUSTOMERS - 1)) "$CONFLICTS"
check "stock ends with 0 available" 0 "$(stock_field "$PRODUCT_ID" .availableQuantity)"
check "stock ends with 0 reserved" 0 "$(stock_field "$PRODUCT_ID" .reservedQuantity)"

echo "RESULT pass=$PASS fail=$FAIL"
[ "$FAIL" -eq 0 ]
