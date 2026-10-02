## ADDED Requirements

### Requirement: Liveness separado do readiness
O serviço MUST expor `GET /actuator/health/liveness` e `GET /actuator/health/readiness` na porta de gerenciamento, como probes independentes. O `liveness` MUST responder `200` com `{"status":"UP"}` enquanto o processo está vivo, e MUST NOT depender de nenhuma dependência externa (DynamoDB ou Kafka). Um `liveness` que falha leva o orquestrador a reiniciar o processo, e reiniciá-lo não conserta uma dependência fora do ar.

#### Scenario: Processo vivo
- **WHEN** a aplicação está de pé
- **THEN** o `liveness` responde `200` com `{"status":"UP"}`

#### Scenario: Liveness independe do DynamoDB
- **WHEN** o DynamoDB está fora do ar
- **THEN** o `liveness` continua respondendo `200`

#### Scenario: Liveness independe do Kafka
- **WHEN** o broker Kafka está fora do ar
- **THEN** o `liveness` continua respondendo `200`

### Requirement: Readiness não depende do DynamoDB
O `readiness` MUST refletir só se o processo está pronto para receber tráfego (contexto iniciado e fora do encerramento). O `readiness` MUST NOT depender do DynamoDB nem do Kafka: com o DynamoDB fora do ar, o serviço continua pronto e a consulta degrada de forma controlada (`503` com `Retry-After`, e circuito aberto falhando rápido). A razão: todas as instâncias compartilham a mesma dependência, então um `readiness` que a verificasse tiraria todas do balanceador ao mesmo tempo, trocando uma resposta controlada por recusa de conexão.

#### Scenario: Aplicação pronta
- **WHEN** a aplicação terminou de subir
- **THEN** o `readiness` responde `200` com `{"status":"UP"}`

#### Scenario: Readiness continua UP com o DynamoDB fora do ar
- **WHEN** o DynamoDB está fora do ar
- **THEN** o `readiness` responde `200`, e `GET /balances/{accountId}` responde `503` com `Retry-After`

#### Scenario: Readiness continua UP com o Kafka fora do ar
- **WHEN** o broker Kafka está fora do ar
- **THEN** o `readiness` responde `200` e a consulta de saldo segue atendendo

#### Scenario: Probes não incluem indicadores de dependência
- **WHEN** o grupo `readiness` é inspecionado
- **THEN** ele contém só o estado de disponibilidade da aplicação

### Requirement: Readiness recusa tráfego no encerramento
Ao receber o pedido de encerramento, o serviço MUST mudar o `readiness` para fora de serviço (`503`, `OUT_OF_SERVICE`) antes de parar de atender, enquanto o `liveness` continua `200` até o processo terminar, para o orquestrador parar de enviar tráfego novo e as requisições em andamento terminarem.

#### Scenario: Encerramento muda o readiness
- **WHEN** o contexto começa a ser encerrado
- **THEN** a disponibilidade de readiness passa a recusar tráfego antes de o servidor web parar

#### Scenario: Liveness segue UP durante o encerramento
- **WHEN** o encerramento está em andamento
- **THEN** o `liveness` continua `UP`

### Requirement: Detalhes de saúde não são expostos
As respostas de health MUST NOT revelar detalhes (nomes de componentes, hosts ou mensagens de exceção); só o `status`.

#### Scenario: Corpo mínimo
- **WHEN** o cliente chama `/actuator/health`, `/actuator/health/liveness` ou `/actuator/health/readiness`
- **THEN** o corpo contém só o campo `status` (e `groups`, quando for o caso), sem `components` nem `details`
