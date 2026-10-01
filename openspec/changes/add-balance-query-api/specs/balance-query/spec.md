## ADDED Requirements

### Requirement: Consulta devolve o snapshot mais recente da conta
O `GetBalanceUseCase` MUST devolver o snapshot que o `BalanceProvider` devolve para a conta, sem alterá-lo, sem recalcular o saldo e sem consultar outra fonte.

#### Scenario: Conta com snapshot devolve o snapshot
- **WHEN** `getBalance` é chamado para uma conta com snapshot gravado
- **THEN** o resultado é o mesmo snapshot que o provider devolveu, com saldo, titular e versão intactos

#### Scenario: A consulta usa só o identificador da conta
- **WHEN** `getBalance` é chamado para uma conta
- **THEN** o provider recebe exatamente esse `AccountId` numa única chamada

### Requirement: Conta sem snapshot é ausência esperada
O `GetBalanceUseCase` MUST modelar a conta sem snapshot no tipo de retorno (`null`), sem lançar exceção, porque a ausência é um resultado esperado da consulta e não uma falha.

#### Scenario: Conta sem snapshot devolve ausência
- **WHEN** `getBalance` é chamado para uma conta que o provider não conhece
- **THEN** o resultado é `null` e nenhuma exceção é lançada

### Requirement: Falhas do armazenamento não são engolidas
O `GetBalanceUseCase` MUST propagar sem tradução toda `BalanceStorageException` do provider, para o adapter de entrada decidir a resposta ao cliente.

#### Scenario: Falha transitória propaga
- **WHEN** o provider lança `TransientStorageException`
- **THEN** `getBalance` lança a mesma exceção

#### Scenario: Armazenamento indisponível propaga
- **WHEN** o provider lança `StorageUnavailableException`
- **THEN** `getBalance` lança a mesma exceção

#### Scenario: Falha permanente propaga
- **WHEN** o provider lança `PermanentStorageException`
- **THEN** `getBalance` lança a mesma exceção
