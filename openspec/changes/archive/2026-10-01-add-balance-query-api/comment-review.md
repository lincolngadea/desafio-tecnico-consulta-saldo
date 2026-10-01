# Comentários da implementação da consulta de saldo

Inventário dos cabeçalhos e contratos dos arquivos da change `add-balance-query-api` ainda existentes no workspace. A retomada atual altera apenas os registros OpenSpec. O filtro de trace e seu teste foram substituídos pela instrumentação de `add-observability`.

## [build.gradle.kts:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/build.gradle.kts:1)

```kotlin
/*
 * L45-L49 dependencies: Actuator, Prometheus e tracing usam o BOM do Boot; a ponte de métricas do circuito tem
 *     versão explícita porque não é gerenciada, sem exportador de traces (add-observability design D11).
 * L51-L52 dependencies: os módulos de teste habilitam métricas e tracing reais nos testes de observabilidade.
 * L96 localAwsCredentials: o DynamoDB Local aceita qualquer par não vazio; credenciais de teste ficam no ambiente
 *     para exercitar a cadeia padrão do SDK, sem credenciais no código de produção (add-observability design D9).
 * L98-L100 tasks.bootRun: o desenvolvimento local usa a mesma cadeia de credenciais da imagem.
 * L102-L144 tasks.withType<Test>: os dois source sets recebem as credenciais locais, sem depender do perfil AWS
 *     pessoal de quem executa os testes.
 *
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceQueryDependencyUnavailableIntegrationTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceQueryDependencyUnavailableIntegrationTest.kt:1)

```kotlin
/*
 * L33-L34 SCHEDULING_TOLERANCE e FAST_FAILURE_LIMIT: a folga cobre o agendamento da JVM além do timeout da leitura,
 *     e o limite de falha rápida prova que o circuito aberto não toca o DynamoDB.
 * L37 BalanceQueryDependencyUnavailableIntegrationTest: aponta o DynamoDB para uma porta fechada, que dá recusa de
 *     conexão, para provar o `503` com `Retry-After` dentro do orçamento da leitura e, depois da janela do circuito,
 *     a falha rápida. O tópico é exclusivo e sem eventos, para a ingestão não competir com o teste.
 *
 * Spec: Dependência indisponível responde 503 com Retry-After; O pior caso da leitura cabe no orçamento de latência;
 *     Circuit breaker de leitura com a classificação de erros compartilhada
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceQueryEndToEndIntegrationTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceQueryEndToEndIntegrationTest.kt:1)

```kotlin
/*
 * L43 BalanceQueryEndToEndIntegrationTest: percorre o caminho inteiro com infraestrutura real: evento no Redpanda,
 *     ingestão, gravação no DynamoDB Local e consulta por HTTP em porta aleatória. Cada teste usa um `accountId`
 *     novo e espera pelo efeito, e o descarte do evento antigo é provado pelo offset confirmado, e não por um tempo
 *     fixo.
 *
 * Spec: Resposta de sucesso segue o contrato do enunciado; Conta inexistente responde 404; accountId inválido
 *     responde 400; Consultas seguidas refletem o snapshot mais recente
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/web/HttpIntegrationSupport.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/web/HttpIntegrationSupport.kt:1)

```kotlin
/*
 * L15-L19 HttpClient.get: cliente HTTP do JDK, para os testes de integração chamarem o servidor real sem acrescentar
 *     dependência de teste.
 *
 * Spec: n/a (add-balance-query-api design D10)
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceIntegrationTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceIntegrationTest.kt:1)

```kotlin
/*
 * L61 TIED_WRITES_EVERY: grupos de gravações dividem o mesmo timestamp, para o desempate pelo id da transação também
 *     ser exercitado sob concorrência.
 * L63-L64 LOW_TEXT_ID e HIGH_TEXT_ID: UUIDs cuja ordem como long com sinal (`UUID.compareTo`) diverge da ordem
 *     textual, que é a que o DynamoDB aplica.
 * L66 DynamoDbBalanceIntegrationTest: exercita o adapter de saldo contra uma instância real do DynamoDB Local, com a
 *     tabela `AccountBalances` criada pelo seed (rode com `make integration-test`). Mocks não mostram que a condição
 *     casa com a ordem do domínio nem que ela vale sob gravações concorrentes; a matriz de pares (gravado, recebido)
 *     confere o DynamoDB contra `SnapshotVersion`, caso a caso.
 * L86-L93 `should end with the greatest version when snapshots of the same account are written concurrently`: as
 *     escritas são disparadas ao mesmo tempo por coroutines em `Dispatchers.IO`, liberadas por um portão
 *     (`CompletableDeferred`), para a corrida ser real: um `runBlocking` sozinho usaria uma thread e serializaria as
 *     chamadas bloqueantes do SDK.
 * L190-L195 expectedResultOf: a matriz de pares confere o DynamoDB contra o domínio nos três resultados: mais novo é
 *     `Applied`, igual é `DuplicateIgnored` e o resto é `StaleIgnored`; é aqui que se prova que a recusa devolve o
 *     item (add-observability design D6).
 *
 * Spec: Gravação condicional do snapshot; Leitura do snapshot por conta
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/AccountIdParser.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/AccountIdParser.kt:1)

```kotlin
/*
 * L14 CANONICAL_UUID: só a forma canônica de 36 caracteres é aceita, porque `UUID.fromString` aceita formas como
 *     `1-1-1-1-1` e o enunciado tipa o parâmetro como UUID.
 * L16 parseAccountId: texto inválido é ausência esperada e volta como `null`, e não como exceção; o controller o
 *     traduz em `400`.
 *
 * Enunciado: O que construir → Exposição (API REST) → Contrato de request → accountId
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/ApiExceptionHandler.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/ApiExceptionHandler.kt:1)

```kotlin
/*
 * L49 ApiExceptionHandler: Exception Translator: traduz as falhas do núcleo e as do Spring MVC para `problem+json`
 *     com `traceId`. Vale para a aplicação inteira porque o Spring não entrega erro de roteamento a um advice
 *     restrito a um controller.
 * L55-L62 handleStorageFailure: o `when` sobre a sealed `BalanceStorageException` é exaustivo e sem `else`: um
 *     subtipo novo quebra a compilação até alguém decidir o seu status. Enunciado: O que será avaliado → Resiliência
 * L65-L68 handleUnexpectedFailure: qualquer falha não prevista vira `500`, com a causa só no log, para o corpo nunca
 *     vazar detalhe interno. Enunciado: O que será avaliado → Tratamento de cenários adversos
 * L70-L80 handleExceptionInternal: ponto único onde todo `ProblemDetail` do próprio Spring MVC (405, rota
 *     inexistente, parâmetro ausente) recebe `traceId` e `instance`.
 * L82-L91 unavailable: `503` leva `Retry-After` para o cliente saber quando tentar de novo; o valor é fixo porque o
 *     Resilience4j não expõe o tempo restante do circuito de forma estável. Não registra o stack, porque com o
 *     circuito aberto cada chamada rápida geraria um. Enunciado: O que será avaliado → Resiliência
 * L93-L101 internalError: a causa completa vai só para o log, com o `traceId` do MDC, e o corpo leva um `detail`
 *     genérico, para a resposta nunca vazar tabela, classe nem stack. Enunciado: O que será avaliado → Tratamento de
 *     cenários adversos
 * L103-L107 problemOf: único ponto que monta o `ProblemDetail` das falhas do núcleo, para todos receberem `traceId`
 *     e `instance` do mesmo jeito que os erros do Spring MVC.
 * L109-L117 describe: `traceId` vem do `Tracer`, que é quem propaga o `traceparent` do HTTP e põe o id no MDC; e
 *     `instance` vem do caminho, porque o `ProblemDetail` do Spring não os preenche sozinho em todos os caminhos de
 *     erro.
 *
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceApi.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceApi.kt:1)

```kotlin
/*
 * L26 BalanceApi: concentra a rota e a documentação OpenAPI (os cinco status e o `Retry-After` do `503`) numa
 *     interface, para as anotações não se misturarem ao fluxo do controller; o Spring herda o mapeamento da
 *     interface. Enunciado: Referências → API e testes → Documentando APIs com OpenAPI/Swagger
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceApiProperties.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceApiProperties.kt:1)

```kotlin
/*
 * L13 BalanceApiProperties: o valor do `Retry-After` do `503` é uma dica ao cliente e precisa poder mudar por
 *     variável de ambiente sem alterar código.
 *
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceController.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceController.kt:1)

```kotlin
/*
 * L16 BalanceController: adapter de entrada: só traduz protocolo, e as ausências viram `400` e `404` por
 *     `ResponseStatusException`, que o advice já converte em `problem+json`. Não decide regra de negócio.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceResponseMapper.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceResponseMapper.kt:1)

```kotlin
/*
 * L23-L24 RESPONSE_ZONE e UPDATED_AT_FORMAT: o exemplo do enunciado usa o offset de Brasília e três dígitos de
 *     milissegundo fixos; a zona (e não o offset `-03:00`) mantém o offset certo em datas com horário de verão, e
 *     `ISO_OFFSET_DATE_TIME` omitiria os zeros finais da fração.
 * L26-L32 toResponse: só traduz o snapshot para o contrato HTTP, sem decisão de negócio.
 * L34-L40 toResponseInstant: os microssegundos do evento ficam no DynamoDB, onde decidem a ordem; a resposta usa
 *     milissegundos, truncados e não arredondados, para o instante nunca passar o do evento. `updated_at` é o
 *     `transaction.timestamp` do evento que gerou o snapshot. Enunciado: O que construir → Exposição (API REST) →
 *     Contrato de resposta → updated_at
 *
 * Enunciado: O que construir → Exposição (API REST) → Contrato de resposta
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceWebConfig.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceWebConfig.kt:1)

```kotlin
/*
 * L13 BalanceWebConfig: liga as propriedades da API, seguindo o padrão das demais configurações do contexto.
 *
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/NoStoreFilter.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/NoStoreFilter.kt:1)

```kotlin
/*
 * L24 NoStoreFilter: o requisito é o saldo mais atual, então nenhuma resposta de `/balances` pode ser guardada por
 *     intermediário. É um filtro, e não o `WebContentInterceptor`, porque o interceptor só roda quando há handler, e
 *     o `405` e a rota inexistente nunca chegam a um (add-balance-query-api design D8). O caminho é lido
 *     decodificado e sem parâmetros de caminho, como o Spring MVC o casa, para `/%62alances` e `/balances;x=1` não
 *     escaparem. Enunciado: O que construir → Exposição (API REST)
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/dto/BalanceResponse.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/dto/BalanceResponse.kt:1)

```kotlin
/*
 * L19 BalanceResponse: é o contrato de resposta do enunciado, com exatamente estes campos e nomes. `updated_at` usa
 *     `@JsonProperty` porque o contrato é snake_case e o Kotlin não. Enunciado: O que construir → Exposição (API
 *     REST) → Contrato de resposta
 * L34 MoneyResponse: agrupa `balance.amount` e `balance.currency`; o `amount` é `BigDecimal` para o saldo sair como
 *     número JSON exato, sem ponto flutuante. Não se chama `BalancePayload` porque o DTO do Kafka já usa esse nome
 *     para outro conceito. Enunciado: O que construir → Exposição (API REST) → Contrato de resposta → balance.amount
 *
 * Enunciado: O que construir → Exposição (API REST) → Contrato de resposta
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/dto/ProblemDetailSchema.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/web/dto/ProblemDetailSchema.kt:1)

```kotlin
/*
 * L12 ProblemDetailSchema: existe só para o OpenAPI mostrar o corpo de erro: o `ProblemDetail` do Spring leva o
 *     `traceId` como membro de extensão, que o springdoc não enxerga na classe. Nunca é instanciado.
 *
 * Enunciado: Referências → API e testes → Documentando APIs com OpenAPI/Swagger
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProvider.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProvider.kt:1)

```kotlin
/*
 * L23 DynamoDbBalanceProvider: lê o snapshot com GetItem pela chave, nunca com Scan, para a consulta continuar
 *     barata em alto volume. Recebe o cliente de leitura, de timeouts curtos e no máximo 1 retry
 *     (add-balance-query-api design D6). A mensagem de falha usa o `AccountId` mascarado, para a conta não ir
 *     inteira para log nem para os headers da DLT. Enunciado: O que construir → Exposição (API REST)
 * L36-L42 consistentGetRequest: leitura fortemente consistente: logo após um Applied, nunca devolve o snapshot
 *     anterior (add-balance-repository design D4). Enunciado: O que será avaliado → Tratamento de concorrência
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/BalanceCircuitBreakerConfig.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/BalanceCircuitBreakerConfig.kt:1)

```kotlin
/*
 * L43-L44 BALANCE_STORAGE_WRITE_CIRCUIT_BREAKER e BALANCE_STORAGE_READ_CIRCUIT_BREAKER: nomes dos circuitos, que
 *     aparecem na mensagem de `StorageUnavailableException` e identificam cada circuito em métricas futuras.
 * L50 BalanceCircuitBreakerConfig: isola em uma configuração a criação dos circuit breakers e a decoração do
 *     repositório e do provider de saldo.
 * L53 balanceCircuitBreakerRegistry: os circuitos nascem de um registry porque o `resilience4j-micrometer` publica
 *     as métricas a partir dele; sem registry, o estado dos circuitos não chegaria ao Prometheus (add-observability
 *     design D5). Enunciado: O que será avaliado → Production readiness
 * L56-L59 balanceWriteCircuitBreaker: circuito da escrita, que pausa a ingestão quando abre; é instância própria
 *     para a API não pausar o consumer (add-balance-query-api design D7). O nome diz a que lado o circuito serve,
 *     porque há dois beans do mesmo tipo.
 * L62-L65 balanceReadCircuitBreaker: circuito da leitura, independente do da escrita: janelas, sondas e perfis de
 *     timeout diferentes não devem se misturar.
 * L68 balanceCircuitBreakerMetrics: publica o estado, as chamadas e a taxa de falha dos dois circuitos,
 *     identificados pelo nome, pela solução pronta do Resilience4j, e não por gauges escritos à mão. Enunciado: O
 *     que será avaliado → Production readiness
 * L73-L76 resilientBalanceRepository: `@Primary` faz o caso de uso receber o repositório protegido; o writer entra
 *     por qualificador de nome para este pacote não depender da classe concreta do adapter DynamoDB.
 * L80-L83 resilientBalanceProvider: `@Primary` faz o caso de consulta receber o provider protegido, com o mesmo
 *     critério de qualificador do repositório.
 * L85-L95 storageCircuitBreakerConfig: definição única do que conta como falha, compartilhada pelos dois circuitos
 *     (DRY): só `TransientStorageException` conta; `PermanentStorageException` é ignorada porque nada diz sobre a
 *     saúde da dependência. `minimumNumberOfCalls` igual à janela evita abrir o circuito com poucas chamadas.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/CircuitBreakerBalanceProvider.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/CircuitBreakerBalanceProvider.kt:1)

```kotlin
/*
 * L19 CircuitBreakerBalanceProvider: Decorator que protege a leitura sem o caso de uso saber que o circuit breaker
 *     existe (add-balance-query-api design D7).
 * L24-L29 findByAccountId: traduz `CallNotPermittedException` para `StorageUnavailableException`, para o tipo do
 *     Resilience4j nunca atravessar o port (Art. 1). O resultado `null` (conta inexistente) conta como sucesso,
 *     porque o `404` é resposta esperada.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/application/GetBalanceService.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/application/GetBalanceService.kt:1)

```kotlin
/*
 * L16 GetBalanceService: só orquestra o caso de uso: o adapter de entrada não pode depender do port de saída, e a
 *     regra "conta sem saldo é ausência e falha de armazenamento sobe" fica numa fronteira testável sem HTTP.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/port/input/GetBalanceUseCase.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/port/input/GetBalanceUseCase.kt:1)

```kotlin
/*
 * L13 GetBalanceUseCase: o controller depende deste port, e não do serviço, para a consulta ser testada com um fake
 *     e a origem do pedido poder mudar sem tocar o caso de uso. A conta sem snapshot é `null`, e não exceção, porque
 *     é um resultado esperado; o contrato de retorno e de exceções fica no KDoc da assinatura.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/port/input/GetBalanceUseCase.kt:14](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/port/input/GetBalanceUseCase.kt:14)

```kotlin
/**
     * Devolve o snapshot de saldo mais recente de [accountId], ou `null` quando a conta não tem snapshot.
     *
     * @throws br.com.itau.challenge.balance.port.output.TransientStorageException quando tentar de novo mais tarde
     * pode dar certo.
     * @throws br.com.itau.challenge.balance.port.output.StorageUnavailableException quando o armazenamento está
     * indisponível no momento.
     * @throws br.com.itau.challenge.balance.port.output.PermanentStorageException quando tentar de novo não ajuda.
     */
```

## [src/main/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbConfig.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbConfig.kt:1)

```kotlin
/*
 * L35-L36 READ_RETRY_BASE_DELAY e READ_RETRY_MAX_DELAY: o backoff padrão do SDK (100 ms e 1 s para throttling)
 *     consumiria o orçamento da leitura; o retry rápido fica em 50 a 100 ms e não é configurável (YAGNI).
 * L40 DynamoDbConfig: cria os clientes DynamoDB da aplicação a partir das propriedades, com timeouts e tentativas
 *     explícitos: um por perfil de acesso, para a leitura ter orçamento curto sem encurtar a escrita
 *     (add-balance-query-api design D6).
 * L44-L55 dynamoDbClient: cliente da escrita e do `hello`; é `@Primary` para a injeção por tipo desses contextos
 *     continuar igual.
 * L58-L71 readDynamoDbClient: cliente da leitura: o SDK só deixa sobrescrever os timeouts por requisição, e não o
 *     número de tentativas nem o backoff, então o "no máximo 1 retry" exige um cliente próprio.
 * L73-L87 clientBuilder: o que é comum aos dois perfis (endpoint, região, credenciais e HTTP) fica num só lugar. As
 *     credenciais vêm da cadeia padrão do SDK (variáveis de ambiente), e não do código, para a configuração só
 *     variar por ambiente (12-Factor, add-observability design D9).
 * L89-L93 overrideConfiguration: os dois limites de tempo (`apiCallAttempt` e `apiCall`) são comuns aos perfis; só a
 *     estratégia de retry difere.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/main/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbProperties.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbProperties.kt:1)

```kotlin
/*
 * L20 DynamoDbProperties: agrupa as configurações dos clientes DynamoDB, para todo timeout e o limite de tentativas
 *     serem explícitos e poderem ser sobrescritos por variável de ambiente.
 * L27 Timeouts: são quatro limites porque cada camada limita a sua parte: uma falha de rede não consome todo o teto
 *     da chamada, e uma tentativa lenta não consome as demais. `apiCall` limita a chamada inteira, somando todas as
 *     tentativas.
 * L34 Retry: `maxAttempts` conta a primeira chamada: 2 significa uma tentativa original e uma repetição.
 * L36-L46 Read: perfil da leitura, com as invariantes na construção (Fail Fast): no máximo 1 retry e `maxAttempts ×
 *     api-call-attempt ≤ api-call`, para o retry nunca ser truncado em silêncio nem o pior caso passar do orçamento.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/AccountIdParserTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/AccountIdParserTest.kt:1)

```kotlin
/*
 * L20 AccountIdParserTest: confere que só a forma canônica de 36 caracteres é aceita, incluindo `1-1-1-1-1`, que
 *     `UUID.fromString` aceitaria.
 *
 * Spec: accountId inválido responde 400
 * Enunciado: O que construir → Exposição (API REST) → Contrato de request → accountId
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceApiPropertiesTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceApiPropertiesTest.kt:1)

```kotlin
/*
 * L17 BalanceApiPropertiesTest: lê o `application.yaml` de verdade, para o padrão documentado e o nome da variável
 *     de ambiente serem os que o código usa.
 *
 * Spec: Dependência indisponível responde 503 com Retry-After
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceControllerTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceControllerTest.kt:1)

```kotlin
/*
 * L50 BalanceControllerTest: teste de contrato do adapter HTTP: o caso de uso é um fake roteirizável, então cada
 *     exceção do núcleo e cada ausência são provadas como o status, os cabeçalhos e o corpo que o cliente vê. O
 *     texto cru do corpo é conferido onde o formato do número importa, e o caminho codificado e com parâmetros de
 *     caminho prova que o `no-store` não tem desvio de URL.
 *
 * Spec: Resposta de sucesso segue o contrato do enunciado; accountId inválido responde 400; Conta inexistente
 *     responde 404; Dependência indisponível responde 503 com Retry-After; Falha inesperada responde 500 sem vazar
 *     detalhe interno; Todo erro é problem+json com traceId; Saldo nunca é servido de cache (inclui o erro produzido
 *     antes do controller)
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceResponseMapperTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/BalanceResponseMapperTest.kt:1)

```kotlin
/*
 * L23 BalanceResponseMapperTest: fixa o formato de `updated_at` (milissegundos, offset de Brasília, três dígitos
 *     fixos) e a escala do valor; o caso de 2017 prova o offset de horário de verão.
 *
 * Spec: Resposta de sucesso segue o contrato do enunciado
 * Enunciado: O que construir → Exposição (API REST) → Contrato de resposta → updated_at
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/OpenApiDocumentationTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/OpenApiDocumentationTest.kt:1)

```kotlin
/*
 * L22 OpenApiDocumentationTest: lê a especificação gerada em `/v3/api-docs`, e não um arquivo escrito à mão, para
 *     provar que ela reflete o código.
 *
 * Spec: Documentação OpenAPI gerada a partir do código
 * Enunciado: Referências → API e testes → Documentando APIs com OpenAPI/Swagger
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/ScriptedGetBalanceUseCase.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/web/ScriptedGetBalanceUseCase.kt:1)

```kotlin
/*
 * L19 ScriptedGetBalanceUseCase: fake que honra o contrato do port e deixa o teste roteirizar o resultado, sem
 *     depender de matchers do Mockito com a value class `AccountId`.
 * L35 ScriptedGetBalanceUseCaseConfiguration: substitui o caso de uso real pelo fake com `@Primary`, só nos testes
 *     que importam esta configuração.
 *
 * Spec: n/a (add-balance-query-api design D10)
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderReadRetryTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderReadRetryTest.kt:1)

```kotlin
/*
 * L41 DynamoDbBalanceProviderReadRetryTest: usa o cliente de leitura de produção contra um servidor HTTP do JDK que
 *     conta as requisições, porque só assim se prova "no máximo 2 tentativas" e "falha permanente não se repete";
 *     mocks do cliente não exercitam a política de retry do SDK.
 *
 * Spec: Leitura repete no máximo uma vez
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderTimeoutTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderTimeoutTest.kt:1)

```kotlin
/*
 * L29 SCHEDULING_TOLERANCE: folga para o encerramento do cliente e a variação de agendamento, além do limite
 *     configurado.
 * L33 DynamoDbBalanceProviderTimeoutTest: prova, com a fábrica do cliente de leitura de produção, que um endpoint
 *     que aceita conexões mas nunca responde não bloqueia quem chamou além do timeout total da leitura.
 * L35 silentEndpoint: o sistema operacional conclui o handshake TCP pela fila de espera, então o socket aceita mas
 *     nunca responde.
 *
 * Spec: Cliente de leitura com timeouts curtos e configuráveis
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/BalanceCircuitBreakerConfigTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/BalanceCircuitBreakerConfigTest.kt:1)

```kotlin
/*
 * L35 BalanceCircuitBreakerConfigTest: um parâmetro que não chega ao circuito real volta ao padrão da biblioteca sem
 *     ninguém perceber, então o teste confere o circuito criado pela fábrica de produção, como no
 *     `DynamoDbConfigTest`; também prova que os dois circuitos são independentes e aplicam a mesma classificação.
 * L147-L151 stateGauge: lê o estado do circuito pela métrica publicada, e não pelo objeto do circuito, para provar o
 *     que o Prometheus vai mostrar.
 * L161-L167 classificação compartilhada: parametrizado sobre os dois beans, para a regra de falha dos dois circuitos
 *     não divergir.
 *
 * Spec: Parâmetros do circuito configuráveis por variável de ambiente; Circuit breaker de leitura com a
 *     classificação de erros compartilhada; Estado do circuit breaker exposto
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/CircuitBreakerBalanceProviderTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/CircuitBreakerBalanceProviderTest.kt:1)

```kotlin
/*
 * L36 ScriptedBalanceProvider: fake que honra o contrato do port e deixa o teste roteirizar o resultado de cada
 *     chamada.
 * L46 CircuitBreakerBalanceProviderTest: usa o circuito de leitura criado pela configuração de produção, para provar
 *     o que conta como falha, e `null` (conta inexistente) conta como sucesso.
 *
 * Spec: Circuit breaker de leitura com a classificação de erros compartilhada
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/CircuitBreakerBalanceRepositoryTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/output/resilience/CircuitBreakerBalanceRepositoryTest.kt:1)

```kotlin
/*
 * L37 ScriptedBalanceRepository: fake que honra o contrato do port e deixa o teste roteirizar o resultado de cada
 *     chamada.
 * L47 CircuitBreakerBalanceRepositoryTest: usa o circuito criado pela configuração de produção, para provar o que
 *     conta como falha. A espera do estado aberto não é aguardada: `transitionToHalfOpenState` mantém o teste
 *     rápido.
 *
 * Spec: Falhas transitórias do armazenamento abrem o circuito; Circuito aberto falha rápido com exceção do port;
 *     Circuito semiaberto sonda a dependência
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/application/GetBalanceServiceTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/application/GetBalanceServiceTest.kt:1)

```kotlin
/*
 * L39 GetBalanceServiceTest: o fake do `BalanceProvider` implementa o mesmo port, e os testes de falha confirmam que
 *     a exceção chega intacta ao chamador. O `SNAPSHOT` é local porque a camada `application` não pode importar o
 *     adapter, nem nos testes.
 *
 * Spec: Consulta devolve o snapshot mais recente da conta; Conta sem snapshot é ausência esperada; Falhas do
 *     armazenamento não são engolidas
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/test/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbClientsWiringTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbClientsWiringTest.kt:1)

```kotlin
/*
 * L20 DynamoDbClientsWiringTest: sobe o contexto real para provar que a injeção de `DynamoDbClient` por tipo, sem
 *     qualificador (writer e `hello`), continua recebendo o cliente da escrita, que é o `@Primary`, e que o cliente
 *     de leitura é outra instância.
 *
 * Spec: Cliente de leitura com timeouts curtos e configuráveis
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbConfigTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbConfigTest.kt:1)

```kotlin
/*
 * L26 DynamoDbConfigTest: um timeout ou uma quantidade de tentativas que não chegam ao cliente real voltam a valores
 *     padrão do SDK sem que ninguém perceba, então o teste confere os clientes criados pela fábrica de produção, e
 *     não só as propriedades; inclui o cliente de leitura e que o da escrita não mudou.
 *
 * Spec: Timeouts explícitos do cliente DynamoDB; Cliente de leitura com timeouts curtos e configuráveis; Leitura
 *     repete no máximo uma vez; Configuração só por variável de ambiente
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbPropertiesFixtures.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbPropertiesFixtures.kt:1)

```kotlin
/*
 * L14-L16 READ_API_CALL_ATTEMPT_TIMEOUT, READ_API_CALL_TIMEOUT e READ_MAX_ATTEMPTS: os valores padrão do perfil de
 *     leitura, em um só lugar para os testes de cliente, de timeout e de retry usarem os mesmos.
 * L18-L32 readProfile: monta o perfil de leitura válido, com os parâmetros que cada teste varia; é público porque os
 *     testes de integração são outro módulo.
 *
 * Spec: n/a (add-balance-query-api design D6)
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbReadPropertiesTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/hello/adapter/output/dynamodb/DynamoDbReadPropertiesTest.kt:1)

```kotlin
/*
 * L20 DynamoDbReadPropertiesTest: lê o `application.yaml` de verdade, e a configuração incoerente é provada pela
 *     falha de subida do contexto, que é o que o operador vê.
 *
 * Spec: Cliente de leitura com timeouts curtos e configuráveis; Leitura repete no máximo uma vez; O pior caso da
 *     leitura cabe no orçamento de latência
 * Enunciado: O que será avaliado → Resiliência
 */
```
