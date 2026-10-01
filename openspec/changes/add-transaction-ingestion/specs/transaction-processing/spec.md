## ADDED Requirements

### Requirement: Transação processada vira snapshot de saldo
O caso de uso `ProcessTransactionUseCase` MUST montar um `BalanceSnapshot` a partir da transação processada e gravá-lo por `BalanceRepository.saveIfNewer`, devolvendo o `SnapshotSaveResult` recebido. A versão do snapshot MUST ser formada pelo timestamp e pelo id da transação do evento. O serviço MUST tratar da mesma forma toda transação, aprovada ou recusada (`DECLINED`), porque todo evento já traz o saldo calculado pelo autorizador.

#### Scenario: Transação aprovada grava o snapshot
- **WHEN** uma transação aprovada é processada
- **THEN** o repositório recebe um snapshot com a conta, o titular, o saldo e a versão (timestamp e id da transação) do evento, e o resultado do repositório é devolvido

#### Scenario: Transação recusada também grava o snapshot
- **WHEN** uma transação recusada (`DECLINED`) é processada
- **THEN** o repositório recebe o snapshot do evento da mesma forma que para uma transação aprovada

#### Scenario: Resultado Applied é devolvido
- **WHEN** o repositório responde `Applied`
- **THEN** o caso de uso devolve `Applied`

#### Scenario: Resultado StaleIgnored é devolvido sem erro
- **WHEN** o repositório responde `StaleIgnored` (evento duplicado ou fora de ordem)
- **THEN** o caso de uso devolve `StaleIgnored` e não lança exceção

#### Scenario: Falha do armazenamento não é engolida
- **WHEN** o repositório lança `TransientStorageException`, `PermanentStorageException` ou `StorageUnavailableException`
- **THEN** o caso de uso propaga a mesma exceção

### Requirement: O saldo do evento é mantido como recebido
O serviço MUST gravar no snapshot o saldo (`account.balance`) do evento, sem recalculá-lo a partir do valor ou do tipo da transação.

#### Scenario: Valor da transação não altera o saldo gravado
- **WHEN** uma transação de débito de 100,00 chega com `account.balance` de 50,00
- **THEN** o snapshot gravado tem saldo 50,00
