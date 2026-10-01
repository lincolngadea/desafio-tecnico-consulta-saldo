## Why

O pedido desta change exige que o teste de **concorrência** do repositório de saldo use **coroutines em paralelo** contra o DynamoDB Local: N escritas simultâneas para a mesma conta, em ordem aleatória, com o saldo final igual ao de maior versão. Esse é o teste que sustenta o critério de avaliação *tratamento de concorrência* do enunciado, e ele precisa mostrar corrida de verdade entre escritores.

O adapter, os ports, a modelagem, a classificação de falhas e os timeouts já existem e estão especificados em `balance-snapshot-storage` (change `add-balance-repository` arquivada em 2026-09-30). Hoje o teste de concorrência usa `Executors` e `invokeAll`, sem coroutines.

## What Changes

- O teste de integração de concorrência passa a usar **coroutines em paralelo**: N `async` liberados ao mesmo tempo por um portão, despachados em várias threads, contra o DynamoDB Local.
- O cenário "Gravações concorrentes da mesma conta" da spec passa a descrever essa garantia de forma explícita: N escritas simultâneas, em ordem aleatória, e saldo final igual ao de maior `SnapshotVersion`.
- `kotlinx-coroutines-core` é declarada no `build.gradle.kts`, só para teste, porque hoje chega apenas por transitividade e fora do classpath de compilação.
- O `design.md` registra as decisões, as alternativas descartadas para a concorrência (lock otimista por `version`, `TransactWriteItems`, tabela de histórico por evento) e os limites (hot partition e relógio do autorizador).

Fora do escopo: qualquer mudança em código de produção, nos ports, na modelagem (PK `accountId`, sem sort key e sem GSI), na classificação de falhas e nos timeouts. Consumer Kafka, endpoint REST e retry/backoff ficam para as changes seguintes.

## Capabilities

### New Capabilities
<!-- Nenhuma. -->

### Modified Capabilities
- `balance-snapshot-storage`: o requisito "Gravação condicional do snapshot" passa a exigir, no cenário de concorrência, N escritas simultâneas em ordem aleatória, executadas em paralelo de fato, com o saldo final igual ao de maior versão.

## Impact

- **Código:** só o teste de integração `DynamoDbBalanceIntegrationTest`, inclusive o cabeçalho de comentário do arquivo (Art. 6), cujas linhas mudam.
- **Dependências:** `org.jetbrains.kotlinx:kotlinx-coroutines-core`, apenas em `testImplementation` (Art. 10, evidência no `design.md`).
- **Specs:** delta `MODIFIED` em `balance-snapshot-storage`.
- **Contexto do projeto:** `openspec/project.md` e `context:` do `config.yaml` registram a dependência e o motivo.
