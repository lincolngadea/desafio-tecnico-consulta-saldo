## MODIFIED Requirements

### Requirement: Gravação condicional do snapshot
O repositório MUST gravar o snapshot de uma conta somente se a conta não tiver snapshot, ou se a versão recebida for mais recente que a armazenada (segundo a ordem de `SnapshotVersion`). A verificação e a escrita MUST ser uma única operação atômica no DynamoDB. Uma versão não mais recente MUST resultar em `StaleIgnored`, sem erro e sem alterar o item; uma gravação efetiva MUST resultar em `Applied`.

#### Scenario: Primeiro snapshot da conta
- **WHEN** um snapshot é gravado para uma conta sem snapshot
- **THEN** o resultado é `Applied` e a leitura da conta devolve esse snapshot

#### Scenario: Evento mais novo substitui o snapshot
- **WHEN** um snapshot com timestamp maior que o armazenado é gravado
- **THEN** o resultado é `Applied` e a leitura devolve o novo snapshot

#### Scenario: Evento mais antigo chega depois (fora de ordem)
- **WHEN** um snapshot com timestamp menor que o armazenado é gravado
- **THEN** o resultado é `StaleIgnored` e a leitura continua devolvendo o snapshot armazenado

#### Scenario: Mensagem duplicada
- **WHEN** o mesmo snapshot (mesmo timestamp e mesmo id de transação) é gravado duas vezes
- **THEN** a segunda gravação resulta em `StaleIgnored` e o item permanece idêntico

#### Scenario: Empate de timestamp com id de transação maior
- **WHEN** um snapshot com o mesmo timestamp do armazenado e id de transação lexicograficamente maior é gravado
- **THEN** o resultado é `Applied`

#### Scenario: Empate de timestamp com id de transação menor
- **WHEN** um snapshot com o mesmo timestamp do armazenado e id de transação lexicograficamente menor é gravado
- **THEN** o resultado é `StaleIgnored`

#### Scenario: Armazenamento concorda com a ordem do domínio
- **WHEN** cada par (armazenado, recebido) da matriz de casos — mais antigo, mais novo, duplicado, empate com id menor, empate com id maior — é gravado no DynamoDB
- **THEN** o resultado é `Applied` exatamente quando `SnapshotVersion` do recebido é mais recente que o do armazenado

#### Scenario: Gravações concorrentes da mesma conta
- **WHEN** N escritas da mesma conta, com versões distintas e algumas com o mesmo timestamp, são disparadas ao mesmo tempo, em ordem aleatória, por coroutines em paralelo contra o DynamoDB Local
- **THEN** nenhuma escrita falha e, ao final, a leitura devolve o snapshot de maior `SnapshotVersion`

#### Scenario: Um item por conta
- **WHEN** vários snapshots da mesma conta são gravados
- **THEN** a tabela contém um único item para a conta, identificado apenas pela partition key `accountId`
