## ADDED Requirements

### Requirement: Logs em JSON com traceId
Toda linha de log escrita no console MUST ser um objeto JSON de uma linha, com no mínimo o instante, o nível, o logger e a mensagem. Quando a linha é escrita durante o tratamento de uma requisição HTTP ou de um registro do Kafka, ela MUST trazer o campo `traceId`. O `traceId` MUST ser o único identificador de correlação: o serviço MUST NOT ter um segundo identificador (`correlationId`) com o mesmo papel.

#### Scenario: A linha de log é um JSON válido
- **WHEN** o serviço registra um log durante uma requisição
- **THEN** a linha é um objeto JSON com instante, nível, logger, mensagem e `traceId`

#### Scenario: Stack trace não quebra a linha
- **WHEN** uma exceção é registrada com stack trace
- **THEN** a saída continua sendo uma única linha JSON, com o stack trace dentro de um campo

#### Scenario: Fora de uma requisição não há traceId
- **WHEN** o serviço registra um log fora de qualquer requisição ou registro (por exemplo, na subida)
- **THEN** a linha é um JSON válido sem o campo `traceId`

### Requirement: traceId propagado do HTTP
O `traceId` de uma requisição HTTP MUST ser o `trace-id` do cabeçalho `traceparent` (W3C Trace Context) quando ele for válido, e um identificador novo de 32 dígitos hexadecimais caso contrário. O mesmo valor MUST aparecer em todo log da requisição e no campo `traceId` do corpo de erro `problem+json`.

#### Scenario: traceparent válido é reaproveitado no log
- **WHEN** a requisição traz `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`
- **THEN** os logs da requisição trazem `traceId` `4bf92f3577b34da6a3ce929d0e0e4736`

#### Scenario: Requisição sem traceparent recebe um traceId novo
- **WHEN** a requisição não traz `traceparent`
- **THEN** os logs trazem um `traceId` de 32 dígitos hexadecimais, diferente de uma requisição para outra

#### Scenario: O traceId do log é o do corpo de erro
- **WHEN** uma requisição termina em erro
- **THEN** o `traceId` do corpo `problem+json` é o mesmo que consta nos logs dessa requisição

#### Scenario: O traceId não vaza para a requisição seguinte
- **WHEN** duas requisições são tratadas na mesma thread, uma depois da outra
- **THEN** os logs da segunda não trazem o `traceId` da primeira

### Requirement: traceId propagado do Kafka
O `traceId` de um registro do tópico de transações MUST ser o `trace-id` do header `traceparent` do registro quando ele for válido, e um identificador novo caso contrário. O mesmo valor MUST aparecer em todo log do processamento do registro, e a publicação na DLT MUST preservar o header `traceparent` original. Esta regra vale só para o listener de transações; o consumer do kit (`hello`) MUST continuar com o comportamento atual.

#### Scenario: Header traceparent do registro é reaproveitado
- **WHEN** um registro com `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01` é consumido
- **THEN** os logs do processamento do registro trazem `traceId` `4bf92f3577b34da6a3ce929d0e0e4736`

#### Scenario: Registro sem header recebe um traceId novo
- **WHEN** um registro sem `traceparent` é consumido
- **THEN** os logs do processamento trazem um `traceId` novo de 32 dígitos hexadecimais, diferente de um registro para outro

#### Scenario: A DLT preserva o header
- **WHEN** um registro com `traceparent` é publicado na DLT por erro permanente
- **THEN** o registro da DLT traz o mesmo `traceparent` do original

#### Scenario: O traceId não vaza para o registro seguinte
- **WHEN** dois registros são processados na mesma thread do consumer, um depois do outro
- **THEN** os logs do segundo não trazem o `traceId` do primeiro

#### Scenario: O consumer do hello não muda
- **WHEN** uma mensagem do tópico `greeting-templates` é consumida
- **THEN** o consumer do `hello` continua com o comportamento atual (commit automático, sem observação)

### Requirement: Dados pessoais e payload nunca vão para o log
O serviço MUST NOT registrar em log o identificador do titular (`owner`) nem o payload de um evento ou de uma resposta, em nenhum nível, inclusive nas mensagens de exceção. O identificador da conta (`accountId`) MUST ser registrado mascarado, mostrando só o primeiro grupo do UUID (8 dígitos hexadecimais). O identificador da transação MAY ser registrado por inteiro.

#### Scenario: Evento malformado com owner no payload
- **WHEN** um evento com JSON inválido, que contém um `owner` reconhecível, é consumido e vai para a DLT
- **THEN** nenhuma linha de log do serviço contém o valor do `owner` nem trecho do payload

#### Scenario: Falha de domínio no evento
- **WHEN** um evento com valor rejeitado pelo domínio (por exemplo, moeda inválida) é consumido
- **THEN** nenhuma linha de log contém o `owner`, o valor do saldo nem o JSON do evento

#### Scenario: Falha do armazenamento com a conta no contexto
- **WHEN** uma operação do DynamoDB falha para uma conta e a falha é registrada
- **THEN** a linha mostra só o primeiro grupo do `accountId`, e não o UUID por inteiro

#### Scenario: Consulta de saldo registrada
- **WHEN** uma consulta de saldo termina com sucesso ou com erro
- **THEN** nenhum log da consulta contém o saldo, o `owner` nem o `accountId` por inteiro

### Requirement: Tipos de domínio têm representação textual segura
`OwnerId` MUST ter `toString` que não revela o UUID, e `AccountId` MUST ter `toString` que revela só o primeiro grupo do UUID, de modo que um objeto de domínio (`ProcessedTransaction`, `BalanceSnapshot`) interpolado em uma mensagem não vaze o `owner` nem a conta. O valor real MUST continuar acessível por `value`, que a serialização e o armazenamento usam.

#### Scenario: toString do titular não revela o valor
- **WHEN** um `OwnerId` é convertido em texto
- **THEN** o texto não contém nenhum dígito do UUID

#### Scenario: toString da conta é mascarado
- **WHEN** um `AccountId` é convertido em texto
- **THEN** o texto contém só o primeiro grupo do UUID

#### Scenario: Objeto composto não vaza
- **WHEN** um `BalanceSnapshot` ou um `ProcessedTransaction` é convertido em texto
- **THEN** o texto não contém o `owner` nem o `accountId` por inteiro

#### Scenario: O valor real segue disponível
- **WHEN** um snapshot é gravado ou respondido
- **THEN** o `id` e o `owner` gravados e devolvidos são os UUIDs completos

### Requirement: Resultado esperado não polui o log
Evento duplicado ou mais antigo é um resultado esperado e frequente neste fluxo, então o listener MUST registrá-lo em nível `DEBUG`, e não `INFO`. A contagem desses eventos fica nas métricas.

#### Scenario: Evento duplicado não gera log INFO
- **WHEN** um evento duplicado é processado
- **THEN** nenhuma linha de nível `INFO` ou acima é escrita para ele

#### Scenario: Evento mais antigo não gera log INFO
- **WHEN** um evento mais antigo que o snapshot é processado
- **THEN** nenhuma linha de nível `INFO` ou acima é escrita para ele
