## MODIFIED Requirements

### Requirement: Falhas transitórias do armazenamento abrem o circuito
O `BalanceRepository` MUST ser envolvido por um circuit breaker. Somente `TransientStorageException` MUST contar como falha; `Applied`, `StaleIgnored`, `DuplicateIgnored` e `PermanentStorageException` MUST NOT contar. O circuito MUST abrir quando a taxa de falhas, na janela configurada, atingir o limiar configurado.

#### Scenario: Taxa de falhas acima do limiar abre o circuito
- **WHEN** a taxa de `TransientStorageException` na janela atinge o limiar configurado
- **THEN** o circuito passa a aberto

#### Scenario: Falha permanente não conta para o circuito
- **WHEN** o repositório lança `PermanentStorageException` repetidamente
- **THEN** o circuito continua fechado e cada chamada propaga a `PermanentStorageException`

#### Scenario: Resultados esperados contam como sucesso
- **WHEN** o repositório responde `Applied`, `StaleIgnored` ou `DuplicateIgnored`
- **THEN** o circuito continua fechado
