## Why

O serviço já ingere eventos e responde a consulta de saldo, mas é uma caixa-preta em produção: os logs são texto livre, sem correlação entre o evento do Kafka e o que o serviço fez com ele (e o `traceId` da consulta HTTP não existe no consumer); não há uma única métrica; não há probes para um orquestrador decidir quando reiniciar ou parar de mandar tráfego; e a imagem Docker roda como root, com a JVM sem limites de memória para container e com tags flutuantes. O enunciado avalia **production readiness** (*logging, métricas, conteinerização*), e as changes anteriores deixaram esses pontos explicitamente para esta (Non-Goals dos `design.md` de `add-transaction-ingestion` e `add-balance-query-api`).

## What Changes

- **Logs estruturados em JSON** (formato nativo do Spring Boot, sem biblioteca nova), com `traceId` em todo log, propagado do HTTP (`traceparent`) e do Kafka (header `traceparent` do registro, que a DLT preserva). O `traceId` é **o** identificador de correlação: não há um segundo `correlationId`. O filtro próprio `TraceIdFilter` da change anterior dá lugar ao Micrometer Tracing, a solução pronta que o `design.md` daquela change já previa.
- **Política de dados sensíveis:** `owner` e payload nunca vão para log; o `accountId` sai mascarado; os tipos de domínio que carregam esses dados passam a ter `toString` seguro, para um `"$snapshot"` descuidado não vazar nada. Um teste prova que um evento malformado com um `owner` sentinela não aparece em log nenhum.
- **Métricas** (Micrometer, expostas em `/actuator/prometheus`):
  - eventos por resultado (`applied`, `stale_ignored`, `duplicate`, `dlq`);
  - latência de processamento por registro (observação do listener do Spring Kafka);
  - consumer lag (métrica do cliente Kafka);
  - estado do circuit breaker de escrita e de leitura (Resilience4j);
  - requisições HTTP por status (`http.server.requests`).
- **Resultado `DuplicateIgnored`:** o pedido separa `duplicate` de `stale_ignored`, mas hoje o domínio os une em `StaleIgnored`. O writer passa a pedir o item que impediu a gravação (`ReturnValuesOnConditionCheckFailure`) e o domínio decide se a versão é a mesma (duplicata) ou mais antiga. **BREAKING** para o contrato do port `BalanceRepository`, interno ao serviço.
- **Health checks separados:** `liveness` (processo vivo) e `readiness` (pronto para tráfego), numa porta de gerenciamento própria. O `readiness` **não** depende do DynamoDB, justificado no `design.md`; ele passa a recusar tráfego assim que o encerramento começa.
- **Contêiner:**
  - `Dockerfile` multi-stage com tags **fixas**, usuário não root com UID numérico e flags de memória da JVM para container, sobrescrevíveis por variável de ambiente;
  - encerramento por `SIGTERM` com a JVM como PID 1, que aciona o graceful shutdown que já existe;
  - credenciais AWS passam a vir da cadeia padrão do SDK (variáveis de ambiente), e não do código (12-Factor).
- **Compose:** o serviço `app` já existe, mas sobe sem esperar os seeds e sem os tópicos de transações, que só `make kafka-topics-ingestion` criava. Passa a esperar os seeds terminarem, e os seeds criam os tópicos. `make up` sobe tudo, sem passos manuais.
- Dependências novas (Art. 10, com evidência no `design.md`): `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel` com o módulo de auto-configuração do Boot, e `resilience4j-micrometer`.

Fora do escopo, documentado no `design.md`: Prometheus, Grafana e coletor OTLP no compose, exportação de traces, alertas, dashboards, autenticação do endpoint de gerenciamento, SLOs e orçamento de erro.

## Capabilities

### New Capabilities
- `structured-logging`: logs em JSON, `traceId` propagado do HTTP e do Kafka, e a política de dados sensíveis (o que nunca é logado e o que é mascarado).
- `service-metrics`: as métricas de resultado por evento, latência, lag, circuit breaker e HTTP, e o endpoint que as expõe.
- `health-probes`: `liveness` e `readiness` separados, o que o `readiness` inclui e o que ele deliberadamente não inclui.
- `container-runtime`: a imagem (tags fixas, não root, JVM em container), o encerramento por `SIGTERM`, a configuração só por ambiente e o serviço `app` no compose subindo com um comando.

### Modified Capabilities
- `balance-snapshot-storage`: "Gravação condicional do snapshot" ganha o resultado `DuplicateIgnored` para o mesmo evento, separado do `StaleIgnored` para o evento mais antigo.
- `transaction-processing`: "Transação processada vira snapshot de saldo" devolve também `DuplicateIgnored`.
- `transaction-ingestion`: "Offset confirmado manualmente só depois da persistência" e "Rebalance sem perda" tratam `DuplicateIgnored` como `StaleIgnored` para o commit.
- `storage-circuit-breaker`: "Falhas transitórias do armazenamento abrem o circuito" lista `DuplicateIgnored` entre os resultados que não contam como falha.

## Impact

- **Pré-condição:** esta change se apoia no código da `add-balance-query-api` (commitada, ainda não arquivada): ela substitui o `TraceIdFilter` e altera o `ApiExceptionHandler`. Os requisitos de `balance-query-api` **não** mudam (o comportamento do `traceId` é o mesmo), então não há delta sobre aquela spec; o arquivamento daquela change deve vir antes do desta.
- **Código:**
  - `balance` — `domain/model` (`SnapshotSaveResult`, `toString` seguro de `AccountId` e `OwnerId`), `adapter/output/dynamodb` (writer), `adapter/output/resilience` (registry do circuit breaker e métricas), `adapter/input/kafka` (listener, recoverer, métrica de resultado, container com observação) e `adapter/input/web` (`ApiExceptionHandler` lê o `traceId` do tracer; `TraceIdFilter` removido);
  - `hello` — `DynamoDbConfig` (credenciais).
- **Dependências:** quatro em `implementation`, três delas gerenciadas pelo BOM do Boot.
- **Configuração:** `management.*`, `logging.structured.*` e variáveis de ambiente novas em `application.yaml`, no compose e no `README.md`; `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` passam a ser exigidas do ambiente (as tarefas `Test` e `bootRun` do Gradle as definem).
- **Infra:** `Dockerfile`, `docker-compose.yml`, `Makefile` e `infra/redpanda/` (novo script de criação dos tópicos de ingestão, fonte única usada pelo seed e pelo `make kafka-topics-ingestion`).
- **Specs:** quatro capabilities novas e quatro requisitos modificados.
- **Validação:** o ambiente de desenvolvimento desta sessão não tem Docker, então parte da validação (build da imagem, `SIGTERM` real, `make up`, testes de integração) fica em tarefas marcadas como dependentes de Docker, para executar no ambiente com o Docker.
- **Contexto do projeto:** `openspec/project.md` e `context:` do `config.yaml` registram as dependências, as variáveis, o endpoint de gerenciamento, o `DuplicateIgnored`, a política de dados sensíveis e as lacunas resolvidas (seeds, usuário root, tags flutuantes).
