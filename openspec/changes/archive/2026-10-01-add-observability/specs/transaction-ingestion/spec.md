## MODIFIED Requirements

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

### Requirement: Rebalance sem perda
Num rebalance, os registros já persistidos e confirmados MUST NOT ser reprocessados, e os não confirmados MUST ser entregues ao novo dono, onde um reprocessamento MUST resultar em `DuplicateIgnored`, sem erro.

#### Scenario: Partições revogadas durante o consumo
- **WHEN** um segundo consumidor do mesmo grupo entra e as partições são redistribuídas durante o consumo
- **THEN** todos os eventos são processados por algum membro do grupo e nenhum vai para a DLT
