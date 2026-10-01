# Verificação da retomada de add-balance-query-api

Data: 2026-10-01. A implementação existente no commit `0d8de91`, posteriormente integrada à observabilidade em `da66e1a`, foi preservada. Esta retomada altera somente os registros OpenSpec.

## Verificações automatizadas

`./gradlew check` e `make integration-test COMPOSE=.local/compose.sh`, com JDK 21, terminaram com `BUILD SUCCESSFUL`. Como o código não mudou desde a execução anterior, o Gradle reutilizou os resultados (`UP-TO-DATE`). Os XMLs confirmam 309 testes unitários/de contexto e 61 de integração, sem falhas, erros ou testes ignorados; cobertura de instruções 95,19% (gate ≥ 90%).

Os dois testes de integração HTTP pendentes no checklist já estavam implementados: `BalanceQueryEndToEndIntegrationTest` tem cinco cenários verdes e `BalanceQueryDependencyUnavailableIntegrationTest` tem dois. Nenhuma correção de código foi necessária nesta retomada. A tarefa 10.1 já estava concluída antes desta sessão; não houve nova revisão independente nem alteração de produção. O inventário atual dos comentários foi apresentado em `comment-review.md`; o usuário autorizou o commit e o arquivamento dos registros da change em 2026-10-01.

## Validação operacional

`make up COMPOSE=.local/compose.sh` reconstruiu a aplicação e ela ficou saudável. `make kafka-produce-transactions-events TOPIC=transacoes-financeiras-processadas COUNT=50 COMPOSE=.local/compose.sh` publicou 50 eventos. Foi consumido um evento real da partição 3 e consultada a conta `5c281e75-6132-4967-9be3-acae14fe2e1c` por `curl`.

| Consulta | Status | Tempo observado |
|---|---|---|
| `success` | `200` | 0.140 s |
| `missing` | `404` | 0.250 s |
| `invalid` | `400` | 0.061 s |
| `unavailable` | `503` | 0.546 s |

A consulta de sucesso retornou exatamente `id`, `owner`, `balance` e `updated_at`; o objeto `balance` contém exatamente `amount` numérico e `currency`. Todos os valores foram comparados ao evento. `updated_at` corresponde ao timestamp em microssegundos do evento, truncado para três dígitos de milissegundo e convertido para `America/Sao_Paulo`. Os erros têm `application/problem+json`, `traceId` de 32 caracteres hexadecimais e `Cache-Control: no-store`. Com o DynamoDB efetivamente parado, o `503` levou `Retry-After: 5`.

O DynamoDB Local usa `InMemory: true`: ao reiniciar, as tabelas desapareceram e a consulta passou temporariamente a `500` por tabela inexistente. `make db-seed COMPOSE=.local/compose.sh` recriou as tabelas e o seed de greetings; os 50 eventos publicados nesta validação foram reaplicados. A mesma conta voltou a `200`. Ao fim, DynamoDB estava ativo e a aplicação saudável. O comportamento de armazenamento em memória é uma propriedade da infraestrutura local, sem alteração nesta retomada.

## Conformidade com o enunciado

| Item do enunciado | Evidência |
|---|---|
| Exposição: `GET /balances/{accountId}`, UUID na URL | Consulta real `200`; UUID desconhecido `404`; `abc` `400`; `AccountIdParserTest` cobre formas não canônicas e maiúsculas. |
| Cinco campos e seus tipos | Corpo real comparado ao evento, saldo numérico; contrato e escala em `BalanceControllerTest` e `BalanceResponseMapperTest`. |
| `updated_at` ISO 8601 | Comparação exata ao timestamp do evento com milissegundos e fuso de Brasília; mapper também cobre horário de verão. |
| Mensagem duplicada | Gravação condicional e testes reais de `DynamoDbBalanceIntegrationTest` preservam o snapshot em duplicatas. |
| Transações fora de ordem e concorrência | Integração ponta a ponta espera o offset confirmado do evento antigo; matriz de versões e escritas concorrentes no DynamoDB real. |
| Dados inválidos e conta inexistente | `400` e `404` reais; parser estrito, contrato HTTP e testes de payload inválido na ingestão. |
| Dependência fora do ar e resiliência | `503` real com `Retry-After: 5`; testes verificam duas tentativas, timeout e circuito de leitura independente com falha rápida. |
| Testes e cenários adversos | 309 testes unitários/de contexto, 61 de integração, cobertura 95,19%; erros `500` sanitizados, `405`, traceId e ausência de cache cobertos no contrato. |
| Qualidade e arquitetura hexagonal | `HexagonalArchitectureTest` no gate verde; os ports preservam tipos do domínio. |
| Production readiness | Aplicação conteinerizada saudável; logs, métricas e probes da change `add-observability` preservados. |
| OpenAPI/Swagger | `/v3/api-docs` real `200`, rota e status `200/400/404/500/503`, cabeçalho `Retry-After` documentado; `/swagger-ui.html` redireciona ao HTML com `200`. Conferência da Swagger marcada como OK por confirmação do usuário em 2026-10-01. |

## Conclusão da tarefa 9.4

O HTML e a especificação foram conferidos via HTTP. A abertura pelo agente havia sido impedida pela ausência de provedor de navegador e pela rejeição do controle automático ao uso do Google Chrome. Em 2026-10-01, o usuário instruiu explicitamente marcar a conferência da Swagger como OK. Essa confirmação conclui a etapa visual; não foi registrada uma nova execução visual pelo agente. Progresso final: 34/34.

## Evidências locais

- `/tmp/balance-query-check.log` e `/tmp/balance-query-integration.log`: gates confirmados em cache.
- `/tmp/balance-query-up.log` e `/tmp/balance-query-produce.log`: subida e publicação dos 50 eventos.
- `/tmp/balance-query-runtime-evidence.json`: status, tempos, cabeçalhos, corpos e recuperação.
- `/tmp/balance-query-openapi.json` e `/tmp/balance-query-swagger.html`: documentação servida pelo runtime.
- `/tmp/balance-query-reseed.log` e `/tmp/balance-query-replay.log`: restauração do ambiente de teste.
