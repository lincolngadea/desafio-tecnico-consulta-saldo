## Why

O serviço já sabe gravar o snapshot de saldo mais recente de uma conta (`BalanceRepository`), mas nada o alimenta: não há consumer do tópico `transacoes-financeiras-processadas`, nem caso de uso que transforme o evento em snapshot. Sem essa ingestão o serviço não cumpre o primeiro item do enunciado (*Ingestão (input via Kafka)*), e os critérios de **resiliência** e de **cenários adversos** só podem ser demonstrados quando o fluxo de entrada existe.

## What Changes

- Novo **caso de uso** `ProcessTransactionUseCase` (port de entrada) e seu serviço de aplicação, que convertem a transação processada em `BalanceSnapshot` e chamam `BalanceRepository.saveIfNewer`. Decide e documenta se evento `DECLINED` atualiza o snapshot.
- Novo **adapter de entrada Kafka** para `transacoes-financeiras-processadas`, sobre o Spring Kafka que o kit já traz:
  - commit **manual** do offset só depois da persistência (at-least-once);
  - erro **permanente** (JSON inválido, campo inválido, falha permanente do armazenamento) vai para a **DLT** com headers do motivo, e o offset é confirmado;
  - erro **transitório** é tentado de novo com **backoff exponencial e jitter**, com limite configurável por variável de ambiente;
  - **graceful shutdown** e tratamento de **rebalance** sem perder nem reprocessar além do at-least-once.
- Novo **circuit breaker** em volta do `BalanceRepository` (padrão Decorator, Resilience4j). Com o circuito aberto, o consumer **pausa as partições** e as retoma por tempo, com o primeiro registro reentregue servindo de sonda do circuito, em vez de descartar ou mandar para a DLT.
- Nova exceção de porta `StorageUnavailableException`, subtipo de `BalanceStorageException`, para "circuito aberto", sem deixar o tipo do Resilience4j atravessar o port.
- Tópico `transacoes-financeiras-processadas` e sua DLT com **6 partições**, criados pelo comando do kit (`make kafka-topic-create`); a escolha é justificada no `design.md`.
- `design.md` registra por que **at-least-once** e não o exactly-once do Kafka, por que a **DLT só recebe erro permanente** e por que **pausar** em vez de descartar quando a dependência cai.
- Dependência nova: `io.github.resilience4j:resilience4j-circuitbreaker` (Art. 10, evidência no `design.md`).

Fora do escopo: endpoint REST `GET /balances/{accountId}`, métricas e logs estruturados (production readiness), reprocessamento automático da DLT. Ficam documentados no `design.md`.

## Capabilities

### New Capabilities
- `transaction-processing`: o caso de uso que transforma uma transação processada em snapshot e o grava condicionalmente, incluindo a decisão sobre eventos `DECLINED`.
- `transaction-ingestion`: o comportamento do consumer Kafka: commit manual, DLT para erro permanente, retry com backoff e jitter para erro transitório, pausa e retomada das partições, shutdown e rebalance.
- `storage-circuit-breaker`: o circuit breaker em volta do repositório de saldo e o contrato de falha "indisponível" que ele expõe.

### Modified Capabilities
<!-- Nenhuma: os requisitos de `balance-snapshot-storage` não mudam. -->

## Impact

- **Código:** contexto `balance` — `domain/model` (evento processado), `port/input` (`ProcessTransactionUseCase`), `port/output` (`StorageUnavailableException`), `application`, `adapter/input/kafka` (listener, mapeamento do evento, configuração do container e do error handler) e `adapter/output` (decorator do circuit breaker). `HexagonalArchitectureTest` já cobre o contexto.
- **Dependências:** `io.github.resilience4j:resilience4j-circuitbreaker` em `implementation`, com versão fixa porque o BOM do Boot não a gerencia.
- **Configuração:** variáveis de ambiente novas em `application.yaml` e no serviço `app` do `docker-compose.yml` (tópico, DLT, grupo, retry, backoff, jitter, circuit breaker).
- **Infra local:** dois tópicos criados por `make kafka-topic-create`; o Makefile e o seed do kit não mudam.
- **Specs:** três capabilities novas.
- **Contexto do projeto:** `openspec/project.md` e `context:` do `config.yaml` registram a dependência, as variáveis, os tópicos, a decisão sobre `DECLINED` e a lacuna resolvida (consumer).
