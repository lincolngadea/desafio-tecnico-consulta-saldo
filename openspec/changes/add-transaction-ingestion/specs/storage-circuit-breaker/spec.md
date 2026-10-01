## ADDED Requirements

### Requirement: Falhas transitórias do armazenamento abrem o circuito
O `BalanceRepository` MUST ser envolvido por um circuit breaker. Somente `TransientStorageException` MUST contar como falha; `Applied`, `StaleIgnored` e `PermanentStorageException` MUST NOT contar. O circuito MUST abrir quando a taxa de falhas, na janela configurada, atingir o limiar configurado.

#### Scenario: Taxa de falhas acima do limiar abre o circuito
- **WHEN** a taxa de `TransientStorageException` na janela atinge o limiar configurado
- **THEN** o circuito passa a aberto

#### Scenario: Falha permanente não conta para o circuito
- **WHEN** o repositório lança `PermanentStorageException` repetidamente
- **THEN** o circuito continua fechado e cada chamada propaga a `PermanentStorageException`

#### Scenario: Resultados esperados contam como sucesso
- **WHEN** o repositório responde `Applied` ou `StaleIgnored`
- **THEN** o circuito continua fechado

### Requirement: Circuito aberto falha rápido com exceção do port
Com o circuito aberto, o decorator MUST lançar `StorageUnavailableException`, subtipo de `BalanceStorageException`, sem chamar o repositório envolvido. Nenhum tipo do Resilience4j MUST atravessar o port.

#### Scenario: Chamada com circuito aberto não alcança o DynamoDB
- **WHEN** `saveIfNewer` é chamado com o circuito aberto
- **THEN** o repositório envolvido não é chamado e `StorageUnavailableException` é lançada

#### Scenario: A exceção não expõe tipo da biblioteca
- **WHEN** `StorageUnavailableException` é lançada pelo circuito aberto
- **THEN** o seu tipo pertence ao port de saída e a mensagem identifica o circuito aberto

### Requirement: Circuito semiaberto sonda a dependência
Passado o tempo de espera configurado, o circuito MUST ficar semiaberto e permitir o número configurado de chamadas de teste. O circuito MUST avaliar as chamadas de teste só depois de todas as permitidas se completarem: se a taxa de falhas atingir o limiar configurado, MUST reabrir; caso contrário, MUST fechar.

#### Scenario: Sondas bem-sucedidas fecham o circuito
- **WHEN** o tempo de espera passa e as chamadas de teste permitidas têm sucesso
- **THEN** o circuito fecha e as chamadas seguintes chegam ao repositório

#### Scenario: Circuito continua semiaberto até as chamadas de teste se completarem
- **WHEN** o tempo de espera passa e só parte das chamadas de teste permitidas foi feita, e ela falhou
- **THEN** o circuito continua semiaberto

#### Scenario: Sondas que falham reabrem o circuito
- **WHEN** o tempo de espera passa e todas as chamadas de teste permitidas lançam `TransientStorageException`
- **THEN** o circuito reabre e a chamada seguinte lança `StorageUnavailableException`

### Requirement: Parâmetros do circuito configuráveis por variável de ambiente
O limiar de falhas, o tamanho da janela, o tempo de espera no estado aberto e o número de chamadas de teste MUST ser configuráveis por `BALANCE_CB_FAILURE_RATE_THRESHOLD`, `BALANCE_CB_SLIDING_WINDOW_SIZE`, `BALANCE_CB_WAIT_DURATION_OPEN` e `BALANCE_CB_HALF_OPEN_CALLS`, com padrões definidos no `application.yaml`.

#### Scenario: Valores padrão
- **WHEN** nenhuma variável é definida
- **THEN** o circuito usa limiar de 50%, janela de 10 chamadas, espera de 30 s e 3 chamadas de teste

#### Scenario: Valores sobrescritos
- **WHEN** as variáveis são definidas
- **THEN** o circuito usa os valores informados
