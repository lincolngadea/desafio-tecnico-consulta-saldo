## Why

O serviço já ingere eventos e grava o snapshot de saldo mais recente de cada conta, e o `BalanceProvider` já lê esse snapshot por chave, mas nada o expõe: não há caso de uso de consulta nem endpoint. Sem o `GET /balances/{accountId}` o serviço não cumpre o segundo item do enunciado (*Exposição (API REST)*), e os critérios de **resiliência** e de **tratamento de cenários adversos** não se demonstram do lado da leitura, que é a que "será consumida por outros sistemas em produção".

## What Changes

- Novo **caso de uso** `GetBalanceUseCase` (port de entrada) e seu serviço de aplicação, que devolvem o snapshot mais recente de uma conta ou `null` quando a conta não tem snapshot (ausência esperada, não erro).
- Novo **adapter de entrada HTTP** `GET /balances/{accountId}` com o contrato de request e de resposta **exatos** do enunciado (`id`, `owner`, `balance.amount`, `balance.currency`, `updated_at`).
- **Modelo de erro** em `application/problem+json` (RFC 9457) com `traceId`:
  - `400` para `accountId` que não é UUID;
  - `404` para conta sem snapshot;
  - `503` com `Retry-After` quando o DynamoDB está indisponível (falha transitória ou circuito aberto);
  - `500` para o inesperado, sem vazar detalhe interno.
- **Cliente DynamoDB de leitura** próprio, com timeouts curtos e **no máximo 1 retry** (2 tentativas), de modo que o pior caso da leitura caiba num orçamento de latência explícito.
- **Circuit breaker de leitura** (Decorator do `BalanceProvider`), com a **mesma classificação de erros** do repositório de escrita (só `TransientStorageException` conta como falha) e instância própria, para a API não pausar a ingestão nem o contrário.
- **Sem cache de saldo**, justificado no `design.md`, e `Cache-Control: no-store` nas respostas da consulta.
- **Documentação OpenAPI/Swagger gerada a partir do código** (springdoc), com os cinco status e o cabeçalho `Retry-After` documentados.
- **Testes:** contrato do adapter HTTP (MockMvc) e teste de integração ponta a ponta (Kafka → ingestão → DynamoDB Local → HTTP real).
- Decisões do enunciado que a change fecha: status HTTP para conta inexistente (404) e para `accountId` inválido (400), formato de `updated_at`, e a origem do `traceId`.
- Dependência nova: `org.springdoc:springdoc-openapi-starter-webmvc-ui` (Art. 10, evidência no `design.md`).

Fora do escopo, documentado no `design.md`: métricas e logs estruturados (production readiness), tracing distribuído completo (OpenTelemetry/Micrometer Tracing), cache de leitura, autenticação e autorização, paginação ou consulta de histórico.

## Capabilities

### New Capabilities
- `balance-query`: o caso de uso que consulta o saldo mais recente de uma conta, com a ausência de snapshot modelada no tipo de retorno.
- `balance-query-api`: o contrato HTTP de `GET /balances/{accountId}`: resposta de sucesso, modelo de erro `problem+json` com `traceId`, política de no-cache e documentação OpenAPI gerada do código.
- `balance-read-resilience`: o cliente de leitura com timeouts curtos e no máximo 1 retry, o orçamento de latência da leitura e o circuit breaker de leitura que compartilha a classificação de erros do repositório de escrita.

### Modified Capabilities
<!-- Nenhuma: balance-snapshot-storage e storage-circuit-breaker não mudam de requisito. A leitura já devolvia `null` para conta sem snapshot e o circuito de escrita segue como está. -->

## Impact

- **Código:** contexto `balance` — `port/input` (`GetBalanceUseCase`), `application` (serviço da consulta), `adapter/input/web` (controller, DTO de resposta, modelo de erro, filtro de `traceId`), `adapter/output/resilience` (decorator e circuit breaker de leitura) e `adapter/output/dynamodb` (provider do cliente de leitura). `DynamoDbConfig` e `DynamoDbProperties` (pacote `hello`, onde o kit os deixou) ganham o cliente de leitura. O `HexagonalArchitectureTest` já cobre o contexto.
- **Dependências:** `org.springdoc:springdoc-openapi-starter-webmvc-ui`, com versão fixa porque o BOM do Boot não a gerencia.
- **Configuração:** variáveis de ambiente novas em `application.yaml` e no serviço `app` do `docker-compose.yml` (timeouts e tentativas da leitura, `Retry-After`). Também mudam `README.md` (endpoint, Swagger UI e variáveis).
- **Comportamento observável fora da consulta:** o tratamento global de erros passa a devolver `problem+json` também nos erros do `/hello`, e a lacuna "sem `@RestControllerAdvice`" do template é resolvida.
- **Specs:** três capabilities novas.
- **Contexto do projeto:** `openspec/project.md` e `context:` do `config.yaml` registram a dependência, as variáveis, o segundo cliente DynamoDB (exceção explícita à regra do cliente único), as decisões do enunciado e a lacuna resolvida.
