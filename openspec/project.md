# Project Context — desafio-tecnico-consulta-saldo

> Fonte de verdade do contexto do projeto para o OpenSpec. Um resumo deste arquivo está em `openspec/config.yaml` (`context:`), que é o que o OpenSpec 1.x injeta nos artefatos.
> **Os dois devem permanecer sincronizados** — ver regras de manutenção no [`CLAUDE.md`](../CLAUDE.md).
> Mapa detalhado do starter kit (diagramas, achados e riscos): [`docs/starter-kit-architecture-map.md`](../docs/starter-kit-architecture-map.md).

## Purpose

Solução do desafio técnico de **consulta de saldo** do processo seletivo de Engenharia de Software do Itaú, construída sobre o template `itau-code-challange-starter-kit` (branch `kotlin`).

**Problema:** consumir o tópico Kafka `transacoes-financeiras-processadas` (Redpanda), persistir no DynamoDB o **snapshot de saldo mais recente de cada conta** e expor `GET /balances/{accountId}` com esse saldo.

**Fonte da verdade dos requisitos:** `.challenge/enunciado.md`, a transcrição integral do PDF do desafio. Ela é local e **não versionada**. Em caso de conflito, o enunciado prevalece sobre este arquivo.

O template traz um contexto de exemplo (`hello`) que demonstra a arquitetura, a infraestrutura e os padrões a seguir. O desafio deve **estender** esse template: reutilizar a arquitetura, os padrões e a infra já configurados, e adicionar o que o problema exigir.

- Detalhes do domínio, requisitos não funcionais e critérios de avaliação estão nas seções *Domain Context*, *Non-Functional Requirements* e *Evaluation Criteria*.
- Entrega: prazo de **3 dias**, em repositório **público** no GitHub. O enunciado (`.challenge/`, já no `.gitignore`) **não** pode ser versionado.

## Tech Stack

| Categoria | Tecnologia |
|-|-|
| Linguagem | Kotlin 2.3.21 (`-Xjsr305=strict`, `-Xannotation-default-target=param-property`) |
| Runtime | Java 21 (Eclipse Temurin; toolchain via foojay resolver) |
| Framework | Spring Boot 4.1.0 / Spring Framework 7 (`spring-boot-starter-webmvc`, `spring-boot-starter-kafka`) |
| Build | Gradle 9.5.1, Kotlin DSL (`build.gradle.kts`) |
| JSON | Jackson 3 (`tools.jackson`, `jackson-module-kotlin`). **Não** usar `com.fasterxml.jackson.databind` |
| Persistência | Amazon DynamoDB via AWS SDK for Java v2 (`software.amazon.awssdk:dynamodb`, BOM 2.46.7, com `apache5-client` declarado para os timeouts de conexão e de socket) — **DynamoDB Local** (`amazon/dynamodb-local:3.3.0`, in-memory) |
| Mensageria | Protocolo Kafka via Spring Kafka; broker local = **Redpanda** `v26.1.14` (single-node, KRaft) |
| Resiliência | Retry com backoff exponencial e jitter, error handler e DLT pelo Spring Kafka (`ExponentialBackOff`, `DefaultErrorHandler`); circuit breaker com `resilience4j-circuitbreaker` 2.4.0, de versão fixa porque o BOM do Boot não a gerencia e o Spring não tem circuit breaker |
| Documentação de API | `springdoc-openapi-starter-webmvc-ui` 3.1.1: gera o OpenAPI (`/v3/api-docs`) e a Swagger UI (`/swagger-ui.html`) a partir das anotações dos controllers e dos DTOs, sem arquivo escrito à mão; versão fixa porque o BOM do Boot não a gerencia |
| Observabilidade | Spring Boot Actuator e Micrometer com `micrometer-registry-prometheus`, Micrometer Tracing com a ponte OpenTelemetry **sem exportador**, e `resilience4j-micrometer` 2.4.0 (versão fixa: fora do BOM); log JSON nativo do Boot (formato `logstash`), sem biblioteca de log |
| Testes | JUnit Jupiter 6.0.3 (gerenciado pelo BOM do Boot 4.1; o README do template diz "JUnit 5"), `kotlin-test`, Mockito 5.23 (`@MockitoBean`), MockMvc, Konsist 0.17.3 e parser Kotlin já transitivo exposto ao compile de teste para referências qualificadas, `kotlinx-coroutines-core` (só teste, versão do BOM do Boot, 1.10.2) para disparar gravações concorrentes em paralelo de verdade no teste de integração |
| Testes de observabilidade | `spring-boot-starter-micrometer-metrics-test` e `spring-boot-micrometer-tracing-test` (as anotações `@AutoConfigureMetrics` e `@AutoConfigureTracing`) |
| Cobertura | JaCoCo 0.8.12, gate mínimo de **90% de instruções** em `./gradlew check` |
| Containers | Docker multi-stage + Docker Compose |
| CI | GitHub Actions: Build, Test & Coverage (unit + integração), Docker, CodeQL |

### Serviços locais (docker-compose)

| Serviço | Porta host | Função |
|-|-|-|
| `app` | 8080 (API), 8082 (gerenciamento) | Aplicação (imagem `runtime`); espera os seeds terminarem e tem healthcheck no `readiness` |
| `dynamodb` | 8000 | DynamoDB Local (`-sharedDb -inMemory`) |
| `dynamodb-seed` | — | `infra/dynamodb/seed.sh`: cria as tabelas `GreetingMessages` e `AccountBalances` (idempotente) e faz o seed |
| `dynamodb-admin` | 8001 | Console web do DynamoDB |
| `redpanda` | 19092 (externo) / `redpanda:9092` (interno) | Broker Kafka |
| `redpanda-seed` | — | `infra/redpanda/config.sh && seed.sh && ingestion-topics.sh`: config do cluster, cria o tópico do `hello`, publica o seed e cria os tópicos de ingestão |
| `redpanda-console` | 8081 | Console web do Kafka |

### Configuração (`src/main/resources/application.yaml`)

Toda configuração externa usa `${ENV_VAR:default-local}`. Os defaults apontam para `localhost`, e o `docker-compose.yml` sobrescreve com os hostnames internos.

| Propriedade | Env var | Default |
|-|-|-|
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:19092` |
| `spring.kafka.consumer.group-id` | `KAFKA_CONSUMER_GROUP_ID` | `hello-greeting-template-consumer` |
| `dynamodb.endpoint` | `DYNAMODB_ENDPOINT` | `http://localhost:8000` |
| `dynamodb.region` | `DYNAMODB_REGION` | `us-east-1` |
| `dynamodb.table-name` | `GREETING_TABLE_NAME` | `GreetingMessages` |
| `dynamodb.balance-table-name` | `BALANCE_TABLE_NAME` | `AccountBalances` |
| `dynamodb.timeouts.{connection,socket,api-call-attempt,api-call}` | `DYNAMODB_{CONNECTION,SOCKET,API_CALL_ATTEMPT,API_CALL}_TIMEOUT` | `500ms`, `1s`, `1s`, `3s` |
| `dynamodb.retry.max-attempts` | `DYNAMODB_MAX_ATTEMPTS` | `3` |
| `greeting-templates.topic-name` | `GREETING_TEMPLATES_TOPIC` | `greeting-templates` |
| `ingestion.{topic-name,dlt-topic-name,consumer-group-id,concurrency}` | `TRANSACTIONS_TOPIC`, `TRANSACTIONS_DLT_TOPIC`, `TRANSACTIONS_CONSUMER_GROUP_ID`, `INGESTION_CONCURRENCY` | `transacoes-financeiras-processadas`, `transacoes-financeiras-processadas.DLT`, `balance-transaction-ingestion`, `1` |
| `ingestion.{max-retries,pause-duration}` | `INGESTION_MAX_RETRIES`, `INGESTION_PAUSE_DURATION` | `3`, `30s` |
| `ingestion.backoff.{initial,multiplier,max,jitter}` | `INGESTION_BACKOFF_{INITIAL,MULTIPLIER,MAX,JITTER}` | `200ms`, `2.0`, `2s`, `100ms` |
| `balance.circuit-breaker.{failure-rate-threshold,sliding-window-size,wait-duration-in-open-state,half-open-calls}` | `BALANCE_CB_{FAILURE_RATE_THRESHOLD,SLIDING_WINDOW_SIZE,WAIT_DURATION_OPEN,HALF_OPEN_CALLS}` | `50`, `10`, `30s`, `3` |
| `dynamodb.read.timeouts.{connection,socket,api-call-attempt,api-call}` | `DYNAMODB_READ_{CONNECTION,SOCKET,API_CALL_ATTEMPT,API_CALL}_TIMEOUT` | `200ms`, `300ms`, `300ms`, `800ms` |
| `dynamodb.read.retry.max-attempts` | `DYNAMODB_READ_MAX_ATTEMPTS` | `2` (o máximo aceito: 1 retry) |
| `balance.api.retry-after` | `BALANCE_API_RETRY_AFTER` | `5s` |
| `management.server.port` | `MANAGEMENT_PORT` | `8082` (probes e métricas, separada da API) |
| `logging.structured.format.console` | `LOG_FORMAT` | `logstash` (JSON) |
| (SDK AWS) | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | obrigatórias; `local` no compose e nas tarefas `test`, `integrationTest` e `bootRun` do Gradle |
| (JVM da imagem) | `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError` |

As credenciais AWS não ficam no código: o cliente usa a cadeia padrão do SDK. Há um `DynamoDbClient` por perfil de acesso, ambos em infraestrutura técnica neutra, fora de `hello`, com timeouts e tentativas explícitos: o `dynamoDbClient` (`@Primary`, escrita e `hello`) e o `readDynamoDbClient` (leitura de saldo, com orçamento curto e no máximo 1 retry). Contextos novos reutilizam um deles, sem criar outro cliente. O perfil de leitura falha na subida se `maxAttempts` passar de 2 ou se `maxAttempts × api-call-attempt > api-call`.

## Project Conventions

### Working Rules (mandatórias)

- **TDD:** cada cenário (`#### Scenario`) das specs do OpenSpec vira um teste **antes** do código de produção (vermelho → verde → refatora).
- **Dinheiro em `BigDecimal`**, nunca `Double`/`Float`, do parse do evento até a resposta HTTP.
- **Núcleo sem dependência de framework** (`domain`, `port` e `application`); sem tipos de outros contextos nessas camadas.
- **Um commit por change do OpenSpec**, em Conventional Commits (ver *Git Workflow*).
- **Contratos vêm do enunciado:** `.challenge/enunciado.md` é a fonte da verdade para o payload do tópico, o request e o response.
  - Toda change que tocar um contrato deve conferir lá os nomes de campo, os tipos e os formatos: snake_case, µs, ISO 8601, UUID.
  - Nunca invente, renomeie ou "melhore" um campo. O que o enunciado não define vira uma decisão explícita no `design.md` da change.
- **Constituição de qualidade de código** (Clean Architecture, SOLID, Clean Code, DRY/KISS/YAGNI, testes, comentários, baixa carga cognitiva e coerência com o codebase, design patterns, não reinventar a roda e conformidade com o enunciado): é **inegociável** e vale também para os testes. O texto completo está no `CLAUDE.md`.
  - Conformidade com o enunciado (Art. 11): toda implementação é validada contra `.challenge/enunciado.md`, item por item, com evidência (teste, arquivo ou comando executado), antes de ser dada como pronta; divergência entre implementação, spec ou design e o enunciado: pare e pergunte, e o enunciado prevalece salvo decisão explícita do usuário no `design.md`; o revisor independente refaz a validação por conta própria.
  - Comentários (Art. 6): ficam **só no cabeçalho do arquivo**, um comentário `/* ... */` antes do `package` (em script shell, linhas `#` logo após o shebang), em português (pt-BR), e nenhum comentário no corpo. O cabeçalho tem uma entrada por trecho criado ou alterado, no formato `L<início>[-L<fim>] <símbolo>: <porquê>`, com o porquê real (para tipo simples, por que existe como tipo distinto), e na última linha `Enunciado: <seção> → <item>`. Uma entrada cujo item difere do principal o traz no fim dela. A numeração é a do arquivo final, contando o cabeçalho, e muda junto com o arquivo. A entrada de tipo ou classe cita só a linha da declaração, e a de função ou trecho cita a faixa até a linha que o fecha (`)` ou `}`, quando houver); o símbolo ajuda a reencontrar o trecho quando as linhas se deslocam. Arquivo trivial (só getter, delegação ou expressão única sem regra) dispensa cabeçalho.
  - A única exceção ao "nada no corpo" é o KDoc de contrato (retorno e exceções) na assinatura de ports e da API pública do domínio, quando a assinatura não basta. O cabeçalho do port aponta para ele.
  - O item é o título da subseção ou o texto em negrito do bullet do enunciado, sem emoji, e pode citar o campo do contrato (`O que construir → Exposição (API REST) → Contrato de resposta → balance.amount`). Sem item: `Enunciado: n/a (<change> design Dn)`, e nunca se inventa um item. Referência ao `design.md` sempre na forma `<change> design Dn`.
  - Nos testes, o cabeçalho traz também `Spec: <requisito>` (vários, separados por `;`, ou `Spec: n/a (<change> design Dn)`) antes da linha `Enunciado:`, e só a classe e os pontos não óbvios entram nele, porque os nomes dos testes já descrevem o comportamento.
  - Também permitidos no cabeçalho: referência a fonte externa e unidade ou formato que o tipo não expressa (µs). Termos técnicos e identificadores ficam como no código (`saveIfNewer`, `ConditionExpression`). Comentário desatualizado é bug.
  - Proibido: comentário que repete o código, comentário de ruído, diário ou autoria, código comentado, banner e `TODO`/`FIXME`. O usuário revisa os comentários manualmente antes de cada commit, e o revisor independente confere que todo arquivo alterado tem o cabeçalho, em pt-BR, com linhas e símbolos que batem, o porquê real e um item do enunciado que existe, sem comentário no corpo (salvo o KDoc de contrato), ruído nem o quê.
  - Os Arts. 8 a 10 valem também para o `design.md`, as specs e as dependências do `build.gradle.kts`.
  - Coerência: seguir os nomes, arquivos, pacotes e a estrutura de testes do codebase, com um nome por conceito. Se o padrão existente violar um artigo, o artigo prevalece e a divergência é explícita. O revisor cita o trecho concreto ao apontar violação de carga cognitiva.
  - Pattern reconhecido: verificar antes de desenhar, sem forçar; KISS e YAGNI prevalecem.
  - Solução pronta, nesta ordem: biblioteca padrão do Kotlin/JDK, Spring (Framework e projetos do ecossistema), AWS SDK e biblioteca consolidada do ecossistema. Código próprio é para a regra de negócio do domínio ou para quando nenhuma alternativa adequada existe. O Art. 1 prevalece sobre qualquer biblioteca.
  - O `design.md` registra o pattern aplicado (ou por que nenhum coube), a alternativa de biblioteca descartada, cada divergência do padrão do codebase e, para dependência nova, a versão, a data da última release e o custo contra o de implementar (dependência nova precisa de manutenção ativa, maturidade e adoção ampla). O contexto do projeto registra só a dependência e o motivo. Sem `design.md` (alteração fora de uma change), o registro vai na descrição do commit e no relatório de revisão.
- **Revisão por agente independente antes do commit de toda change:**
  - O revisor é um subagente com contexto limpo, somente leitura.
  - Os achados são classificados como bloqueante, ajuste ou sugestão.
  - O ciclo se repete até não restar bloqueante nem ajuste, com no máximo 3 rodadas; depois disso, a decisão sobe para o usuário.
  - Procedimento completo no `CLAUDE.md`.

### Architecture Patterns — Hexagonal (Ports & Adapters)

Cada bounded context vive em `br.com.itau.challenge.<contexto>` com as camadas abaixo. O exemplo é `hello`.

```
br/com/itau/challenge/<contexto>/
├── domain/
│   ├── model/          data classes do domínio (sem Spring, sem libs externas)
│   └── exception/      exceções de domínio (extends RuntimeException)
├── port/
│   ├── input/          casos de uso oferecidos  → `fun interface <Verbo><Coisa>UseCase`
│   └── output/         dependências necessárias → `fun interface <Coisa>Repository|Provider`
├── application/        `class <X>Service : <X>UseCase`; Kotlin puro, orquestra via ports
└── adapter/
    ├── input/web/      `@RestController` + `dto/`   (driving)
    ├── input/kafka/    `@KafkaListener`  + `dto/`   (driving)
    └── output/dynamodb/ `@Component` implementando ports de saída (driven)
```

**Regra de dependência** (sempre em direção ao domínio):

```
adapter ──▶ port ──▶ domain
             ▲         ▲
       application ────┘
```

- `domain`, `port` e `application` usam somente JDK/Kotlin e o núcleo do próprio contexto, sem frameworks. `domain` não depende de outras camadas.
- `port` não depende de `application` nem de `adapter`.
- `application` não depende de `adapter` nem da composição. Spring registra os services externamente, com configuração separada por contexto.
- `adapter` fala com casos de uso **via ports de entrada**, sem importar application ou a raiz de composição; modelos do domínio continuam permitidos para tradução.
- DTOs de transporte (HTTP/Kafka) ficam no adapter e são convertidos para modelos de domínio ali.
- O circuit breaker é um Decorator do `BalanceRepository` (escrita) e do `BalanceProvider` (leitura) em `adapter/output/resilience`, exposto como `@Primary`. Os dois circuitos são instâncias independentes, para a API não pausar a ingestão nem o contrário, e partilham uma só definição do que conta como falha. Erros HTTP: toda a aplicação responde `application/problem+json` com `traceId` (do `traceparent` W3C, ou novo, também no MDC do log), e toda resposta de `/balances/**` leva `Cache-Control: no-store`; o saldo nunca é cacheado. Cada listener tem container, grupo e commit próprios: propriedades globais de listener (ex.: `spring.kafka.listener.ack-mode`) afetariam o consumer `hello`, e o error handler não pode ser um bean, porque o Boot o aplicaria a todos os containers.
- A verificação arquitetural descobre contextos automaticamente pelas camadas dos fontes de produção, incluindo contextos parciais; infraestrutura/composição não são contextos. A mesma política verifica produção e fixtures negativas, incluindo aliases e referências qualificadas, e falha em escopo vazio.
- Testes estáticos declaram no Gradle suas entradas externas e inventários (documentos, OpenSpec, infra/http, workflows de CI e fontes), incluindo criação/rename/remoção; não forçar execução permanente nem incluir material pessoal. Esses arquivos entram somente no estágio Docker test, por COPY seletivo; builder/runtime permanecem separados.

**Fluxo de exemplo (`hello`):**
- `GET /hello` → `GreetingController` → `GetGreetingUseCase` ← `GreetingService` → `GreetingTemplateProvider` ← `DynamoDbGreetingTemplateProvider` (Scan).
- Tópico `greeting-templates` → `GreetingTemplateConsumer` → `SaveGreetingTemplateUseCase` ← `SaveGreetingTemplateService` → `GreetingTemplateRepository` ← `DynamoDbGreetingTemplateWriter` (PutItem).

### Code Style

- **Idioma do código:** inglês (classes, métodos, mensagens de exceção, nomes de teste). Comentários e documentação (README, specs) em português (pt-BR).
- **Indentação:** 4 espaços nos arquivos de `hello/` e nos testes. `Application.kt` e `build.gradle.kts` usam tabs (padrão do Spring Initializr). Seguir o estilo do arquivo que estiver sendo editado.
- **Trailing commas** em listas de parâmetros/argumentos multilinha.
- **Injeção de dependência por construtor**, com propriedades `private val`. Valor de config avulso via `@Value("\${prop}")` no construtor; um grupo de valores que levaria a mais de 3 parâmetros vira uma `@ConfigurationProperties` (data class imutável).
- **Ports como `fun interface`**, para que os testes usem lambdas como fakes.
- **Modelos de domínio e DTOs como `data class`** imutáveis (`val`).
- **Constantes de atributo/coluna** como `private const val` no topo do arquivo (ex.: `TEMPLATE_ATTRIBUTE = "template"`).
- **Invariantes de negócio no domínio:** value objects e entidades validam na construção e lançam exceções de domínio; o service só orquestra.
  - O `hello` do template valida no service. Não replicar esse padrão nos contextos novos.
  - Invariantes técnicas do adapter usam `check(...)` ou `require(...)`.
- **JSON no Kafka:** consumir como `String` (`StringDeserializer`) e desserializar com o `ObjectMapper` (Jackson 3) injetado do Spring.
- **Imagens Docker** sempre com versão fixa e completa (ex.: `21.0.12_8`), nunca `latest` nem só a versão maior.
- Sem Lombok nem geração de código. Kotlin idiomático.

### Testing Strategy

| Onde | Source set / task | Infra | Roda em |
|-|-|-|-|
| `src/test/kotlin/...` | `test` → `./gradlew test` / `check` | Nenhuma (fakes/mocks) | `make test`, CI `unit-test` |
| `src/integrationTest/kotlin/...` | `integrationTest` → `./gradlew integrationTest` | DynamoDB Local + Redpanda reais | `make integration-test`, CI `integration-test` |

- **Espelhamento de pacote:** um arquivo de teste por classe de produção, no mesmo pacote (`<Classe>Test.kt` / `<Classe>IntegrationTest.kt`).
- **Nomes de teste** em crase e em inglês: `` `should <comportamento> [when <condição>]` ``.
- **Estrutura AAA** (arrange / act / assert) separada por linhas em branco. Asserts de `kotlin.test` (`assertEquals`, `assertFailsWith`, …).

Técnica por camada:

| Camada | Técnica |
|-|-|
| domain | Asserts diretos sobre modelos e exceções |
| application | Fakes via lambda das `fun interface` de saída (sem Mockito) |
| adapter web | `@SpringBootTest` + `@AutoConfigureMockMvc`, com o port de saída substituído por `@MockitoBean`, ou o de entrada por um fake `@Primary` quando o port tem um decorator `@Primary` ou usa value class |
| adapter Kafka (unit) | Chamar o método do listener direto, com um fake do use case e `JsonMapper` real |
| adapter DynamoDB (unit) | `mock(DynamoDbClient::class.java)` + `ArgumentCaptor` para validar requests |
| arquitetura | `HexagonalArchitectureTest` (Konsist) |
| integração DynamoDB | Client real contra DynamoDB Local; IDs únicos (`UUID`) e limpeza em `@AfterEach` |
| integração Kafka | `@SpringBootTest` real + `KafkaTemplate`; `verify(mock, timeout(10_000))` no port de saída |

Gates e regras:
- `./gradlew check` = testes unitários + `jacocoTestCoverageVerification` (**≥ 90% de instruções**). Só `Application`/`ApplicationKt` ficam excluídos (`jacocoCoverageExclusions` em `build.gradle.kts`).
- Integração **não** faz parte de `check` e **não** conta para cobertura.
- `./gradlew integrationTest` também dispara os testes unitários (o `jacocoTestReport` depende de `test`).
- Relatório HTML: `build/reports/jacoco/test/html/index.html`.
- Toda funcionalidade nova nasce de teste (TDD, ver *Working Rules*) e mantém o gate. Fluxos principais **e** corner cases (duplicata, fora de ordem, conta inexistente, dado inválido, dependência indisponível) precisam de teste. Adapters que tocam infra real devem ganhar teste de integração.

### Observabilidade e contêiner

- **Logs:** JSON de uma linha no console, com `traceId` em todo log durante uma requisição HTTP ou um registro Kafka. O `traceId` vem do `traceparent` (W3C) do HTTP ou do header do registro, ou é gerado; a DLT preserva o header. A propagação é do Micrometer Tracing, e a observação do Kafka fica só no container de transações, com o `ObservationRegistry` explícito (sem ele a observação é um no-op silencioso).
- **Dados sensíveis:** `owner` e payload nunca vão para log, nem nas mensagens de exceção; o `accountId` sai só com o primeiro grupo do UUID. Isso vale por construção, no `toString` de `OwnerId` e `AccountId`. Evento duplicado ou antigo é logado em `DEBUG`.
- **Métricas** (Prometheus em `/actuator/prometheus`, só `health` e `prometheus` expostos, na porta de gerenciamento): `balance.transactions.processed{result=applied|stale_ignored|duplicate|dlq}`, o timer `spring.kafka.listener`, `kafka.consumer.fetch.manager.records.lag.max`, `resilience4j.circuitbreaker.*` dos circuitos `balance-storage` e `balance-storage-read`, e `http.server.requests`. Nenhuma tag leva identificador de conta, titular ou transação. O lag pode ficar defasado com as partições pausadas.
- **Probes:** o `liveness` depende só do processo; o `readiness` só do estado da aplicação (recusa tráfego ao começar o encerramento) e **não** depende do DynamoDB nem do Kafka: as instâncias dividem a dependência, e verificá-la as tiraria todas do balanceador juntas.
- **Contêiner:** processo como usuário não root (UID `10001`), `JAVA_TOOL_OPTIONS` sobrescrevível, `ENTRYPOINT` em forma exec (a JVM é o PID 1 e recebe o `SIGTERM`) e `stop_grace_period` maior que `spring.lifecycle.timeout-per-shutdown-phase`.
- **Testes:** no Boot 4 a instrumentação de métricas e a de tracing ficam desligadas nos testes sem `@AutoConfigureMetrics` e `@AutoConfigureTracing`. Os testes MockMvc do Actuator usam `management.server.port=` (vazio, mesma porta da API), e os testes com servidor de verdade usam `management.server.port=0`. Os arquivos de infra (`Dockerfile`, `docker-compose.yml`, scripts), o workflow do CodeQL e o `README.md` (`ReadmeTest`: seções, ADRs e os alvos `make`, variáveis e classes de teste que ele cita) têm testes estáticos que rodam em `./gradlew check`.

### Git Workflow

- Branch principal: **`kotlin`** (a CI também observa `main`).
- CI em todo push/PR para `main`/`kotlin`: compilação, `check` + integração, build da imagem Docker e CodeQL.
- CodeQL usa build manual, com Kotlin compilado em processo, um único worker Gradle e heap do Gradle de 4 GiB (`-Dorg.gradle.jvmargs` só no comando da análise), e sem `setup-gradle` ou outro cache Gradle. O extrator Kotlin é single-threaded e travou o `autobuild` no `compileKotlin`; em processo, o heap padrão de 512 MiB esgota com o extrator; e uma task restaurada do cache não é extraída. Não voltar ao `autobuild` nem igualar o CodeQL aos outros workflows nesse ponto.
- Commits seguem **[Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)**: `<type>(<scope>)!: <descrição>`.
  - `type`: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style`, `revert`.
  - `scope` (opcional): bounded context ou área (ex.: `balance`, `hello`, `infra`, `openspec`, `deps`).
  - Descrição (e corpo/rodapé) em **português**, no imperativo, minúscula, sem ponto final, com até 72 caracteres no cabeçalho. `type` e `scope` permanecem em inglês, pois são os tokens do padrão.
  - Quebra de compatibilidade: `!` após o tipo/escopo e/ou rodapé `BREAKING CHANGE: ...`.
  - **Um commit por change do OpenSpec**, contendo código, testes, specs e as atualizações de contexto (`project.md` + `context:`) que a change motivou.

## Commands (Makefile)

Pré-requisito: Docker com Compose. `make` nativo em Linux/macOS; no Windows, usar WSL2. `make help` lista tudo.

### Aplicação

| Comando | O que faz |
|-|-|
| `make build` | `docker build --target runtime` → imagem `itau-hello-world` |
| `make run` / `make up` | `docker compose up --build` (foreground / `-d`) — stack completa |
| `make logs` | `docker compose logs -f` (todos os serviços) |
| `make stop` | `docker compose down` |
| `make http` | Executa `http/hello.http` via `httpyac` em container Node (env `docker`) |

### DynamoDB

| Comando | O que faz |
|-|-|
| `make db-up` | Sobe `dynamodb` + `dynamodb-seed` + `dynamodb-admin` |
| `make db-seed` | Reexecuta o seed (idempotente: sobrescreve os itens) |
| `make db-scan` | `aws dynamodb scan` na tabela `GreetingMessages` (nome fixo) |
| `make db-down` | Para os serviços do DynamoDB |

### Kafka / Redpanda

| Comando | O que faz |
|-|-|
| `make kafka-up` | Sobe `redpanda` + `redpanda-seed` + `redpanda-console` |
| `make kafka-seed` | Reexecuta o seed (republica as mensagens; tópicos são append-only) |
| `make kafka-topic-create NAME=<t> [PARTITIONS=1]` | Cria tópico. **Auto-criação de tópicos está desligada.** |
| `make kafka-topics-ingestion` | Cria, se não existirem, os tópicos de ingestão (6 partições) pelo `infra/redpanda/ingestion-topics.sh`, o mesmo script que o seed do compose roda; o `make integration-test` o executa |
| `make kafka-produce-accounts-events TOPIC=<t> [COUNT=100]` | Publica eventos de conta aleatórios |
| `make kafka-produce-transactions-events TOPIC=<t> [COUNT=100]` | Publica eventos de transação + conta aleatórios |
| `make kafka-consume TOPIC=<t>` | Imprime as mensagens do tópico (timeout de 5s) |
| `make kafka-down` | Para os serviços do Redpanda |

### Testes e limpeza

| Comando | O que faz |
|-|-|
| `make test` | `docker build --target test` → `./gradlew check` dentro do build (sem infra). O BuildKit pode reaproveitar o cache e não reexecutar se nada mudou. |
| `make integration-test` | `db-up` + `kafka-up` → `docker compose wait` dos seeds → `./gradlew integrationTest` **no host (requer JDK local)** |
| `make clean-containers` | Remove todos os containers do projeto, incluindo órfãos e volumes |
| `make clean` | Remove as imagens `itau-hello-world` e `itau-hello-world-test` |

**Loop de dev pela IDE:** `make db-up && make kafka-up`, aguardar os seeds (`make logs` ou consoles web) e rodar `Application.kt` / `./gradlew bootRun`.

## Domain Context

- **Fonte:** tópico `transacoes-financeiras-processadas`. Cada evento é uma transação de crédito ou débito **já processada pelo autorizador** (aprovada **ou rejeitada**), e **todo** evento traz o **saldo da conta já calculado**.
- **Premissa central:** o serviço **não recalcula saldo**. Ele guarda o **snapshot mais recente por conta** (`account.id`), e "mais recente" é definido pelo `transaction.timestamp` do evento, não pela ordem de chegada.
- **Leitura:** `GET /balances/{accountId}` devolve o snapshot mais atual da conta. `accountId` é um **UUID** no path.
- **Contrato de resposta** (nomes de campo exatos, em snake_case):

| Campo | Tipo | Descrição |
|-|-|-|
| `id` | UUID | Identificador da conta (`account.id`) |
| `owner` | UUID | Identificador do titular (`account.owner`) |
| `balance.amount` | Number | Saldo atual |
| `balance.currency` | String | Código ISO 4217 (ex.: `BRL`) |
| `updated_at` | String | Data/hora da última atualização em **ISO 8601** (o exemplo usa milissegundos e offset: `2025-07-05T18:04:13.433-03:00`) |

- **Formato do evento** (o mesmo de `make kafka-produce-transactions-events TOPIC=transacoes-financeiras-processadas`):

```jsonc
{ "transaction": { "id": "<uuid>", "type": "CREDIT|DEBIT", "amount": 0.01..10000.00,
                   "currency": "BRL", "status": "APPROVED|DECLINED", "timestamp": <epoch µs> },
  "account":     { "id": "<uuid>", "owner": "<uuid>", "created_at": <epoch µs>,
                   "status": "ENABLED",
                   "balance": { "amount": 0.00..20000.00, "currency": "BRL" } } }
```

- Timestamps em **microssegundos** desde a epoch. Valores monetários com 2 casas decimais, moeda `BRL`.
- O gerador cria `account.id` aleatório por evento. Para testar a consulta, use um `account.id` lido do tópico (`make kafka-consume`) ou publique eventos próprios.
- O tópico precisa ser criado explicitamente, porque a auto-criação está desligada no cluster. **Decidido:** 6 partições para `transacoes-financeiras-processadas` e para a DLT `transacoes-financeiras-processadas.DLT`, criadas com `make kafka-topic-create NAME=<tópico> PARTITIONS=6`.
- **Ambiguidades do enunciado.** Não assuma uma resposta: cada uma deve ser decidida e justificada no `design.md` da change que a tocar.
- **Ambiguidades já decididas** (changes `add-balance-repository`, `add-transaction-ingestion`, `add-balance-query-api` e `add-observability`):
  - Conta sem snapshot responde `404`, `accountId` fora da forma canônica de UUID (36 caracteres) responde `400`, dependência indisponível responde `503` com `Retry-After`, e todo erro é `problem+json` com `traceId`.
  - `updated_at` sai em milissegundos (truncados) e no offset de `America/Sao_Paulo`, com três dígitos fixos de fração.
  - Sem cache de saldo: a leitura é fortemente consistente e toda resposta de `/balances/**` traz `Cache-Control: no-store`.
  - O mesmo evento (mesma versão) resulta em `DuplicateIgnored` e uma versão armazenada mais nova em `StaleIgnored`; a distinção vem do item que o `PutItem` condicional devolve na recusa (`ReturnValuesOnConditionCheckFailure`), sem leitura extra.
  - O `traceId` é o único identificador de correlação (não há `correlationId`), no formato W3C `traceparent`.
  - O `readiness` não depende do DynamoDB nem do Kafka.
  - Todo evento, aprovado ou `DECLINED`, atualiza o snapshot pela mesma regra de versão: o evento traz o saldo calculado e o serviço não o interpreta. Para `DECLINED`, só `updated_at` avança.
  - `updated_at` vem do `transaction.timestamp` do evento que gerou o snapshot.
  - "Mais recente" é definido só por `SnapshotVersion` no domínio: maior `timestamp` e, no empate, maior id da transação na forma textual minúscula (não `UUID.compareTo`). O adapter DynamoDB aplica essa ordem numa `ConditionExpression`.

## Non-Functional Requirements

Serviço de **missão crítica, 24/7 e alto volume**. A solução precisa se manter correta nestes cenários adversos:

| Cenário | Comportamento esperado |
|-|-|
| **Mensagens duplicadas** | Reprocessar não altera o resultado (idempotência). |
| **Mensagens fora de ordem** | Um evento mais antigo nunca sobrescreve um snapshot mais recente, inclusive com consumidores concorrentes. |
| **Dados inválidos** (evento ou request) | Um evento inválido não trava o consumo nem corrompe o snapshot; fica isolado e observável. Um request inválido (ex.: `accountId` que não é UUID) recebe uma resposta de erro explícita. |
| **Conta inexistente** | A consulta devolve uma resposta de erro explícita, nunca um 500. |
| **Dependências indisponíveis** (DynamoDB, broker) | Degradação controlada com retry, backoff e circuit breaker, sem perda silenciosa de eventos. |
| **Alto volume** | Escrita e leitura por chave (sem `Scan`), consumo escalável horizontalmente. |

## Evaluation Criteria

A solução será avaliada por (lista do enunciado):

1. **Modelagem de dados no DynamoDB:** escolha de partition key, sort key e índices secundários.
2. **Tratamento de concorrência:** o saldo reflete a transação mais recente mesmo com mensagens fora de ordem.
3. **Resiliência:** retries, backoff e circuit breaker, onde for oportuno.
4. **Testes:** fluxos principais e corner cases (ex.: mensagens duplicadas, transações fora de ordem, conta inexistente).
5. **Qualidade de código:** organização, legibilidade e aderência à arquitetura hexagonal do starter-kit.
6. **Tratamento de cenários adversos:** comportamento da API em situações inesperadas ou de borda.
7. **Production readiness:** logging, métricas e conteinerização.
8. **Pattern ou algoritmo não implementado** deve ser **documentado** com o que poderia ser feito e os motivadores.

## Important Constraints

- Manter a **arquitetura hexagonal** e fazer o teste de arquitetura cobrir os contextos novos.
- Manter o **gate de cobertura ≥ 90%** (`./gradlew check`), senão o build e a CI quebram.
- A stack deve subir apenas com Docker (`make up`). Imagens com versões fixas.
- Não commitar segredos. As credenciais AWS vêm do ambiente (cadeia padrão do SDK): o compose e o Gradle definem `local`/`local`, que valem só para o ambiente local, assim como o `endpointOverride` do DynamoDB.
- Lacunas do template que o desafio exige tratar (ver *Non-Functional Requirements*):
  - O exemplo usa `Scan` por request. Para consultas, preferir `GetItem`/`Query` por chave.

## External Dependencies

| Dependência | Uso | Local |
|-|-|-|
| Amazon DynamoDB | Persistência | DynamoDB Local, `http://localhost:8000` (console `:8001`) |
| Kafka (Redpanda) | Entrada de eventos | `localhost:19092` (console `:8081`); cluster com `auto_create_topics_enabled=false` |
| GitHub Actions | CI (build, testes, docker, CodeQL) | `.github/workflows/` |
