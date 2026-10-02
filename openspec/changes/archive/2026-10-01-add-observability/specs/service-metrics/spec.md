## ADDED Requirements

### Requirement: Eventos contados por resultado
O serviço MUST publicar o contador `balance.transactions.processed` com a tag `result`, cujos valores são `applied`, `stale_ignored`, `duplicate` e `dlq`. Cada registro do tópico de transações MUST incrementar exatamente um desses valores quando chega a um desfecho. Um registro devolvido para nova tentativa ou pausado MUST NOT incrementar nenhum, porque ainda não tem desfecho.

#### Scenario: Evento aplicado
- **WHEN** um evento com versão mais nova é gravado
- **THEN** o contador com `result=applied` sobe em 1

#### Scenario: Evento mais antigo
- **WHEN** um evento com versão mais antiga que a armazenada é processado
- **THEN** o contador com `result=stale_ignored` sobe em 1

#### Scenario: Evento duplicado
- **WHEN** o mesmo evento é processado duas vezes
- **THEN** o contador com `result=duplicate` sobe em 1 na segunda vez

#### Scenario: Evento vai para a DLT
- **WHEN** um registro é publicado na DLT por erro permanente
- **THEN** o contador com `result=dlq` sobe em 1

#### Scenario: Falha transitória não conta
- **WHEN** um registro falha por erro transitório e é tentado de novo
- **THEN** nenhum valor de `result` sobe até o desfecho final

#### Scenario: Pausa não conta
- **WHEN** um registro é devolvido porque o armazenamento está indisponível
- **THEN** nenhum valor de `result` sobe

### Requirement: Latência de processamento por registro
O serviço MUST registrar o tempo de processamento de cada registro do listener de transações (do início do tratamento até a confirmação ou a falha), pela observação do Spring Kafka, identificada pelo id do listener. O tempo MUST ser registrado também quando o processamento falha.

#### Scenario: Registro processado gera uma amostra
- **WHEN** um registro é processado com sucesso
- **THEN** o timer do listener de transações registra uma amostra com o id do listener

#### Scenario: Falha também gera uma amostra
- **WHEN** o processamento de um registro lança exceção
- **THEN** o timer registra uma amostra com o resultado de erro

### Requirement: Consumer lag exposto
O serviço MUST expor o lag do consumer do listener de transações pela métrica do cliente Kafka `kafka.consumer.fetch.manager.records.lag.max` (o maior lag entre as partições atribuídas àquele consumer), identificada pelo `client.id`, que carrega o group id do listener. A série por partição (`records.lag`) é criada pelo próprio cliente depois do primeiro ciclo e MAY aparecer mais tarde. A métrica mede o lag de busca (posição do consumer contra o fim do log), e não o de processamento: ela pode marcar 0 enquanto há registros buscados e ainda não processados.

#### Scenario: Lag disponível com o consumer ativo
- **WHEN** o listener de transações está consumindo
- **THEN** a métrica de lag máximo existe para o consumer cujo `client.id` carrega o group id do listener de transações

### Requirement: Estado do circuit breaker exposto
O serviço MUST expor o estado do circuit breaker de escrita (`balance-storage`) e o de leitura (`balance-storage-read`) como métrica, com o nome do circuito e o estado (fechado, aberto, semiaberto), e as chamadas por resultado.

#### Scenario: Estado fechado
- **WHEN** os dois circuitos estão fechados
- **THEN** a métrica de estado marca `closed` para os dois, identificados pelo nome

#### Scenario: Abertura é refletida
- **WHEN** o circuito de leitura abre
- **THEN** a métrica de estado marca `open` para `balance-storage-read` e mantém `closed` para `balance-storage`

#### Scenario: Chamadas contadas por resultado
- **WHEN** o provider de saldo responde com sucesso e depois com falha transitória
- **THEN** as métricas de chamadas do circuito de leitura contam uma chamada bem-sucedida e uma com erro

### Requirement: Métricas HTTP por status
O serviço MUST registrar o tempo e a contagem das requisições HTTP por método, rota, status e resultado (`http.server.requests`). A tag de rota MUST ser o padrão da rota (`/balances/{accountId}`), e nunca o caminho com o valor.

#### Scenario: Consulta com sucesso
- **WHEN** `GET /balances/{accountId}` responde `200`
- **THEN** há uma amostra com `status=200`, `uri=/balances/{accountId}` e `method=GET`

#### Scenario: Consulta com erro
- **WHEN** a consulta responde `400`, `404`, `503` e `500` em requisições distintas
- **THEN** há amostras separadas para cada um desses status na mesma rota

#### Scenario: Rota inexistente tem rótulo de baixa cardinalidade
- **WHEN** o cliente chama dez caminhos diferentes que não existem
- **THEN** as amostras usam um único valor de rota para todos, e não um por caminho

### Requirement: Baixa cardinalidade
Nenhuma métrica MUST ter como tag um identificador de conta, de titular ou de transação, nem outro valor sem limite.

#### Scenario: Consultas a contas distintas
- **WHEN** três contas diferentes são consultadas
- **THEN** o conjunto de séries de `http.server.requests` não cresce com o número de contas

#### Scenario: Eventos de contas distintas
- **WHEN** eventos de contas diferentes são processados
- **THEN** o conjunto de séries de `balance.transactions.processed` continua com no máximo os quatro valores de `result`

### Requirement: Endpoint de métricas na porta de gerenciamento
As métricas MUST ser expostas em `/actuator/prometheus` no formato de texto do Prometheus, na porta de gerenciamento (`MANAGEMENT_PORT`, padrão `8082`), e MUST NOT ser expostas na porta da API (`8080`). Só `health` e `prometheus` MUST estar expostos.

#### Scenario: Prometheus na porta de gerenciamento
- **WHEN** o cliente chama `GET /actuator/prometheus` na porta de gerenciamento
- **THEN** a resposta é `200` com o texto do Prometheus, contendo as métricas deste serviço

#### Scenario: Porta da API não expõe o actuator
- **WHEN** o cliente chama `/actuator/prometheus` na porta da API
- **THEN** a resposta é `404`

#### Scenario: Só health e prometheus
- **WHEN** o cliente chama `/actuator/env` ou `/actuator/beans` na porta de gerenciamento
- **THEN** a resposta é `404`

#### Scenario: Porta de gerenciamento configurável
- **WHEN** a aplicação sobe com `MANAGEMENT_PORT` definida
- **THEN** o endpoint de gerenciamento atende nessa porta
