# Comentários para revisão humana

Versão proposta para o commit; código, build e Docker foram selecionados conforme scope.md. Cada bloco abaixo é o cabeçalho do arquivo com suas linhas reais. Aval concedido pelo usuário em 2026-10-02 (“pode confirmar”); os 25 cabeçalhos aprovados permanecem inalterados.


## .dockerignore:1


```
# L9-L10 http: mantém o alvo local usado pelos links do README.
# L17-L21 openspec: somente contexto, changes e specs participam do inventário e dos links dos testes.
# L23-L27 exclusões privadas: material pessoal e credenciais não participam de nenhuma imagem de teste ou runtime.
# Enunciado: O que será avaliado → Production readiness
```


## Dockerfile:1


```
# syntax=docker/dockerfile:1
# L13-L18 test: os testes leem arquivos do repositório; a cópia fica neste estágio para que documentos
#     invalidem a verificação sem participar do builder/runtime (enforce-hexagonal-architecture design D5).
# Enunciado: O que será avaliado → Testes
```


## build.gradle.kts:1


```
/*
 * L51-L55 dependencies: Actuator, Prometheus e tracing usam o BOM do Boot; a ponte de métricas do circuito tem
 *     versão explícita porque não é gerenciada, sem exportador de traces (add-observability design D11).
 * L57-L58 dependencies: os módulos de teste habilitam métricas e tracing reais nos testes de observabilidade.
 * L103 localAwsCredentials: o DynamoDB Local aceita qualquer par não vazio; credenciais de teste ficam no ambiente
 *     para exercitar a cadeia padrão do SDK, sem credenciais no código de produção (add-observability design D9).
 * L105-L107 tasks.bootRun: o desenvolvimento local usa a mesma cadeia de credenciais da imagem.
 * L109-L151 tasks.withType<Test>: os dois source sets recebem as credenciais locais, sem depender do perfil AWS
 *     pessoal de quem executa os testes.
 * L61 dependencies: expõe no compile de teste o parser já transitivo do Konsist para verificar referências
 *     qualificadas pela API pública, sem um parser próprio (enforce-hexagonal-architecture design D2).
 * L154 tasks.test.systemProperty: o worker Gradle não expõe os jars em java.class.path; a política usa o
 *     classpath real para reconhecer funções qualificadas de frameworks, sem whitelist manual de namespaces.
 * L153-L161 tasks.test: os testes estáticos leem fontes e documentos fora do classpath; declarar as árvores
 *     preserva a invalidação por arquivo novo/renomeado/removido e o reaproveitamento sem mudanças.
 *
 * Enunciado: O que será avaliado → Production readiness
 */
```


## src/integrationTest/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceIntegrationTest.kt:1


```
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


## src/main/kotlin/br/com/itau/challenge/balance/application/GetBalanceService.kt:1


```
/*
 * L14 GetBalanceService: só orquestra o caso de uso: o adapter de entrada não pode depender do port de saída, e a
 *     regra "conta sem saldo é ausência e falha de armazenamento sobe" fica numa fronteira testável sem HTTP.
 *
 * Enunciado: O que construir → Exposição (API REST)
 */
```


## src/main/kotlin/br/com/itau/challenge/balance/application/ProcessTransactionService.kt:1


```
/*
 * L14 ProcessTransactionService: só orquestra o caso de uso: a regra de montar o snapshot fica no domínio e a de
 *     ordem no `SnapshotVersion`, aplicada pelo repositório.
 *
 * Enunciado: O que construir → Ingestão (input via Kafka)
 */
```


## src/main/kotlin/br/com/itau/challenge/configuration/BalanceUseCaseConfiguration.kt:1


```
/*
 * L19 BalanceUseCaseConfiguration: mantém a composição Spring fora do núcleo e recebe ports por tipo para preservar
 *     a seleção dos decorators primários sem acoplar os casos de uso aos adapters.
 *
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/main/kotlin/br/com/itau/challenge/configuration/HelloUseCaseConfiguration.kt:1


```
/*
 * L19 HelloUseCaseConfiguration: mantém a composição Spring fora do núcleo e recebe ports por tipo para preservar
 *     a seleção dos decorators primários sem acoplar os casos de uso aos adapters.
 *
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/main/kotlin/br/com/itau/challenge/hello/application/GreetingService.kt:1


```
/*
 * L14 GreetingService: o caso de uso continua construível com fakes, sem conhecer o container responsável
 *     pela composição da aplicação (enforce-hexagonal-architecture design D1).
 *
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/main/kotlin/br/com/itau/challenge/hello/application/SaveGreetingTemplateService.kt:1


```
/*
 * L14 SaveGreetingTemplateService: o caso de uso continua construível com fakes, sem conhecer o container responsável
 *     pela composição da aplicação (enforce-hexagonal-architecture design D1).
 *
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/main/kotlin/br/com/itau/challenge/infrastructure/dynamodb/DynamoDbConfig.kt:1


```
/*
 * L35-L36 READ_RETRY_BASE_DELAY e READ_RETRY_MAX_DELAY: o backoff padrão do SDK (100 ms e 1 s para throttling)
 *     consumiria o orçamento da leitura; o retry rápido fica em 50 a 100 ms e não é configurável (YAGNI).
 * L40 DynamoDbConfig: compõe os clients em infraestrutura neutra, sem carregar hello, com timeouts e tentativas
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


## src/main/kotlin/br/com/itau/challenge/infrastructure/dynamodb/DynamoDbProperties.kt:1


```
/*
 * L21 DynamoDbProperties: agrupa os perfis compartilhados fora de hello, para todo timeout e o limite de tentativas
 *     serem explícitos e poderem ser sobrescritos por variável de ambiente.
 * L28 Timeouts: são quatro limites porque cada camada limita a sua parte: uma falha de rede não consome todo o teto
 *     da chamada, e uma tentativa lenta não consome as demais. `apiCall` limita a chamada inteira, somando todas as
 *     tentativas.
 * L35 Retry: `maxAttempts` conta a primeira chamada: 2 significa uma tentativa original e uma repetição.
 * L37 Read: separa o orçamento curto da consulta sem encurtar as tentativas da escrita.
 * L38-L46 init: as invariantes falham na subida para não truncar o retry nem exceder o orçamento da leitura:
 *     no máximo 1 retry e `maxAttempts × api-call-attempt ≤ api-call` (Fail Fast).
 *
 * Enunciado: O que será avaliado → Resiliência
 */
```


## src/test/kotlin/br/com/itau/challenge/ApplicationTests.kt:1


```
/*
 * L21 ApplicationTests: verifica a unicidade dos ports no contexto real, pois uma composição isolada
 *     não detectaria services registrados em duplicidade pela varredura da aplicação.
 *
 * Spec: Composição Spring é externa ao núcleo
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/test/kotlin/br/com/itau/challenge/HexagonalArchitectureTest.kt:1


```
/*
 * L16 HexagonalArchitectureTest: aplica a política provada por fixtures em todo fonte de produção para
 *     impedir que um contexto novo ou incompleto escape da verificação arquitetural.
 *
 * Spec: Núcleo livre de frameworks em cada contexto; Adapters acessam casos de uso por ports;
 *     Dependências internas apontam para dentro do próprio contexto; Verificação descobre todos os bounded contexts
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/test/kotlin/br/com/itau/challenge/architecture/ClasspathPackages.kt:1


```
/*
 * L15 ClasspathPackages: usa os pacotes reais dos jars de teste para reconhecer chamadas qualificadas a
 *     funções Kotlin sem nome de tipo, sem confundir cadeias comuns de receivers com dependências.
 *
 * Spec: Núcleo livre de frameworks em cada contexto
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/test/kotlin/br/com/itau/challenge/architecture/HexagonalPolicy.kt:1


```
/*
 * L13 HexagonalPolicy: descobre o contexto pela estrutura de pacotes para proteger contextos futuros e
 *     mantém o núcleo restrito ao JDK/Kotlin e às camadas internas do próprio contexto.
 *
 * Spec: Núcleo livre de frameworks em cada contexto; Adapters acessam casos de uso por ports;
 *     Dependências internas apontam para dentro do próprio contexto; Verificação descobre todos os bounded contexts
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/test/kotlin/br/com/itau/challenge/architecture/HexagonalPolicyTest.kt:1


```
/*
 * L22 HexagonalPolicyTest: injeta fontes mínimas no mesmo guard da produção; uma fonte inválida deve falhar
 *     sem precisar compilar um terceiro contexto de negócio.
 *
 * Spec: Núcleo livre de frameworks em cada contexto; Adapters acessam casos de uso por ports;
 *     Dependências internas apontam para dentro do próprio contexto; Verificação descobre todos os bounded contexts
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/test/kotlin/br/com/itau/challenge/architecture/KotlinDependencies.kt:1


```
/*
 * L25 KotlinDependencies: usa o parser Kotlin já transitivo de Konsist para que aliases e referências
 *     qualificadas não escapem do guard, sem interpretar comentários ou strings como dependências.
 *
 * Spec: Núcleo livre de frameworks em cada contexto
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderReadRetryTest.kt:1


```
/*
 * L41 DynamoDbBalanceProviderReadRetryTest: usa o cliente de leitura de produção contra um servidor HTTP do JDK que
 *     conta as requisições, porque só assim se prova "no máximo 2 tentativas" e "falha permanente não se repete";
 *     mocks do cliente não exercitam a política de retry do SDK.
 *
 * Spec: Leitura repete no máximo uma vez
 * Enunciado: O que será avaliado → Resiliência
 */
```


## src/test/kotlin/br/com/itau/challenge/balance/adapter/output/dynamodb/DynamoDbBalanceProviderTimeoutTest.kt:1


```
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


## src/test/kotlin/br/com/itau/challenge/configuration/UseCaseCompositionTest.kt:1


```
/*
 * L44 UseCaseCompositionTest: carrega somente a composição explícita, sem varrer os services, para provar
 *     que os ports de entrada são únicos.
 * L74-L95 should block only reads / should block only writes: provocar circuito aberto prova a seleção dos
 *     decorators primários, sem inspecionar campos privados. Enunciado: O que será avaliado → Resiliência
 *
 * Spec: Composição Spring é externa ao núcleo; Infraestrutura DynamoDB compartilhada não pertence a hello
 * Enunciado: O que será avaliado → Qualidade de código
 */
```


## src/test/kotlin/br/com/itau/challenge/infrastructure/dynamodb/DynamoDbClientsWiringTest.kt:1


```
/*
 * L20 DynamoDbClientsWiringTest: sobe o contexto real para provar que a injeção de `DynamoDbClient` por tipo, sem
 *     qualificador (writer e `hello`), continua recebendo o cliente da escrita, que é o `@Primary`, e que o cliente
 *     de leitura é outra instância.
 *
 * Spec: Cliente de leitura com timeouts curtos e configuráveis
 * Enunciado: O que será avaliado → Resiliência
 */
```


## src/test/kotlin/br/com/itau/challenge/infrastructure/dynamodb/DynamoDbConfigTest.kt:1


```
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


## src/test/kotlin/br/com/itau/challenge/infrastructure/dynamodb/DynamoDbPropertiesFixtures.kt:1


```
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


## src/test/kotlin/br/com/itau/challenge/infrastructure/dynamodb/DynamoDbReadPropertiesTest.kt:1


```
/*
 * L20 DynamoDbReadPropertiesTest: lê o `application.yaml` de verdade, e a configuração incoerente é provada pela
 *     falha de subida do contexto, que é o que o operador vê.
 *
 * Spec: Cliente de leitura com timeouts curtos e configuráveis; Leitura repete no máximo uma vez; O pior caso da
 *     leitura cabe no orçamento de latência
 * Enunciado: O que será avaliado → Resiliência
 */
```
