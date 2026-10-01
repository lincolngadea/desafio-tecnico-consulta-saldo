## Context

- O contexto `balance` já tem o domínio do snapshot, o port `BalanceRepository` (gravação condicional por `SnapshotVersion`, resultado `Applied`/`StaleIgnored`) e a classificação de falhas do armazenamento (`TransientStorageException`, `PermanentStorageException`). Falta o que alimenta tudo isso: o consumer do tópico `transacoes-financeiras-processadas` e o caso de uso que ele chama.
- `ProcessTransactionUseCase` **não existe** no código nem em nenhuma spec; esta change o cria, junto com o evento de domínio que ele recebe.
- O kit usa **Spring Kafka 4.1.0** (Boot 4.1, `StringDeserializer` + `ObjectMapper` do Jackson 3) e traz um consumer de exemplo (`hello`) com commit automático e sem tratamento de erro. O produtor do kit publica **sem chave**, então nenhuma ordem por conta vem do broker. O `make kafka-topic-create NAME=<t> [PARTITIONS=n]` cria tópicos (auto-criação desligada, replicação 1).
- Requisitos não funcionais do `project.md`: sem perda silenciosa de eventos, evento inválido isolado e observável, degradação controlada com retry, backoff e circuit breaker, consumo escalável horizontalmente.
- O cliente DynamoDB já limita cada chamada (`api-call` 3 s, 3 tentativas do SDK). Uma `TransientStorageException` chega, portanto, **depois** das tentativas do SDK.

## Goals / Non-Goals

**Goals:**
- Caso de uso `ProcessTransactionUseCase` com o evento de domínio, decidindo e documentando o tratamento de `DECLINED`.
- Consumer Kafka com commit manual após a persistência, DLT só para erro permanente, retry transitório com backoff exponencial e jitter configurável, pausa e retomada das partições, shutdown e rebalance seguros.
- Circuit breaker em volta do repositório, sem tipo de biblioteca atravessando o port.
- Número de partições definido e justificado; tópicos criados pelo comando do kit.

**Non-Goals:**
- Endpoint REST de consulta e seu mapeamento de erros (change da API).
- Métricas, logs estruturados e alertas (production readiness). Esta change só emite os sinais mínimos: log de cada descarte, de cada DLT e de cada pausa e retomada.
- Reprocessamento automático da DLT (ver R3), e retry não bloqueante com tópicos de retry (ver D5).
- Ordem por conta entre partições: a ordem é decidida por `SnapshotVersion`, não pela chegada.

## Decisions

### D1. Caso de uso e evento de domínio

- **Escolha:** `ProcessedTransaction` (domínio: id da transação, timestamp, conta, titular, saldo) e `fun interface ProcessTransactionUseCase { fun processTransaction(transaction: ProcessedTransaction): SnapshotSaveResult }`. `ProcessTransactionService` (`@Service`, exceção do Art. 1) monta o `BalanceSnapshot` e chama `BalanceRepository.saveIfNewer`. Reusa `AccountId`, `OwnerId`, `TransactionId`, `Money` e `EventTimestamp`, que já validam na construção.
- **Retorna o resultado em vez de `Unit`:** o listener registra se foi `Applied` ou `StaleIgnored`, sem `try/catch`, e o teste observa o efeito pela API pública.
- **Ambiguidade do enunciado: evento `DECLINED` atualiza o snapshot?** **Decidido: sim.** O enunciado afirma que todo evento traz o saldo da conta já calculado pelo autorizador, e a premissa central é que o serviço não interpreta nem recalcula. Aplicar a mesma regra a todo evento (maior `SnapshotVersion` vence) mantém uma única regra e faz `updated_at` ser, sempre, o `transaction.timestamp` do evento que gerou o snapshot, como já decidido na change anterior. Para uma transação recusada, o saldo do evento é o saldo anterior, então o valor não muda; só `updated_at` avança.
  - **Alternativa descartada:** ignorar `DECLINED`. Exigiria modelar `TransactionStatus`, um filtro no caso de uso e uma segunda decisão sobre status desconhecido, e deixaria `updated_at` atrás do último evento visto.
  - **Como inverter, se o negócio discordar:** um filtro de uma linha em `ProcessTransactionService`, mais o campo `status` no evento de domínio. Não é implementado agora (YAGNI).
  - **Consequência:** o campo `transaction.status` do evento é lido e ignorado; um valor fora de `APPROVED|DECLINED` **não** invalida o evento.

### D2. At-least-once, e não o exactly-once do Kafka

- **Escolha:** `AckMode.MANUAL_IMMEDIATE`; o listener confirma o offset **somente depois** de `saveIfNewer` retornar. Falha entre a gravação e o commit faz o broker reentregar o registro, e a reentrega resulta em `StaleIgnored`.
- **Por que não exactly-once:** as transações do Kafka (produtor idempotente, `read_committed`, `sendOffsetsToTransaction`) tornam atômico o ciclo **Kafka → processamento → Kafka**. Aqui o destino é o **DynamoDB**, fora da transação do Kafka. Sem 2PC ou outbox, o efeito no DynamoDB continuaria sendo at-least-once, e pagaríamos o coordenador de transações, a latência extra e novos modos de falha por nenhuma garantia adicional.
- **Por que at-least-once basta:** a gravação já é um **Idempotent Receiver** por construção (condição estrita em `SnapshotVersion`; duplicata é `StaleIgnored`, sem tabela de deduplicação). At-least-once somado a gravação idempotente dá **efeito único observável** no estado.
- **Alternativa descartada:** at-most-once (commit antes de gravar): uma queda perde o evento em silêncio, o que o requisito não funcional proíbe.

### D3. Anti-corruption: do JSON ao domínio, e o erro permanente de dado

- **Escolha:** o listener recebe `String` (padrão do kit), o `ObjectMapper` injetado lê um DTO em `adapter/input/kafka/dto` (valores monetários em `BigDecimal`, timestamps em `Long` µs, nomes de campo do enunciado) e um mapper traduz o DTO para `ProcessedTransaction`.
- **Falha de parse ou de invariante do domínio** (JSON malformado, campo obrigatório ausente, UUID, moeda ou timestamp inválidos) vira `MalformedTransactionEventException` (adapter), com a causa preservada. É um **erro permanente**: tentar de novo nunca muda o resultado.
- **Por quê no adapter:** traduzir protocolo é papel do adapter (Art. 1); o domínio continua sem Jackson. Uma única exceção de adapter dá ao error handler **um** tipo para classificar, em vez de uma lista de exceções de domínio.

### D4. Dead Letter Channel só para erro permanente

- **Destino por exceção:**

| Falha | Tentar de novo? | Destino |
|-|-|-|
| `MalformedTransactionEventException` | não | DLT, offset confirmado |
| `PermanentStorageException` | não | DLT, offset confirmado (ver R2) |
| `TransientStorageException` | sim, com backoff e jitter (D5) | após o limite, pausa (D6) |
| `StorageUnavailableException` | não agora | pausa (D6) |
| qualquer outra exceção (falha não classificada) | não | DLT, tratada como permanente |

- **Escolha:** `DeadLetterPublishingRecoverer` do Spring Kafka, no tópico `<tópico>.DLT`, que já grava os headers do motivo (`kafka_dlt-exception-cause-fqcn` com a causa real, já que `kafka_dlt-exception-fqcn` guarda só o `ListenerExecutionFailedException` que embrulha a causa, `kafka_dlt-exception-message`, `kafka_dlt-exception-stacktrace`, tópico, partição e offset originais). O `DefaultErrorHandler` classifica com `defaultFalse()` e só `TransientStorageException` é tentada de novo, de modo que uma falha não classificada não gasta tentativas e vai direto para a DLT, onde fica preservada para diagnóstico. O `DefaultErrorHandler` roda com `setCommitRecovered(true)`, porque em modo manual é ele que confirma o offset depois que a DLT aceitou o registro.
- **Por que a DLT recebe só erro permanente:**
  - Erro permanente é uma propriedade da **mensagem**: reprocessar não ajuda e o registro bloquearia a partição (poison pill). Isolá-lo na DLT preserva o evento para diagnóstico e libera o consumo.
  - Erro transitório é uma propriedade da **dependência**. Mandar eventos bons para a DLT durante uma queda do DynamoDB esvaziaria o fluxo principal para um tópico que ninguém reprocessa sozinho, e o saldo lido ficaria **desatualizado** até alguém intervir. Isso é perda silenciosa de fato, só que com os dados guardados em outro lugar.
  - Com a regra, a DLT significa sempre "dado ruim, ver o motivo"; o volume nela é um sinal limpo.
- **Alternativa descartada:** DLT depois de N retries de qualquer erro. Mistura os dois significados e transforma uma indisponibilidade em um backlog manual.

### D5. Retry transitório: backoff exponencial com jitter do Spring

- **Escolha:** `ExponentialBackOff` do Spring Framework 7, com `setJitter` e `setMaxAttempts`, dentro do `DefaultErrorHandler`. O `ExponentialBackOffWithMaxRetries` do Spring Kafka foi descartado no spike: ele limita as tentativas pelo tempo somado dos intervalos, e com jitter devolveu 5 intervalos para um limite de 4. As tentativas bloqueiam a thread do consumer, que continua fazendo `poll` ao fim do backoff.
- **Configuração** (todas por variável de ambiente, 12-Factor): `INGESTION_MAX_RETRIES` (reprocessamentos além da primeira tentativa, padrão 3), `INGESTION_BACKOFF_INITIAL` (200 ms), `INGESTION_BACKOFF_MULTIPLIER` (2.0), `INGESTION_BACKOFF_MAX` (2 s), `INGESTION_BACKOFF_JITTER` (100 ms, valor absoluto aplicado a cada intervalo).
- **Jitter e crescimento:** o jitter é aplicado em torno do intervalo corrente, e o crescimento seguinte parte do valor já alterado; por isso os testes verificam o crescimento exponencial com jitter zero e o jitter pelo primeiro intervalo.
- **Por que o jitter:** várias instâncias e partições falhando juntas, ao retomar, atacariam o DynamoDB no mesmo instante. O jitter espalha as retentativas.
- **Limite do tempo bloqueado:** no pior caso, `(1 + INGESTION_MAX_RETRIES) × api-call (3 s)` mais os backoffs, cerca de 14 s com os padrões. Isso fica muito abaixo de `max.poll.interval.ms` (5 min). Um teste de configuração garante essa relação para os valores padrão.
- **Alternativas descartadas:**
  - Resilience4j `Retry`: segunda biblioteca para uma capacidade que o Spring já oferece (Art. 10).
  - Retry não bloqueante com tópicos de retry (`@RetryableTopic`): resolve bloqueio de partição por **mensagem**, que aqui é tratado pela DLT. Para falha de **dependência** ele empilharia eventos nos tópicos de retry e manteria o problema, além de multiplicar tópicos e partições.

### D6. Circuit breaker no repositório, pausa e retomada das partições

- **Decorator:** `CircuitBreakerBalanceRepository` implementa `BalanceRepository` e envolve o writer do DynamoDB (padrão **Decorator** + **Circuit Breaker**), de modo que o caso de uso nem sabe que ele existe. Fica em `balance/adapter/output/resilience`, e uma `@Configuration` o expõe como o `BalanceRepository` injetado.
  - Só `TransientStorageException` conta como falha do breaker. `PermanentStorageException` é ignorada por ele, porque não diz nada sobre a saúde da dependência. `Applied` e `StaleIgnored` contam como sucesso.
  - Com o circuito aberto, a `CallNotPermittedException` do Resilience4j é traduzida para `StorageUnavailableException`, novo subtipo de `BalanceStorageException` no port. Tipo de biblioteca nunca atravessa o port (Art. 1).
  - Configuração: `BALANCE_CB_FAILURE_RATE_THRESHOLD` (50 %), `BALANCE_CB_SLIDING_WINDOW_SIZE` (10, usado também como `minimumNumberOfCalls`), `BALANCE_CB_WAIT_DURATION_OPEN` (30 s), `BALANCE_CB_HALF_OPEN_CALLS` (3).
- **Pausa:** diante de `StorageUnavailableException`, ou de `TransientStorageException` com as tentativas esgotadas, o consumer **não** manda para a DLT e **não** confirma o offset. Ele pausa as partições atribuídas (`MessageListenerContainer.pause()`, que continua fazendo `poll` para manter a sessão no grupo, sem buscar registros) e agenda a retomada após `INGESTION_PAUSE_DURATION` (30 s). O registro volta na retomada.
- **Retomar "ao fechar" exige uma correção de leitura:** um consumer pausado não faz chamadas, e sem chamadas o breaker não fecha sozinho. Por isso a retomada é por **tempo**, e o primeiro registro reentregue é a **sonda** do estado `HALF_OPEN`: sucesso fecha o circuito e o consumo segue; falha só reabre o circuito depois de todas as chamadas de teste permitidas (`BALANCE_CB_HALF_OPEN_CALLS`, padrão 3) se completarem com a taxa de falhas no limiar; até lá o circuito segue semiaberto, e o consumer pausa de novo pelas tentativas esgotadas ou pelo `StorageUnavailableException`. Cada ciclo de pausa com a dependência fora do ar custa, portanto, até essas chamadas reais ao DynamoDB. O efeito pedido (parar enquanto a dependência está fora e voltar quando ela se recupera) se mantém, sem acoplar o adapter de entrada ao de saída por eventos do breaker.
  - `INGESTION_PAUSE_DURATION` e `BALANCE_CB_WAIT_DURATION_OPEN` são independentes: se a pausa for menor que a espera do breaker, a sonda recebe `StorageUnavailableException` e o consumer pausa de novo, sem custo; se for maior, só atrasa a retomada. Os padrões iguais são intencionais, e a correção não depende disso.
- **Por que pausar em vez de descartar quando a dependência cai:**
  - **Descartar perde evento.** Só o saldo mais recente importa, mas isso não salva uma conta inativa: o evento descartado era o último, e o saldo lido fica errado para sempre, em silêncio. Viola o requisito de não perder eventos.
  - **Continuar consumindo também é errado:** cada registro gastaria todas as suas tentativas e falharia, gerando carga e ruído contra uma dependência que já está fora, e o atraso se acumularia na memória do processo em vez de no broker.
  - **Pausar usa o próprio Kafka como buffer durável:** o atraso (lag) acumula no broker, dentro da retenção, sem memória nem perda, e o consumo recomeça exatamente de onde parou. O lag crescente é o sinal observável da indisponibilidade.
- **Rebalance durante a pausa:** o Spring Kafka mantém pausadas as partições recém-atribuídas enquanto o container está pausado; um teste de integração cobre esse caso (R1).

### D7. Partições e criação dos tópicos

- **Escolha: 6 partições** para `transacoes-financeiras-processadas`, e 6 para `transacoes-financeiras-processadas.DLT`, criadas por `make kafka-topic-create NAME=<tópico> PARTITIONS=6` (replicação 1, fixa no comando do kit).
- **Justificativa:**
  - O número de partições é o **teto de paralelismo** do consumo: até 6 consumidores úteis no mesmo grupo (instâncias × `INGESTION_CONCURRENCY`). O requisito é alto volume e escala horizontal.
  - Estimativa, **a validar com carga**: uma gravação condicional sequencial leva alguns milissegundos, ou seja, na casa de 100 a 200 mensagens por segundo por thread, e 6 partições dão ordem de grandeza de 600 a 1.200 mensagens por segundo.
  - Partições podem ser **aumentadas** depois, nunca diminuídas, então começar com folga moderada é o lado seguro; mais que isso só aumentaria o custo de rebalance e de arquivos num broker de nó único.
  - **Não há restrição de ordem por conta:** o produtor do kit não usa chave, e a ordem é decidida por `SnapshotVersion`. Aumentar partições no futuro não quebra nenhuma garantia.
  - A DLT usa o mesmo número porque o resolvedor padrão do Spring publica na **mesma partição** de origem; assim não há código de roteamento a manter (KISS).
- Os tópicos não entram no seed do kit (`make kafka-up`), que cria só o tópico de exemplo. O README e o `project.md` passam a listar os dois comandos de criação.

### D8. Container dedicado, group id e commit

- **Escolha:** um `ConcurrentKafkaListenerContainerFactory` próprio para este listener (`containerFactory`), com `AckMode.MANUAL_IMMEDIATE`, `enable.auto.commit=false`, `auto-offset-reset=earliest`, `INGESTION_CONCURRENCY` (padrão 1) e grupo dedicado `TRANSACTIONS_CONSUMER_GROUP_ID` (`balance-transaction-ingestion`).
- **Por que dedicado:** a propriedade global `spring.kafka.listener.ack-mode` mudaria o consumer `hello`, que não confirma nada e passaria a nunca commitar. Cada listener tem sua fábrica e seu grupo, e o `hello` fica intocado.
- **Nomes de tópico e DLT** por `TRANSACTIONS_TOPIC` e `TRANSACTIONS_DLT_TOPIC`.

### D9. Graceful shutdown e rebalance

- **Shutdown:** ao fechar o contexto, o container para de buscar, **termina o registro em andamento** (ele só confirma o offset depois de gravar) e sai do grupo, o que antecipa o rebalance das suas partições. O `shutdownTimeout` do container (30 s), o `spring.lifecycle.timeout-per-shutdown-phase` (30 s) e o `stop_grace_period` do serviço `app` no compose (40 s) ficam acima do pior caso de um registro (D5). A retomada agendada na pausa é cancelada junto.
- **Rebalance:** com `MANUAL_IMMEDIATE` não há offsets acumulados na memória; o que foi gravado e confirmado fica no broker, e o que estava em andamento é reentregue ao novo dono e resolvido como `StaleIgnored`. Mantém-se a estratégia de atribuição padrão do cliente Kafka (a cooperativa já faz parte dela); não há `ConsumerRebalanceListener` próprio (YAGNI) porque não há estado local para devolver.

### D10. Dependência nova: Resilience4j (Art. 10)

- **Escolha:** `io.github.resilience4j:resilience4j-circuitbreaker` **2.4.0**, em `implementation`, **com versão fixa** (o BOM do Spring Boot 4.1 não a gerencia).
- **Evidência:** 2.4.0 publicada em 14/03/2026 (a anterior, 2.3.0, em 03/01/2025). Biblioteca Java pura, que só depende de `resilience4j-core` e `slf4j-api`, mantida e amplamente adotada para circuit breaker no ecossistema JVM.
- **Custo contra implementar:** uma linha no build e um decorator pequeno, contra escrever à mão a máquina de estados (fechado, aberto, semiaberto), a janela deslizante e a segurança entre threads, que é código crítico e difícil de testar.
- **Alternativas avaliadas:**
  - Spring Framework 7 (`spring-core` 7.0.8): tem `RetryTemplate` e `RetryPolicy`, mas **nenhum** circuit breaker. Por isso o retry fica no Spring (D5) e só o breaker vem de fora.
  - Spring Cloud CircuitBreaker: apenas uma abstração por cima do mesmo Resilience4j, com uma camada e um versionamento extra sem benefício aqui.
  - Starter `resilience4j-spring-boot` (anotações, auto-configuração): feito para versões anteriores do Boot; usamos a biblioteca direto, com configuração explícita, que também é mais legível.
  - Implementação própria: descartada, pelo custo acima.

## Conformidade, padrões e coerência

- **Camadas (Art. 1):** `domain` ganha `ProcessedTransaction`; `port/input` ganha `ProcessTransactionUseCase`; `port/output` ganha `StorageUnavailableException`; `application` ganha `ProcessTransactionService`; `adapter/input/kafka` traduz protocolo e decide o destino de cada falha; `adapter/output/resilience` envolve o repositório. Nenhum tipo de Kafka, Jackson ou Resilience4j atravessa um port, e o domínio continua sem framework.
- **Padrões nomeados (Art. 9):** Idempotent Receiver (D2), Anti-Corruption Layer (D3), Dead Letter Channel (D4), Retry com backoff exponencial e jitter (D5), Circuit Breaker e Decorator (D6), Competing Consumers para a escala horizontal (D7).
- **Coerência com o kit (Art. 8):** segue o consumer `hello` (`@Component`, `@KafkaListener`, DTO em `adapter/input/kafka/dto`, `String` mais `ObjectMapper`, configuração em `application.yaml` com `${ENV:default}`). **Divergências explícitas:** (a) container, grupo e modo de commit próprios, porque o `hello` usa o commit automático e a propriedade global o quebraria (D8); (b) pacote `adapter/output/resilience` para o decorator, porque ele não é um adapter de DynamoDB.
- **Ambiguidades do enunciado tocadas:** `DECLINED` (D1). A resposta HTTP para conta inexistente e `accountId` inválido não é tocada e fica para a change da API.
- **Padrão ou algoritmo não implementado (critério 8 do enunciado):** retry não bloqueante com tópicos de retry (D5), exactly-once do Kafka (D2) e reprocessamento automático da DLT (R3), cada um com o motivador descrito na decisão ou no risco correspondente.

## Risks / Trade-offs

- **[R1] O mecanismo de "pausar e redeliverar" depende de comportamento interno do Spring Kafka 4.1.0.** O `DefaultErrorHandler` sozinho não pausa o container por falha de dependência. O mecanismo validado no spike: o `IngestionRecoverer` pede a pausa ao `ListenerContainerPauseService` e lança `ListenerPausedException`, subtipo de `KafkaBackoffException`. Duas propriedades do Spring sustentam isso: (a) `SeekUtils.doSeeks` trata uma exceção que contenha `KafkaBackoffException` como recuo, loga em DEBUG (e não `ERROR`, com stack trace, a cada pausa) e devolve o registro por seek, sem commit; (b) o `FailedRecordTracker` só limpa o estado de um registro depois de uma recuperação bem-sucedida, então, passada a pausa, o registro reentregue (a sonda) chega ao recoverer sem gastar novas tentativas. → Os cenários da spec viram testes de integração contra o Redpanda, que quebram se uma atualização do Spring mudar esse comportamento. Se a DLT estiver fora do ar ou o pedido de pausa lançar exceção, o Spring devolve o registro por seek sem pausa, e o resultado é um laço de reentrega, que é o comportamento padrão do Spring e fica registrado como limitação (R7).
- **[R2] `PermanentStorageException` na DLT pode virar inundação.** Uma tabela inexistente ou credencial negada é permanente, mas afeta **todos** os eventos, e todos iriam para a DLT. → Eles não se perdem, ficam na DLT com o motivo e podem ser reprocessados depois da correção. Parar o consumer seria a alternativa, mas derrubaria o serviço por um problema de configuração. O sinal operacional é a taxa da DLT (alerta fica para a change de observabilidade).
- **[R3] A DLT não é reprocessada automaticamente.** Um evento ruim só volta ao fluxo por ação humana (republicar no tópico de origem, o que é seguro pelo D2). → Documentado como evolução, junto com um comando de reprocessamento.
- **[R4] Retries em camadas multiplicam chamadas.** O SDK já tenta até 3 vezes e o consumer repete até 1 + `INGESTION_MAX_RETRIES` vezes: até 12 chamadas por registro com os padrões. → O breaker corta a amplificação quando a falha é sistêmica, e os limites são pequenos e configuráveis.
- **[R5] Timestamp do autorizador é a base da ordem** (risco herdado da change anterior): relógio atrasado numa origem faz um evento posterior perder. → Inalterado; a taxa de `StaleIgnored`, agora logada pelo listener, torna o problema visível.
- **[R6] Evento `DECLINED` aplicado muda `updated_at` sem mudar o saldo.** → Decisão D1, com o caminho de reversão descrito.
- **[R7] Falha da própria DLT ou do pedido de pausa.** Sem pausa e sem DLT, o registro volta por seek e é reentregue em laço, com log de erro do Spring a cada volta. → Limitação aceita: a DLT usa o mesmo broker do consumo, então uma DLT fora do ar costuma coincidir com o consumo parado. A métrica e o alerta sobre esse laço ficam para a change de observabilidade.
