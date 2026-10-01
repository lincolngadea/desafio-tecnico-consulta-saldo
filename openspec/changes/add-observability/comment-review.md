# Comentários para revisão humana

Cabeçalhos e KDocs dos arquivos novos ou alterados da mudança `add-observability`. Comentários aprovados pelo usuário em 2026-10-01. Os comentários dos arquivos de infraestrutura Docker e seus testes permanecem registrados aqui, mas esses arquivos foram excluídos do commit a pedido do usuário.

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

## [infra/redpanda/ingestion-topics.sh:2](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/infra/redpanda/ingestion-topics.sh:2)

```sh
# L14 PARTITIONS: 6 partições é a decisão da `add-transaction-ingestion` (design D7): dá escala horizontal do
#     consumo, e o valor é sobrescrevível por ambiente.
# L15-L18 TOPICS: os nomes vêm das mesmas variáveis que a aplicação lê (`TRANSACTIONS_TOPIC` e
#     `TRANSACTIONS_DLT_TOPIC`), com os mesmos padrões; este script é a fonte única da criação dos tópicos, usada
#     pelo seed e por `make kafka-topics-ingestion` (add-observability design D10).
# L26-L33 for topic: `describe` antes de `create` deixa o script idempotente, porque a criação automática de tópicos
#     está desligada e o seed roda a cada `make up`.
#
# Enunciado: Como começar → Criando o tópico Kafka
```

## [src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionMetricsIntegrationTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionMetricsIntegrationTest.kt:1)

```kotlin
/*
 * L40 IngestionMetricsIntegrationTest: sobe a aplicação real contra o Redpanda e o DynamoDB Local, com o caso de uso
 *     real, para provar os quatro desfechos no contador. Cada teste compara com o valor anterior, porque o contador
 *     é compartilhado pelo contexto.
 * L131-L134 listenerTimers: restringe a amostra ao tópico e ao listener de transações deste contexto, para
 *     tráfego do hello não satisfazer a verificação de latência.
 * L138-L140 failedListenerTimerCount: a falha precisa ter uma série de erro própria, sem aproveitar amostras
 *     bem-sucedidas de outro teste.
 *
 * Spec: Eventos contados por resultado; Latência de processamento por registro; Consumer lag exposto
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionObservabilityIntegrationTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionObservabilityIntegrationTest.kt:1)

```kotlin
/*
 * L49 TraceIdCapturingUseCase: dublê que honra o contrato do port e guarda o `traceId` do MDC no instante em que o
 *     caso de uso roda, que é o que todo log do processamento usa; assim o teste prova a propagação sem depender de
 *     um log que o fluxo normal não escreve.
 * L69 IngestionObservabilityIntegrationTest: sobe a aplicação real contra o Redpanda, com o tracing ligado, para
 *     provar a correlação do Kafka: o `traceparent` do registro vira o `traceId` do processamento, a DLT o preserva
 *     e nenhum log leva o `owner` nem trecho do payload (um `owner` sentinela num evento malformado).
 *
 * Spec: traceId propagado do Kafka; Dados pessoais e payload nunca vão para o log
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionIngestionIntegrationTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionIngestionIntegrationTest.kt:1)

```kotlin
/*
 * L34 TransactionIngestionIntegrationTest: sobe a aplicação real contra o Redpanda, com o caso de uso roteirizado, e
 *     confere o commit do offset e a DLT pelo broker, e não por mocks.
 *
 * Spec: Offset confirmado manualmente só depois da persistência; Erro permanente vai para a DLT com o motivo; Erro
 *     transitório é tentado de novo com backoff exponencial e jitter
 * Enunciado: O que construir → Ingestão (input via Kafka)
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

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionErrorHandlerFactory.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionErrorHandlerFactory.kt:1)

```kotlin
/*
 * L18 IngestionErrorHandlerFactory: monta o error handler do container sem expô-lo como bean, porque o Spring Boot
 *     aplicaria um bean de error handler também ao container do consumer do kit (add-transaction-ingestion design
 *     D8).
 * L23-L28 newErrorHandler: só `TransientStorageException` é tentada de novo (`defaultFalse`); `setCommitRecovered`
 *     porque em commit manual o handler confirma o offset depois que a DLT aceita o registro
 *     (add-transaction-ingestion design D4).
 *
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionRecoverer.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionRecoverer.kt:1)

```kotlin
/*
 * L22 IngestionRecoverer: decide o destino de um registro de que o Spring desistiu: dependência fora do ar pausa o
 *     consumer e mantém o registro; todo o resto vai para a DLT. Assim evento bom nunca vai para a DLT por culpa do
 *     DynamoDB (add-transaction-ingestion design D4).
 * L29-L43 accept: a pausa é pedida antes de lançar `ListenerPausedException`, para o Spring devolver o registro sem
 *     commit; o descarte para a DLT é logado só com o tipo da causa, porque a mensagem pode conter trecho do
 *     payload, e o motivo completo já vai nos headers da DLT. O `dlq` só é contado depois de a DLT aceitar o
 *     registro, porque sem a publicação o registro não tem desfecho.
 * L45-L46 Throwable.isDependencyFailure: percorre a cadeia de causas porque o Spring embrulha a exceção do listener
 *     em `ListenerExecutionFailedException`.
 *
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionEventListener.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionEventListener.kt:1)

```kotlin
/*
 * L20 INGESTION_LISTENER_ID: id fixo, para a configuração do error handler achar o container e pausá-lo.
 * L23 TransactionEventListener: ponto de entrada do tópico de transações: lê o evento, chama o caso de uso e só
 *     então confirma o offset (at-least-once, add-transaction-ingestion design D2).
 * L36-L44 consume: o offset é confirmado depois de `processTransaction` retornar. O desfecho é contado na métrica, e
 *     duplicado ou mais antigo, que são resultado esperado neste fluxo, saem em `DEBUG` com o `accountId` mascarado,
 *     para não inundar o log em alto volume.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionEventMapper.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionEventMapper.kt:1)

```kotlin
/*
 * L25 TransactionEventMapper: traduz o JSON do enunciado para o evento de domínio no adapter, para o domínio
 *     continuar sem Jackson (Art. 1).
 * L29-L36 toTransaction: qualquer falha de parse ou de invariante do domínio vira
 *     `MalformedTransactionEventException`, um erro permanente que vai para a DLT em vez de travar o consumo.
 * L38-L39 safeCause: conserva a categoria da falha sem mensagens de terceiros que podem ecoar saldo, titular ou
 *     payload nos logs e nos headers da DLT (add-observability design D4).
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionIngestionConfig.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionIngestionConfig.kt:1)

```kotlin
/*
 * L47 TRANSACTION_INGESTION_CONTAINER_FACTORY: nome do container exclusivo do listener de transações, referenciado
 *     pelo `@KafkaListener`.
 * L49 SHUTDOWN_TIMEOUT: fica acima do pior caso de bloqueio de um registro, para o encerramento terminar o registro
 *     em andamento (add-transaction-ingestion design D9).
 * L50 PAUSE_SCHEDULER_THREAD_NAME_PREFIX: prefixo que identifica nos logs a thread que retoma o consumer após a
 *     pausa.
 * L54 TransactionIngestionConfig: reúne a infraestrutura do consumer de transações, separada do consumer do kit
 *     (add-transaction-ingestion design D8).
 * L60-L61 ingestionResumeScheduler: agenda a retomada da pausa; é encerrado junto com o contexto, o que cancela a
 *     retomada pendente.
 * L64-L67 ingestionPauseService: acha o container pelo id no registry e usa o agendador para retomá-lo, em vez de
 *     código próprio de pausa (Art. 10).
 * L70-L78 ingestionListenerPause: a pausa pelo container vira bean para a fábrica do error handler depender só do
 *     recoverer, e não de quatro colaboradores (Art. 3).
 * L81-L85 transactionDeadLetterRecoverer: publica na DLT preservando os headers do registro, inclusive o
 *     `traceparent`, para o evento descartado continuar correlacionável.
 * L88-L92 ingestionRecoverer: junta a decisão entre DLT e pausa com a contagem do `dlq` num só bean.
 * L95-L109 transactionIngestionContainerFactory: container próprio deste listener: o commit manual global quebraria
 *     o consumer do kit, que não confirma offset. `enable.auto.commit=false` vai nas propriedades do container, e
 *     não numa cópia do consumer factory, para não descartar o que o Boot registra nele (add-transaction-ingestion
 *     design D8). A observação e o `ObservationRegistry` ficam só aqui: sem o registry a observação seria um no-op
 *     silencioso, e a propriedade global ligaria o consumer do kit (add-observability design D2).
 *
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionOutcomeMetrics.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionOutcomeMetrics.kt:1)

```kotlin
/*
 * L21 TransactionOutcomeMetrics: único ponto que conta o desfecho de cada evento (applied, stale_ignored, duplicate,
 *     dlq). Fica no adapter, e não no caso de uso, porque Micrometer não entra na camada de aplicação (Art. 1). Os
 *     quatro contadores nascem no construtor, para a série existir com zero e a cardinalidade ficar fixa em quatro.
 * L29-L35 record: o `when` sobre a sealed `SnapshotSaveResult` é exaustivo e sem `else`: um novo resultado quebra a
 *     compilação até alguém decidir sua métrica.
 *
 * Enunciado: O que será avaliado → Production readiness
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

## [src/main/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceWriter.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceWriter.kt:1)

```kotlin
/*
 * L30-L34 STORED_VERSION_IS_OLDER: tradução para o armazenamento da ordem de SnapshotVersion (timestamp e, no
 *     empate, o id da transação como texto); mantenha as duas em sincronia. O DynamoDB avalia a condição dentro da
 *     própria gravação, então o compare-and-set é atômico e dispensa leitura prévia.
 * L37 DynamoDbBalanceWriter: grava o snapshot com um único PutItem condicional, para consumidores concorrentes nunca
 *     trocarem um saldo mais novo por um mais antigo.
 * L47-L53 putIfNewer: condição que falha é o resultado esperado para evento antigo ou duplicado (mensagens fora de
 *     ordem e repetidas), então vira resultado tipado, e não erro.
 * L55-L60 toResult: a recusa da condição devolve o item que a causou (`ReturnValuesOnConditionCheckFailure`), então
 *     distinguir duplicata de evento antigo não custa uma leitura nem quebra a atomicidade; sem o item, o desfecho
 *     conservador é `StaleIgnored`, sem erro. Enunciado: O que será avaliado → Tratamento de concorrência
 *
 * Enunciado: O que será avaliado → Tratamento de concorrência
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

## [src/main/kotlin/br/com/itau/challenge/balance/domain/model/AccountId.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/domain/model/AccountId.kt:1)

```kotlin
/*
 * L14 AccountId: identifica a conta cujo saldo mais recente é gravado. É um tipo próprio para nunca ser confundido
 *     com o id do titular ou o da transação, que também são UUIDs.
 * L15 toString: mostra só o primeiro grupo do UUID para a conta ir mascarada para o log
 *     (add-observability design D4).
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/domain/model/OwnerId.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/domain/model/OwnerId.kt:1)

```kotlin
/*
 * L14 OwnerId: identifica o titular da conta, devolvido ao cliente como `owner`. É um tipo próprio para nunca ser
 *     confundido com o id da conta ou o da transação, que também são UUIDs.
 * L15 toString: não revela o titular, dado pessoal que nunca pode ir para log, nem por interpolação de um objeto
 *     composto (add-observability design D4).
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/domain/model/SnapshotSaveResult.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/domain/model/SnapshotSaveResult.kt:1)

```kotlin
/*
 * L14 SnapshotSaveResult: evento antigo ou duplicado é um resultado esperado, e não um erro, porque este fluxo
 *     recebe mensagens repetidas e fora de ordem com frequência.
 * L19 DuplicateIgnored: o mesmo evento de novo (reentrega, retentativa ou queda entre gravar e confirmar) é
 *     diferente de um evento fora de ordem: a causa e o tratamento operacional diferem, e a métrica os separa
 *     (add-observability design D6).
 * L22-L25 refusedBecauseOf: a regra de "mesma versão é duplicata, versão armazenada mais nova é antigo" fica no
 *     domínio, e o armazenamento só entrega a versão que recusou a gravação.
 *
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/port/input/ProcessTransactionUseCase.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/port/input/ProcessTransactionUseCase.kt:1)

```kotlin
/*
 * L13 ProcessTransactionUseCase: o consumer depende deste port, e não do serviço, para a ingestão ser testada com um
 *     fake e a origem dos eventos poder mudar sem tocar o caso de uso; o contrato de retorno e de exceções fica no
 *     KDoc da assinatura.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/port/input/ProcessTransactionUseCase.kt:14](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/port/input/ProcessTransactionUseCase.kt:14)

```kotlin
/**
     * Grava o saldo trazido por [transaction] como snapshot da conta, sem recalculá-lo.
     *
     * @return o resultado da gravação: [SnapshotSaveResult.Applied], [SnapshotSaveResult.StaleIgnored] ou
     * [SnapshotSaveResult.DuplicateIgnored].
     * @throws br.com.itau.challenge.balance.port.output.TransientStorageException quando tentar de novo mais tarde
     * pode dar certo.
     * @throws br.com.itau.challenge.balance.port.output.StorageUnavailableException quando o armazenamento está
     * indisponível no momento.
     * @throws br.com.itau.challenge.balance.port.output.PermanentStorageException quando tentar de novo não ajuda.
     */
```

## [src/main/kotlin/br/com/itau/challenge/balance/port/output/BalanceRepository.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/port/output/BalanceRepository.kt:1)

```kotlin
/*
 * L13 BalanceRepository: grava o snapshot de saldo mais recente de cada conta, para evento duplicado, fora de ordem
 *     ou concorrente nunca sobrescrever um saldo mais novo. O contrato de `saveIfNewer` (retorno e exceções)
 *     continua no KDoc da assinatura.
 *
 * Enunciado: O que será avaliado → Tratamento de concorrência
 */
```

## [src/main/kotlin/br/com/itau/challenge/balance/port/output/BalanceRepository.kt:14](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/main/kotlin/br/com/itau/challenge/balance/port/output/BalanceRepository.kt:14)

```kotlin
/**
     * Grava [snapshot] somente se a conta ainda não tem snapshot ou se a versão dele é mais nova que a gravada,
     * conforme `SnapshotVersion`; a verificação e a gravação são uma única operação atômica.
     *
     * @return [SnapshotSaveResult.Applied] quando gravou, [SnapshotSaveResult.DuplicateIgnored] quando o snapshot
     * gravado tem a mesma versão do oferecido (o mesmo evento de novo), e [SnapshotSaveResult.StaleIgnored] quando
     * ele é mais novo (evento fora de ordem).
     * @throws TransientStorageException quando tentar de novo mais tarde pode dar certo.
     * @throws PermanentStorageException quando tentar de novo não ajuda.
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

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionRecovererTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/IngestionRecovererTest.kt:1)

```kotlin
/*
 * L27 IngestionRecovererTest: cobre a decisão entre DLT e pausa para cada tipo de falha, incluindo a falha não
 *     classificada, que vai para a DLT.
 *
 * Spec: Erro permanente vai para a DLT com o motivo; Dependência indisponível pausa as partições em vez de
 *     descartar; Eventos contados por resultado
 * Enunciado: O que será avaliado → Resiliência
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionEventListenerTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionEventListenerTest.kt:1)

```kotlin
/*
 * L40 TransactionEventListenerTest: o `Acknowledgment` falso registra a ordem dos passos para provar que o offset só
 *     é confirmado depois da gravação. Os testes de `DECLINED` e de valor diferente do saldo usam o caso de uso
 *     real, porque só se provam a partir do JSON.
 * L159-L166 capturedLogsOf: anexa um appender ao logger do listener para provar que duplicado e antigo não geram
 *     linha de nível INFO ou acima, o que um teste de saída do console não distingue de outros logs.
 *
 * Spec: Offset confirmado manualmente só depois da persistência; Transação processada vira snapshot de saldo; O
 *     saldo do evento é mantido como recebido; Eventos contados por resultado; Resultado esperado não polui o log
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionEventMapperTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionEventMapperTest.kt:1)

```kotlin
/*
 * L35 TransactionEventMapperTest: confere o mapeamento com o `JsonMapper` real, incluindo parse, UUID, moeda,
 *     escala, timestamp e campos ausentes. Os casos de privacidade incluem escala inválida e titular usado como
 *     moeda ou UUID inválido, para cobrir causas que ecoam entrada.
 * L119-L124 assertNoSensitiveData: imprime o stack trace inteiro, que é tudo o que um log pode conter da falha, e
 *     confere que nem o titular nem o saldo do evento estão nele.
 *
 * Spec: Evento do tópico é lido conforme o contrato do enunciado; Erro permanente vai para a DLT com o motivo; Dados
 *     pessoais e payload nunca vão para o log
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionOutcomeMetricsTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/input/kafka/TransactionOutcomeMetricsTest.kt:1)

```kotlin
/*
 * L19 TransactionOutcomeMetricsTest: usa um registry em memória para provar que cada desfecho incrementa só a sua
 *     série, e que a cardinalidade fica fixa em quatro mesmo com muitos eventos.
 *
 * Spec: Eventos contados por resultado; Baixa cardinalidade
 * Enunciado: O que será avaliado → Production readiness
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

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderCredentialsTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderCredentialsTest.kt:1)

```kotlin
/*
 * L25 DynamoDbBalanceProviderCredentialsTest: um provider de credenciais que falha reproduz o ambiente sem
 *     credenciais: a consulta tem de falhar de forma explícita e permanente, sem pôr valor de credencial na
 *     mensagem.
 *
 * Spec: Configuração só por variável de ambiente
 * Enunciado: O que será avaliado → Tratamento de cenários adversos
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderTest.kt:1)

```kotlin
/*
 * L31 DynamoDbBalanceProviderTest: a leitura é o que a API expõe ao cliente, então um cliente simulado permite
 *     conferir, sem infraestrutura, que a consulta é fortemente consistente (nunca um saldo defasado) e que falha de
 *     leitura é classificada, e não engolida.
 *
 * Spec: Leitura do snapshot por conta; Classificação das falhas do DynamoDB
 * Enunciado: O que construir → Exposição (API REST)
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceWriterTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceWriterTest.kt:1)

```kotlin
/*
 * L49 DynamoDbBalanceWriterTest: a requisição condicional é o contrato com o DynamoDB: um cliente simulado permite
 *     conferir a `ConditionExpression` e a classificação das falhas sem infraestrutura, enquanto o comportamento
 *     real fica no teste de integração.
 * L255-L258 refusedBy: faz o cliente simulado recusar a condição devolvendo o item que bloqueou a gravação, como o
 *     DynamoDB faz com `ReturnValuesOnConditionCheckFailure`.
 *
 * Spec: Gravação condicional do snapshot; Classificação das falhas do DynamoDB
 * Enunciado: O que será avaliado → Tratamento de concorrência
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

## [src/test/kotlin/br/com/itau/challenge/balance/application/ProcessTransactionServiceTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/application/ProcessTransactionServiceTest.kt:1)

```kotlin
/*
 * L53 ProcessTransactionServiceTest: o fake do `BalanceRepository` implementa o mesmo port, e os testes de falha
 *     confirmam que a exceção chega intacta ao chamador.
 *
 * Spec: Transação processada vira snapshot de saldo
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/domain/model/SafeTextRepresentationTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/domain/model/SafeTextRepresentationTest.kt:1)

```kotlin
/*
 * L33 SafeTextRepresentationTest: prova que interpolar um objeto de domínio em uma mensagem não vaza o titular nem a
 *     conta inteira, porque o `toString` é a única forma de um dado pessoal chegar a um log sem ninguém o pedir.
 *
 * Spec: Tipos de domínio têm representação textual segura
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/balance/domain/model/SnapshotSaveResultTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/balance/domain/model/SnapshotSaveResultTest.kt:1)

```kotlin
/*
 * L24 SnapshotSaveResultTest: fixa a regra de recusa no domínio: só a mesma versão é duplicata, e o empate de
 *     timestamp com id de transação menor é evento antigo.
 *
 * Spec: Gravação condicional do snapshot
 * Enunciado: O que será avaliado → Tratamento de concorrência
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

## [src/test/kotlin/br/com/itau/challenge/observability/ComposeFileTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/ComposeFileTest.kt:1)

```kotlin
/*
 * L23 ComposeFileTest: lê o `docker-compose.yml` e o `application.yaml` com o YAML do Boot, para comparar o
 *     `stop_grace_period` com o tempo de encerramento gracioso configurado, que estão em arquivos diferentes.
 *
 * Spec: Imagens com versão fixa; JVM ajustada para contêiner; Encerramento por SIGTERM; Serviço app sobe com um
 *     comando
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/CorrelationLoggingTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/CorrelationLoggingTest.kt:1)

```kotlin
/*
 * L50 CorrelationLoggingTest: usa o log real em JSON, lido linha a linha, com o tracing ligado, para provar a
 *     correlação ponta a ponta: o `traceId` do log é o do corpo de erro, vem do `traceparent` e não vaza para a
 *     requisição seguinte.
 * L102-L114 should not write a trace id when the log is outside any request: emite o log da própria thread do teste,
 *     depois de uma requisição, porque a linha de startup depende da ordem de execução e do cache de contexto.
 *
 * Spec: Logs em JSON com traceId; traceId propagado do HTTP; Dados pessoais e payload nunca vão para o log
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/DockerfileTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/DockerfileTest.kt:1)

```kotlin
/*
 * L21 DockerfileTest: lê o `Dockerfile` do disco, porque o ambiente de desenvolvimento pode não ter Docker; é a
 *     proteção possível sem construir a imagem, e a validação real (`id -u`, `SIGTERM`) fica para o ambiente com
 *     Docker.
 *
 * Spec: Imagens com versão fixa; Imagem multi-stage mínima; Processo sem privilégio de root; JVM ajustada para
 *     contêiner; Encerramento por SIGTERM
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/HealthProbesTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/HealthProbesTest.kt:1)

```kotlin
/*
 * L32 HealthProbesTest: o contexto sobe sem DynamoDB nem Kafka, então o `liveness` e o `readiness` UP já provam que
 *     não dependem deles; o grupo `readiness` é conferido pelo bean real, e não por uma propriedade que poderia nem
 *     existir.
 *
 * Spec: Liveness separado do readiness; Readiness não depende do DynamoDB; Detalhes de saúde não são expostos
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/IngestionTopicsScriptTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/IngestionTopicsScriptTest.kt:1)

```kotlin
/*
 * L18 IngestionTopicsScriptTest: garante que os nomes dos tópicos e as partições vivem num só script, usado pelo
 *     seed e pelo `Makefile`, para o conhecimento não ficar duplicado.
 *
 * Spec: Serviço app sobe com um comando
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/KafkaObservationTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/KafkaObservationTest.kt:1)

```kotlin
/*
 * L26 KafkaObservationTest: a observação ligada sem o `ObservationRegistry` da aplicação é um no-op silencioso,
 *     então o teste confere os dois, e que o consumer do `hello` continua sem observação.
 *
 * Spec: traceId propagado do Kafka
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/ManagementPortTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/ManagementPortTest.kt:1)

```kotlin
/*
 * L29 ManagementPortTest: sobe o servidor de verdade, com a API e o gerenciamento em portas aleatórias distintas,
 *     porque o MockMvc não serve a porta de gerenciamento separada.
 *
 * Spec: Endpoint de métricas na porta de gerenciamento
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/ManagementPropertiesTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/ManagementPropertiesTest.kt:1)

```kotlin
/*
 * L15 ManagementPropertiesTest: lê o `application.yaml` de verdade, para os padrões documentados e os nomes das
 *     variáveis de ambiente serem os que o código usa.
 *
 * Spec: Endpoint de métricas na porta de gerenciamento; Configuração só por variável de ambiente
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/NoCredentialsInCodeTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/NoCredentialsInCodeTest.kt:1)

```kotlin
/*
 * L16 NoCredentialsInCodeTest: varre o código de produção atrás dos tipos de credencial estática do SDK, porque a
 *     regra é ausência de credencial escrita, e só uma varredura prova uma ausência.
 *
 * Spec: Configuração só por variável de ambiente
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/ReadinessOnShutdownTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/ReadinessOnShutdownTest.kt:1)

```kotlin
/*
 * L21 ReadinessOnShutdownTest: o `REFUSING_TRAFFIC` é publicado pelo contexto de servidor web ao fechar, então o
 *     teste sobe um contexto servlet de verdade; num contexto sem web o evento não existe. As portas aleatórias
 *     são argumentos de maior precedência que o application.yaml, para não disputar a porta 8082 com outro teste.
 *
 * Spec: Readiness recusa tráfego no encerramento
 * Enunciado: O que será avaliado → Production readiness
 */
```

## [src/test/kotlin/br/com/itau/challenge/observability/ServiceMetricsTest.kt:1](/Users/lincolngadea/Developer/desafio-itau/desafio-tecnico-consulta-saldo/src/test/kotlin/br/com/itau/challenge/observability/ServiceMetricsTest.kt:1)

```kotlin
/*
 * L43 ServiceMetricsTest: sobe o contexto real com métricas ligadas e conta as amostras por status e por rota; só os
 *     timers HTTP são limpos entre os testes, porque limpar o registry apagaria os contadores e os estados
 *     pré-registrados.
 *
 * Spec: Métricas HTTP por status; Baixa cardinalidade; Endpoint de métricas na porta de gerenciamento
 * Enunciado: O que será avaliado → Production readiness
 */
```
