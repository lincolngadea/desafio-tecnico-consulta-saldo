## Context

- O repositório de saldo já está implementado e especificado: `DynamoDbBalanceWriter` (`PutItem` condicional), `DynamoDbBalanceProvider` (`GetItem` com `ConsistentRead`), classificação de falhas, timeouts explícitos e a tabela `AccountBalances` (PK `accountId`, sem sort key e sem GSI). Esta change não altera nada disso.
- O que falta é o **teste de concorrência** do pedido: N escritas simultâneas para a mesma conta, em ordem aleatória, com coroutines em paralelo contra o DynamoDB Local. O teste atual (`DynamoDbBalanceIntegrationTest`) dispara 32 escritas com `Executors.newFixedThreadPool` e `invokeAll`.
- `kotlinx-coroutines-core` 1.10.2 já está no classpath de **runtime** dos testes, por transitividade de outra biblioteca de teste, mas não no de **compilação**, então o teste não pode usá-la sem declaração explícita.

## Goals / Non-Goals

**Goals:**
- Provar a garantia de concorrência com coroutines rodando em paralelo de verdade, contra o DynamoDB Local.
- Registrar no `design.md` as alternativas descartadas para a concorrência e os limites do modelo.

**Non-Goals:**
- Qualquer mudança em código de produção, ports, modelagem, classificação de falhas ou timeouts.
- Retry, backoff e circuit breaker: a change de resiliência decide o que fazer com a falha transitória.
- Consumer Kafka e endpoint REST.

## Decisions

### D1. Teste de concorrência com coroutines em paralelo
- **Escolha:** `runBlocking(Dispatchers.IO)` lança N `async`, um por snapshot, em ordem embaralhada. Todos esperam um `CompletableDeferred` (o portão) e só começam quando ele é completado, para as escritas realmente se sobreporem. Depois, `awaitAll()` e a leitura final.
- **Por quê:** o `runBlocking` sozinho usa uma thread só e serializaria as chamadas bloqueantes do SDK, escondendo a corrida. `Dispatchers.IO` dá threads suficientes (até 64) para as 32 escritas. O portão evita que as primeiras terminem antes de as últimas começarem.
- **Dados do teste:** versões distintas, com grupos que dividem o mesmo timestamp, para o desempate pelo id da transação também ser exercido sob concorrência. A asserção é que nenhuma escrita falha e que a leitura devolve o snapshot de maior `SnapshotVersion`.
- **Prova de que o teste pega o defeito:** uma tarefa troca temporariamente a `ConditionExpression` por uma gravação incondicional e confirma que o teste fica vermelho, antes de restaurar a condição.
- **Alternativa descartada:** manter `Executors` e `invokeAll` (JDK). Funciona e testa o mesmo, mas não atende ao pedido de usar coroutines.

### D2. Dependência `kotlinx-coroutines-core` só para teste (Art. 10)
- **Escolha:** declarar `org.jetbrains.kotlinx:kotlinx-coroutines-core` em `testImplementation`. Nada entra no código de produção.
- **Evidência:** versão resolvida 1.10.2 (publicada em 08/04/2025). A última release é a 1.11.0 (publicada em 07/05/2026, segundo o Maven Central). É a biblioteca oficial de coroutines da JetBrains, mantida e amplamente adotada no ecossistema Kotlin e Spring.
- **Custo contra implementar:** uma linha no build, contra montar à mão um portão e um despacho paralelo com `Executors` e `CountDownLatch`, que é código de teste a mais para manter.
- **Versão:** gerenciada pelo BOM do Spring Boot 4.1 e resolvida em 1.10.2, então o `build.gradle.kts` a declara sem versão.
- **Alternativas avaliadas:** JDK (`Executors`, `CompletableFuture`), que funciona mas não atende ao pedido; `kotlinx-coroutines-test` (`runTest`), descartada porque o tempo virtual não serve a um teste contra I/O real.

### D3. Alternativas descartadas para a concorrência
| Alternativa | Como seria | Por que não |
|-|-|-|
| **Ler, comparar e gravar na aplicação** | `GetItem`, comparar, `PutItem` | É check-then-act: duas instâncias leem o mesmo valor e a última a gravar vence, mesmo sendo a mais antiga. A escrita condicional faz a verificação e a gravação numa operação só, sem janela de corrida. |
| **Lock otimista por `version`** | `GetItem`, comparar no app, `PutItem` com `version = :expected` e retentar em conflito | A `version` ordena pela chegada ao serviço, e é preciso ordenar pelo fato na origem: o timestamp do evento já é a versão semântica. Ainda custaria duas idas ao banco por evento e um laço de retentativas sob contenção. |
| **`TransactWriteItems`** | Transação com a escrita condicional | Serve para atomicidade entre vários itens, e escrevemos um. Custa o dobro de WCU e adiciona `TransactionConflictException` sob contenção. Só seria necessária se gravássemos histórico e saldo juntos. Fica como evolução documentada. |
| **Tabela de histórico por evento** | Um item por evento (PK `accountId`, SK `timestamp#transactionId`), leitura com `Query` descendente e `Limit 1` | Idempotência e ordem sairiam pela chave, mas o armazenamento cresce sem limite, cada leitura vira `Query` e o enunciado pede o saldo atual, não o extrato. O tópico Kafka já é o log; a tabela é uma visão materializada dele. |

### D4. Conformidade com a constituição e padrões
- **Camadas tocadas:** nenhuma de produção. Só `src/integrationTest` e o `build.gradle.kts`. A regra de dependência (Art. 1) e os ports ficam como estão.
- **Pattern (Art. 9):** nenhum cabe, porque é um teste. O portão (`CompletableDeferred`) é a forma idiomática de coroutines para largada simultânea.
- **Coerência (Art. 8):** o teste mantém o nome, as fixtures e o padrão Arrange-Act-Assert do arquivo, e só troca o disparo paralelo. O cabeçalho de comentário do arquivo (Art. 6) é atualizado junto, com as linhas novas.
- **Ambiguidades do enunciado:** nenhuma nova. As já resolvidas (`updated_at` e desempate) continuam valendo.

## Risks / Trade-offs

- **[Hot partition]** Todas as escritas de uma conta vão para um único item e, portanto, para uma partição. O limite é de cerca de 1.000 WCU/s por partição, folga grande para uma conta. → Se não fosse, os eventos da conta seriam agrupados no consumer antes de gravar.
- **[Relógio do autorizador]** A ordem depende do `transaction.timestamp`, gerado na origem. Se os relógios das instâncias divergirem, um evento posterior com relógio atrasado pode perder para um anterior. É o maior risco do modelo. → A correção definitiva é um número de sequência por conta emitido na origem. Fica como limitação e evolução. A taxa de `StaleIgnored` por conta torna o problema visível.
- **[Teste não determinístico]** Concorrência real pode, em tese, não produzir sobreposição. → O portão maximiza a sobreposição, e a tarefa de prova (condição desligada) confirma que o teste detecta o defeito.
- **[Leitura forte]** Custa o dobro de RCU e pode falhar onde a leitura eventual responderia. Mantido: saldo desatualizado em banco é incidente, então escolhemos consistência em vez de latência (PACELC).
