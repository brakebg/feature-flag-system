#!/usr/bin/env bash
# Spec 11.5: the screenshot stack is seeded through the Admin API with the sample data of the
# designs, once and in a fixed order (so the audit log screen is the same on every run).
# Usage: scripts/lib/seed-shots.sh <api-base-url>
set -euo pipefail
API=$1
token=$(curl -fsS -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)
auth=(-H "Authorization: Bearer $token" -H 'Content-Type: application/json')

group_id() { curl -fsS "$API/api/v1/admin/groups" "${auth[@]}" | jq -r --arg k "$1" '.[] | select(.key==$k) | .id'; }
create_group() { # key name description
  curl -fsS -o /dev/null -X POST "$API/api/v1/admin/groups" "${auth[@]}" \
    -d "$(jq -n --arg k "$1" --arg n "$2" --arg d "$3" '{key:$k,name:$n,description:$d}')"
}
flag() { # groupId key enabled description
  local detail existing
  detail=$(curl -fsS "$API/api/v1/admin/groups/$1" "${auth[@]}")
  existing=$(jq -r --arg k "$2" '.flags[] | select(.key==$k) | .id' <<<"$detail")
  if [ -z "$existing" ]; then
    curl -fsS -o /dev/null -X POST "$API/api/v1/admin/groups/$1/flags" "${auth[@]}" \
      -d "$(jq -n --arg k "$2" --argjson e "$3" --arg d "$4" '{key:$k,enabled:$e,description:$d}')"
  else
    local version
    version=$(jq -r --arg k "$2" '.flags[] | select(.key==$k) | .version' <<<"$detail")
    curl -fsS -o /dev/null -X PATCH "$API/api/v1/admin/flags/$existing" "${auth[@]}" \
      -d "$(jq -n --argjson e "$3" --arg d "$4" --argjson v "$version" '{enabled:$e,description:$d,version:$v}')"
  fi
}

# orders exists from the dev seed (V2): set its description, then its flags.
orders=$(group_id orders)
curl -fsS -o /dev/null -X PATCH "$API/api/v1/admin/groups/$orders" "${auth[@]}" \
  -d '{"description":"Checkout and order lifecycle","version":0}'
flag "$orders" new-checkout true 'New one-page checkout flow'
flag "$orders" split-payments false 'Pay one order with two cards'
flag "$orders" order-tracking-v2 true 'Live courier tracking map'
flag "$orders" guest-reorder false 'Reorder without signing in'

create_group payments Payments 'Payment providers and wallets'
g=$(group_id payments)
flag "$g" apple-pay true 'Apple Pay at checkout'
flag "$g" three-ds-v2 true '3-D Secure 2 challenge flow'
flag "$g" crypto-checkout false 'Pay with stablecoins'

create_group search Search 'Catalog search and ranking'
g=$(group_id search)
flag "$g" semantic-search false 'Vector-based product search'
flag "$g" typo-tolerance true 'Fuzzy matching for misspellings'

create_group notifications Notifications 'Email, SMS and push'
g=$(group_id notifications)
flag "$g" push-digest false 'Daily push summary'
flag "$g" sms-shipping-updates true 'SMS when an order ships'
flag "$g" quiet-hours true 'No pushes 22:00 to 07:00'
echo "seeded screenshot stack at $API"
