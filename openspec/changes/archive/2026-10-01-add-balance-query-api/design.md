## Context

- O contexto `balance` já tem o snapshot de saldo, o port `BalanceProvider` (`findByAccountId`, `null` para conta sem snapshot, leitura fortemente consistente por `GetItem`) e a classificação de falhas do armazenamento (`TransientStorageException`, `PermanentStorageException`, `StorageUnavailableException`). Falta o que expõe isso: o caso de uso de consulta e o adapter HTTP.
- `GetBalanceUseCase` **não existe** no código nem em nenhuma spec; esta change o cria.
- O kit não tem `@RestControllerAdvice` (lacuna registrada no `project.md`): exceção de domínio vira `500` com o corpo padrão do Boot. O único controller, `GreetingController`, não trata erro.
- Há **um** `DynamoDbClient`, com `api-call` de 3 s e 3 tentativas do SDK, pensado para a escrita e o `hello`. Uma leitura que pode levar 3 s no pior caso, mais 3 tentativas, não cabe num endpoint síncrono consumido por outros sistemas.
- O circuit breaker da escrita (`balance-storage`) protege o `BalanceRepository` e é consumido pelo consumer, que pausa as partições quando ele abre. A leitura não passa por ele.
- O enunciado define o request e a resposta (`GET /balances/{accountId}`, cinco campos), mas não define o status HTTP de conta inexistente nem de `accountId` inválido, o formato exato de `updated_at` além do exemplo, nem o que é "mais atual" na borda HTTP.

## Goals / Non-Goals

**Goals:**
- `GetBalanceUseCase` e o endpoint com o contrato do enunciado, byte a byte nos nomes e tipos.
- Modelo de erro único (`problem+json` com `traceId`) com os status `400`, `404`, `503` (com `Retry-After`) e `500`.
- Leitura com orçamento de latência explícito: timeouts curtos, no máximo 1 retry, circuit breaker com a classificação de erros que a escrita já usa.
- Justificar a ausência de cache de saldo.
- OpenAPI/Swagger gerado do código; testes de contrato do adapter e teste de integração ponta a ponta.

**Non-Goals:**
- Métricas, logs estruturados e alertas (production readiness). A change só garante o `traceId` no MDC e no corpo de erro, e o log de cada `500` e `503`.
- Tracing distribuído completo (spans, exportação). Ver D5.
- Autenticação e autorização, rate limiting, paginação e histórico de saldo.
- Cache de leitura (D8) e `ETag`/GET condicional.

## Decisions

### D1. Caso de uso e adapter

- **Escolha:** `fun interface GetBalanceUseCase { fun getBalance(accountId: AccountId): BalanceSnapshot? }`, com `GetBalanceService` (`@Service`, exceção do Art. 1) que só chama `BalanceProvider.findByAccountId`. O controller fala com o port de entrada, traduz o resultado para o DTO e a ausência para `404`.
- **Ausência no tipo (Art. 3):** conta sem snapshot é resultado esperado, então é `null`, e não uma `AccountNotFoundException`. Quem decide que ausência vira `404` é o adapter, que conhece o protocolo.
- **Por que o serviço existe sendo uma delegação:** o adapter não pode depender do port de saída (`adapter → port de entrada ← application`), e a regra "conta sem saldo é ausência, falha de armazenamento sobe" fica numa fronteira testável sem HTTP.
- **Pattern (Art. 9):** Use Case do hexagonal. Nenhum outro coube: sem regra de negócio nova, KISS prevalece.

### D2. Contrato HTTP

- **Rota:** `GET /balances/{accountId}`, no **plural**, como no enunciado (*Contrato de request*, o exemplo e a nota 1 da transcrição). O pedido desta change escreveu `/balance/{accountId}`; o enunciado prevalece (Art. 11) e o pedido manda o contrato "EXATO do enunciado".
- **Resposta:** DTO `BalanceResponse(id, owner, balance(amount, currency), updated_at)`, com `@JsonProperty("updated_at")` e nenhum outro campo. `id` e `owner` saem de `AccountId`/`OwnerId` em minúsculas e forma canônica.
- **`balance.amount` como número:** `BigDecimal` de ponta a ponta, escrito pelo Jackson 3 como número JSON. O `Money` já garante a escala da moeda (`183.10`), e com escala fixa a notação do `BigDecimal` nunca é científica. Um teste fixa o texto cru (`"amount":183.10`).
- **Ambiguidade do enunciado: formato de `updated_at`. Decidido:** o `transaction.timestamp` do evento (decisão já tomada na ingestão), convertido para `America/Sao_Paulo`, truncado para **milissegundos** e escrito com três dígitos fixos de fração e offset (`2025-07-04T12:02:44.589-03:00`), o formato do exemplo do enunciado (`2025-07-05T18:04:13.433-03:00`).
  - **Por que o offset de Brasília:** o exemplo do enunciado o usa, e o cliente típico é brasileiro. Usa-se o `ZoneId` (e não o offset fixo `-03:00`) para o offset continuar correto para datas anteriores a 2019, quando havia horário de verão.
  - **Por que milissegundos:** é a precisão do exemplo. Os microssegundos ficam no DynamoDB e na `SnapshotVersion`, onde decidem a ordem; a resposta não precisa deles. Truncar (e não arredondar) mantém o instante sempre menor ou igual ao do evento.
  - **Três dígitos fixos:** `DateTimeFormatter.ISO_OFFSET_DATE_TIME` omite a fração zero e os zeros finais (`.43`), e o exemplo do enunciado mostra sempre três dígitos. Usa-se o padrão `uuuu-MM-dd'T'HH:mm:ss.SSSXXX`.
  - **Alternativa descartada:** UTC com `Z` e microssegundos. É ISO 8601 válido, mas se afasta do exemplo que o enunciado fixa.
- **Schema de erro só para documentação:** `ProblemDetailSchema` descreve no OpenAPI o corpo que o `ProblemDetail` do Spring produz em tempo de execução (o `traceId` é um membro de extensão, que o springdoc não enxerga na classe do Spring); ele nunca é instanciado. O DTO interno do saldo se chama `MoneyResponse`, e não `BalancePayload`, para um nome não servir a dois conceitos (o DTO do Kafka já usa `BalancePayload`).
- **Idioma e documentação:** a interface `BalanceApi` concentra as anotações OpenAPI e o mapeamento (`@GetMapping`), e `BalanceController` a implementa só com a lógica. Assim as ~25 linhas de anotação de status não se misturam com o fluxo (Art. 8). O Spring herda as anotações de mapeamento da interface.

### D3. Status HTTP: conta inexistente e `accountId` inválido

- **Ambiguidades do enunciado: resolvidas.**
  - **Conta sem snapshot → `404`.** O recurso identificado pela URL (`/balances/{accountId}`) não existe. É o que `GET` num recurso ausente significa no HTTP (RFC 9110, 15.5.5), e um cliente pode distinguir "não existe" de "falhou" sem ler o corpo.
    - **Alternativas descartadas:** `200` com saldo `0.00` (mente: uma conta sem evento não tem saldo zero, tem saldo desconhecido, e o cliente não distinguiria de uma conta zerada); `204` (o contrato de sucesso tem corpo, e `204` é para "existe e está vazio").
  - **`accountId` que não é UUID → `400`.** A requisição é malformada antes de qualquer consulta (RFC 9110, 15.5.1). `422` descreve requisição bem formada com semântica inválida, e aqui falha a própria sintaxe do identificador.
- **UUID estrito:** o adapter aceita só a forma canônica de 36 caracteres (hexadecimal, 8-4-4-4-12), em maiúsculas ou minúsculas. `UUID.fromString` aceita `1-1-1-1-1`, e o enunciado tipa o parâmetro como UUID. Uma expressão regular de uma linha resolve, no adapter: `parseAccountId` devolve `AccountId?` (texto inválido é ausência esperada no tipo, Art. 3) e o controller a traduz em `400`. O `AccountId` do domínio recebe um `UUID` já válido e não muda.
  - **Alternativa descartada:** `@Pattern` no `@PathVariable` com `spring-boot-starter-validation`. Seria "solução pronta" (Art. 10), mas traz uma dependência e um novo tipo de exceção para uma regra de uma linha; o custo da dependência supera o de escrevê-la.

### D4. Modelo de erro: `problem+json` com `traceId`

- **Escolha:** `ProblemDetail` do Spring (RFC 9457) e um `@RestControllerAdvice` que estende `ResponseEntityExceptionHandler`. A base já traduz as exceções do próprio MVC (`405`, `404` de rota, `406`, `400` de parâmetro ausente) para `problem+json`; o advice acrescenta `traceId` a todas (`handleExceptionInternal`) e trata as exceções do núcleo.
- **Tabela de tradução:**

| Origem | Status | Observação |
|-|-|-|
| `parseAccountId` devolve `null` | `400` | o controller lança `ResponseStatusException` do Spring, que a base do advice já traduz |
| `getBalance` devolve `null` | `404` | idem: `ResponseStatusException(NOT_FOUND)`, sem exceção de domínio |
| `TransientStorageException`, `StorageUnavailableException` | `503` + `Retry-After` | log em `WARN` com o `traceId` |
| `PermanentStorageException` e qualquer outra `Exception` | `500` | `detail` genérico; causa só no log, em `ERROR`, com o `traceId` |

  `BalanceStorageException` é `sealed`, então o `when` que mapeia suas três subclasses é exaustivo, sem `else`: um subtipo novo quebra a compilação até alguém decidir seu status (Art. 2, OCP).
- **`type` fica `about:blank`.** A RFC 9457 admite isso quando o status HTTP basta como tipo; `title` é a frase padrão do status, e `detail` carrega a diferença. Tipos próprios (`urn:...`) seriam especulação (YAGNI) sem um cliente que os consuma.
- **`Retry-After`:** segundos inteiros, de `balance.api.retry-after` (`BALANCE_API_RETRY_AFTER`, padrão `5s`). Um valor único para os dois casos de `503`: é uma dica e não uma promessa. Com o circuito aberto o ideal seria o tempo restante de `waitDurationInOpenState` (30 s), mas o Resilience4j não o expõe de forma estável, e levar a duração de um adapter para o outro violaria a regra de dependência. Um cliente que voltar antes recebe outro `503` rápido (o circuito falha sem tocar o DynamoDB), que é barato.
- **Efeito global, deliberado:** o advice vale para a aplicação inteira, pois o Spring não entrega `problem+json` para erros de roteamento a um advice restrito a um controller (a exceção sai antes de existir um handler). O `/hello` passa a devolver `problem+json` nos seus erros, e a lacuna "sem `@RestControllerAdvice`" do template deixa de existir. As exceções de domínio do `hello` continuam sendo `500`, como antes, agora com o corpo padronizado. O advice mora em `balance/adapter/input/web`, porque hoje só o `balance` tem contrato de erro; deve migrar para um pacote compartilhado quando um segundo contexto precisar dele.
- **Pattern (Art. 9):** Exception Translator (`@ControllerAdvice`) sobre Problem Details. **Solução pronta (Art. 10):** `ProblemDetail`, `ResponseEntityExceptionHandler`, `ResponseStatusException` e `ErrorResponse` do Spring Framework 7. Nada próprio além da tabela acima.

### D5. `traceId`

- **Escolha:** um `OncePerRequestFilter`, com precedência máxima, que define o `traceId` da requisição e o coloca em um atributo da requisição e no MDC (`traceId`), e o remove no `finally`. O valor é o `trace-id` do cabeçalho `traceparent` (W3C Trace Context) quando ele é válido (`00-<32 hex>-<16 hex>-<2 hex>`, com `trace-id` e `parent-id` não zerados) e, caso contrário, 32 dígitos hexadecimais aleatórios. O advice lê o atributo para preencher o corpo, e `logging.pattern.correlation` (`[%X{traceId:-}] `) imprime o MDC em todo log.
- **Por que reaproveitar o `traceparent`:** um gateway ou chamador que já propaga o contexto continua com o mesmo id de ponta a ponta, e o formato é o mesmo que um tracer adotará depois. Trocar o filtro por um tracer não muda o campo do corpo.
- **Alternativa avaliada e descartada (Art. 10): Micrometer Tracing** (`spring-boot-starter-opentelemetry` ou a ponte Brave/OTel). É a solução pronta para tracing distribuído e preencheria o MDC sozinha. Custo: duas ou três dependências novas, configuração de amostragem e exportação, e o Actuator, tudo para um único campo de correlação. O pedido desta change é `traceId` no corpo de erro; spans, exportadores e métricas pertencem à change de production readiness, que deve substituir o filtro por essa solução. Registrado em R5.
- **Por que o corpo do erro, e não um cabeçalho de resposta:** o pedido manda o `traceId` no corpo, e o cliente que reporta um erro o tem à mão. Um cabeçalho `X-Trace-Id` na resposta de sucesso é um acréscimo que ninguém pediu (YAGNI).

### D6. Cliente DynamoDB de leitura: exceção explícita à regra do cliente único

- **Problema:** o pedido exige "timeout curto no cliente Dynamo para leitura" e "no máximo 1 retry rápido". O cliente atual tem `apiCall` de 3 s e 3 tentativas, e a mesma instância atende a escrita, que **precisa** de mais folga.
- **Escolha:** um segundo `DynamoDbClient` `readDynamoDbClient` (perfil de leitura genérico, sem conhecimento de `balance`), definido no mesmo `DynamoDbConfig` do kit, com o cliente atual como `@Primary` (a injeção do `hello` e do writer, por tipo e sem qualificador, não muda). `DynamoDbBalanceProvider` o recebe por `@Qualifier`. As propriedades `dynamodb.read.{timeouts,retry}` reaproveitam os tipos `Timeouts` e `Retry`, com variáveis `DYNAMODB_READ_*`.
- **Padrões e valores:**

| Parâmetro | Escrita (atual) | Leitura (novo) |
|-|-|-|
| `connection` | 500 ms | 200 ms |
| `socket` | 1 s | 300 ms |
| `api-call-attempt` | 1 s | 300 ms |
| `api-call` | 3 s | 800 ms |
| `max-attempts` | 3 | 2 (1 retry) |

- **Orçamento de latência da leitura:** o pior caso é `api-call`, 800 ms: duas tentativas de até 300 ms mais um backoff de até 100 ms entre elas. O `GetItem` por chave leva milissegundos num DynamoDB saudável, então 300 ms por tentativa já é muita folga; estourar indica um problema real e não vale esperar mais. O enunciado não fixa um SLO, então o orçamento é decisão desta change.
- **Retry rápido:** o backoff do SDK vira constante pequena (`exponentialDelay(50 ms, 100 ms)`, para falha comum e para throttling; o padrão do SDK 2.46 usa uma base de 100 ms e de 1 s para throttling, que consumiria o orçamento). Não é configurável (YAGNI).
- **Invariantes na construção (`require`, Art. 3):** `maxAttempts` de 1 a 2, e `maxAttempts × api-call-attempt ≤ api-call`. Configuração incoerente derruba a subida com mensagem útil, em vez de degradar em silêncio. A segunda regra existe porque um `api-call` menor que as tentativas truncaria o retry sem avisar.
- **Retry idempotente:** `GetItem` é leitura pura, então repetir é seguro. A classificação transitória/permanente já existente (`translatingSdkFailures`) vale para o cliente novo sem mudança, e falha permanente não é repetida pelo SDK.
- **Alternativas descartadas:**
  - **Um cliente só, com `overrideConfiguration` por requisição.** O SDK 2.46 permite sobrescrever `apiCallTimeout` e `apiCallAttemptTimeout` por requisição, mas **não** o número de tentativas nem o backoff; o "no máximo 1 retry" ficaria sem garantia. Também não muda `connection` nem `socket`.
  - **Um retry próprio no adapter sobre o cliente atual.** Multiplicaria as tentativas (3 do SDK por 2 do adapter = 6) e duplicaria o que o SDK já faz.
  - **`resilience4j-retry`.** Mesma duplicação, e uma dependência nova para algo que o AWS SDK, a solução pronta mais próxima na ordem do Art. 10, já oferece.
- **Divergência explícita da regra do `project.md`** ("o `DynamoDbClient` do kit é o único da aplicação"): passa a haver dois, e a regra é reescrita no `project.md` e no `context:` ("um cliente por perfil de acesso, ambos definidos em `DynamoDbConfig`"). **Custo:** um segundo pool de conexões HTTP, pequeno e sem uso fora da leitura.

### D7. Circuit breaker de leitura

- **Escolha:** `CircuitBreakerBalanceProvider` (Decorator do `BalanceProvider`, mesmo molde de `CircuitBreakerBalanceRepository`), exposto como `@Primary` para o `GetBalanceService` receber o provider protegido. Com o circuito aberto lança `StorageUnavailableException`, que o adapter traduz para `503` (D4).
- **Classificação compartilhada:** a definição do que conta como falha (`TransientStorageException` registrada, `PermanentStorageException` ignorada, janela e limiar de `balance.circuit-breaker.*`) é extraída para **uma** função usada pelos dois circuitos. É conhecimento único (DRY), e um teste parametrizado prova que os dois a aplicam. Um resultado `null` (conta inexistente) é sucesso: `404` é resposta esperada e não pode abrir o circuito.
- **Instância própria, e não o circuito de escrita:**
  - o consumer **pausa as partições** quando o circuito de escrita abre; um circuito compartilhado deixaria uma rajada de consultas lentas pausar a ingestão, e o inverso faria a ingestão derrubar a API;
  - as duas janelas se misturariam, e a de maior tráfego dominaria a taxa de falhas;
  - as chamadas de sonda do estado semiaberto seriam disputadas entre a API e o consumer;
  - os dois clientes têm perfis de timeout diferentes, então "falha" não significa a mesma coisa.
  Numa queda real do DynamoDB os dois abrem, o que é o esperado; a diferença é que cada lado se recupera por conta própria.
- **Nomes (Art. 8):** com dois `CircuitBreaker` no contexto, o bean existente `balanceCircuitBreaker` passa a se chamar `balanceWriteCircuitBreaker`, o novo é `balanceReadCircuitBreaker`, e a injeção usa `@Qualifier`. É a regra do escoteiro dentro do escopo: dois beans do mesmo tipo com um nome que parece o geral seriam um nome para dois conceitos. O nome do circuito da escrita (`balance-storage`) não muda; o novo é `balance-storage-read`.
- **Pattern (Art. 9):** Circuit Breaker e Decorator, como na change anterior. A biblioteca (Resilience4j 2.4.0) já está no build, sem dependência nova.

### D8. Sem cache de saldo

- **Decisão:** nenhum cache de saldo, nem local nem distribuído, e `Cache-Control: no-store` em toda resposta de `/balances/**`, aplicado por um `NoStoreFilter` (`OncePerRequestFilter` restrito a `/balances` e `/balances/**`, com `CacheControl.noStore()` do Spring), em um único ponto.
  - **Por que filtro e não `WebContentInterceptor`** (a solução pronta do Spring MVC, Art. 10): o interceptor só roda quando há handler, e o `405` e a rota inexistente nunca chegam a um; um teste mostrou que o `405` ficava sem o cabeçalho. O filtro cobre toda resposta sob o caminho, com uma classe de 10 linhas.
- **Por quê:**
  1. **O requisito é o saldo mais atual.** O enunciado pede "o saldo mais atual" e avalia que "o saldo reflete a transação mais recente". A leitura já é fortemente consistente (`consistentRead`, `add-balance-repository` design D4) para que uma consulta logo após um `Applied` nunca devolva o snapshot anterior. Qualquer cache, mesmo com TTL curto, reintroduz leitura obsoleta e quebra essa garantia.
  2. **A invalidação é o problema difícil.** Com várias instâncias, uma gravação num nó teria de invalidar o cache dos demais, e os eventos chegam fora de ordem. Um cache seria uma segunda fonte de verdade que pode divergir do `SnapshotVersion`.
  3. **O custo que o cache evitaria é baixo.** É um `GetItem` por chave primária, sem `Scan`, que escala horizontalmente e custa milissegundos. Não há leitura cara para amortizar.
  4. **Saldo é dado financeiro sensível.** Um intermediário que guardasse a resposta serviria um valor velho a outro cliente.
- **Alternativas descartadas:** cache local com TTL (Caffeine/Spring Cache: janela de inconsistência por instância); DAX (suas leituras de item são eventualmente consistentes, e as fortemente consistentes passam direto, sem ganho); `ETag` e GET condicional (a leitura ao DynamoDB continuaria acontecendo, só se economizaria o corpo).
- **Quando reconsiderar:** se a leitura passar a ser o gargalo medido (custo ou latência), a evolução é um cache com invalidação por evento ou DAX para leituras tolerantes, com a regra de consistência dita no contrato. Documentado como evolução (critério 8 do enunciado).

### D9. Documentação OpenAPI: dependência nova (Art. 10)

- **Escolha:** `org.springdoc:springdoc-openapi-starter-webmvc-ui` **3.1.1**, em `implementation`, **com versão fixa** (o BOM do Boot 4.1 não a gerencia). A documentação vem das anotações em `BalanceApi` (`@Operation`, `@ApiResponse`, `@Header` do `Retry-After`) e dos DTOs, e a especificação sai em `/v3/api-docs`, a UI em `/swagger-ui.html`.
- **Evidência:** 3.1.1 publicada em 06/09/2026 (3.1.0 em 01/08/2026; a linha 3.x, a primeira para o Boot 4, começou em 3.0.0 em 21/11/2025). O POM de 3.1.1 tem o `spring-boot-starter-parent` **4.1.0**, a mesma versão do kit, e `swagger-core` 2.2.55. É o gerador padrão de OpenAPI para Spring MVC, com lançamentos mensais.
- **Custo contra implementar:** uma linha no build, contra manter à mão uma especificação YAML que se desvia do código e uma Swagger UI embutida. O critério do pedido é "gerada a partir do código", e a geração por anotação é exatamente isso.
- **Alternativas avaliadas:** o Spring Framework não gera OpenAPI; Spring REST Docs gera documentação a partir de testes, não uma especificação OpenAPI publicada; escrever o YAML à mão (descartada, pelo desvio).
- **Interação com Jackson 3:** o `swagger-core` usa Jackson 2 internamente, e o resto do projeto Jackson 3. Os dois coexistem (pacotes diferentes), e o código do projeto continua a usar só `tools.jackson`. Validado no primeiro passo das tarefas, antes do resto (R3).

### D10. Estratégia de testes

- **Unitários:** `GetBalanceServiceTest` (fake do `BalanceProvider` que honra o contrato, sem Mockito); `CircuitBreakerBalanceProviderTest` e o teste parametrizado da classificação compartilhada; propriedades de leitura (padrões, sobrescrita, invariantes); `DynamoDbConfig` para os dois clientes; mapeamento do DTO (`updated_at`, escala do `amount`); filtro de `traceId`; parser do `accountId`.
- **Contrato do adapter HTTP:** `@SpringBootTest` + `MockMvc`, **com o port de entrada `GetBalanceUseCase` substituído por um fake roteirizável (`ScriptedGetBalanceUseCase`, `@Primary` numa `@TestConfiguration`)**, e não por `@MockitoBean`. O padrão do projeto troca o port de saída, mas aqui o provider tem dois beans (o `DynamoDbBalanceProvider` e o decorator `@Primary`), e o contrato que se testa é o do adapter: cada exceção do núcleo vira o status certo, com os cabeçalhos e o corpo certos. O fake em vez do Mockito porque `AccountId` é value class e seus parâmetros ficam com nome mangled e sem matcher conveniente (o mesmo motivo do `ScriptedProcessTransactionUseCase`). Divergência explícita e pequena.
- **Timeout e retry reais, sem Mockito:** o teste do endpoint silencioso (`ServerSocket` que aceita e não responde, como o `DynamoDbBalanceProviderTimeoutTest`) e um servidor HTTP mínimo do JDK (`com.sun.net.httpserver`) que responde `500` na primeira requisição e conta as requisições, para provar "no máximo 2 tentativas" e "permanente não repete". Sem dependência de teste nova.
- **Ponta a ponta (integração, DynamoDB Local + Redpanda reais):** servidor em porta aleatória e cliente HTTP real. Publica evento no tópico, espera a ingestão e consulta: `200` com os valores do evento, evento fora de ordem preserva o mais novo, `DECLINED` avança só `updated_at`, conta desconhecida `404`, id inválido `400`, e uma classe com o endpoint do DynamoDB apontado para uma porta fechada prova `503` com `Retry-After` dentro do orçamento e, depois da janela do circuito, falha rápida.
- **OpenAPI:** o teste do contrato lê `/v3/api-docs` e confere caminho, parâmetro, os cinco status, o `Retry-After` do `503` e os schemas.
- **Gate:** `./gradlew check` com cobertura ≥ 90%.

## Conformidade, padrões e coerência

- **Camadas (Art. 1):** `port/input` ganha `GetBalanceUseCase`; `application` ganha `GetBalanceService`; `adapter/input/web` traduz protocolo (rota, UUID, status, `problem+json`, `traceId`) e não decide regra de negócio; `adapter/output/resilience` ganha o decorator de leitura; `adapter/output/dynamodb` troca o cliente injetado. O domínio não muda e nenhum tipo de Spring, Jackson, SDK ou Resilience4j atravessa um port. O `HexagonalArchitectureTest` cobre os pacotes novos.
- **Padrões nomeados (Art. 9):** Use Case (D1), Exception Translator sobre Problem Details (D4), Circuit Breaker e Decorator (D7), Retry com backoff do AWS SDK (D6), Fail Fast na configuração (D6). Cache-Aside avaliado e descartado (D8).
- **Soluções prontas (Art. 10):** `ProblemDetail`, `ResponseEntityExceptionHandler` e `ResponseStatusException` (D4), `CacheControl` e `OncePerRequestFilter` (D8), retry e timeouts do AWS SDK (D6), springdoc (D9). Descartadas com motivo: `spring-boot-starter-validation` (D3), `resilience4j-retry` (D6), Micrometer Tracing (D5), `WebContentInterceptor` (D8), Caffeine, DAX (D8).
- **Coerência com o codebase (Art. 8):** segue o `hello` (`@RestController` com DTO em `adapter/input/web/dto`, construtor com `private val`, configuração `${ENV:default}` no `application.yaml`) e o `balance` (cabeçalho de comentário, testes espelhados, nomes de teste em crase). **Divergências explícitas:** (a) controller implementa uma interface `BalanceApi` que concentra a documentação, ao contrário do `GreetingController` (D2); (b) o port de entrada, e não o de saída, é substituído nos testes de contrato (D10); (c) segundo `DynamoDbClient` (D6); (d) o bean `balanceCircuitBreaker` é renomeado (D7).
- **Ambiguidades do enunciado tocadas e decididas:** status de conta inexistente e de `accountId` inválido (D3), formato de `updated_at` (D2). A decisão de que `updated_at` vem do `transaction.timestamp` já estava tomada e é só reaproveitada.
- **Padrão ou algoritmo não implementado (critério 8 do enunciado):** cache de leitura (D8), tracing distribuído (D5), `Retry-After` calculado a partir do circuito (D4), tipos `type` próprios nos problemas (D4) e autenticação (Non-Goals), cada um com o motivador na decisão correspondente.

## Risks / Trade-offs

- **[R1] Timeouts curtos podem gerar `503` falso em partida a frio.** A primeira chamada de uma JVM contra o DynamoDB Local (JIT, pool de conexões, resolução de DNS no Docker) pode passar de 300 ms. → O retry cobre parte; os valores são configuráveis por `DYNAMODB_READ_*`; os testes de integração aquecem o cliente; se o valor padrão causar falso `503` no `make up`, sobe-se `api-call-attempt` mantendo a invariante do D6.
- **[R2] O advice global muda o corpo de erro do `/hello`** (do JSON padrão do Boot para `problem+json`). → Os testes existentes do `hello` só conferem status; um cenário da spec fixa o novo corpo. Quem consumia o formato antigo do `/hello` é só o exemplo do kit.
- **[R3] Springdoc 3.1.1 é recente e usa Jackson 2 por dentro; o advice também pode aparecer na especificação.** O springdoc varre `@ControllerAdvice` e acrescenta as respostas dos seus handlers a todas as operações. → O primeiro passo das tarefas é um teste vermelho do `/v3/api-docs` e da UI com a dependência instalada; se a varredura poluir a especificação, as `@ApiResponse` explícitas em `BalanceApi` têm precedência e `springdoc.override-with-generic-response=false` a desliga. Se a combinação não funcionar com o Boot 4.1.0, o problema sobe ao usuário antes de seguir (Art. 10).
- **[R4] Um segundo pool de conexões dobra os sockets abertos para o DynamoDB.** → Cada pool usa o padrão do Apache5 (50 conexões, alocadas sob demanda); o ganho é isolamento: uma API lenta não esgota as conexões da ingestão.
- **[R5] O `traceId` não é um trace distribuído.** O DynamoDB e o Kafka não recebem o id, então a correlação fica restrita à aplicação (log e corpo de erro). → O filtro reaproveita o `traceparent` de entrada, e a change de observabilidade o substitui por Micrometer Tracing sem mudar o contrato.
- **[R6] `Retry-After` fixo pode orientar mal o cliente.** Com o circuito aberto por 30 s, o cliente que voltar em 5 s recebe outro `503`. → O custo é uma resposta rápida sem tocar o DynamoDB, e o valor é configurável.
- **[R7] Falha permanente vira `500` e não alerta ninguém.** Um item gravado malformado ou uma tabela inexistente é um problema operacional, e o cliente só vê `500`. → O log de `ERROR` leva a causa e o `traceId`; o alerta sobre a taxa de `500` fica para a change de observabilidade.
- **[R8] Sem autenticação, o saldo de qualquer conta é consultável por quem alcança o serviço.** → Fora do escopo do desafio e do pedido; o serviço é interno e atrás de um gateway. Registrado como evolução.
- **[R9] A leitura fortemente consistente custa o dobro de capacidade de leitura do DynamoDB.** → Aceito: é o preço da garantia "saldo mais atual" (D8, motivo 1) e o volume de leitura por chave é baixo.

## Migration Plan

Mudança aditiva: nenhum dado, tabela ou tópico muda, e o `hello` só muda o formato do corpo de erro (R2). Reversão: reverter o commit da change. As variáveis `DYNAMODB_READ_*` e `BALANCE_API_RETRY_AFTER` têm padrão e podem ser omitidas.

## Open Questions

Nenhuma.
