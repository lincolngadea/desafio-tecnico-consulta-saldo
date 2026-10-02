## ADDED Requirements

### Requirement: Seções obrigatórias do README
O `README.md` MUST conter, como títulos de seção e nesta ordem, `Visão da solução`, `Como rodar`, `Decisões de arquitetura`, `Estratégia de testes`, `Resiliência e observabilidade`, `O que eu faria com mais tempo` e `Como o repositório foi construído`, seguidas de `Variáveis de ambiente` e `Comandos do Makefile` como referência. O README MUST NOT conservar o bloco de instruções ao candidato do template do kit.

#### Scenario: Todas as seções existem
- **WHEN** o README é lido
- **THEN** cada um dos nove títulos aparece como título de seção

#### Scenario: As seções seguem a ordem do pedido
- **WHEN** os títulos de seção do README são listados
- **THEN** `Visão da solução` vem antes de `Como rodar`, e `Como o repositório foi construído` vem antes de `Variáveis de ambiente`

#### Scenario: Instruções ao candidato removidas
- **WHEN** o README é lido
- **THEN** ele não contém o título `Instruções para o candidato`

### Requirement: Visão da solução com fluxo e arquitetura hexagonal
A seção `Visão da solução` MUST abrir com um parágrafo que diga o que o serviço faz (ingere transações do Kafka, guarda o snapshot de saldo mais recente de cada conta no DynamoDB e o expõe em `GET /balances/{accountId}`) e MUST trazer dois diagramas Mermaid: o **fluxo** de ingestão e de consulta, e a **arquitetura hexagonal** com a regra de dependência `adapter → port ← application → domain`. Os nomes dos nós MUST ser de componentes que existem no código.

#### Scenario: Dois diagramas Mermaid
- **WHEN** a seção `Visão da solução` é lida
- **THEN** ela contém exatamente dois blocos ```` ```mermaid ````

#### Scenario: Diagramas só citam componentes reais
- **WHEN** os nomes de classe dos diagramas são comparados com `src/main/kotlin`
- **THEN** cada nome corresponde a um tipo declarado no código

#### Scenario: O parágrafo cita as três pontas do serviço
- **WHEN** o parágrafo de abertura é lido
- **THEN** ele cita Kafka, DynamoDB e `GET /balances/{accountId}`

### Requirement: Como rodar leva do zero à primeira consulta
A seção `Como rodar` MUST listar os pré-requisitos (Docker com Compose, `make`, e JDK 21 só para `make integration-test` e para o IDE) e os passos, nesta ordem: subir o ambiente, criar o tópico `transacoes-financeiras-processadas`, gerar eventos de teste e chamar `GET /balances/{accountId}` com um `curl`. Todo alvo `make` citado MUST existir no `Makefile`. A seção MUST dizer que o gerador de eventos usa uma conta aleatória por evento e indicar como produzir um duplicado e um evento fora de ordem. A seção MUST NOT citar o setup pessoal de Docker remoto do autor.

#### Scenario: Passos na ordem
- **WHEN** a seção `Como rodar` é lida
- **THEN** subir o ambiente, criar o tópico, gerar eventos e consultar a API aparecem nessa ordem

#### Scenario: Os alvos make citados existem
- **WHEN** cada `make <alvo>` citado no README é comparado com os alvos do `Makefile`
- **THEN** todos os alvos existem

#### Scenario: A consulta documenta os cinco status
- **WHEN** a seção sobre a chamada da API é lida
- **THEN** ela lista `200`, `400`, `404`, `503` e `500`, o corpo `application/problem+json` e o cabeçalho `Cache-Control: no-store`

#### Scenario: A limitação do gerador está dita
- **WHEN** a seção é lida
- **THEN** ela diz que o gerador usa uma conta aleatória por evento e que duplicado e fora de ordem se demonstram publicando à mão

#### Scenario: Sem referência ao setup pessoal
- **WHEN** o README é pesquisado por `.local/` e por `docker-host`
- **THEN** nenhuma ocorrência é encontrada

### Requirement: Decisões registradas como ADR curto
A seção `Decisões de arquitetura` MUST trazer sete ADRs, numerados `ADR-1` a `ADR-7`, nesta ordem: snapshot em vez de recálculo, modelagem do DynamoDB, escrita condicional, *at-least-once* com idempotência, DLQ vs pausa do consumer, `ConsistentRead` e ausência de cache. Cada ADR MUST ter os campos **Contexto**, **Decisão** e **Consequência**, e uma linha **Detalhe** que cita a decisão de origem na forma `<change> design Dn`. O ADR MUST registrar a decisão tomada nos `design.md`, sem mudá-la, e usar `DuplicateIgnored` e `StaleIgnored` como na decisão mais recente.

#### Scenario: Sete ADRs
- **WHEN** a seção `Decisões de arquitetura` é lida
- **THEN** ela contém sete títulos `ADR-1` a `ADR-7`, nos temas listados

#### Scenario: Cada ADR tem os três campos
- **WHEN** qualquer ADR é lido
- **THEN** ele contém **Contexto**, **Decisão** e **Consequência**, todos com texto

#### Scenario: Cada ADR aponta para a decisão de origem
- **WHEN** a linha **Detalhe** de qualquer ADR é lida
- **THEN** ela cita pelo menos uma referência no formato `<change> design Dn` de uma change que existe em `openspec/changes/archive/`

#### Scenario: O ADR de escrita condicional traz a condição
- **WHEN** o `ADR-3` é lido
- **THEN** ele cita `ConditionExpression`, `SnapshotVersion` e o desempate pelo maior id de transação

#### Scenario: O ADR de DLQ vs pausa separa os dois destinos
- **WHEN** o `ADR-5` é lido
- **THEN** ele diz que a DLT recebe só erro permanente e que a falha de dependência pausa as partições

#### Scenario: Ambiguidades do enunciado reportadas
- **WHEN** a seção é lida
- **THEN** ela traz a tabela das ambiguidades do enunciado (rota no plural, origem de `updated_at`, status de conta inexistente, evento rejeitado, empate de `timestamp`) com a decisão e a origem de cada uma

### Requirement: Estratégia de testes com pirâmide e cobertura
A seção `Estratégia de testes` MUST descrever a pirâmide (testes unitários em `src/test`, de integração em `src/integrationTest` contra DynamoDB Local e Redpanda reais), os cenários que o enunciado cobra (duplicado, fora de ordem, conta inexistente, concorrência, dependência indisponível) com a classe de teste que cobre cada um, o gate de cobertura do JaCoCo e o caminho do relatório. Toda classe de teste citada MUST existir no repositório.

#### Scenario: Pirâmide em dois níveis com o comando de cada um
- **WHEN** a seção é lida
- **THEN** ela cita `./gradlew check` para os unitários e `make integration-test` para os de integração

#### Scenario: Cenários do enunciado mapeados para testes
- **WHEN** a tabela de cenários é lida
- **THEN** ela mapeia duplicado, fora de ordem, conta inexistente, concorrência e dependência indisponível para uma classe de teste cada

#### Scenario: Classes de teste citadas existem
- **WHEN** cada classe de teste citada no README é procurada em `src/test` e `src/integrationTest`
- **THEN** todas existem

#### Scenario: Cobertura documentada
- **WHEN** a seção é lida
- **THEN** ela cita o gate de 90% do JaCoCo e o caminho `build/reports/jacoco/test/html/index.html`

### Requirement: Resiliência e observabilidade com as métricas a olhar
A seção `Resiliência e observabilidade` MUST descrever o que existe (circuitos separados de leitura e de escrita, timeouts e retries por perfil, DLT, pausa e retomada, encerramento gracioso, *probes* de liveness e readiness, logs estruturados com `traceId`) e MUST listar as métricas a olhar, com o nome exato, a etiqueta e o que o valor indica: `balance_transactions_processed_total{result}`, `spring_kafka_listener_seconds`, `kafka_consumer_fetch_manager_records_lag_max`, `resilience4j_circuitbreaker_state{name}` e `http_server_requests_seconds`. A seção MUST dizer que a métrica de lag do consumer não é confiável com as partições pausadas e que o estado do circuito é o sinal nesse caso.

#### Scenario: As cinco métricas citadas
- **WHEN** a seção é lida
- **THEN** ela cita os cinco nomes de métrica acima

#### Scenario: Os nomes de métrica são os do código
- **WHEN** cada métrica citada é comparada com as definidas no código e nas specs `service-metrics` e `storage-circuit-breaker`
- **THEN** todas existem com o mesmo nome

#### Scenario: A ressalva do lag está dita
- **WHEN** a linha da métrica de lag é lida
- **THEN** ela diz que o valor pode ficar parado com as partições pausadas e remete ao estado do circuito

#### Scenario: Readiness sem dependência externa explicada
- **WHEN** a descrição dos *probes* é lida
- **THEN** ela diz que a readiness não depende de DynamoDB nem de Kafka e dá o motivo

### Requirement: O que eu faria com mais tempo, com motivador
A seção `O que eu faria com mais tempo` MUST ser uma tabela com uma linha por item e as colunas **Item** e **Motivador**. Todo item MUST ter motivador não vazio, e MUST ter origem num `design.md` ou numa spec, onde a lacuna ou o fora de escopo está declarado. Os itens MUST incluir, no mínimo: número de sequência por conta emitido pelo autorizador, rejeição de `timestamp` no futuro, reprocessamento da DLT, partições validadas sob carga, tabela de histórico com `TransactWriteItems`, cache com invalidação por evento, gauge de lag por `AdminClient`, exportação de traces, alertas e dashboards, e autenticação.

#### Scenario: Todo item tem motivador
- **WHEN** as linhas da tabela são lidas
- **THEN** nenhuma célula de **Motivador** está vazia

#### Scenario: Os itens mínimos estão presentes
- **WHEN** a tabela é lida
- **THEN** cada um dos dez itens mínimos aparece

#### Scenario: Nenhum item inventado
- **WHEN** cada item é comparado com os `design.md` e as specs
- **THEN** cada item tem uma fonte que o declara como lacuna, risco ou fora de escopo

### Requirement: Como o repositório foi construído
A seção `Como o repositório foi construído` MUST explicar o *Spec-Driven Development* com OpenSpec: o papel de `openspec/project.md`, `openspec/config.yaml`, `openspec/specs/` e `openspec/changes/archive/`, o ciclo proposal, design, specs, tasks, apply e archive, as changes arquivadas, a revisão por agente independente antes de cada commit e a regra de um commit por change. A seção MUST citar `CLAUDE.md` como a constituição de qualidade e MUST linkar só arquivos que existem no repositório.

#### Scenario: Pastas do openspec explicadas
- **WHEN** a seção é lida
- **THEN** ela explica `project.md`, `config.yaml`, `specs/` e `changes/archive/`

#### Scenario: Changes arquivadas listadas
- **WHEN** a lista de changes é comparada com `openspec/changes/archive/` e `openspec/changes/`
- **THEN** cada change arquivada aparece e nenhuma citada deixa de existir em uma das duas pastas

#### Scenario: Links locais existem
- **WHEN** cada link relativo do README é resolvido
- **THEN** o arquivo ou a pasta de destino existe

#### Scenario: O enunciado é citado e não linkado
- **WHEN** o README é lido
- **THEN** ele cita o `enunciado.md` pelo nome da seção e do item e não contém link para `.challenge/`

### Requirement: Referências do README existem no repositório
Toda variável de ambiente da tabela `Variáveis de ambiente` MUST existir no `application.yaml`, no `docker-compose.yml` ou no `Dockerfile` (as flags da JVM da imagem, `JAVA_TOOL_OPTIONS`, só existem nele), e a tabela MUST incluir `BALANCE_TABLE_NAME` e as variáveis `DYNAMODB_*` do cliente de escrita. A tabela `Comandos do Makefile` MUST listar só alvos que existem e MUST dizer que `make db-scan` e `make http` atuam só sobre o exemplo `hello`.

#### Scenario: Variáveis documentadas existem
- **WHEN** cada variável da tabela é procurada no `application.yaml`, no `docker-compose.yml` e no `Dockerfile`
- **THEN** todas aparecem em pelo menos um dos três

#### Scenario: Variáveis do cliente de escrita documentadas
- **WHEN** a tabela é lida
- **THEN** ela inclui `BALANCE_TABLE_NAME`, `DYNAMODB_CONNECTION_TIMEOUT`, `DYNAMODB_SOCKET_TIMEOUT`, `DYNAMODB_API_CALL_ATTEMPT_TIMEOUT`, `DYNAMODB_API_CALL_TIMEOUT` e `DYNAMODB_MAX_ATTEMPTS`

#### Scenario: Alvos do Makefile documentados existem
- **WHEN** cada alvo da tabela `Comandos do Makefile` é comparado com o `Makefile`
- **THEN** todos existem

#### Scenario: Alvos restritos ao hello sinalizados
- **WHEN** as linhas de `make db-scan` e `make http` são lidas
- **THEN** cada uma diz que atua só sobre o exemplo `hello`
