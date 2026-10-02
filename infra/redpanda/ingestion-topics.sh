#!/bin/bash
# L14 PARTITIONS: 6 partições é a decisão da `add-transaction-ingestion` (design D7): dá escala horizontal do
#     consumo, e o valor é sobrescrevível por ambiente.
# L15-L18 TOPICS: os nomes vêm das mesmas variáveis que a aplicação lê (`TRANSACTIONS_TOPIC` e
#     `TRANSACTIONS_DLT_TOPIC`), com os mesmos padrões; este script é a fonte única da criação dos tópicos, usada
#     pelo seed e por `make kafka-topics-ingestion` (add-observability design D10).
# L26-L33 for topic: `describe` antes de `create` deixa o script idempotente, porque a criação automática de tópicos
#     está desligada e o seed roda a cada `make up`.
#
# Enunciado: Como começar → Criando o tópico Kafka
set -euo pipefail

BROKERS="${REDPANDA_BROKERS:-redpanda:9092}"
PARTITIONS="${INGESTION_PARTITIONS:-6}"
TOPICS=(
  "${TRANSACTIONS_TOPIC:-transacoes-financeiras-processadas}"
  "${TRANSACTIONS_DLT_TOPIC:-transacoes-financeiras-processadas.DLT}"
)

echo "Waiting for Redpanda broker at ${BROKERS}..."
until rpk cluster info --brokers "${BROKERS}" >/dev/null 2>&1; do
  echo "  not ready yet, retrying in 2s..."
  sleep 2
done

for topic in "${TOPICS[@]}"; do
  if rpk topic describe "${topic}" --brokers "${BROKERS}" >/dev/null 2>&1; then
    echo "Topic '${topic}' already exists, skipping creation."
  else
    echo "Creating topic '${topic}' with ${PARTITIONS} partitions..."
    rpk topic create "${topic}" --brokers "${BROKERS}" --partitions "${PARTITIONS}" --replicas 1
  fi
done
