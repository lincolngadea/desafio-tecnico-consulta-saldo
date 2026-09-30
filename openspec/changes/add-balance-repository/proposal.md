## Why

A ingestão (Kafka) e a consulta (`GET /balances/{accountId}`) dependem do mesmo alicerce: guardar no DynamoDB o **snapshot mais recente de cada conta** e lê-lo por chave, de forma correta mesmo com mensagens duplicadas, fora de ordem e consumidores concorrentes. Esse alicerce responde diretamente a dois critérios de avaliação do enunciado, *modelagem de dados no DynamoDB* e *tratamento de concorrência*, e por isso vem antes do consumer e do endpoint.

## What Changes

- Novo bounded context `balance` com o **modelo mínimo de domínio** que o port de persistência precisa:
  - value objects validados na construção: identificadores da conta, do titular e da transação, dinheiro (`BigDecimal` + moeda ISO 4217) e instante do evento em µs;
  - `SnapshotVersion` (timestamp do evento + id da transação), a **definição autoritativa** de "mais recente";
  - o snapshot de saldo da conta;
  - o resultado tipado de uma gravação: `Applied | StaleIgnored`.
- Ports de saída: gravar um snapshot só se ele for mais novo que o armazenado, e buscar o snapshot de uma conta (ausência modelada no tipo).
- Dois adapters DynamoDB, um por port, como no kit (`DynamoDbBalanceWriter` grava e `DynamoDbBalanceProvider` lê):
  - `PutItem` com `ConditionExpression`, que traduz a ordem do `SnapshotVersion` num compare-and-set atômico. `ConditionalCheckFailed` vira `StaleIgnored`, não erro;
  - `GetItem` com `ConsistentRead=true`;
  - erros do SDK classificados em **transitórios** (throttling, 5xx, timeout, falha de rede) e **permanentes**, expostos por exceções do núcleo, sem vazar tipos do SDK pelo port.
- Timeouts explícitos no `DynamoDbClient` do starter kit (conexão, socket, tentativa e chamada total), configuráveis por variável de ambiente.
- Nova tabela `AccountBalances` (PK `accountId`, sem sort key e sem GSI), criada pelo seed do DynamoDB Local e configurável por variável de ambiente.
- Testes unitários do domínio e do adapter, testes de integração contra o DynamoDB Local do kit, e o `HexagonalArchitectureTest` estendido ao contexto `balance`.

Fora do escopo: consumer Kafka, caso de uso de ingestão, endpoint REST, retry/backoff/circuit breaker de aplicação, métricas e logs estruturados. Cada um vem na sua change.

## Capabilities

### New Capabilities
- `balance-snapshot-storage`: persistência e leitura do snapshot de saldo por conta, com gravação condicional pela ordem do evento (idempotente e resistente a fora de ordem e concorrência), resultado tipado da gravação, leitura fortemente consistente, classificação de falhas da dependência e timeouts explícitos.

### Modified Capabilities
<!-- Nenhuma: ainda não há specs em openspec/specs/. -->

## Impact

- **Código novo:** `br.com.itau.challenge.balance` (`domain/model`, `domain/exception`, `port/output`, `adapter/output/dynamodb`) e os testes em `src/test` e `src/integrationTest`.
- **Código alterado:**
  - `hello/adapter/output/dynamodb/DynamoDbConfig.kt` ganha timeouts explícitos. Ele é o único `DynamoDbClient` da aplicação, então o cliente do kit é reutilizado;
  - `HexagonalArchitectureTest` passa a cobrir `balance`.
- **Configuração e infra:** `application.yaml` (nome da tabela e timeouts), `docker-compose.yml` (variáveis de ambiente do `app` e do `dynamodb-seed`) e `infra/dynamodb/seed.sh` (cria a tabela `AccountBalances`).
- **Dependências:** `software.amazon.awssdk:apache5-client` declarado explicitamente (o BOM 2.46.7 só fixa a versão; o módulo já vem em runtime com o `dynamodb`), para compilar contra o builder que configura os timeouts de conexão e de socket.
- **Contexto do projeto:** `openspec/project.md` e `context:` do `config.yaml` (nova tabela, novas variáveis de ambiente, decisão sobre `updated_at` e desempate).
