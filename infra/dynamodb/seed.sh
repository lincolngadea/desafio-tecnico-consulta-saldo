#!/bin/bash
set -euo pipefail

ENDPOINT_URL="${DYNAMODB_ENDPOINT_URL:-http://dynamodb:8000}"
TABLE_NAME="${GREETING_TABLE_NAME:-GreetingMessages}"
BALANCE_TABLE_NAME="${BALANCE_TABLE_NAME:-AccountBalances}"
REGION="${AWS_DEFAULT_REGION:-us-east-1}"
SEED_FILE="/dynamodb-seed/greeting-messages.json"

echo "Waiting for DynamoDB Local at ${ENDPOINT_URL}..."
until aws dynamodb list-tables --endpoint-url "${ENDPOINT_URL}" --region "${REGION}" >/dev/null 2>&1; do
  echo "  not ready yet, retrying in 2s..."
  sleep 2
done
echo "DynamoDB Local is ready."

# Creates a single-key (HASH, string) on-demand table unless it already exists.
create_table() {
  local table_name="$1"
  local key_attribute="$2"

  if aws dynamodb describe-table --table-name "${table_name}" --endpoint-url "${ENDPOINT_URL}" --region "${REGION}" >/dev/null 2>&1; then
    echo "Table '${table_name}' already exists, skipping creation."
    return
  fi

  echo "Creating table '${table_name}'..."
  aws dynamodb create-table \
    --table-name "${table_name}" \
    --attribute-definitions AttributeName="${key_attribute}",AttributeType=S \
    --key-schema AttributeName="${key_attribute}",KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --endpoint-url "${ENDPOINT_URL}" \
    --region "${REGION}" >/dev/null
  aws dynamodb wait table-exists \
    --table-name "${table_name}" \
    --endpoint-url "${ENDPOINT_URL}" \
    --region "${REGION}"
  echo "Table '${table_name}' created."
}

create_table "${TABLE_NAME}" id
# One item per account: the only access pattern is get/put by account id (no sort key, no GSI).
create_table "${BALANCE_TABLE_NAME}" accountId

echo "Seeding greeting messages from ${SEED_FILE}..."
aws dynamodb batch-write-item \
  --request-items "file://${SEED_FILE}" \
  --endpoint-url "${ENDPOINT_URL}" \
  --region "${REGION}" >/dev/null

COUNT=$(aws dynamodb scan \
  --table-name "${TABLE_NAME}" \
  --endpoint-url "${ENDPOINT_URL}" \
  --region "${REGION}" \
  --select COUNT \
  --query 'Count' \
  --output text)

echo "Seed complete. '${TABLE_NAME}' now has ${COUNT} item(s)."
