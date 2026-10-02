## Context

- O serviço tem ingestão (consumer Kafka com commit manual, DLT, retry, pausa e circuit breaker de escrita) e consulta (`GET /balances/{accountId}`, com circuit breaker de leitura e erro `problem+json` com `traceId`). Os `design.md` das duas changes deixaram para esta, como Non-Goals, métricas, logs estruturados e alertas.
- **Logs hoje:** texto livre (padrão do Boot), com `logging.pattern.correlation` mostrando um `traceId` que só existe em requisições HTTP, vindo do `TraceIdFilter`. O consumer não tem `traceId`. O listener loga `accountId` por inteiro em cada evento duplicado ou antigo, e `translatingSdkFailures` põe o `accountId` por inteiro na mensagem das exceções, que o Spring Kafka copia para os headers da DLT e para os logs.
- **Métricas hoje:** nenhuma. O Actuator não está no build.
- **Health hoje:** nenhum.
- **Contêiner hoje:** `Dockerfile` multi-stage, mas com `eclipse-temurin:21-jdk` e `:21-jre` (tags flutuantes, contra a regra do `project.md`), `ENTRYPOINT ["java","-jar","app.jar"]` como root e sem flags de memória. O compose já tem o serviço `app`, com `stop_grace_period: 40s`, mas ele só tem `depends_on` simples (não espera os seeds terminarem) e os tópicos de transações só eram criados por `make kafka-topics-ingestion`, passo manual. O encerramento gracioso já existe no código: `server.shutdown` gracioso (padrão do Boot), `spring.lifecycle.timeout-per-shutdown-phase: 30s` e `shutdownTimeout` de 30 s no container do consumer.
- **Credenciais:** `DynamoDbConfig` escreve `AwsBasicCredentials.create("local", "local")` no código, contra o 12-Factor.
- O ambiente onde esta sessão roda **não tem Docker**. O build da imagem, o `SIGTERM` real e a stack subindo precisam de uma máquina com Docker.
- Boot 4.1.0, Spring Kafka 4.1.0, Micrometer 1.17.0 e Micrometer Tracing 1.7.0 (BOM do Boot). No Boot 4 o Actuator foi modularizado (`spring-boot-micrometer-metrics`, `spring-boot-micrometer-tracing-*`, `spring-boot-health`).

## Goals / Non-Goals

**Goals:**
- Log JSON com correlação ponta a ponta (HTTP e Kafka) e dado sensível fora do log por construção.
- As cinco métricas pedidas, cada uma por solução pronta, com baixa cardinalidade.
- Probes separadas, com a dependência do DynamoDB justificada.
- Imagem segura e ajustada para contêiner, encerramento por `SIGTERM` e configuração só por ambiente.
- `make up` sobe tudo funcional.

**Non-Goals:**
- Prometheus, Grafana, coletor OTLP e exportação de traces: o serviço os expõe e emite, mas a infra de observabilidade não faz parte do kit do desafio.
- Alertas, dashboards, SLOs e orçamento de erro: ficam descritos em D5 e R4 como evolução.
- Autenticação e rede segura do endpoint de gerenciamento: a porta própria é a mitigação de projeto (D7), sem autenticação.
- Reprocessamento da DLT e métricas do broker.
- Mudar o `hello`: o consumer do kit continua intacto.

## Decisions

### D1. Uma só noção de correlação: o `traceId` (W3C `traceparent`)

- **Pedido:** "`traceId`/`correlationId` propagado do Kafka (header) e do HTTP". Dois nomes para o mesmo papel violariam o Art. 8 ("um conceito, um nome"). **Decidido:** existe **um** identificador, o `traceId`; não há `correlationId`.
- **Formato e header:** W3C Trace Context, header `traceparent`, tanto no HTTP quanto no Kafka. É o padrão que o Spring Kafka, o Spring MVC e qualquer autorizador moderno propagam sozinhos.
- **Ambiguidade do enunciado:** o payload e o tópico do enunciado não definem nenhum header de correlação; o autorizador está fora do escopo. Decidido que o serviço **aceita** `traceparent` se vier e **gera** um se não vier, nunca falha por isso. Se o autorizador usar outro header, a tradução é um ponto de extensão (R6), não implementado agora (YAGNI).

### D2. Tracing: Micrometer Tracing com a ponte OpenTelemetry, sem exportador

- **Escolha:** `spring-boot-micrometer-tracing-opentelemetry` (auto-configuração do Boot 4.1.0) e `io.micrometer:micrometer-tracing-bridge-otel` (1.7.0, de 08/06/2026), que traz só o SDK de traces e **nenhum exportador**. O resultado, sem escrever código: o filtro de observação do Spring MVC lê o `traceparent` (ou cria um novo), o `traceId` entra no MDC, e o listener do Spring Kafka, com a observação ligada, faz o mesmo com o header do registro.
- **Substitui o `TraceIdFilter`** da `add-balance-query-api`, como o `design.md` daquela change (D5) previa. O `ApiExceptionHandler` passa a ler o `traceId` do `Tracer` (`currentSpan().context().traceId()`), e o atributo e a chave de MDC próprios saem. O comportamento observável (spec `balance-query-api`: `traceparent` válido reaproveitado, inválido ignorado, id novo de 32 dígitos, MDC limpo ao fim) **não muda**; os testes do filtro viram testes de comportamento sobre a pilha real.
- **Observação só no container de transações:** `containerProperties.isObservationEnabled = true` no `ConcurrentKafkaListenerContainerFactory` próprio, e **não** `spring.kafka.listener.observation-enabled`, porque a propriedade global afetaria o consumer `hello` (a mesma razão do `ack-mode` na D8 da `add-transaction-ingestion`).
- **DLT:** o `DeadLetterPublishingRecoverer` copia os headers do registro original, então o `traceparent` chega à DLT e o reprocessamento manual da DLT continua correlacionável. Um teste de integração prova.
- **Amostragem:** não há exportador, então a probabilidade de amostragem não muda nada observável, mas o `traceId` e o MDC existem mesmo para spans não amostrados. Fica no padrão do Boot.
- **Alternativas avaliadas:**
  - **Manter o filtro e escrever um `RecordInterceptor` para o Kafka (código próprio).** Funcionaria, mas reimplementa o que o Micrometer Tracing e o Spring Kafka já fazem, e o formato do `traceparent` e a regra de ids inválidos passariam a ser nossos (Art. 10).
  - **Brave (`spring-boot-micrometer-tracing-brave`).** Equivalente, mas a propagação padrão é B3 e exigiria configurar W3C; o OTel é o padrão e já é W3C.
  - **`spring-boot-starter-opentelemetry`.** Traz o exportador OTLP de traces e de métricas, que tentaria conectar em `localhost:4318` e logaria falha de conexão sem coletor; rejeitado por trazer o que não usamos.
- **Pattern (Art. 9):** Context Propagation / Correlation Identifier. Não há classe própria.

### D3. Logs estruturados: formato nativo do Spring Boot

- **Escolha:** `logging.structured.format.console=${LOG_FORMAT:logstash}` (Boot 3.4+, sem dependência nova), por padrão em todo ambiente, no mesmo estilo `${ENV:default}` das demais propriedades. O MDC (`traceId`, `spanId`) vai como campos do JSON, e o stack trace como campo da mesma linha.
- **Por que `logstash` e não `ecs`:** o campo se chama literalmente `traceId`, como o pedido e o corpo de erro; o ECS o aninharia em `trace.id`.
- **Alternativas descartadas:** `logstash-logback-encoder` (dependência nova sem ganho: o formato nativo cobre); `logback.xml` próprio (código de configuração a manter).
- **Efeitos:** `logging.pattern.correlation` deixa de ter uso e sai do `application.yaml`.
- **Consequência para os testes:** o teste de log existente (`ApiExceptionHandlerLoggingTest`) passa a ler JSON; ele continua afirmando a mesma coisa (a causa e o `traceId` na mesma linha).

### D4. Dados sensíveis: política por construção, e não por disciplina

- **Política:**
  | Dado | Nos logs |
  |-|-|
  | `owner` | **nunca**, em nenhum nível |
  | payload do evento e saldo | **nunca** |
  | `accountId` | mascarado: só o primeiro grupo do UUID (`5b19c8b6`) |
  | `transactionId` | por inteiro (não identifica pessoa e é a chave para achar o evento) |
  | credenciais AWS | nunca |
- **Por construção:** `OwnerId.toString()` não revela nada (`OwnerId(****)`), e `AccountId.toString()` mostra só o primeiro grupo. Assim, interpolar um `ProcessedTransaction` ou um `BalanceSnapshot` (data classes, que interpolam os campos pelo `toString`) não vaza. O valor real continua em `value`, que a serialização e o armazenamento usam. A política vive nos value objects do domínio (Art. 3, sem obsessão por primitivos), sem dependência de framework e sem um helper de mascaramento espalhado.
- **Onde o `accountId` entrava em log hoje:** o listener (a mensagem de evento antigo) e `translatingSdkFailures` (a mensagem da exceção, que também vai para os headers da DLT). Os dois passam a usar o `toString` mascarado.
- **Risco real, a medir:** a mensagem de uma `JacksonException` pode trazer um trecho do payload (a localização "source"). O `MalformedTransactionEventException` já tem mensagem fixa, mas a **causa** vai na cadeia do stack trace que o `DefaultErrorHandler` do Spring Kafka registra. Um teste unitário do mapper e um teste de integração com um `owner` sentinela provam que nenhuma linha o contém; **resultado do spike (tarefa 3b.1):** o `JsonMapper` padrão do Jackson 3 não inclui a fonte do JSON, então nenhuma feature precisa ser desligada. A revisão independente revelou que mensagens de validação ainda ecoavam saldo com escala inválida, moeda arbitrária e UUID inválido. O mapper substitui a causa por um diagnóstico seguro com o nome do tipo original, sem sua mensagem nem sua cadeia de causas; a mensagem externa conserva a distinção entre parse e rejeição pelo domínio. Testes com saldo inválido e titular sentinela nesses campos protegem os logs e os headers da DLT. O diagnóstico perde o detalhe original da validação para cumprir a política de privacidade.
- **Nível `DEBUG` para o esperado:** duplicado e antigo são o fluxo normal deste sistema (`add-transaction-ingestion` D1); em alto volume, um `INFO` por evento inundaria o log. Quem quer saber quantos são lê a métrica (D5).
- **Fora do alcance da política:** o corpo de erro `404` ecoa o `accountId` que o próprio cliente enviou; é a resposta ao cliente, e não log.

### D5. Métricas: soluções prontas, com um contador próprio

- **Princípio:** só o que é regra do serviço é código próprio. O resto é solução pronta.
- **Tabela:**
  | Pedido | Solução | Nome da métrica |
  |-|-|-|
  | eventos por resultado | contador próprio, no adapter Kafka | `balance.transactions.processed{result=applied\|stale_ignored\|duplicate\|dlq}` |
  | latência de processamento | observação do Spring Kafka (D2) | `spring.kafka.listener{spring.kafka.listener.id=balance-transaction-ingestion}` |
  | consumer lag | métrica do cliente Kafka, ligada pelo Boot | `kafka.consumer.fetch.manager.records.lag.max{client.id}` |
  | estado do circuit breaker | `resilience4j-micrometer` | `resilience4j.circuitbreaker.state{name}`, `.calls`, `.failure.rate` |
  | HTTP por status | observação do Spring MVC | `http.server.requests{status,uri,method,outcome}` |
- **Contador de resultado no adapter, e não no caso de uso:** `application` não importa Micrometer (Art. 1). O `TransactionEventListener` já recebe o `SnapshotSaveResult`, então um `when` exaustivo sobre a sealed incrementa o contador, e o `IngestionRecoverer` incrementa `dlq` depois de a DLT aceitar o registro. Uma classe pequena do adapter (`TransactionOutcomeMetrics`) concentra os nomes. Um novo subtipo de `SnapshotSaveResult` quebra a compilação até decidir sua métrica (Art. 2, OCP).
- **`dlq` só depois da DLT aceitar:** se a publicação na DLT falhar, o registro não tem desfecho e não é contado, como na regra da spec.
- **Estado do circuit breaker:** o `resilience4j-micrometer` liga a um `CircuitBreakerRegistry`. Hoje os circuitos são criados com `CircuitBreaker.of(...)`, fora de registry. `BalanceCircuitBreakerConfig` passa a criar um registry e a obter os dois circuitos dele, e a registrar as métricas com `TaggedCircuitBreakerMetrics`. Os nomes dos circuitos (`balance-storage`, `balance-storage-read`) e a definição única de falha não mudam.
- **Baixa cardinalidade (regra de ouro):** nenhuma tag recebe `accountId`, `transactionId` nem `owner`. A rota HTTP é o padrão (`/balances/{accountId}`); rotas inexistentes caem num valor único. Um teste com várias contas confere que o número de séries não cresce.
- **Exposição:** Prometheus (`/actuator/prometheus`), o formato de fato; custo: `micrometer-registry-prometheus`, gerenciada pelo BOM.
- **Consumer lag: limite conhecido.** O lag do cliente Kafka é calculado quando ele busca registros (**medido na tarefa 6.5:** logo após o consumo o Boot publica `records.lag.max` por `client.id`, o maior lag entre as partições do consumer, e a série por partição só é criada mais tarde pelo cliente); com as partições **pausadas** (circuito aberto, D6 da `add-transaction-ingestion`), o cliente não busca, e o valor pode ficar defasado justo na indisponibilidade. É o limite da solução pronta. **Alternativa avaliada:** um gauge próprio que pergunta ao broker (`AdminClient`: fim do log menos offset confirmado), preciso mesmo com o consumer pausado, ao custo de código próprio, uma chamada ao broker por coleta e mais uma permissão. Descartada agora; o risco R4 fixa o sinal alternativo (estado do circuito aberto + registro parado) e a validação em Docker na tarefa 8.
- **Fora de escopo:** alertas e dashboards (descritos em R4), e métricas do DynamoDB (o cliente da AWS tem métricas próprias do SDK, não habilitadas: custo e cardinalidade).

### D6. Resultado `DuplicateIgnored`: ambiguidade resolvida, com mudança de contrato

- **Ambiguidade do enunciado:** o enunciado pede tratar "a mesma mensagem duas vezes" e avalia isso nos testes, mas não pede distinguir duplicata de evento fora de ordem. O **pedido** desta change pede os dois rótulos (`stale_ignored` e `duplicate`) em métricas. Hoje o domínio os une em `StaleIgnored`. **Decidido:** separar, porque duplicata (retentativa, reentrega do broker, queda entre gravar e confirmar) e fora de ordem (relógio ou concorrência) têm causas e tratamentos operacionais diferentes, e uma taxa de duplicatas alta aponta para o broker ou para o autorizador, enquanto uma de eventos antigos aponta para ordenação.
- **Como, sem custar uma leitura:** o `PutItem` já é condicional. Com `ReturnValuesOnConditionCheckFailure=ALL_OLD`, o `ConditionalCheckFailedException` traz o item que impediu a gravação (`exception.item()`). O writer o lê como `SnapshotVersion` (reaproveitando `BalanceItem`) e o **domínio** decide: mesma versão é `DuplicateIgnored`, versão armazenada mais nova é `StaleIgnored`. A regra mora no domínio (Art. 1), e a atomicidade e o custo da gravação não mudam.
- **Contrato do port:** `SnapshotSaveResult` ganha `DuplicateIgnored` (**BREAKING**, interno). Os quatro requisitos que citam `StaleIgnored` são alterados nesta change (`balance-snapshot-storage`, `transaction-processing`, `transaction-ingestion`, `storage-circuit-breaker`). Todo `when` sobre a sealed continua exaustivo e sem `else`.
- **Degradação segura (R2):** se o armazenamento não devolver o item (comportamento do DynamoDB Local ou futura mudança), o resultado é `StaleIgnored`, sem erro: o evento já foi corretamente ignorado, só o rótulo da métrica fica menos preciso. Um item devolvido e malformado é `PermanentStorageException`, como a leitura.
- **Alternativa descartada:** distinguir no listener, guardando eventos vistos. Seria estado novo e uma segunda fonte de verdade, contra a decisão de idempotência por gravação condicional (`add-transaction-ingestion` D2).
- **Pattern (Art. 9):** Idempotent Receiver, já aplicado; o resultado tipado (Result) ganha um caso.

### D7. Health: liveness, readiness e a dependência do DynamoDB

- **Probes do Actuator** (`management.endpoint.health.probes.enabled=true`), na **porta de gerenciamento** (`MANAGEMENT_PORT`, padrão `8082`; o `8081` do host já é o Redpanda Console). Só `health` e `prometheus` expostos; `show-details=never`. Porta separada para a API pública (`8080`) não expor métricas nem probes, sem autenticação nova (Non-Goals).
- **`liveness`:** só o estado de vida do processo. Uma probe de vida que falha leva a um **reinício**, e reiniciar não conserta o DynamoDB nem o Kafka; ligar vida a dependências causaria reinício em cascata na indisponibilidade.
- **`readiness`: não depende do DynamoDB (nem do Kafka). Decisão e justificativa:**
  1. As instâncias compartilham o **mesmo** DynamoDB. Se o DynamoDB cai, um `readiness` que o verifique derrubaria **todas** as instâncias do balanceador ao mesmo tempo, e o cliente passaria a receber recusa de conexão em vez da resposta controlada que o serviço já produz (`503` com `Retry-After`, e circuito aberto falhando em milissegundos, `add-balance-query-api` D6 e D7). A degradação controlada **é** o comportamento desejado; tirar o tráfego a piora.
  2. Não há para onde redirecionar: nenhuma instância é "mais saudável" que outra em relação à dependência compartilhada.
  3. O circuito aberto e a métrica de estado (D5) já sinalizam a dependência, para alerta, sem acoplar ao roteamento de tráfego.
  4. Na subida, a corrida com o seed (tabela ainda não criada) não é resolvida por `readiness`, e sim por ordem de subida no compose (D10).
  5. O consumer Kafka também não entra: ele pausa e retoma sozinho (`add-transaction-ingestion` D6), e a falta dele não impede atender leituras.
- **O que o `readiness` cobre:** o estado de disponibilidade da aplicação, que o Spring muda para `REFUSING_TRAFFIC` ao receber o encerramento, **antes** de o servidor web parar (o `readiness` vira `503`, o `liveness` segue `UP`). Isso dá ao balanceador o tempo de tirar a instância enquanto as requisições em andamento terminam (D8).
- **Alternativa avaliada:** incluir um indicador de DynamoDB no `readiness` (ou num grupo `dependencies` informativo). Descartado no `readiness` pelas razões acima; um indicador informativo fora das probes foi deixado de fora (YAGNI), porque o estado do circuito já cumpre esse papel.
- **Pattern:** Health Check API (liveness e readiness do Kubernetes).

### D8. Contêiner: `Dockerfile`, JVM, `SIGTERM`

- **Tags fixas (corrige a divergência do `Dockerfile` com a regra do `project.md`):** `eclipse-temurin:21.0.12_8-jdk-noble` (build) e `eclipse-temurin:21.0.12_8-jre-noble` (execução), de 18/09/2026 no Docker Hub. Uma tag completa é reproduzível; `21-jre` muda sem aviso.
- **Multi-stage mantido** (`base`, `test`, `builder`, `runtime`): o estágio `runtime` só tem o JRE e o jar. Sem camada de jar extraída (`layertools`): a imagem é reconstruída por inteiro a cada mudança, o que é aceitável para este serviço; evolução registrada.
- **Usuário não root com UID numérico** (`10001`), para `runAsNonRoot` de um orquestrador poder verificar; o diretório de trabalho pertence a ele.
- **Flags de JVM por `JAVA_TOOL_OPTIONS`** (lida pela própria JVM, sobrescrevível por ambiente sem reconstruir): `-XX:MaxRAMPercentage=70.0` (o padrão de 25% desperdiça o contêiner), `-XX:+ExitOnOutOfMemoryError` (um processo sem memória sai e o orquestrador o reinicia, em vez de ficar meio vivo), e o suporte a contêiner do JDK 21 já é padrão. O serviço `app` do compose ganha `mem_limit: 768m` para as flags terem sobre o quê agir (70% do limite como heap, o resto para metaspace, threads e buffers do Kafka).
- **`SIGTERM`:** o `ENTRYPOINT` em forma exec (`["java","-jar","app.jar"]`) faz a JVM ser o PID 1 e receber o sinal direto; `STOPSIGNAL SIGTERM` explícito. Um `sh -c` no meio interceptaria o sinal. O Spring então encerra gracioso (**medido no contêiner, tarefa 8.6:** em 2026-10-01, um tópico de uma partição e grupos exclusivos isolaram a verificação; o DynamoDB foi brevemente pausado e os timeouts de escrita do contêiner de teste ampliados para 20/25 s, sem mudar a configuração entregue. Um thread dump confirmou o registro dentro de `DefaultDynamoDbClient.putItem` e `TransactionEventListener.consume` antes do sinal. Após `docker stop --timeout 40` e a retomada do banco, o snapshot foi lido com consistência forte, o grupo apresentou offset confirmado 1 para fim de log 1, lag 0 e nenhum membro ativo. A saída foi 143 em 3,257 s, sem OOM nem SIGKILL): o `readiness` recusa tráfego, o servidor web termina as requisições em andamento, o consumer termina o registro em andamento e sai do grupo (`add-transaction-ingestion` D9). Os tempos já são coerentes: `timeout-per-shutdown-phase` 30 s, `shutdownTimeout` do container 30 s e `stop_grace_period` 40 s; um teste estático impede que alguém suba o primeiro acima do último.
- **Healthcheck sem instalar `curl`:** a imagem `jre-noble` não traz `curl`, e instalá-lo aumenta a superfície. O healthcheck do compose usa o `bash` da imagem com `/dev/tcp` contra o `readiness`.
- **`.dockerignore`:** passa a excluir `.challenge` (o enunciado não pode sair do repositório nem entrar numa imagem), `openspec`, `docs` e `.claude`.
- **Alternativas descartadas:** `distroless` (sem shell, o que impede o healthcheck simples e o `id -u` de verificação); jlink de um runtime mínimo (complexidade sem necessidade); buildpacks (`bootBuildImage`) trocariam o `Dockerfile` do kit, que o desafio manda aproveitar.

### D9. 12-Factor: auditoria da configuração

- **Auditoria:** o `application.yaml` já usa `${ENV:default}` nas propriedades próprias e, pelo *relaxed binding* do Spring, **toda** propriedade (inclusive as literais, como `spring.kafka.consumer.auto-offset-reset`) pode ser sobrescrita por variável de ambiente sem alterar código. Os desvios reais são os que não passam pelo Spring:
  | Item | Hoje | Decisão |
  |-|-|-|
  | credenciais AWS | `AwsBasicCredentials.create("local","local")` no código | `DefaultCredentialsProvider` (variáveis `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY`) |
  | `SHUTDOWN_TIMEOUT` do container (30 s) | constante no código | mantido: é um teto de segurança coerente com o `timeout-per-shutdown-phase` (teste estático) |
  | backoff do retry de leitura (50 a 100 ms) | constante no código | mantido, não configurável (YAGNI, `add-balance-query-api` D6) |
  | endpoints e hosts | `${ENV:localhost}` | já em ambiente |
- **Credenciais:** é a única mudança. `DefaultCredentialsProvider` lê as variáveis de ambiente (e propriedades de sistema, perfis, IAM do contêiner). Sem credenciais, o SDK falha com `SdkClientException`, que o adapter já classifica como falha permanente (`500` genérico, sem valor de credencial no log). Efeito: o compose define `AWS_ACCESS_KEY_ID=local` e `AWS_SECRET_ACCESS_KEY=local` para o DynamoDB Local; as tarefas `Test` e `bootRun` do Gradle definem as mesmas variáveis; `./gradlew check` continua sem infraestrutura.
- **Custo:** quem roda `bootRun` fora do Gradle precisa das duas variáveis; está no `README.md`.

### D10. Compose e `make up`

- **Lacuna do template resolvida:** "o serviço `app` no compose não espera os seeds terminarem". Passa a ter `depends_on` com `condition: service_completed_successfully` para `dynamodb-seed` e `redpanda-seed`, e um healthcheck que consulta o `readiness`. Isso também resolve a corrida da subida que o `readiness` deliberadamente não resolve (D7).
- **Tópicos de ingestão criados pelo seed:** um script novo, `infra/redpanda/ingestion-topics.sh` (idempotente, 6 partições, `rpk`), é a **fonte única** dos dois nomes e das partições. O `redpanda-seed` o chama depois do `config.sh` e do `seed.sh`, e `make kafka-topics-ingestion` passa a chamar o mesmo script pelo serviço `redpanda-seed`. O alvo `integration-test` continua funcionando, e a duplicação de conhecimento (nomes e partições, hoje no `Makefile`) desaparece (Art. 4, DRY).
- **`make up`** continua sendo `docker compose up --build -d`; com as dependências acima ele sobe tudo funcional. Não se usa `--wait`: um seed que termina com sucesso não é um serviço de longa vida, e versões do Compose diferem no tratamento disso.
- **Portas publicadas:** `8080` (API) e `8082` (gerenciamento). A infra de observabilidade externa (Prometheus) não entra (Non-Goals).

### D11. Dependências novas (Art. 10)

| Dependência | Versão | Última release | Custo contra implementar |
|-|-|-|-|
| `org.springframework.boot:spring-boot-starter-actuator` | 4.1.0 (BOM) | 10/06/2026 | probes, health e métricas HTTP prontos; escrever isso seria reescrever o Actuator |
| `io.micrometer:micrometer-registry-prometheus` | 1.17.0 (BOM) | 08/06/2026 | o formato de texto do Prometheus e o endpoint; custo zero de código |
| `org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry` e `io.micrometer:micrometer-tracing-bridge-otel` | 4.1.0 e 1.7.0 (BOM) | 10/06/2026 e 08/06/2026 | `traceId` no MDC e propagação W3C no HTTP e no Kafka, sem exportador; substitui o `TraceIdFilter` e dispensa um interceptor próprio |
| `io.github.resilience4j:resilience4j-micrometer` | 2.4.0, versão fixa (fora do BOM) | 14/03/2026 | publica estado, chamadas e taxa de falha do circuito; implementar à mão exigiria ouvir os eventos do circuito e manter gauges |

- Todas são mantidas pelos mesmos projetos que o build já usa (Spring, Micrometer, Resilience4j), de adoção ampla. O `resilience4j-micrometer` traz também `resilience4j-bulkhead` e `resilience4j-retry` de forma transitiva, sem uso por nós.
- **Alternativas descartadas:** `spring-boot-starter-opentelemetry` e `starter-zipkin` (D2); `logstash-logback-encoder` (D3); registry Micrometer de outro backend (o Prometheus é o padrão de fato, e o formato de texto é lido por qualquer coletor).

### D12. Estratégia de testes

- **Unitários (sem infra, em `./gradlew check`):**
  - `toString` seguro de `OwnerId` e `AccountId` e dos objetos compostos; `SnapshotSaveResult` e a regra de duplicata no domínio;
  - writer com cliente mockado: `ReturnValuesOnConditionCheckFailure=ALL_OLD`, item devolvido igual (duplicata), mais novo (antigo), ausente (antigo) e malformado (permanente);
  - listener e recoverer com `SimpleMeterRegistry`: um incremento por desfecho, nenhum em falha transitória nem em pausa;
  - `BalanceCircuitBreakerConfig`: os dois circuitos no registry e o estado na métrica;
  - `@SpringBootTest` com MockMvc e `management.server.port` igual ao da API nos testes (o MockMvc não serve a porta de gerenciamento separada): `/actuator/health/*`, `/actuator/prometheus`, `http.server.requests` por status, baixa cardinalidade, JSON do log com `traceId`, `traceparent` do HTTP no log e no corpo, e o `readiness` mudando no encerramento do contexto;
  - testes estáticos dos arquivos de infra (lidos do disco): `Dockerfile` (tags fixas, estágios, `USER` numérico, `JAVA_TOOL_OPTIONS`, `ENTRYPOINT` exec, `STOPSIGNAL`), `docker-compose.yml` (tags fixas, `depends_on` com condição, healthcheck, `mem_limit`, `stop_grace_period` maior que o encerramento) e os scripts do seed.
- **Integração (Redpanda e DynamoDB Local reais, dependem de Docker):** o `traceparent` do registro no log do processamento e na DLT; o `owner` sentinela ausente de todo log num evento malformado; os quatro resultados no contador; o lag disponível; o `PutItem` devolvendo o item da recusa no DynamoDB Local (R2).
- **Validação em Docker (tarefa 8):** build da imagem, `id -u`, `SIGTERM` com registro em andamento, `make up` ponta a ponta e o lag com as partições pausadas (R4).

## Conformidade, padrões e coerência

- **Camadas (Art. 1):** o domínio ganha `DuplicateIgnored` e o `toString` seguro, sem framework; `application` não muda; Micrometer, Micrometer Tracing, Actuator e Resilience4j ficam nos adapters; nenhum tipo de tecnologia atravessa um port.
- **Padrões nomeados (Art. 9):** Correlation Identifier e Context Propagation (D1, D2), Idempotent Receiver e Result (D6), Health Check API (D7), Decorator (já existente, agora observável), Exception Translator (já existente, agora lendo o `Tracer`).
- **Soluções prontas (Art. 10):** o formato JSON do Boot (D3), o Micrometer Tracing (D2), a observação do Spring Kafka e a métrica de lag do cliente (D5), `resilience4j-micrometer` (D5), os probes do Actuator (D7), `JAVA_TOOL_OPTIONS` (D8). Código próprio só onde há regra do serviço: o contador de resultado e a regra de duplicata.
- **Coerência com o codebase (Art. 8):** contador e métricas ficam no adapter que já trata o evento (`adapter/input/kafka`); configuração por `${ENV:default}` no `application.yaml` e no compose; testes espelhados e nomes `should ... when ...`. **Divergências explícitas:** (a) `DynamoDbConfig` deixa de escrever credenciais (D9); (b) testes lêem arquivos de infra do disco, que nenhum teste fazia antes, porque o ambiente da sessão não tem Docker (D12); (c) o `Dockerfile` é alterado, e não substituído por buildpacks (D8).
- **Ambiguidades do enunciado tocadas e decididas:** o que é "mesma mensagem duas vezes" para fins de observação (D6), qual identificador de correlação e qual header (D1), e se o `readiness` depende do DynamoDB (D7).
- **Padrão ou algoritmo não implementado (critério 8 do enunciado):** gauge de lag via `AdminClient` (D5), exportação de traces e coletor (Non-Goals), alertas e dashboards (R4), layered jar na imagem (D8), indicador de saúde de dependência (D7), cada um com o motivador na decisão correspondente.

## Risks / Trade-offs

- **[R1] A combinação Boot 4.1.0, Micrometer Tracing 1.7.0 e Spring Kafka 4.1.0 pode ter nomes ou comportamentos diferentes do esperado** (por exemplo, o nome dos artefatos de auto-configuração, os campos do JSON ou o nome do timer). → **Resultado do spike (tarefa 1.2):** o BOM do Boot 4.1.0 gerencia todas as dependências, exceto `resilience4j-micrometer`, e não entra nenhum exportador OTLP; o JSON do `logstash` traz `@timestamp`, `message`, `logger_name`, `level`, `traceId`, `spanId` e `stack_trace` como campos do topo da linha; `/actuator/prometheus` só responde depois de `management.endpoints.web.exposure.include`; `http.server.requests` sai por `status` e com `uri` em padrão (`/balances/{accountId}`); em testes, a instrumentação de métricas e a de tracing precisam de `@AutoConfigureMetrics` e `@AutoConfigureTracing`, dos módulos de teste `spring-boot-starter-micrometer-metrics-test` e `spring-boot-micrometer-tracing-test`. A tarefa 1 foi um spike com testes que fixaram o contrato (JSON com `traceId`, `traceparent` no MDC, timer do listener, lag, circuito, `http.server.requests`); se algo não existir, o `design.md` é corrigido antes de seguir, e um problema sem solução sobe ao usuário (Art. 10).
- **[R2] O DynamoDB Local pode não devolver o item na recusa condicional.** → O resultado degrada para `StaleIgnored`, sem erro (D6), e a spec cobre o caso do item ausente. **Medido (tarefa 3.4):** o DynamoDB Local 3.3.0 **devolve** o item da recusa; a matriz de pares do `DynamoDbBalanceIntegrationTest` passou com `DuplicateIgnored` para versões iguais, contra o DynamoDB Local real.
- **[R3] Cobertura do JaCoCo (gate de 90%).** Os adapters e testes novos contam. → Os testes novos são parte de cada tarefa; o gate é verificado ao fim de cada grupo.
- **[R4] O lag do cliente Kafka mede a busca, e não o processamento: ele subestima justo na indisponibilidade** (D5). **Medido no contêiner (tarefa 6.6):** com o DynamoDB parado e 30 eventos publicados, o lag real do broker (`rpk group describe`) ficou em **30** durante toda a medição, e `kafka_consumer_fetch_manager_records_lag_max` ficou em **0,0**, porque os registros já tinham sido buscados, só não processados; o circuito de escrita abriu cerca de 40 s depois. A métrica sozinha, portanto, não alerta. → O sinal operacional na indisponibilidade é o estado do circuito (`open`) combinado com o contador de resultado parado; o alerta e o gauge via `AdminClient` ficam como evolução documentada. A tarefa 8 mede o comportamento real.
- **[R5] `DefaultCredentialsProvider` quebra quem rodava o serviço sem variáveis de ambiente.** → O compose, o Gradle (`test`, `integrationTest`, `bootRun`) e o `README.md` as definem; sem elas a falha é explícita (`500` genérico, causa no log).
- **[R6] O autorizador pode propagar a correlação em outro header.** → Não há header definido no enunciado; o serviço gera um `traceId` quando não há `traceparent`. Ler outro header é uma extensão pequena (um `Propagator`), não implementada agora.
- **[R7] O endpoint de gerenciamento não tem autenticação.** → Fica na porta separada, que a rede não deve expor (e que o compose publica só para o desenvolvedor). Segurança de gerenciamento é Non-Goal; registrada como evolução.
- **[R8] O desempenho do JSON e do tracing.** O log estruturado e a observação por registro têm custo. → Custo pequeno frente a uma chamada ao DynamoDB; sem exportador, o span não é enviado a lugar nenhum. Medir fica para a evolução de carga.
- **[R9] A validação em contêiner não é executável nesta sessão.** → Fica nas tarefas marcadas como dependentes de Docker, para o ambiente que o tiver; os testes estáticos dão a proteção possível sem Docker, e a spec exige a verificação real (`id -u`, `SIGTERM`, `make up`).

- **[R10] A resolução de DNS fica fora do orçamento de latência da leitura.** Medido no contêiner (tarefa 8.7): com o container do DynamoDB parado, o nome `dynamodb` leva cerca de **7,9 s** para falhar no Docker Desktop, e a primeira consulta respondeu `503` em **7,85 s**, e não em até 800 ms. A resolução de nome é uma chamada bloqueante que o `connectionTimeout` e o `apiCallTimeout` do SDK não interrompem. As consultas seguintes falharam em cerca de 16 ms (resolução negativa em cache e circuito aberto), o `readiness` seguiu `UP` e a métrica do circuito marcou `open`. → Em produção o endpoint costuma continuar resolvível, e as falhas são recusa de conexão ou timeout, que o teste `BalanceQueryDependencyUnavailableIntegrationTest` e o do endpoint silencioso provam dentro do orçamento. Limitação aceita e documentada; a evolução é um `DnsResolver` com timeout no cliente HTTP do SDK (o `Apache5HttpClient` aceita um resolvedor próprio).

## Migration Plan

Mudança aditiva, com duas quebras internas: o contrato do port `BalanceRepository` (`DuplicateIgnored`) e a exigência de credenciais AWS no ambiente. Ordem: arquivar `add-balance-query-api` antes; implementar; rodar `make integration-test` e a validação em Docker. Reversão: reverter o commit; os tópicos e a tabela não mudam, e as variáveis novas têm padrão.

## Open Questions

Nenhuma bloqueante. Dependem de validação com Docker, e não de decisão: o comportamento do DynamoDB Local na recusa condicional (R2) e do lag com partições pausadas (R4).
