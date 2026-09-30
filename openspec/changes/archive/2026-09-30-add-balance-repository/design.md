## Context

- O desafio pede o **saldo mais atual por conta**. Cada evento do tópico já traz o saldo calculado pelo autorizador, então o serviço guarda um **snapshot**, não recalcula nada.
- O ambiente tem mensagens duplicadas, fora de ordem e consumidores concorrentes (várias instâncias ou partições). Por isso "mais recente" precisa ser decidido pela ordem do evento, não pela ordem de chegada, e de forma atômica.
- O starter kit já traz um `DynamoDbClient` (`hello/adapter/output/dynamodb/DynamoDbConfig.kt`) sem timeouts, com os defaults do SDK. O seed cria uma única tabela fixa.
- Esta é a primeira change do contexto `balance`. Não há domínio, ports nem specs anteriores.

## Goals / Non-Goals

**Goals:**
- Modelo mínimo de domínio para o snapshot, com a regra de ordem autoritativa no domínio.
- Ports de saída para gravar condicionalmente e ler por chave.
- Adapter DynamoDB com compare-and-set atômico, leitura fortemente consistente, classificação de falhas e timeouts explícitos.
- Tabela `AccountBalances` criada no ambiente local e testes de integração contra o DynamoDB Local do kit.

**Non-Goals:**
- Consumer Kafka, parse do evento, caso de uso de ingestão, DLT: change de ingestão.
- Endpoint REST, status HTTP para conta inexistente ou `accountId` inválido: change da API.
- Retry com backoff e circuit breaker na aplicação, métricas e logs: change de resiliência e observabilidade. Esta change só entrega a **classificação** que essas camadas vão usar.
- Decidir se evento `DECLINED` atualiza o snapshot. O repositório grava o que recebe; a decisão é do caso de uso de ingestão.

## Decisions

### D1. Modelagem: PK `accountId`, sem sort key, sem GSI

| Atributo | Tipo | Papel |
|-|-|-|
| `accountId` | S | Partition key (UUID canônico minúsculo) |
| `ownerId` | S | Titular |
| `balanceAmount` | N | Saldo (decimal exato) |
| `balanceCurrency` | S | ISO 4217 |
| `lastEventTimestamp` | N | `transaction.timestamp` em µs; primeira chave da ordem |
| `lastTransactionId` | S | Id da transação; desempate da ordem |

- **Por quê:** o único padrão de acesso é "dado um `accountId`, o snapshot atual", tanto na escrita quanto na leitura. Um item por conta atende os dois com `PutItem`/`GetItem`, O(1), sem `Scan` nem `Query`.
- **Sem sort key:** uma sort key só faz sentido com vários itens por conta (histórico). Não há esse padrão de acesso (ver D6).
- **Sem GSI:** nenhuma consulta por outro atributo é pedida (ex.: por titular). Um GSI dobraria o custo de escrita, seria só eventualmente consistente e seria código "para o futuro" (YAGNI).
- **Tamanho do item:** cerca de 200 bytes, ou seja, 1 WCU por escrita e 1 RCU por leitura fortemente consistente.
- **Nomes:** `AccountBalances`, configurável por `BALANCE_TABLE_NAME` (12-Factor, config no ambiente). Criada em `infra/dynamodb/seed.sh` com `PAY_PER_REQUEST`, igual à tabela do kit.

### D2. "Mais recente" definido no domínio, aplicado atomicamente no adapter

- **Escolha:** `SnapshotVersion(timestamp: EventTimestamp, transactionId: TransactionId) : Comparable` no domínio é a **única definição autoritativa** da ordem. O adapter traduz essa ordem para um compare-and-set do DynamoDB:
  ```
  attribute_not_exists(accountId)
    OR lastEventTimestamp < :timestamp
    OR (lastEventTimestamp = :timestamp AND lastTransactionId < :transactionId)
  ```
- **Por que no domínio:** o Art. 2 da constituição diz que um repositório não decide o que é "mais recente". O repositório garante a **atomicidade** (capacidade da tecnologia) e o domínio define a **regra**, assim como um `WHERE version < :v` num banco relacional.
- **Risco de divergência entre as duas representações:** um teste de integração percorre a matriz de casos (mais antigo, mais novo, duplicado, empate com id menor e com id maior) e prova que o DynamoDB devolve `Applied` exatamente quando `compareTo` diz que o recebido é mais recente. Os fakes do port nos testes das próximas changes também usam `compareTo`, para se comportarem como a implementação real (LSP).
- **Desempate lexicográfico pela forma textual do UUID**, não por `UUID.compareTo`. O `compareTo` do JDK compara `long`s com sinal e **não** coincide com a comparação de strings do DynamoDB (bytes UTF-8). A forma canônica minúscula é ASCII, então `String.compareTo` no Kotlin e a comparação do DynamoDB concordam.
- **Condição estrita (`<`):** duplicata (mesma versão) não é mais recente, logo resulta em `StaleIgnored`. Isso dá idempotência sem tabela de deduplicação.
- **Alternativa descartada:** chave de versão única gerada pelo domínio (µs com zero à esquerda + `#` + id). Ela eliminaria a segunda representação, mas leva um detalhe de codificação lexicográfica para o domínio e foge do pedido. Decisão tomada com o usuário.

### D3. `ConditionalCheckFailed` é resultado, não erro

- **Escolha:** o port devolve `SnapshotSaveResult`, sealed com `Applied` e `StaleIgnored`. O adapter captura **apenas** `ConditionalCheckFailedException` e devolve `StaleIgnored`.
- **Por quê:** fora de ordem e duplicata são o caso normal do domínio, não exceção (Art. 3: sem exceção para fluxo normal). Com o resultado tipado, o caso de uso consegue contar e logar descartes (métrica de taxa de `StaleIgnored`, útil para detectar o problema de relógio de R2) sem `try/catch`.
- **Alternativas descartadas:** `Boolean` (parâmetro de retorno sem significado, e a leitura `if (saved)` esconde o motivo); deixar a exceção vazar (tipo do SDK atravessando o port, contra o Art. 1).
- **Não pedimos `ReturnValuesOnConditionCheckFailure`:** o item antigo não é usado por ninguém (YAGNI).

### D4. Leitura com `ConsistentRead=true`

- **Custo:** a leitura fortemente consistente consome o dobro da eventual. Com itens menores que 4 KB, são 1 RCU contra 0,5 RCU (ou 1 RRU contra 0,5 RRU em on-demand). O custo absoluto por consulta é baixo, e a leitura é sempre um `GetItem` de um item só.
- **Consistência:** sem ela, uma consulta logo depois de uma gravação pode ler uma réplica atrasada e devolver o saldo **anterior** à transação que o cliente acabou de fazer. Em core banking, esse é o erro mais visível para o cliente. A leitura forte elimina essa fonte de atraso a um custo pequeno.
- **Limite honesto:** o atraso dominante continua sendo o lag do consumer em relação ao tópico, não a réplica. A leitura forte não resolve esse lag; ele precisa ser medido (change de observabilidade).
- **PACELC:** em operação normal, escolhemos consistência em vez de latência (PC/EC para esta leitura). Em partição, a leitura forte pode falhar onde a eventual responderia. Nesse caso a falha vira transitória (D5) e a API decide como degradar.
- **Consequências para depois:** leitura forte não funciona em GSI (não temos), nem entre regiões em Global Tables, e o DAX não a serve do cache. Ficam registradas para quando houver cache ou multirregião.

### D5. Classificação de falhas do SDK

- **Escolha:** exceção sealed `BalanceStorageException` em `balance/port/output`, com os subtipos `TransientStorageException` e `PermanentStorageException`. A causa original é sempre preservada.
  - Ficam no `port` porque fazem parte do **contrato** do port (o que o núcleo precisa saber sobre a falha) e não são regra de negócio. Assim nenhum tipo do SDK atravessa o port (Art. 1), e os fakes conseguem honrar o contrato (LSP).
- **Transitória** (tentar de novo mais tarde pode resolver):
  - throttling (`AwsServiceException.isThrottlingException`, que cobre `ProvisionedThroughputExceededException`, `RequestLimitExceeded`, `ThrottlingException`);
  - status HTTP 5xx;
  - `ApiCallTimeoutException` e `ApiCallAttemptTimeoutException`;
  - `SdkClientException` com `IOException` na cadeia de causas (conexão recusada, reset, timeout de socket).
- **Permanente:** todo o resto. Por exemplo, 4xx que não é throttling (`ResourceNotFoundException`, `ValidationException`, credencial inválida), `SdkClientException` sem I/O, e item armazenado malformado na leitura.
- **Para que serve:** a change de resiliência retenta só a transitória com backoff e conta só ela no circuit breaker. A permanente é erro de configuração ou de contrato: repetir não ajuda, e mandar a mensagem para a DLT esconderia um problema de infraestrutura. A decisão de parar o consumer ou alertar é daquela change.
- **Alternativa descartada:** `SdkException.retryable()` sozinho. Ele segue a política interna do SDK e não deixa a regra explícita e testável aqui.

### D6. Alternativas descartadas para a concorrência

| Alternativa | Como seria | Por que não |
|-|-|-|
| **Lock otimista por `version`** | `GetItem`, comparar no app, `PutItem` com `version = :expected` e retentar em conflito | Duas idas ao banco por evento. Sob contenção (conta quente), vira um laço de retentativas. E ainda precisaria comparar o timestamp, porque a `version` do banco não diz nada sobre a ordem do evento. A condição por `SnapshotVersion` já **é** um controle otimista, em uma ida e sem leitura prévia. |
| **`TransactWriteItems`** | Transação com a escrita condicional | Serve para atomicidade entre **vários** itens, e escrevemos um. Custa o dobro de WCU, adiciona `TransactionConflictException` sob contenção e não traz nenhuma garantia a mais para um item. Seria necessária só se gravássemos histórico e snapshot juntos. |
| **Tabela de histórico por evento** | Um item por evento (PK `accountId`, SK `timestamp#transactionId`), leitura com `Query` descendente e `Limit 1` | Idempotência e ordem sairiam de graça pela chave, mas o armazenamento cresce sem limite (exigiria TTL), cada leitura vira `Query` e o enunciado pede o saldo atual, não o extrato. O **tópico Kafka já é o log**; a tabela é uma view materializada dele (DDIA, cap. 11). Auditoria é do autorizador, que é o sistema de registro. |

### D7. Timeouts e tentativas explícitos no cliente do kit

- **Escolha:** reutilizar o `DynamoDbClient` do kit (um cliente por aplicação) e completar sua configuração. Os valores vêm de uma `@ConfigurationProperties` (`dynamodb.*`), porque a função da `@Bean` passaria de 3 parâmetros (Art. 3).

| Propriedade | Env var | Default | Motivo |
|-|-|-|-|
| `dynamodb.timeouts.connection` | `DYNAMODB_CONNECTION_TIMEOUT` | `500ms` | Handshake TCP/TLS na mesma região leva poucos ms; acima disso o endpoint está inacessível |
| `dynamodb.timeouts.socket` | `DYNAMODB_SOCKET_TIMEOUT` | `1s` | Leitura sem resposta no nível HTTP |
| `dynamodb.timeouts.api-call-attempt` | `DYNAMODB_API_CALL_ATTEMPT_TIMEOUT` | `1s` | O DynamoDB responde em poucos ms (p99); 1 s absorve GC e rede sem prender a thread |
| `dynamodb.timeouts.api-call` | `DYNAMODB_API_CALL_TIMEOUT` | `3s` | Teto de todas as tentativas; muito abaixo do `max.poll.interval.ms` do Kafka (5 min) e do timeout que um cliente HTTP tolera |
| `dynamodb.retry.max-attempts` | `DYNAMODB_MAX_ATTEMPTS` | `3` | Estratégia `standard` do SDK (backoff com jitter e cota de retentativa), agora explícita |

- **HTTP client:** `Apache5HttpClient`, o cliente síncrono que o `dynamodb` 2.46.7 já traz em runtime (`apache5-client`). A dependência `software.amazon.awssdk:apache5-client` passa a ser declarada só porque agora compilamos contra o builder dele, para configurar os timeouts de conexão e de socket.
  - **Evidência (Art. 10):** `apache5-client` 2.46.7, a mesma release do SDK pelo BOM (publicada em 09/06/2026). A última release do artefato é a 2.55.8 (29/09/2026), então a manutenção é ativa. Ela já vem em runtime com o `dynamodb` (confirmado no grafo do `runtimeClasspath`), então declará-la só a torna explícita para compilar: o custo é zero. A alternativa `apache-client` (Apache HttpClient 4, legado) foi descartada porque não é dependência do `dynamodb` e acrescentaria uma segunda pilha HTTP ao classpath.
- **Retentativa no SDK:** é a camada mais barata para falhas curtas (um throttling pontual) e tem cota que evita tempestade de retentativas. Uma `TransientStorageException` significa "o SDK já tentou dentro do teto de 3 s". A retentativa de aplicação (próxima change) precisa ter backoff mais longo e considerar essas tentativas, para não multiplicar a carga (SRE, cap. 22).
- **Onde fica:** `DynamoDbConfig` continua em `hello/adapter/output/dynamodb`. Mover o cliente para um pacote compartilhado está fora do escopo e fica registrado para quando o `hello` for removido. O adapter de `balance` só injeta o bean; não importa nada de `hello`.

### D8. Ambiguidades do enunciado resolvidas aqui

- **Origem de `updated_at`:** é o `transaction.timestamp` do evento que produziu o snapshot (`lastEventTimestamp`). É o instante em que o saldo passou a valer segundo o autorizador, é determinístico e não muda em replay. O momento da persistência mediria o atraso da ingestão, não a idade do saldo, e exigiria um atributo a mais. A formatação ISO 8601 é da change da API. Decisão tomada com o usuário.
- **Desempate de timestamp:** id da transação, como em D2. É arbitrário, mas **determinístico**: todas as instâncias convergem para o mesmo snapshot, qualquer que seja a ordem de chegada (last-writer-wins com desempate total, DDIA cap. 5). O enunciado não define qual de dois eventos simultâneos é o "verdadeiro"; sem um número de sequência do autorizador, nenhuma escolha é mais correta que esta.

### D9. Conformidade com a constituição

- **Camadas:** `balance/domain/model` e `domain/exception` (Kotlin puro), `balance/port/output` (ports e exceções do contrato) e `balance/adapter/output/dynamodb`. Não há `application` nesta change: não existe caso de uso ainda.
- **Value objects:** `AccountId`, `OwnerId` e `TransactionId` (`UUID`), `Money` (`BigDecimal` + `java.util.Currency`, normalizado para as casas decimais da moeda), `EventTimestamp` (µs, positivo), `SnapshotVersion` e `BalanceSnapshot`. `java.util` é JDK, não framework.
  - `Money` rejeita mais casas que a moeda permite, em vez de arredondar: arredondar dinheiro em silêncio é um bug. A normalização para a escala da moeda também resolve a normalização de números do DynamoDB (`183.10` volta como `183.1`).
  - Saldo negativo é permitido, porque o valor vem pronto do autorizador (cheque especial).
- **Ports (ISP):** `fun interface BalanceRepository { saveIfNewer(snapshot): SnapshotSaveResult }` e `fun interface BalanceProvider { findByAccountId(accountId): BalanceSnapshot? }`, na convenção do kit (Repository para escrita, Provider para leitura). Um adapter por port: `DynamoDbBalanceWriter` implementa `BalanceRepository` e `DynamoDbBalanceProvider` implementa `BalanceProvider`. É a mesma divisão do kit (`DynamoDbGreetingTemplateWriter` e `DynamoDbGreetingTemplateProvider`), e cada classe tem um só motivo de mudar: o caminho de escrita (condição, resultado tipado) ou o de leitura (consistência, item malformado). O esquema do item e a tradução de falhas ficam em funções compartilhadas (`BalanceItem.kt`, `DynamoDbFailureTranslation.kt`).
- **Adapter só traduz:** mapeia snapshot ↔ item, converte a ordem do domínio em condição e as falhas do SDK em exceções do contrato. Não decide nada de negócio.
- **Arquitetura verificada:** o `HexagonalArchitectureTest` passa a cobrir `br.com.itau.challenge.balance..`.
- **Coerência com o kit (Art. 8):**
  - Nomes: `DynamoDb<Conceito>Writer` e `DynamoDb<Conceito>Provider`, ports `<Conceito>Repository` e `<Conceito>Provider`, `DynamoDbClient` e nome da tabela injetados por construtor com `@Value`.
  - Constantes de atributo no topo do arquivo. Aqui elas são `internal` e vivem em `BalanceItem.kt` porque o mapeamento e a condição de gravação as compartilham.
  - Testes unitários com `mock(DynamoDbClient::class.java)` e `ArgumentCaptor`, e teste de integração `DynamoDb<Conceito>IntegrationTest` com o cliente real e limpeza em `@AfterEach`.
  - `dynamodb.table-name` é a tabela do greeting (`GreetingMessages`), e `dynamodb.balance-table-name` segue o prefixo por contexto. Renomear a propriedade do kit fica para quando o `hello` for removido, como o `DynamoDbConfig`.
  - `DynamoDbBalanceProviderTimeoutTest` descreve um comportamento do cliente configurado (falha transitória dentro do timeout total), por isso leva o nome do comportamento, e o das classes testadas segue `<Classe>Test`.
  - Único ponto que cruza contextos: os testes de `balance` reutilizam `DynamoDbConfig` e `DynamoDbProperties` de `hello` para criar o cliente real. O código de produção não importa nada de `hello` (D7).
- **Patterns (Art. 9):**
  - *Ports & Adapters* e *Repository*, do kit.
  - *Value Object* para ids, dinheiro e instante.
  - *Result type* com `sealed interface` (`SnapshotSaveResult`) em vez de exceção para o fluxo normal.
  - *Exception Translation*: o adapter converte as exceções do SDK nas do contrato do port, na borda. A hierarquia pronta do Spring Framework (`org.springframework.dao`: `TransientDataAccessException`, `NonTransientDataAccessException`, `QueryTimeoutException`) foi avaliada e descartada: ela é do Spring, e o Art. 1 e o `HexagonalArchitectureTest` proíbem framework nos ports, então o contrato precisa ser do núcleo (D5).
  - *Compare-and-Set* (concorrência otimista por escrita condicional), descrito em D2 e D6.
  - *Mapper* para snapshot ↔ item (`BalanceItem.kt`).
  - Nenhum outro coube: Strategy ou Specification para a ordem seriam abstração sem segundo caso de uso (YAGNI).
- **Bibliotecas avaliadas (Art. 10):**
  - Usadas: `ClientOverrideConfiguration` e a estratégia de retentativa `standard` do SDK (D7), `@ConfigurationProperties` do Spring, `java.util.Currency` e `BigDecimal` do JDK, `AwsServiceException.isThrottlingException` e `HttpStatusFamily` do SDK na classificação (D5).
  - *DynamoDB Enhanced Client* (`dynamodb-enhanced`) e o *Spring Cloud AWS DynamoDB* (`DynamoDbTemplate`, que o envolve): descartados.
    - O Enhanced Client até aceita classes imutáveis (`ImmutableTableSchema`, `StaticTableSchema`). Mas o domínio usa value classes e `Money` com construtor privado, então cada tipo pediria um `AttributeConverter`, ou um item paralelo ao domínio.
    - O mapeamento manual é curto (`BalanceItem.kt`, cerca de 60 linhas) e o kit usa o cliente de baixo nível (Art. 8).
    - A condição de gravação continuaria escrita à mão, então a biblioteca não tira o ponto mais delicado.
  - *JSR 354 / Moneta* para dinheiro: descartado. `BigDecimal` com `Currency` cobre a necessidade (sem conversão de câmbio nem formatação) sem dependência nova.
  - `RetryUtils` do SDK para classificar falhas: descartado. É API interna do SDK (`@SdkProtectedApi`) e responde "o SDK deve retentar?", enquanto D5 responde "vale retentar mais tarde na aplicação?", depois que o SDK já esgotou as tentativas.

## Risks / Trade-offs

- **[Hot partition]** Todas as escritas de uma conta vão para um único item e, portanto, uma única partição. O limite é de cerca de 1.000 WCU/s por partição, e escritas rejeitadas pela condição **também consomem WCU**. Uma conta muito ativa (ex.: conta de adquirência) satura antes das outras e recebe throttling, que é classificado como transitório.
  → **Mitigação:** a adaptive capacity isola a partição quente das demais. Na ingestão, é possível coalescer por conta dentro de cada lote do Kafka (gravar só a maior versão do lote), reduzindo escritas sem mudar a semântica. Fica documentado para a change de ingestão. Sharding de escrita não se aplica, porque o saldo é um valor só.
- **[Relógio do autorizador]** A ordem depende do `transaction.timestamp`, gerado pelo autorizador. Um evento posterior com relógio atrasado (drift entre instâncias do autorizador) é descartado como `StaleIgnored`, e o saldo fica errado até o próximo evento. Um relógio adiantado é pior: um timestamp no futuro "congela" a conta até o tempo real alcançá-lo.
  → **Mitigação:** a métrica da taxa de `StaleIgnored` por conta torna o problema visível. Rejeitar timestamps além de uma tolerância no futuro é uma opção documentada. A solução definitiva é um **número de sequência por conta** emitido pelo autorizador, que é o sistema de registro. Relógio físico não dá causalidade (DDIA, cap. 8).
- **[Desempate arbitrário]** Com timestamps iguais, vence o id de transação lexicograficamente maior, que pode não ser o último evento real.
  → É determinístico e convergente, e é o limite do que o payload permite. A solução definitiva também é o número de sequência.
- **[Duas representações da ordem]** `SnapshotVersion.compareTo` e a `ConditionExpression` podem divergir numa manutenção.
  → **Mitigação:** o teste de equivalência (D2) quebra se isso acontecer.
- **[Leitura forte]** Custa o dobro de RCU e pode falhar onde a leitura eventual responderia.
  → Aceito (D4). A falha é transitória e a API decide como degradar.
- **[Retentativa em duas camadas]** O SDK retentar e a aplicação retentar pode multiplicar a carga sob falha.
  → **Mitigação:** o teto de 3 s, a cota do SDK e a regra registrada em D7 para a change de resiliência.
- **[Cliente no pacote `hello`]** Remover o exemplo do kit sem mover o `DynamoDbConfig` quebra o contexto `balance`.
  → Registrado em D7.

## Migration Plan

- **Local:** `make db-up` (ou `make up`) passa a criar `AccountBalances` pelo seed. O seed é idempotente: se a tabela já existe, não recria.
- **Produção (fora do escopo):** a tabela seria criada por IaC com PITR ligado. Não há migração de dados; o snapshot é reconstruível reprocessando o tópico, porque a gravação é idempotente e respeita a ordem.
- **Rollback:** reverter o commit. A tabela nova não afeta o `hello`.

## Open Questions

Nenhuma bloqueante. As decisões de `updated_at` e da regra de ordem foram tomadas com o usuário.
