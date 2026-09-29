# Mapa do Starter Kit — `itau-code-challange-starter-kit` (branch `kotlin`)

> Levantamento feito lendo **todos** os arquivos versionados do repositório (código, testes, build, Makefile, Docker, CI e infra).
> Objetivo: entender a arquitetura hexagonal, onde estender, o que o Makefile faz e onde ficam os testes, antes de implementar o desafio de **consulta de saldo**.

---

## 1. Visão geral em uma página

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          itau-code-challange-starter-kit                     │
│  Kotlin 2.3.21 · Java 21 · Spring Boot 4.1.0 · Gradle 9.5.1 (Kotlin DSL)     │
│  Jackson 3 (tools.jackson) · AWS SDK v2 (DynamoDB) · Spring Kafka            │
│  JUnit 6 · Mockito · MockMvc · Konsist · JaCoCo (gate 90% instruções)        │
├──────────────────────────────────────────────────────────────────────────────┤
│  Código de exemplo: um único bounded context chamado `hello`                 │
│    • GET /hello?name=X  → lê template aleatório do DynamoDB e formata        │
│    • tópico greeting-templates (Kafka) → grava template no DynamoDB          │
├──────────────────────────────────────────────────────────────────────────────┤
│  Infra local (docker-compose): app · DynamoDB Local · dynamodb-admin         │
│                                Redpanda (Kafka) · Redpanda Console · seeds   │
└──────────────────────────────────────────────────────────────────────────────┘
```

Inventário de arquivos:

| Área | Arquivos |
|-|-|
| Código de produção | 19 arquivos `.kt` em `src/main/kotlin` + `application.yaml` |
| Testes unitários | 12 classes / 30 testes em `src/test/kotlin` |
| Testes de integração | 2 classes / 3 testes em `src/integrationTest/kotlin` |
| Build | `build.gradle.kts`, `settings.gradle.kts`, `gradle/wrapper/*` |
| Containers | `Dockerfile` (multi-stage), `docker-compose.yml`, `.dockerignore` |
| Automação | `Makefile` (20 alvos) |
| Infra/seeds | `infra/dynamodb/*`, `infra/redpanda/*` |
| Chamadas manuais | `http/hello.http`, `http/http-client.env.json` |
| CI | `.github/workflows/{build,test,docker,codeql}.yml` |

---

## 2. Arquitetura hexagonal

### 2.1 Pacotes

```
src/main/kotlin/br/com/itau/challenge/
├── Application.kt                                  @SpringBootApplication (scan em br.com.itau.challenge..)
└── hello/                                          ← bounded context de exemplo
    ├── domain/
    │   ├── model/Greeting.kt                       data class (message)
    │   ├── model/GreetingTemplate.kt               data class (id, template)
    │   ├── exception/BlankRequesterNameException.kt
    │   └── exception/InvalidGreetingTemplateException.kt
    ├── port/
    │   ├── input/GetGreetingUseCase.kt             fun interface  (driving)
    │   ├── input/SaveGreetingTemplateUseCase.kt    fun interface  (driving)
    │   ├── output/GreetingTemplateProvider.kt      fun interface  (driven – leitura)
    │   └── output/GreetingTemplateRepository.kt    fun interface  (driven – escrita)
    ├── application/
    │   ├── GreetingService.kt                      @Service implements GetGreetingUseCase
    │   └── SaveGreetingTemplateService.kt          @Service implements SaveGreetingTemplateUseCase
    └── adapter/
        ├── input/web/GreetingController.kt         @RestController  GET /hello
        ├── input/web/dto/GreetingResponse.kt
        ├── input/kafka/GreetingTemplateConsumer.kt @KafkaListener   greeting-templates
        ├── input/kafka/dto/GreetingTemplateMessage.kt
        ├── output/dynamodb/DynamoDbConfig.kt       @Configuration → bean DynamoDbClient
        ├── output/dynamodb/DynamoDbGreetingTemplateProvider.kt   implements GreetingTemplateProvider (Scan)
        └── output/dynamodb/DynamoDbGreetingTemplateWriter.kt     implements GreetingTemplateRepository (PutItem)
```

### 2.2 O hexágono

```
      DRIVING (entrada)                 NÚCLEO                         DRIVEN (saída)

 HTTP GET /hello                                                                   
   │                                                                               
   ▼                                                                               
 GreetingController ──▶ «GetGreetingUseCase» ◀── GreetingService                   
 (adapter/input/web)      (port/input)             │  (application)                
                                                   ▼                               
                                        «GreetingTemplateProvider» ◀── DynamoDbGreetingTemplateProvider ──Scan──┐
                                              (port/output)              (adapter/output/dynamodb)              │
                                                                                                                ▼
 Kafka greeting-templates                                                                             ┌───────────────────┐
   │                                                                                                  │ DynamoDB          │
   ▼                                                                                                  │ GreetingMessages  │
 GreetingTemplateConsumer ──▶ «SaveGreetingTemplateUseCase» ◀── SaveGreetingTemplateService           └───────────────────┘
 (adapter/input/kafka)          (port/input)                      │  (application)                              ▲
                                                                  ▼                                             │
                                                   «GreetingTemplateRepository» ◀── DynamoDbGreetingTemplateWriter ──PutItem
                                                        (port/output)                (adapter/output/dynamodb)

      Todas as camadas usam domain/ (Greeting, GreetingTemplate, exceções).
      «X» = interface (porta).  A ◀── B  =  B implementa A.
```

### 2.3 Regra de dependência (e o que realmente é verificado)

```
   adapter ──────┐
      │          ▼
      │        port ───────▶ domain
      ▼          ▲             ▲
  application ───┘─────────────┘
```

Verificada por `src/test/kotlin/br/com/itau/challenge/hello/HexagonalArchitectureTest.kt` (Konsist):

| Regra | Verificada? |
|-|-|
| `domain` não depende de nenhuma outra camada | ✅ `domain.dependsOnNothing()` |
| `domain` não importa `org.springframework*` | ✅ teste dedicado |
| `port` não depende de `application` nem `adapter` | ✅ |
| `application` não depende de `adapter` | ✅ |
| `adapter` não depende de `application` (só de `port`) | ❌ **não verificado** — o diagrama do README sugere, mas o teste permite |
| `port`/`application` livres de Spring | ❌ não verificado (`application` usa `@Service` de propósito) |
| **Outros bounded contexts além de `hello`** | ❌ **escopo fixo em `br.com.itau.challenge.hello..`** |

> ⚠️ **Ponto crítico:** se o desafio for implementado num pacote novo (ex.: `br.com.itau.challenge.balance`), o teste de arquitetura **não cobre nada dele**. É preciso generalizar o escopo/camadas (ex.: `"..domain.."`, `"..port.."`) ou duplicar o teste.

### 2.4 Padrões idiomáticos do kit

- **Ports são `fun interface`** → nos testes, fakes viram lambdas (`GreetingTemplateProvider { "Hello, %s!" }`), sem Mockito.
- **Services usam `@Service`** e injeção por construtor; não conhecem HTTP/Kafka/DynamoDB.
- **DTOs vivem dentro do adapter** (`adapter/input/web/dto`, `adapter/input/kafka/dto`) e são convertidos para modelos de domínio no adapter.
- **Validação de negócio no service**, lançando exceções de domínio (`RuntimeException` puras).
- **Configuração via `@Value("\${...}")`** com defaults em `application.yaml` sobrescritos por env vars.
- **Kafka com `StringDeserializer`** + parse manual com o `ObjectMapper` (Jackson 3) do próprio Spring.
- Nomes de testes em crase: `` `should ... when ...` ``; estrutura Arrange / Act / Assert separada por linhas em branco.

### 2.5 Configuração (`src/main/resources/application.yaml`)

| Propriedade | Env var | Default local |
|-|-|-|
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:19092` |
| `spring.kafka.consumer.group-id` | `KAFKA_CONSUMER_GROUP_ID` | `hello-greeting-template-consumer` |
| `spring.kafka.consumer.auto-offset-reset` | — | `earliest` |
| `dynamodb.endpoint` | `DYNAMODB_ENDPOINT` | `http://localhost:8000` |
| `dynamodb.region` | `DYNAMODB_REGION` | `us-east-1` |
| `dynamodb.table-name` | `GREETING_TABLE_NAME` | `GreetingMessages` |
| `greeting-templates.topic-name` | `GREETING_TEMPLATES_TOPIC` | `greeting-templates` |

---

## 3. Pontos de extensão

### 3.1 Mapa "quero adicionar X → mexo em Y"

| Quero adicionar… | Onde mexer |
|-|-|
| **Novo bounded context** (ex.: saldo) | `src/main/kotlin/br/com/itau/challenge/<ctx>/{domain,port/{input,output},application,adapter/...}` — espelhar `hello/`. **Ajustar `HexagonalArchitectureTest`** para cobri-lo. |
| **Novo caso de uso** | `port/input/<X>UseCase.kt` (`fun interface`) + `application/<X>Service.kt` (`@Service`) |
| **Nova dependência externa** | `port/output/<X>.kt` (`fun interface`) + implementação em `adapter/output/<tecnologia>/` |
| **Novo endpoint REST** | `adapter/input/web/<X>Controller.kt` + `dto/`; exemplo em `http/*.http` |
| **Novo consumidor Kafka** | `adapter/input/kafka/<X>Consumer.kt` com `@KafkaListener(topics = ["\${<prop>}"])` → propriedade em `application.yaml` → env var no serviço `app` do `docker-compose.yml` → criação do tópico (`infra/redpanda/seed.sh` ou `make kafka-topic-create`) |
| **Nova tabela DynamoDB** | Nova propriedade em `application.yaml` (hoje só existe `dynamodb.table-name`, singular) → `infra/dynamodb/seed.sh` (hoje cria **uma** tabela, fixa, com PK `id`) → env var no `docker-compose.yml` |
| **Tratamento de erros HTTP** | Não existe `@RestControllerAdvice` — criar em `adapter/input/web/` |
| **Tratamento de erros Kafka / DLT** | Não existe `DefaultErrorHandler`/DLT customizado — criar bean de configuração no adapter Kafka |
| **Novo alvo de automação** | `Makefile` — qualquer alvo com `## descrição` aparece sozinho no `make help` |
| **Exclusão de cobertura** | `jacocoCoverageExclusions` em `build.gradle.kts` |
| **Novo job de CI / serviço no job de integração** | `.github/workflows/test.yml` (lista de serviços do `docker compose up` é explícita) |

### 3.2 Esqueleto sugerido para o desafio de saldo

Os geradores de eventos já existentes (`infra/redpanda/produce-*-events.sh`) entregam o formato de entrada do domínio real:

```jsonc
// make kafka-produce-transactions-events TOPIC=... COUNT=...
{
  "transaction": { "id": "uuid", "type": "CREDIT|DEBIT", "amount": 123.45,
                   "currency": "BRL", "status": "APPROVED|DECLINED",
                   "timestamp": 1727600000000000 },          // epoch em MICROssegundos
  "account":     { "id": "uuid", "owner": "uuid", "created_at": 1500000000000000,
                   "status": "ENABLED",
                   "balance": { "amount": 9876.54, "currency": "BRL" } }
}

// make kafka-produce-accounts-events TOPIC=... COUNT=...
{ "account": { "id": "uuid", "owner": "uuid", "created_at": 1727600000000000,
               "status": "ENABLED|DISABLED" } }
```

Isso sugere um mapeamento natural para o hexágono:

```
  Kafka: transactions/accounts ──▶ adapter/input/kafka/*Consumer
                                        │
                                        ▼
                                  port/input: Apply…UseCase ──▶ application ──▶ port/output: Balance/Account repo ──▶ adapter/output/dynamodb
                                                                                                                          │
  HTTP GET /accounts/{id}/balance ─▶ adapter/input/web ─▶ port/input: GetBalanceUseCase ─▶ application ─▶ port/output ────┘
```

Observações para o design (sem decidir nada ainda):
- IDs são UUID aleatórios a cada execução — eventos de transação e de conta **não compartilham** `account.id` entre si.
- `timestamp`/`created_at` estão em **microssegundos** (`date +%s%6N`).
- `amount` é decimal com 2 casas → usar `BigDecimal` no domínio.
- Tópicos Kafka precisam ser criados explicitamente (`auto_create_topics_enabled=false`).
- Evento `transaction` já traz `account.balance` — a spec dirá se é fonte da verdade ou se o saldo é recalculado.

---

## 4. O que o Makefile faz

Variáveis: `IMAGE=itau-hello-world`, `COMPOSE=docker compose`, `HTTP_DIR=http`, `COMPOSE_PROJECT=<nome do diretório>`, `PARTITIONS?=1`, `COUNT?=100`. Alvo padrão: `help`.

### 4.1 Aplicação

| Alvo | Comando real | Efeito |
|-|-|-|
| `help` | `grep` + `awk` sobre `##` | Lista alvos documentados |
| `build` | `docker build --target runtime -t itau-hello-world .` | Imagem final (JRE + `app.jar`) |
| `run` | `docker compose up --build` | Stack inteira em foreground |
| `up` | `docker compose up --build -d` | Stack inteira em background |
| `logs` | `docker compose logs -f` | Logs de **todos** os serviços (não só da app) |
| `stop` | `docker compose down` | Remove containers da stack |
| `http` | `docker run node:20-alpine npx httpyac send hello.http --all -e docker` | Executa as requisições do `.http` contra `host.docker.internal:8080` |

### 4.2 DynamoDB

| Alvo | Efeito |
|-|-|
| `db-up` | `up -d dynamodb dynamodb-seed dynamodb-admin` → DynamoDB in-memory + console (8001) + seed |
| `db-seed` | Reexecuta `infra/dynamodb/seed.sh` (cria tabela se faltar; `batch-write-item` sobrescreve os 5 itens) |
| `db-scan` | `aws dynamodb scan --table-name GreetingMessages` (nome **fixo** no Makefile) |
| `db-down` | `stop` dos 3 serviços |

### 4.3 Kafka / Redpanda

| Alvo | Efeito |
|-|-|
| `kafka-up` | `up -d redpanda redpanda-seed redpanda-console` → broker + console (8081) + `config.sh && seed.sh` |
| `kafka-seed` | Reexecuta o seed: cria o tópico se faltar e **republica** as 5 mensagens (append-only, duplica) |
| `kafka-topic-create NAME= [PARTITIONS=1]` | `rpk topic create` (valida `NAME`) |
| `kafka-produce-accounts-events TOPIC= [COUNT=100]` | Gera eventos de conta aleatórios |
| `kafka-produce-transactions-events TOPIC= [COUNT=100]` | Gera eventos transação+conta aleatórios |
| `kafka-consume TOPIC=` | `timeout 5 rpk topic consume` — imprime o que existe e sai |
| `kafka-down` | `stop` dos 3 serviços |

`infra/redpanda/config.sh` roda antes do seed e: desliga `auto_create_topics_enabled`, e força `core_balancing_continuous=false` / `partition_autobalancing_mode=node_add` (evita consumir licença trial Enterprise).

### 4.4 Testes e limpeza

| Alvo | Efeito |
|-|-|
| `test` | `docker build --target test` → roda `./gradlew check` **durante o build da imagem** (unit + gate JaCoCo). Não precisa de infra. |
| `integration-test` | depende de `db-up kafka-up` → `docker compose wait dynamodb-seed redpanda-seed` → `./gradlew integrationTest` **no host** |
| `clean-containers` | `down --remove-orphans --volumes` + `docker rm -f` por label do projeto |
| `clean` | `docker rmi -f itau-hello-world itau-hello-world-test` |

### 4.5 Fluxo do `Dockerfile` (multi-stage)

```
 eclipse-temurin:21-jdk ── base (copia gradle + src)
                              ├── test     : ./gradlew check        ◀── make test
                              └── builder  : ./gradlew bootJar → app.jar
 eclipse-temurin:21-jre ── runtime : java -jar app.jar (8080)       ◀── make build / compose app / CI docker
```

### 4.6 Pegadinhas do Makefile

- `make test` roda dentro de um `docker build`: se nada mudou, o BuildKit **usa cache e não reexecuta os testes**; os relatórios (JaCoCo/JUnit) ficam **dentro da camada**, não saem para `build/` no host.
- `make integration-test` executa `./gradlew` **no host** → exige JDK local, apesar de o README dizer que Docker é o único pré-requisito.
- `make http` diz "Call all .http files", mas o comando está fixo em `hello.http` — novos arquivos `.http` não serão executados sem ajustar o alvo.
- `db-scan` tem o nome da tabela fixo (`GreetingMessages`).
- `help` formata com `%-12s`: nomes longos (`kafka-produce-transactions-events`) ficam desalinhados (cosmético).
- Referências antigas a `make redpanda-up` / `make redpanda-topic-create` em comentários (`GreetingTemplateConsumerIntegrationTest`, `infra/redpanda/config.sh`) — os nomes atuais são `kafka-*`.

---

## 5. Onde ficam os testes

### 5.1 Layout e source sets

```
src/
├── test/kotlin/…                    source set "test"            → ./gradlew test / check  (make test, CI unit-test)
│   ├── ApplicationTests.kt                 @SpringBootTest contextLoads
│   └── hello/
│       ├── HexagonalArchitectureTest.kt    Konsist (2 testes)
│       ├── domain/model/                   GreetingTest (3), GreetingTemplateTest (3)
│       ├── domain/exception/               BlankRequesterName… (1), InvalidGreetingTemplate… (1)
│       ├── application/                    GreetingServiceTest (7), SaveGreetingTemplateServiceTest (3)
│       └── adapter/
│           ├── input/web/                  GreetingControllerTest (3)  @SpringBootTest + MockMvc + @MockitoBean
│           ├── input/kafka/                GreetingTemplateConsumerTest (1)  lambda fake + JsonMapper real
│           └── output/dynamodb/            …ProviderTest (4), …WriterTest (1)  Mockito no DynamoDbClient
│
└── integrationTest/kotlin/…         source set "integrationTest" → ./gradlew integrationTest (make integration-test, CI integration-test)
    └── hello/adapter/
        ├── input/kafka/GreetingTemplateConsumerIntegrationTest.kt     @SpringBootTest real + Redpanda real; repo mockado
        └── output/dynamodb/DynamoDbGreetingTemplateIntegrationTest.kt DynamoDB Local real; sem Spring
```

Os testes espelham exatamente o pacote de produção — um arquivo de teste por classe.

### 5.2 Estratégia por camada

| Camada | Técnica | Infra externa? |
|-|-|-|
| domain | asserts diretos em data classes/exceções | não |
| application | **fakes via lambda** das `fun interface` de output | não |
| adapter web | `@SpringBootTest` + `@AutoConfigureMockMvc`, port de saída com `@MockitoBean` | não (Kafka listener sobe, mas só loga se não houver broker) |
| adapter kafka (unit) | chama `consume(payload)` direto com fake do use case | não |
| adapter dynamodb (unit) | `mock(DynamoDbClient)` + `ArgumentCaptor` | não |
| arquitetura | Konsist | não |
| integração DynamoDB | cliente real → DynamoDB Local, limpa item no `@AfterEach` | **sim** (`make db-up`) |
| integração Kafka | contexto Spring real, `KafkaTemplate` publica, `verify(timeout(10s))` no repo | **sim** (`make kafka-up`) |

### 5.3 Wiring no Gradle (`build.gradle.kts`)

- Source set `integrationTest` enxerga `main` **e** `test` (reutiliza helpers de teste); herda `testImplementation`/`testRuntimeOnly`.
- `integrationTest` **não** faz parte de `check`.
- Todo `Test` task é `finalizedBy(jacocoTestReport)`, e `jacocoTestReport` `dependsOn(test)` → rodar `./gradlew integrationTest` **também roda os testes unitários**.
- Cobertura considera só a execução do `test` (integração não soma).
- Gate: `jacocoTestCoverageVerification` ≥ **90% de instruções** (exclui só `Application`/`ApplicationKt`), plugado em `check`. Um resumo tabular é impresso no console.
- Relatório: `build/reports/jacoco/test/html/index.html`.

### 5.4 CI (`.github/workflows`)

| Workflow | Gatilho | O que roda |
|-|-|-|
| `build.yml` | push/PR em `main`, `kotlin` | `./gradlew assemble testClasses` |
| `test.yml` | idem | job `unit-test`: `./gradlew check` + upload JaCoCo/test-results → job `integration-test`: compose (`dynamodb`, `dynamodb-seed`, `redpanda`, `redpanda-seed`) + `wait` + `./gradlew integrationTest` |
| `docker.yml` | idem | `docker build --target runtime` |
| `codeql.yml` | idem + cron semanal | análise CodeQL java-kotlin |

---

## 6. Achados, lacunas e riscos

| # | Achado | Impacto |
|-|-|-|
| 1 | **Não há `@RestControllerAdvice`.** `BlankRequesterNameException` é `RuntimeException` sem `@ResponseStatus` → `GET /hello?name=%20%20` responde **500**, embora `http/hello.http` diga "should respond with a JSON 400". `name` ausente dá 400 (erro do próprio Spring). Não há teste cobrindo nome em branco no controller. | Modelo a **não** copiar; criar handler de erro no desafio |
| 2 | **Sem error handler/DLT no Kafka.** Mensagem inválida (JSON quebrado ou template em branco) cai no `DefaultErrorHandler` padrão: retenta algumas vezes e descarta com log. | Definir política de erro/DLT para eventos de saldo |
| 3 | `HexagonalArchitectureTest` limitado ao pacote `hello` e não restringe `adapter → application`. | Novo contexto fica sem guarda de arquitetura |
| 4 | `DynamoDbConfig` está dentro de `hello/adapter/output/dynamodb`, com credenciais fixas `local/local` e `endpointOverride` sempre ativo. | Bean compartilhado entre contextos; não é prod-ready |
| 5 | `DynamoDbGreetingTemplateProvider` faz **Scan** completo a cada request. | Não usar como padrão para consulta de saldo (usar `GetItem`/`Query` por chave) |
| 6 | `seed.sh` do DynamoDB cria **uma** tabela fixa; `application.yaml` tem uma única `table-name`. | Precisa ser estendido para novas tabelas |
| 7 | Serviço `app` no compose usa `depends_on` simples (sem `condition: service_completed_successfully`). | App pode subir antes da tabela/tópico existirem |
| 8 | `auto-offset-reset: earliest` + group id fixo. | Primeiro start consome todo o histórico do tópico |
| 9 | `make test` cacheado pelo BuildKit; `make integration-test` precisa de JDK no host; `make http` fixo em `hello.http`. | Ver seção 4.6 |
| 10 | Comentários desatualizados (`redpanda-up`, descrição do task `integrationTest` só cita `db-up`). | Cosmético |

---

## 7. Portas e consoles locais

| Serviço | Porta host | Uso |
|-|-|-|
| app | 8080 | API |
| dynamodb | 8000 | DynamoDB Local (in-memory, `-sharedDb`) |
| dynamodb-admin | 8001 | Console web DynamoDB |
| redpanda | 19092 | Kafka externo (interno: `redpanda:9092`) |
| redpanda-console | 8081 | Console web Kafka |

Loop rápido de dev: `make db-up && make kafka-up`, esperar os seeds, rodar `Application.kt` pela IDE (defaults do `application.yaml` já apontam para `localhost`).
