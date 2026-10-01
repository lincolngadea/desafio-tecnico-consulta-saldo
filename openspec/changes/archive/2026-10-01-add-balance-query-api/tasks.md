## 1. Dependência e spike do OpenAPI (R3)

- [x] 1.1 Confirmar com `./gradlew dependencyInsight` que o BOM do Boot não gerencia o springdoc, e declarar `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1` em `implementation` no `build.gradle.kts`
- [x] 1.2 Teste vermelho de spike (`OpenApiDocumentationTest`, `@SpringBootTest` + MockMvc): "A Swagger UI é servida" e `/v3/api-docs` respondendo `200` com o `/hello` listado; registrar no `design.md` qualquer ajuste de combinação com o Boot 4.1.0 e o Jackson 3, e **parar e perguntar** se a combinação não funcionar (Art. 10)

## 2. Caso de uso (TDD)

- [x] 2.1 Teste vermelho (`GetBalanceServiceTest`, fake do `BalanceProvider` que honra o contrato, sem Mockito): "Conta com snapshot devolve o snapshot", "A consulta usa só o identificador da conta", "Conta sem snapshot devolve ausência" e "Falha transitória propaga", "Armazenamento indisponível propaga" e "Falha permanente propaga"
- [x] 2.2 Criar `fun interface GetBalanceUseCase` com KDoc do contrato (retorno e exceções) e `GetBalanceService` (`@Service`), até 2.1 ficar verde

## 3. Cliente DynamoDB de leitura (TDD)

- [x] 3.1 Teste vermelho das propriedades de leitura (`DynamoDbReadPropertiesTest`): "Valores padrão", "Valores sobrescritos", "Mais de duas tentativas é rejeitado", "Orçamento coerente" e "Orçamento incoerente", com a mensagem de erro útil
- [x] 3.2 Criar `dynamodb.read.{timeouts,retry}` em `DynamoDbProperties` (reusando `Timeouts` e `Retry`, com as invariantes por `require`), as chaves com `${DYNAMODB_READ_*:default}` no `application.yaml` e as variáveis no serviço `app` do `docker-compose.yml`, até 3.1 ficar verde
- [x] 3.3 Teste vermelho (`DynamoDbConfigTest`): "A escrita e o hello não mudam" (cliente `@Primary` com a configuração atual, injeção por tipo sem qualificador) e o cliente de leitura com a sua configuração
- [x] 3.4 Teste vermelho (`DynamoDbBalanceProviderReadRetryTest`, servidor HTTP do JDK que conta requisições): "Sucesso na segunda tentativa", "Falha nas duas tentativas" (exatamente 2 requisições) e "Falha permanente não é repetida" (exatamente 1)
- [x] 3.5 Ajustar `DynamoDbBalanceProviderTimeoutTest`: "Endpoint que não responde" passa a usar o cliente de leitura e o seu `api-call`
- [x] 3.6 Criar o `readDynamoDbClient` em `DynamoDbConfig` (cliente atual `@Primary`, backoff rápido `exponentialDelay(50 ms, 100 ms)` para falha comum e throttling) e injetá-lo no `DynamoDbBalanceProvider` por `@Qualifier`, até 3.3 a 3.5 ficarem verdes; conferir que `DynamoDbBalanceIntegrationTest` e os testes do `hello` continuam verdes

## 4. Circuit breaker de leitura (TDD)

- [x] 4.1 Teste vermelho (`CircuitBreakerBalanceProviderTest`, fake do provider): "Taxa de falhas acima do limiar abre o circuito", "Falha permanente não conta para o circuito", "Conta inexistente conta como sucesso", "Chamada com circuito aberto não alcança o DynamoDB" e "Sondas bem-sucedidas fecham o circuito"
- [x] 4.2 Teste vermelho (`BalanceCircuitBreakerConfigTest`): "Os dois circuitos são independentes" e "A classificação é a mesma nos dois circuitos" (parametrizado sobre os dois beans)
- [x] 4.3 Extrair a definição de falha e de ignorado numa única função, renomear `balanceCircuitBreaker` para `balanceWriteCircuitBreaker` com `@Qualifier` no ponto de injeção, criar `balanceReadCircuitBreaker` (`balance-storage-read`) e `CircuitBreakerBalanceProvider` exposto como `@Primary`, até 4.1 e 4.2 ficarem verdes; conferir que os testes do circuito de escrita continuam verdes

## 5. Adapter HTTP (TDD)

- [x] 5.1 Teste vermelho do parser do identificador (`AccountIdParserTest`): UUID canônico em minúsculas e maiúsculas, "Texto que não é UUID", "UUID em forma não canônica" (`1-1-1-1-1`) e "UUID sem hifens"
- [x] 5.2 Teste vermelho do mapeamento para o DTO (`BalanceResponseMapperTest`): "O instante é devolvido em milissegundos com offset de Brasília", "A fração de segundo tem sempre três dígitos" e a escala do valor
- [x] 5.3 Teste vermelho do filtro (`TraceIdFilterTest`): "traceId gerado quando não há traceparent", "traceparent válido é reaproveitado", "traceparent inválido é ignorado" e "O MDC é limpo ao fim da requisição"
- [x] 5.4 Teste vermelho de contrato (`BalanceControllerTest`, `@SpringBootTest` + MockMvc, `GetBalanceUseCase` por um fake roteirizável `@Primary`, `ScriptedGetBalanceUseCase`):
  - sucesso: "Conta existente devolve os cinco campos do contrato", "O saldo é um número com a escala da moeda" (texto cru), "Saldo negativo é devolvido como está" e "Identificador em maiúsculas devolve a mesma conta";
  - erros: "Texto que não é UUID", "UUID em forma não canônica" e "UUID sem hifens" (`400`, caso de uso não chamado); "Conta sem snapshot" (`404`); "Falha transitória do armazenamento" e "Circuito aberto" (`503` com `Retry-After`); "Falha permanente do armazenamento" e "Exceção não prevista" (`500` sem a mensagem original); "Corpo de erro com os campos do problema";
  - roteamento: "Método não suportado" (`405` com `Allow`), "Rota inexistente" (`404`) e "Erro do /hello também usa problem+json";
  - cache: "Resposta de sucesso não pode ser guardada", "Resposta de erro não pode ser guardada", "Erro produzido antes do controller também não pode ser guardado" (`405`, rota inexistente, caminho codificado e com parâmetros de caminho) e "Consultas seguidas refletem o snapshot mais recente"
- [x] 5.5 Teste vermelho (`BalanceApiPropertiesTest`): "Valor padrão e valor sobrescrito do Retry-After"
- [x] 5.6 Teste vermelho do log: "A causa vai para o log com o traceId" (`OutputCaptureExtension`)
- [x] 5.7 Implementar, até 5.1 a 5.6 ficarem verdes: `BalanceApi` (anotações OpenAPI e mapeamento) e `BalanceController`, o DTO e o mapper, o parser estrito do `accountId` (devolve `AccountId?`; o controller lança `ResponseStatusException`), o `TraceIdFilter`, o advice sobre `ResponseEntityExceptionHandler` com o `when` exaustivo sobre `BalanceStorageException`, `BalanceApiProperties` (`balance.api.retry-after`), o `NoStoreFilter` (`no-store` em `/balances/**`, casando o caminho decodificado e sem parâmetros de caminho) e `logging.pattern.correlation` no `application.yaml`
- [x] 5.8 Conferir que `GreetingControllerTest` e `ApplicationTests` continuam verdes e que o `/hello` mantém o comportamento, salvo o corpo de erro

## 6. Documentação OpenAPI (TDD)

- [x] 6.1 Teste vermelho (`OpenApiDocumentationTest`, evolução do spike de 1.2): "A especificação descreve a rota e os cinco status", "O 503 documenta o Retry-After" e "Os schemas refletem os DTOs"
- [x] 6.2 Anotar `BalanceApi` e os DTOs (`@Operation`, `@ApiResponse`, `@Header`, `@Schema` com exemplo do enunciado) até 6.1 ficar verde, tratando a varredura do advice pelo springdoc (R3)

## 7. Integração ponta a ponta

- [x] 7.1 Teste de integração vermelho (`BalanceQueryEndToEndIntegrationTest`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, Redpanda e DynamoDB Local reais, cliente HTTP real): publica evento e consulta, com os cenários "Conta existente devolve os cinco campos do contrato", "Conta sem snapshot", "Texto que não é UUID", evento fora de ordem preservando o mais novo e evento `DECLINED` avançando só `updated_at`
- [x] 7.2 Teste de integração vermelho (`BalanceQueryDependencyUnavailableIntegrationTest`, endpoint do DynamoDB apontado para uma porta fechada): "Falha transitória do armazenamento" dentro do orçamento da leitura e, depois da janela do circuito, "Circuito aberto" com falha rápida e `Retry-After`; isolar de qualquer consumo de eventos
- [x] 7.3 Fazer 7.1 e 7.2 ficarem verdes sem alterar o código de produção, salvo defeito que eles revelem

## 8. Documentação operacional

- [x] 8.1 Documentar no `README.md` o endpoint, os status e o formato de erro, a Swagger UI (`/swagger-ui.html`) e a especificação (`/v3/api-docs`), as variáveis de ambiente novas (`DYNAMODB_READ_*`, `BALANCE_API_RETRY_AFTER`) e a decisão de não usar cache

## 9. Verificação

- [x] 9.1 `./gradlew check` verde (gate de cobertura ≥ 90%) e `make integration-test` verde
- [x] 9.2 Cabeçalho de comentário (Art. 6) em todo arquivo novo ou alterado: uma entrada por trecho (linhas, símbolo e porquê), `Spec:` nos testes e `Enunciado:` na última linha, em pt-BR, sem comentário no corpo; atualizar os cabeçalhos dos arquivos alterados (`DynamoDbConfig`, `DynamoDbProperties`, `DynamoDbBalanceProvider`, `BalanceCircuitBreakerConfig`, `DynamoDbBalanceProviderTimeoutTest` e os demais)
- [x] 9.3 Confirmar que o `HexagonalArchitectureTest` cobre os pacotes novos e que nenhum tipo de Spring, Jackson, SDK ou Resilience4j atravessa um port
- [x] 9.4 Validar a implementação contra o `.challenge/enunciado.md` (Art. 11): percorrer *Exposição (API REST)* (rota plural, parâmetro, os cinco campos e seus tipos, `updated_at`), *Pense além do happy path* (duplicata, dado inválido, dependência fora do ar) e *O que será avaliado* (resiliência, testes, cenários adversos, production readiness); com `make up` e `make kafka-produce-transactions-events TOPIC=transacoes-financeiras-processadas COUNT=50` rodando, executar `curl` com um `account.id` lido do tópico (`200`), com um UUID desconhecido (`404`), com `abc` (`400`) e com o DynamoDB parado (`503` com `Retry-After`), e abrir a Swagger UI; registrar a tabela item do enunciado → evidência para o relatório de revisão e parar para perguntar se algo divergir

## 10. Revisão e contexto

- [x] 10.1 Independent agent review: seguir o procedimento do `CLAUDE.md` (subagente com contexto limpo via Agent, nunca fork; somente leitura; entrega de proposal, design, specs, tasks, diff, `CLAUDE.md` e `.challenge/enunciado.md`; achados bloqueante/ajuste/sugestão com `arquivo:linha`; no máximo 3 rodadas) e registrar rodadas, correções e rejeições para o usuário
- [x] 10.2 Apresentar ao usuário os comentários novos e alterados (`arquivo:linha` e texto) e só commitar depois do aval
- [x] 10.3 Atualizar `openspec/project.md` e `context:` do `config.yaml` (Regra 3 do `CLAUDE.md`), na mesma tarefa: dependência springdoc e o motivo; variáveis de ambiente novas; a regra do cliente DynamoDB único reescrita para "um por perfil de acesso" e o segundo cliente; o circuit breaker de leitura e a renomeação; as decisões do enunciado (`404`, `400`, formato de `updated_at`, origem do `traceId`); a decisão de não ter cache; a lacuna do `@RestControllerAdvice` resolvida e a ambiguidade de status removida da lista de abertas; validar o YAML com `openspec list --json`

## Evidências da retomada (2026-10-01)

As tarefas 7.1–7.3 já tinham implementação no commit `0d8de91`, atualizada e verificada por `add-observability`. Os cinco cenários de `BalanceQueryEndToEndIntegrationTest` e os dois de `BalanceQueryDependencyUnavailableIntegrationTest` passaram contra Redpanda e DynamoDB Local reais; nenhuma mudança de produção foi necessária nesta retomada. `./gradlew check` e `make integration-test COMPOSE=.local/compose.sh` confirmaram os resultados em cache do Gradle para código inalterado: 309 testes no workspace, 61 de integração, cobertura de instruções 95,2%.

Os comentários atuais foram apresentados por meio de `comment-review.md` (arquivo, linha e texto completo). Não houve alteração de código nesta retomada. Em 2026-10-01, o usuário autorizou marcar a conferência da Swagger como OK e realizar o commit seguido do arquivamento. A tarefa 9.4 foi concluída por essa confirmação, somada às verificações reais do contrato, do HTML e do OpenAPI via HTTP; os resultados e a tabela de conformidade estão em `review.md`. Progresso final: 34/34 tarefas.
