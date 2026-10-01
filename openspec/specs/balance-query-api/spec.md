# balance-query-api Specification

## Purpose
Contrato HTTP da consulta de saldo, incluindo respostas, rastreabilidade, controle de cache e documentação OpenAPI.

## Requirements

### Requirement: Resposta de sucesso segue o contrato do enunciado
`GET /balances/{accountId}` MUST responder `200` com `Content-Type: application/json` e um corpo com exatamente os campos `id` (UUID da conta), `owner` (UUID do titular), `balance.amount` (número), `balance.currency` (código ISO 4217) e `updated_at` (string ISO 8601), com esses nomes. O corpo MUST NOT conter outros campos. `balance.amount` MUST ser um número JSON com a escala das unidades menores da moeda (`183.10`, nunca a string `"183.10"` nem `183.1`), sem passar por ponto flutuante. `updated_at` MUST ser o `transaction.timestamp` do evento que gerou o snapshot, em milissegundos e com o offset `-03:00` (`America/Sao_Paulo`), com sempre três dígitos de fração, como no exemplo do enunciado.

#### Scenario: Conta existente devolve os cinco campos do contrato
- **WHEN** o cliente chama `GET /balances/5b19c8b6-0cc4-4c72-a989-0c2ee15fa975` e a conta tem snapshot
- **THEN** a resposta é `200`, `Content-Type: application/json`, e o corpo tem `id`, `owner`, `balance.amount`, `balance.currency` e `updated_at` com os valores do snapshot e nenhum outro campo

#### Scenario: O saldo é um número com a escala da moeda
- **WHEN** o snapshot da conta tem saldo `183.10 BRL`
- **THEN** o corpo contém `"amount":183.10` como número JSON, sem aspas

#### Scenario: Saldo negativo é devolvido como está
- **WHEN** o snapshot da conta tem saldo `-25.00 BRL`
- **THEN** o corpo contém `"amount":-25.00`

#### Scenario: O instante é devolvido em milissegundos com offset de Brasília
- **WHEN** o snapshot foi gerado por um evento com `timestamp` `1751641364589998` (µs)
- **THEN** `updated_at` é `2025-07-04T12:02:44.589-03:00`

#### Scenario: A fração de segundo tem sempre três dígitos
- **WHEN** o `timestamp` do evento cai num segundo exato (fração zero)
- **THEN** `updated_at` termina em `.000-03:00`

#### Scenario: Identificador em maiúsculas devolve a mesma conta
- **WHEN** o cliente chama a rota com o UUID da conta escrito em maiúsculas
- **THEN** a resposta é `200` e `id` é o UUID em minúsculas

### Requirement: accountId inválido responde 400
O adapter MUST aceitar como `accountId` só o UUID na forma canônica de 36 caracteres (8-4-4-4-12 dígitos hexadecimais, maiúsculos ou minúsculos). Qualquer outro valor MUST responder `400` com `application/problem+json`, sem chamar o caso de uso. A decisão não depende de `UUID.fromString`, que aceita formas não canônicas.

#### Scenario: Texto que não é UUID
- **WHEN** o cliente chama `GET /balances/abc`
- **THEN** a resposta é `400` com `application/problem+json` e o caso de uso não é chamado

#### Scenario: UUID em forma não canônica
- **WHEN** o cliente chama `GET /balances/1-1-1-1-1`
- **THEN** a resposta é `400` com `application/problem+json` e o caso de uso não é chamado

#### Scenario: UUID sem hifens
- **WHEN** o cliente chama a rota com 32 dígitos hexadecimais sem hifens
- **THEN** a resposta é `400`

### Requirement: Conta inexistente responde 404
Quando a conta não tem snapshot, o adapter MUST responder `404` com `application/problem+json`. A resposta MUST NOT ser `200` com saldo zerado nem `500`.

#### Scenario: Conta sem snapshot
- **WHEN** o cliente chama a rota com um UUID válido de uma conta que não tem snapshot
- **THEN** a resposta é `404` com `application/problem+json` e `traceId`

### Requirement: Dependência indisponível responde 503 com Retry-After
Quando o armazenamento falha de forma transitória (`TransientStorageException`) ou está indisponível (`StorageUnavailableException`, circuito aberto), o adapter MUST responder `503` com `application/problem+json` e o cabeçalho `Retry-After` em segundos inteiros, cujo valor é configurável por variável de ambiente.

#### Scenario: Falha transitória do armazenamento
- **WHEN** o caso de uso lança `TransientStorageException`
- **THEN** a resposta é `503` com `application/problem+json` e `Retry-After` com o valor configurado

#### Scenario: Circuito aberto
- **WHEN** o caso de uso lança `StorageUnavailableException`
- **THEN** a resposta é `503` com `application/problem+json` e `Retry-After` com o valor configurado

#### Scenario: Valor padrão e valor sobrescrito do Retry-After
- **WHEN** a aplicação sobe sem configuração, e depois com `BALANCE_API_RETRY_AFTER` definida
- **THEN** o `Retry-After` é `5` no primeiro caso e o valor configurado, em segundos, no segundo

### Requirement: Falha inesperada responde 500 sem vazar detalhe interno
Toda falha não tratada acima (incluindo `PermanentStorageException` e snapshot gravado malformado) MUST responder `500` com `application/problem+json` e um `detail` genérico. O corpo MUST NOT conter nome de tabela, mensagem de exceção, nome de classe nem stack trace, e a causa MUST ser registrada no log com o `traceId`.

#### Scenario: Falha permanente do armazenamento
- **WHEN** o caso de uso lança `PermanentStorageException` com uma mensagem que cita a tabela
- **THEN** a resposta é `500` com `application/problem+json` e a mensagem original não aparece no corpo

#### Scenario: Exceção não prevista
- **WHEN** o caso de uso lança uma `RuntimeException` qualquer
- **THEN** a resposta é `500` com `application/problem+json` e `traceId`

#### Scenario: A causa vai para o log com o traceId
- **WHEN** uma resposta `500` é produzida
- **THEN** o log de erro contém a causa e o mesmo `traceId` do corpo da resposta

### Requirement: Todo erro é problem+json com traceId
Toda resposta de erro da aplicação MUST usar `application/problem+json` (RFC 9457) com `status`, `title`, `detail`, `instance` e `traceId`, inclusive os erros que o Spring MVC produz antes de chegar ao controller (método não suportado, rota inexistente, tipo de mídia não aceito). O `traceId` MUST ser o `trace-id` do cabeçalho `traceparent` (W3C Trace Context) quando ele for válido, e um identificador novo de 32 dígitos hexadecimais caso contrário. O `traceId` MUST estar disponível no MDC do log durante a requisição e MUST ser limpo ao fim dela.

#### Scenario: Corpo de erro com os campos do problema
- **WHEN** qualquer resposta de erro é produzida pela consulta
- **THEN** o corpo tem `status` igual ao status HTTP, `title`, `detail`, `instance` com o caminho da requisição e `traceId`

#### Scenario: traceId gerado quando não há traceparent
- **WHEN** a requisição não tem `traceparent`
- **THEN** o `traceId` do erro tem 32 dígitos hexadecimais e muda de uma requisição para outra

#### Scenario: traceparent válido é reaproveitado
- **WHEN** a requisição traz `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`
- **THEN** o `traceId` do erro é `4bf92f3577b34da6a3ce929d0e0e4736`

#### Scenario: traceparent inválido é ignorado
- **WHEN** a requisição traz um `traceparent` malformado ou com `trace-id` zerado
- **THEN** o `traceId` do erro é gerado e a resposta não falha

#### Scenario: O MDC é limpo ao fim da requisição
- **WHEN** a requisição termina, com sucesso ou com erro
- **THEN** o `traceId` não permanece no MDC da thread

#### Scenario: Método não suportado
- **WHEN** o cliente chama `POST /balances/{accountId}`
- **THEN** a resposta é `405` com `application/problem+json`, `traceId` e o cabeçalho `Allow`

#### Scenario: Rota inexistente
- **WHEN** o cliente chama `GET /balances` sem `accountId`
- **THEN** a resposta é `404` com `application/problem+json` e `traceId`

#### Scenario: Erro do /hello também usa problem+json
- **WHEN** o cliente chama `GET /hello` sem o parâmetro `name`
- **THEN** a resposta é `400` com `application/problem+json` e `traceId`

### Requirement: Saldo nunca é servido de cache
O serviço MUST NOT manter cache de saldo, e toda resposta de `/balances/**` (sucesso ou erro) MUST trazer `Cache-Control: no-store`.

#### Scenario: Resposta de sucesso não pode ser guardada
- **WHEN** a consulta responde `200`
- **THEN** a resposta traz `Cache-Control: no-store`

#### Scenario: Resposta de erro não pode ser guardada
- **WHEN** a consulta responde `404` ou `503`
- **THEN** a resposta traz `Cache-Control: no-store`

#### Scenario: Erro produzido antes do controller também não pode ser guardado
- **WHEN** a consulta responde `405` (método não suportado) ou `404` (rota inexistente sob `/balances`)
- **THEN** a resposta traz `Cache-Control: no-store`

#### Scenario: Consultas seguidas refletem o snapshot mais recente
- **WHEN** o snapshot da conta muda entre duas consultas
- **THEN** a segunda consulta devolve o saldo novo e o provider é chamado nas duas

### Requirement: Documentação OpenAPI gerada a partir do código
A aplicação MUST publicar a especificação OpenAPI em `/v3/api-docs` e a Swagger UI, ambas geradas dos controllers e dos DTOs, sem arquivo de especificação escrito à mão. A especificação MUST descrever `GET /balances/{accountId}` com o parâmetro `accountId` do tipo `uuid`, as respostas `200`, `400`, `404`, `503` (com o cabeçalho `Retry-After`) e `500`, o schema do sucesso com os cinco campos do contrato e o schema de erro com `traceId`.

#### Scenario: A especificação descreve a rota e os cinco status
- **WHEN** o cliente chama `GET /v3/api-docs`
- **THEN** o documento contém o caminho `/balances/{accountId}` com o parâmetro `accountId` de formato `uuid` e as respostas `200`, `400`, `404`, `503` e `500`

#### Scenario: O 503 documenta o Retry-After
- **WHEN** o cliente lê a resposta `503` na especificação
- **THEN** ela declara o cabeçalho `Retry-After`

#### Scenario: Os schemas refletem os DTOs
- **WHEN** o cliente lê os schemas da especificação
- **THEN** o sucesso tem `id`, `owner`, `balance` (`amount`, `currency`) e `updated_at`, e o erro tem `traceId`

#### Scenario: A Swagger UI é servida
- **WHEN** o cliente chama `GET /swagger-ui/index.html`
- **THEN** a resposta é `200` com HTML
