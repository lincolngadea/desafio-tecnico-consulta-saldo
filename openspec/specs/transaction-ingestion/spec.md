# transaction-ingestion Specification

## Purpose
Consumer Kafka de transacoes-financeiras-processadas: commit manual, DLT, retry, pausa, shutdown e rebalance. Created by archiving change add-transaction-ingestion.
## Requirements
### Requirement: Evento do tópico é lido conforme o contrato do enunciado
O consumer MUST ler cada mensagem de `transacoes-financeiras-processadas` como JSON no formato do enunciado e chamar `ProcessTransactionUseCase` com a transação correspondente. Valores monetários MUST ser lidos como `BigDecimal`, sem passar por ponto flutuante. Um `transaction.status` fora de `APPROVED|DECLINED` MUST NOT invalidar o evento.

#### Scenario: Evento válido chama o caso de uso
- **WHEN** uma mensagem válida é consumida
- **THEN** o caso de uso recebe o id e o timestamp (µs) da transação, o id e o titular da conta e o saldo (valor e moeda)

#### Scenario: Valor monetário é lido sem perda de precisão
- **WHEN** a mensagem traz `account.balance.amount` com valor 1234.50
- **THEN** o saldo recebido pelo caso de uso é exatamente 1234.50

#### Scenario: Status desconhecido não invalida o evento
- **WHEN** a mensagem traz um `transaction.status` fora de `APPROVED|DECLINED`
- **THEN** o evento é processado normalmente

### Requirement: Offset confirmado manualmente só depois da persistência
O consumer MUST usar commit manual e MUST confirmar o offset de um registro somente depois de `ProcessTransactionUseCase` retornar (`Applied`, `StaleIgnored` ou `DuplicateIgnored`) ou de o registro ser aceito pela DLT. O consumer MUST NOT confirmar o offset de um registro cuja persistência falhou por erro transitório (at-least-once).

#### Scenario: Offset confirmado depois da gravação
- **WHEN** o caso de uso retorna `Applied`
- **THEN** o offset do registro é confirmado e só depois disso

#### Scenario: Evento duplicado ou fora de ordem também é confirmado
- **WHEN** o caso de uso retorna `StaleIgnored` ou `DuplicateIgnored`
- **THEN** o offset do registro é confirmado e nenhum erro é registrado

#### Scenario: Falha transitória não confirma o offset
- **WHEN** o caso de uso lança `TransientStorageException` em todas as tentativas
- **THEN** o offset do registro não é confirmado

#### Scenario: Queda entre a gravação e o commit não perde nem corrompe
- **WHEN** o mesmo evento, já gravado, é entregue de novo, como numa reentrega depois de uma queda entre a gravação e o commit
- **THEN** o snapshot permanece idêntico, nada vai para a DLT e o offset da reentrega é confirmado

### Requirement: Erro permanente vai para a DLT com o motivo
Uma falha permanente MUST NOT ser tentada de novo e MUST ser publicada na DLT `<tópico>.DLT` com o payload original e headers que identifiquem o motivo (classe e mensagem da exceção) e a origem (tópico, partição e offset). Depois de a DLT aceitar o registro, o offset MUST ser confirmado e o consumo MUST seguir com o próximo registro. São erros permanentes: JSON malformado, campo obrigatório ausente, valor inválido para o domínio (UUID, moeda, timestamp) e `PermanentStorageException`. A DLT MUST NOT receber erro transitório.

#### Scenario: JSON malformado vai para a DLT
- **WHEN** uma mensagem que não é JSON válido é consumida
- **THEN** ela é publicada na DLT com os headers do motivo, o offset é confirmado e o caso de uso não é chamado

#### Scenario: Campo inválido vai para a DLT
- **WHEN** uma mensagem traz um `account.id` que não é UUID, uma moeda inválida ou um timestamp não positivo
- **THEN** ela é publicada na DLT com os headers do motivo e o offset é confirmado

#### Scenario: Campo obrigatório ausente vai para a DLT
- **WHEN** uma mensagem não traz `account.balance`
- **THEN** ela é publicada na DLT com os headers do motivo e o offset é confirmado

#### Scenario: Falha permanente do armazenamento vai para a DLT
- **WHEN** o caso de uso lança `PermanentStorageException`
- **THEN** a mensagem é publicada na DLT, sem nova tentativa, e o offset é confirmado

#### Scenario: A DLT preserva payload e origem
- **WHEN** uma mensagem é publicada na DLT
- **THEN** o payload é idêntico ao original e os headers trazem a classe e a mensagem da exceção, o tópico, a partição e o offset de origem

#### Scenario: Evento inválido não trava o consumo
- **WHEN** uma mensagem inválida é seguida de uma mensagem válida na mesma partição
- **THEN** a mensagem válida é processada e o snapshot da sua conta é gravado

#### Scenario: Erro transitório não vai para a DLT
- **WHEN** o caso de uso lança `TransientStorageException`
- **THEN** nada é publicado na DLT

### Requirement: Erro transitório é tentado de novo com backoff exponencial e jitter
Diante de `TransientStorageException`, o consumer MUST tentar o mesmo registro de novo, com intervalos que crescem exponencialmente a partir do intervalo inicial, limitados ao intervalo máximo, com um jitter aleatório acima do mínimo. O número de novas tentativas MUST ser limitado por `INGESTION_MAX_RETRIES`.

#### Scenario: Sucesso numa nova tentativa
- **WHEN** o caso de uso lança `TransientStorageException` na primeira tentativa e funciona na segunda
- **THEN** o snapshot é gravado, o offset é confirmado e nada é publicado na DLT

#### Scenario: Os intervalos crescem exponencialmente
- **WHEN** o registro falha em tentativas consecutivas
- **THEN** cada intervalo é o anterior multiplicado pelo multiplicador configurado, sem passar do intervalo máximo, mais o jitter

#### Scenario: O jitter varia os intervalos
- **WHEN** o mesmo cenário de falha é repetido
- **THEN** os intervalos observados ficam dentro da faixa do jitter configurado e não são todos idênticos

#### Scenario: O limite de novas tentativas é respeitado
- **WHEN** `INGESTION_MAX_RETRIES` vale 3 e o caso de uso falha sempre
- **THEN** o caso de uso é chamado 4 vezes para o registro (1 + 3) e o consumer passa a pausa

### Requirement: Dependência indisponível pausa as partições em vez de descartar
Quando o circuito estiver aberto (`StorageUnavailableException`) ou as tentativas de um erro transitório se esgotarem, o consumer MUST pausar as partições atribuídas, MUST NOT publicar na DLT, MUST NOT confirmar o offset e MUST NOT perder o registro. Passado `INGESTION_PAUSE_DURATION`, o consumer MUST retomar as partições e reentregar o registro, que serve de sonda do circuito. Se a sonda falhar, o consumer MUST pausar de novo; se tiver sucesso, o consumo MUST seguir normalmente. A pausa MUST permanecer depois de um rebalance.

#### Scenario: Circuito aberto pausa o consumo
- **WHEN** o caso de uso lança `StorageUnavailableException`
- **THEN** as partições atribuídas são pausadas, nada é publicado na DLT e o offset não é confirmado

#### Scenario: Retries esgotados pausam o consumo
- **WHEN** as tentativas de um `TransientStorageException` se esgotam
- **THEN** as partições atribuídas são pausadas, nada é publicado na DLT e o offset não é confirmado

#### Scenario: Retomada após a pausa reentrega o registro
- **WHEN** `INGESTION_PAUSE_DURATION` passa
- **THEN** as partições são retomadas e o mesmo registro é entregue de novo ao caso de uso

#### Scenario: Sonda bem-sucedida normaliza o consumo
- **WHEN** a dependência voltou e o registro reentregue é gravado
- **THEN** o offset é confirmado e os registros seguintes são consumidos normalmente

#### Scenario: Sonda que falha pausa de novo
- **WHEN** a dependência continua fora e o registro reentregue falha
- **THEN** as partições são pausadas outra vez, sem DLT e sem confirmar o offset

#### Scenario: Nenhum evento é perdido numa indisponibilidade
- **WHEN** a dependência fica indisponível durante o consumo de N mensagens e depois se recupera
- **THEN** os snapshots das N mensagens acabam gravados, sem nenhuma na DLT

#### Scenario: A pausa sobrevive a um rebalance
- **WHEN** ocorre um rebalance com o consumer pausado
- **THEN** as partições atribuídas continuam pausadas até a retomada

### Requirement: Graceful shutdown
Ao encerrar, o consumer MUST terminar o registro em andamento, confirmar o seu offset se ele foi persistido, deixar o grupo de consumidores e cancelar a retomada agendada.

#### Scenario: Registro em andamento termina antes do encerramento
- **WHEN** o contexto é encerrado enquanto um registro está sendo processado
- **THEN** o registro é gravado e o seu offset é confirmado antes de o consumer parar

#### Scenario: Instância sai do grupo
- **WHEN** o consumer para
- **THEN** ele deixa o grupo, e as suas partições passam a outra instância sem esperar o timeout de sessão

#### Scenario: Retomada agendada é cancelada no encerramento
- **WHEN** o consumer é encerrado durante uma pausa
- **THEN** o agendador da retomada é encerrado junto, e nenhuma retomada fica pendente

### Requirement: Rebalance sem perda
Num rebalance, os registros já persistidos e confirmados MUST NOT ser reprocessados, e os não confirmados MUST ser entregues ao novo dono, onde um reprocessamento MUST resultar em `DuplicateIgnored`, sem erro.

#### Scenario: Partições revogadas durante o consumo
- **WHEN** um segundo consumidor do mesmo grupo entra e as partições são redistribuídas durante o consumo
- **THEN** todos os eventos são processados por algum membro do grupo e nenhum vai para a DLT

### Requirement: Tópicos com partições definidas e criados pelo comando do kit
Os tópicos `transacoes-financeiras-processadas` e `transacoes-financeiras-processadas.DLT` MUST ser criados com 6 partições por `make kafka-topic-create`, e a documentação MUST registrar os comandos.

#### Scenario: Criação pelo comando do kit
- **WHEN** `make kafka-topic-create NAME=transacoes-financeiras-processadas PARTITIONS=6` e o equivalente para a DLT são executados
- **THEN** cada tópico existe com 6 partições

### Requirement: Parâmetros do consumer configuráveis por variável de ambiente
Tópico, DLT, grupo, concorrência, limite de novas tentativas, intervalo inicial, multiplicador, intervalo máximo, jitter e duração da pausa MUST ser configuráveis por `TRANSACTIONS_TOPIC`, `TRANSACTIONS_DLT_TOPIC`, `TRANSACTIONS_CONSUMER_GROUP_ID`, `INGESTION_CONCURRENCY`, `INGESTION_MAX_RETRIES`, `INGESTION_BACKOFF_INITIAL`, `INGESTION_BACKOFF_MULTIPLIER`, `INGESTION_BACKOFF_MAX`, `INGESTION_BACKOFF_JITTER` e `INGESTION_PAUSE_DURATION`, com padrões no `application.yaml`.

#### Scenario: Valores padrão
- **WHEN** nenhuma variável é definida
- **THEN** o consumer usa 3 novas tentativas, intervalo inicial de 200 ms, multiplicador 2.0, intervalo máximo de 2 s, jitter de 100 ms e pausa de 30 s

#### Scenario: Limite de tentativas sobrescrito
- **WHEN** `INGESTION_MAX_RETRIES` é definida
- **THEN** o consumer usa esse limite

#### Scenario: O tempo máximo bloqueado cabe no max.poll.interval
- **WHEN** os valores padrão estão em uso
- **THEN** o pior caso de tempo bloqueado num registro é menor que `max.poll.interval.ms`
