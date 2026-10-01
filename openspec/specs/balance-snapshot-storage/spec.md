# balance-snapshot-storage Specification

## Purpose
TBD - created by archiving change add-balance-repository. Update Purpose after archive.
## Requirements
### Requirement: Ordem entre versões de snapshot
O domínio MUST definir, num único lugar (`SnapshotVersion`), quando uma versão de snapshot é mais recente que outra. A comparação usa primeiro o timestamp do evento (µs) e, em caso de empate, a forma canônica textual (minúscula) do id da transação, comparada lexicograficamente. Duas versões com o mesmo timestamp e o mesmo id de transação são iguais: nenhuma é mais recente.

#### Scenario: Timestamp maior é mais recente
- **WHEN** duas versões têm timestamps diferentes
- **THEN** a de maior timestamp é a mais recente, independentemente dos ids de transação

#### Scenario: Empate de timestamp desempatado pelo id da transação
- **WHEN** duas versões têm o mesmo timestamp e ids de transação diferentes
- **THEN** a de id lexicograficamente maior é a mais recente

#### Scenario: Mesmo evento não é mais recente que si mesmo
- **WHEN** duas versões têm o mesmo timestamp e o mesmo id de transação
- **THEN** nenhuma das duas é mais recente que a outra

### Requirement: Invariantes dos value objects do snapshot
Os value objects do snapshot MUST validar suas invariantes na construção e lançar uma exceção de domínio com mensagem útil quando forem violadas.

#### Scenario: Moeda fora da ISO 4217 é rejeitada
- **WHEN** um valor monetário é criado com um código de moeda que não existe na ISO 4217
- **THEN** a criação falha com exceção de domínio

#### Scenario: Casas decimais acima das da moeda são rejeitadas
- **WHEN** um valor monetário em `BRL` é criado com mais de 2 casas decimais (ex.: `10.123`)
- **THEN** a criação falha com exceção de domínio, sem arredondar

#### Scenario: Valor monetário é normalizado para as casas da moeda
- **WHEN** um valor monetário em `BRL` é criado com `183.1`
- **THEN** o valor guardado é `183.10` (escala 2) e é igual a um criado com `183.10`

#### Scenario: Saldo negativo é aceito
- **WHEN** um valor monetário é criado com quantia negativa
- **THEN** a criação é aceita, porque o saldo vem calculado pelo autorizador

#### Scenario: Timestamp do evento não positivo é rejeitado
- **WHEN** um timestamp de evento é criado com zero ou valor negativo em µs
- **THEN** a criação falha com exceção de domínio

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

### Requirement: Leitura do snapshot por conta
O repositório MUST buscar o snapshot de uma conta por chave (`GetItem`, nunca `Scan` ou `Query`) com leitura fortemente consistente, e MUST modelar a ausência como resultado nulo, não como exceção.

#### Scenario: Conta com snapshot
- **WHEN** a conta tem snapshot gravado
- **THEN** a leitura devolve id da conta, id do titular, saldo com a escala da moeda, moeda, timestamp do evento e id da transação idênticos aos gravados

#### Scenario: Conta inexistente
- **WHEN** a conta não tem snapshot
- **THEN** a leitura devolve ausência (nulo), sem lançar exceção

#### Scenario: Leitura fortemente consistente
- **WHEN** o repositório busca um snapshot
- **THEN** a requisição ao DynamoDB é um `GetItem` pela chave `accountId` com `ConsistentRead=true`

#### Scenario: Item armazenado malformado
- **WHEN** o item da conta não tem um atributo obrigatório ou tem um valor inválido (ex.: moeda inexistente)
- **THEN** a leitura falha com a exceção de falha permanente do armazenamento

### Requirement: Classificação das falhas do DynamoDB
O adapter MUST traduzir toda falha do SDK, na gravação e na leitura, numa exceção do núcleo que diga se a falha é transitória (vale tentar de novo mais tarde) ou permanente (tentar de novo não resolve). A exceção MUST preservar a original como causa, e nenhum tipo do SDK MUST atravessar o port. `ConditionalCheckFailedException` MUST NOT ser tratada como falha.

#### Scenario: Throttling é transitório
- **WHEN** o DynamoDB responde com throttling (ex.: `ProvisionedThroughputExceededException`)
- **THEN** a operação falha com a exceção de falha transitória

#### Scenario: Erro 5xx é transitório
- **WHEN** o DynamoDB responde com status 5xx
- **THEN** a operação falha com a exceção de falha transitória

#### Scenario: Timeout é transitório
- **WHEN** a chamada estoura o timeout da tentativa ou o da chamada total
- **THEN** a operação falha com a exceção de falha transitória

#### Scenario: Falha de rede é transitória
- **WHEN** o cliente não consegue se comunicar com o DynamoDB por erro de I/O (ex.: conexão recusada)
- **THEN** a operação falha com a exceção de falha transitória

#### Scenario: Erro 4xx de requisição ou configuração é permanente
- **WHEN** o DynamoDB responde com um erro 4xx que não é throttling (ex.: `ResourceNotFoundException`, `ValidationException`)
- **THEN** a operação falha com a exceção de falha permanente

#### Scenario: Causa original preservada
- **WHEN** uma falha do SDK é traduzida
- **THEN** a exceção lançada tem a exceção do SDK como causa

### Requirement: Timeouts explícitos do cliente DynamoDB
O `DynamoDbClient` da aplicação MUST ter timeouts explícitos de conexão, de socket, por tentativa e da chamada total, e um número máximo de tentativas explícito, todos configuráveis por variável de ambiente com default. Nenhuma chamada ao DynamoDB MUST ficar bloqueada além do timeout da chamada total.

#### Scenario: Cliente configurado com os valores declarados
- **WHEN** o cliente é criado a partir da configuração
- **THEN** o timeout por tentativa, o timeout da chamada total e o máximo de tentativas do cliente são os configurados

#### Scenario: DynamoDB que não responde
- **WHEN** o endpoint aceita a conexão mas nunca responde
- **THEN** a operação falha com a exceção de falha transitória dentro do timeout da chamada total

