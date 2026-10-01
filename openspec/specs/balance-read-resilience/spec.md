# balance-read-resilience Specification

## Purpose
Resiliência da leitura do saldo no DynamoDB, com timeouts, retries e circuit breaker independentes da escrita.

## Requirements

### Requirement: Cliente de leitura com timeouts curtos e configuráveis
O `BalanceProvider` MUST usar um cliente DynamoDB próprio da leitura, com timeouts de conexão, de socket, de tentativa e da chamada inteira mais curtos que os do cliente de escrita, todos configuráveis por variável de ambiente. O cliente de escrita e o do `hello` MUST continuar com a configuração atual. Um endpoint que aceita a conexão e nunca responde MUST fazer a leitura falhar com `TransientStorageException` dentro do timeout da chamada inteira.

#### Scenario: Valores padrão
- **WHEN** a aplicação sobe sem configuração da leitura
- **THEN** conexão `200ms`, socket `300ms`, tentativa `300ms`, chamada inteira `800ms` e `2` tentativas no máximo

#### Scenario: Valores sobrescritos
- **WHEN** `DYNAMODB_READ_CONNECTION_TIMEOUT`, `DYNAMODB_READ_SOCKET_TIMEOUT`, `DYNAMODB_READ_API_CALL_ATTEMPT_TIMEOUT`, `DYNAMODB_READ_API_CALL_TIMEOUT` e `DYNAMODB_READ_MAX_ATTEMPTS` são definidas
- **THEN** o cliente de leitura usa esses valores

#### Scenario: A escrita e o hello não mudam
- **WHEN** o cliente de leitura passa a existir
- **THEN** o cliente de escrita e o do `hello` mantêm `500ms`, `1s`, `1s`, `3s` e `3` tentativas, e a injeção deles continua sem qualificador

#### Scenario: Endpoint que não responde
- **WHEN** o DynamoDB aceita a conexão e nunca responde
- **THEN** `findByAccountId` lança `TransientStorageException` em no máximo o timeout da chamada inteira da leitura mais uma folga de agendamento

### Requirement: Leitura repete no máximo uma vez
A leitura MUST fazer no máximo 2 tentativas (1 retry), e só para falha transitória: timeout, erro de servidor (5xx), throttling ou falha de I/O. Falha permanente (requisição inválida, tabela inexistente, acesso negado) MUST NOT ser repetida. A configuração MUST ser rejeitada na subida se `maxAttempts` for maior que 2.

#### Scenario: Sucesso na segunda tentativa
- **WHEN** a primeira tentativa falha com erro 5xx e a segunda responde com o item
- **THEN** `findByAccountId` devolve o snapshot e o servidor recebeu 2 requisições

#### Scenario: Falha nas duas tentativas
- **WHEN** as duas tentativas falham com erro 5xx
- **THEN** `findByAccountId` lança `TransientStorageException` e o servidor recebeu exatamente 2 requisições

#### Scenario: Falha permanente não é repetida
- **WHEN** a primeira tentativa falha com erro de requisição inválida
- **THEN** `findByAccountId` lança `PermanentStorageException` e o servidor recebeu exatamente 1 requisição

#### Scenario: Mais de duas tentativas é rejeitado
- **WHEN** `DYNAMODB_READ_MAX_ATTEMPTS` é `3`
- **THEN** a aplicação não sobe, com uma mensagem que cita o limite de 1 retry

### Requirement: O pior caso da leitura cabe no orçamento de latência
O tempo da chamada inteira (`apiCall`) MUST ser suficiente para todas as tentativas, ou seja, `maxAttempts × apiCallAttempt ≤ apiCall`, para o retry ter espaço e o pior caso da leitura ser o próprio `apiCall`. A configuração que violar a relação MUST ser rejeitada na subida.

#### Scenario: Orçamento coerente
- **WHEN** a aplicação sobe com os valores padrão da leitura
- **THEN** `2 × 300ms` é menor ou igual a `800ms` e a aplicação sobe

#### Scenario: Orçamento incoerente
- **WHEN** a tentativa é de `500ms`, a chamada inteira é de `800ms` e `maxAttempts` é `2`
- **THEN** a aplicação não sobe, com uma mensagem que cita a relação

### Requirement: Circuit breaker de leitura com a classificação de erros compartilhada
O `BalanceProvider` injetado nos casos de uso MUST ser envolvido por um circuit breaker próprio da leitura, construído com a mesma definição do circuito de escrita: só `TransientStorageException` conta como falha, `PermanentStorageException` é ignorada, e os parâmetros vêm de `balance.circuit-breaker.*`. Um resultado ausente (conta sem snapshot) MUST contar como sucesso. Com o circuito aberto, o decorator MUST lançar `StorageUnavailableException` sem chamar o provider envolvido. O circuito de leitura e o de escrita MUST ser instâncias independentes.

#### Scenario: Taxa de falhas acima do limiar abre o circuito
- **WHEN** a taxa de `TransientStorageException` na janela atinge o limiar configurado
- **THEN** o circuito de leitura passa a aberto

#### Scenario: Falha permanente não conta para o circuito
- **WHEN** o provider lança `PermanentStorageException` repetidamente
- **THEN** o circuito continua fechado e cada chamada propaga a `PermanentStorageException`

#### Scenario: Conta inexistente conta como sucesso
- **WHEN** o provider devolve `null` repetidamente
- **THEN** o circuito continua fechado

#### Scenario: Chamada com circuito aberto não alcança o DynamoDB
- **WHEN** `findByAccountId` é chamado com o circuito aberto
- **THEN** o provider envolvido não é chamado e `StorageUnavailableException` é lançada, sem tipo do Resilience4j

#### Scenario: Sondas bem-sucedidas fecham o circuito
- **WHEN** o circuito está semiaberto e as chamadas de teste configuradas têm sucesso
- **THEN** o circuito volta a fechado

#### Scenario: Os dois circuitos são independentes
- **WHEN** o circuito de escrita abre
- **THEN** o circuito de leitura continua fechado, e o inverso também vale

#### Scenario: A classificação é a mesma nos dois circuitos
- **WHEN** cada circuito recebe `TransientStorageException` e `PermanentStorageException`
- **THEN** ambos contam a primeira como falha e ignoram a segunda
