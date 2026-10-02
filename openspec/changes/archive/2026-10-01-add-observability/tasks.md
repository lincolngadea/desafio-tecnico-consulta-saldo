## 1. Dependências e spike (R1)

- [x] 1.1 Confirmar com `./gradlew dependencyInsight` quais artefatos o BOM do Boot gerencia, e declarar em `implementation` no `build.gradle.kts`: `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `spring-boot-micrometer-tracing-opentelemetry`, `micrometer-tracing-bridge-otel` e `io.github.resilience4j:resilience4j-micrometer:2.4.0` (versão fixa)
- [x] 1.2 Testes vermelhos de spike (`@SpringBootTest` com MockMvc, `management.server.port` igual ao da API e `@AutoConfigureObservability`): o JSON do log com `traceId` (campo no topo da linha, `logstash`), o `traceparent` virando `traceId` no MDC, `/actuator/prometheus` com `http.server.requests` e `/actuator/health/liveness`; registrar no `design.md` qualquer nome ou comportamento que difira (R1) e **parar e perguntar** se uma peça não existir no Boot 4.1.0 (Art. 10)
- [x] 1.3 Definir `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` nas tarefas `Test` (inclui `integrationTest`) e `bootRun` do `build.gradle.kts`, antes de a cadeia padrão de credenciais entrar (seção 7)

## 2. Domínio: representação segura e `DuplicateIgnored` (TDD)

- [x] 2.1 Teste vermelho (`OwnerIdTest`, `AccountIdTest`, `ProcessedTransactionTest`, `BalanceSnapshotTest`): "toString do titular não revela o valor", "toString da conta é mascarado", "Objeto composto não vaza" e "O valor real segue disponível"
- [x] 2.2 Criar o `toString` seguro em `OwnerId` e `AccountId`, até 2.1 ficar verde
- [x] 2.3 Teste vermelho (`SnapshotSaveResultTest`): a regra de recusa no domínio: mesma versão resulta em `DuplicateIgnored`, versão armazenada mais nova (por timestamp e por desempate do id de transação) resulta em `StaleIgnored`
- [x] 2.4 Acrescentar `DuplicateIgnored` à sealed `SnapshotSaveResult` e a regra de recusa no domínio, até 2.3 ficar verde; conferir que todo `when` sobre a sealed continua exaustivo e sem `else`

## 3. Writer: a distinção sem leitura extra (TDD)

- [x] 3.1 Teste vermelho (`DynamoDbBalanceWriterTest`, cliente mockado): o `PutItem` pede `ReturnValuesOnConditionCheckFailure=ALL_OLD`; "Mensagem duplicada" (item devolvido de mesma versão resulta em `DuplicateIgnored`), "Evento mais antigo" e "Empate com id menor" (resultam em `StaleIgnored`), "A distinção não custa uma leitura" (nenhuma chamada de leitura), "Armazenamento não devolve o item da recusa" (`StaleIgnored`) e "Item da recusa malformado" (`PermanentStorageException`)
- [x] 3.2 Alterar o writer para pedir o item da recusa e classificar pelo domínio, até 3.1 ficar verde
- [x] 3.3 Atualizar `ProcessTransactionServiceTest`, `CircuitBreakerBalanceRepositoryTest` e os fakes dos testes para "Resultado DuplicateIgnored é devolvido sem erro" e "Resultados esperados contam como sucesso"
- [x] 3.4 [Docker] Atualizar `DynamoDbBalanceIntegrationTest`: a matriz de pares passa a esperar `DuplicateIgnored` exatamente para versões iguais; confirmar no DynamoDB Local 3.3.0 que a recusa devolve o item (R2) e registrar o resultado no `design.md`

## 3b. Dados sensíveis nas mensagens (TDD)

- [x] 3b.1 Teste vermelho (`DynamoDbFailureTranslationTest`, `TransactionEventMapperTest`): "Falha do armazenamento com a conta no contexto" (mensagem com só o primeiro grupo do `accountId`) e "Falha de domínio no evento" (a mensagem da exceção não contém owner, saldo nem JSON); spike do Jackson 3: um JSON inválido com `owner` reconhecível não deixa trecho do payload na cadeia de causas, e desligar a feature de "source" no `JsonMapper` se deixar (D4)
- [x] 3b.2 Ajustar `translatingSdkFailures` (usa o `toString` mascarado) até 3b.1 ficar verde (a configuração do `JsonMapper` não precisou mudar: o spike mostrou que o padrão do Jackson 3 não vaza)

## 4. Métrica de resultado por evento e log do listener (TDD)

- [x] 4.1 Teste vermelho (`TransactionOutcomeMetricsTest`, `SimpleMeterRegistry`): "Evento aplicado", "Evento mais antigo", "Evento duplicado" e "Evento vai para a DLT" (um incremento de `balance.transactions.processed` com o `result` certo); "Eventos de contas distintas" (no máximo quatro séries, sem tag de identificador)
- [x] 4.2 Teste vermelho (`TransactionEventListenerTest`, `IngestionRecovererTest`): o listener incrementa um resultado por `SnapshotSaveResult` (`when` exaustivo) e confirma o offset; "Falha transitória não conta" e "Pausa não conta"; `dlq` só depois de a DLT aceitar o registro; "Evento duplicado não gera log INFO" e "Evento mais antigo não gera log INFO" (nível `DEBUG`)
- [x] 4.3 Criar `TransactionOutcomeMetrics` no adapter Kafka e ligá-la ao listener e ao recoverer, e passar o log de duplicado e antigo para `DEBUG`, até 4.1 e 4.2 ficarem verdes

## 5. Correlação: traceId do HTTP e do Kafka, e log JSON (TDD)

- [x] 5.1 Teste vermelho (log JSON, `OutputCaptureExtension` lendo as linhas com `JsonMapper`): "A linha de log é um JSON válido", "Stack trace não quebra a linha", "Fora de uma requisição não há traceId"
- [x] 5.2 Teste vermelho (HTTP, MockMvc): "traceparent válido é reaproveitado no log", "Requisição sem traceparent recebe um traceId novo", "O traceId do log é o do corpo de erro" e "O traceId não vaza para a requisição seguinte"; os cenários de `traceparent` da spec `balance-query-api` continuam verdes sem o `TraceIdFilter`
- [x] 5.3 Teste vermelho (Kafka, unitário do container factory): o container de transações tem observação ligada e o do `hello` não ("O consumer do hello não muda")
- [x] 5.4 [Docker] Testes de integração vermelhos contra o Redpanda: "Header traceparent do registro é reaproveitado", "Registro sem header recebe um traceId novo", "A DLT preserva o header", "O traceId não vaza para o registro seguinte" e "Evento malformado com owner no payload" (um `owner` sentinela ausente de toda linha de log)
- [x] 5.5 Implementar, até 5.1 a 5.3 ficarem verdes: `logging.structured.format.console` (`${LOG_FORMAT:logstash}` no `application.yaml`, sem `logging.pattern.correlation`), `ApiExceptionHandler` lendo o `traceId` do `Tracer`, `isObservationEnabled` só no container de transações, e a remoção do `TraceIdFilter` e do seu teste (o comportamento passa a ser coberto por 5.2)
- [x] 5.6 Atualizar `ApiExceptionHandlerLoggingTest` e `BalanceControllerTest` para o log em JSON e o `traceId` do tracer; conferir "Consulta de saldo registrada" (nenhum log da consulta contém saldo, owner nem `accountId` por inteiro)

## 6. Métricas de circuito, HTTP e endpoint (TDD)

- [x] 6.1 Teste vermelho (`BalanceCircuitBreakerConfigTest`, `SimpleMeterRegistry`): os dois circuitos no mesmo registry; "Estado fechado", "Abertura é refletida" e "Chamadas contadas por resultado"; os nomes `balance-storage` e `balance-storage-read` e a definição única de falha não mudam
- [x] 6.2 Testes vermelhos (`@SpringBootTest` + MockMvc + `@AutoConfigureObservability`): "Consulta com sucesso", "Consulta com erro" (`400`, `404`, `503` e `500` em séries separadas), "Rota inexistente tem rótulo de baixa cardinalidade", "Consultas a contas distintas" e "Prometheus na porta de gerenciamento" (a "Latência de processamento por registro" só se prova com o broker, e vai para a 6.5)
- [x] 6.3 Teste vermelho (`@SpringBootTest(webEnvironment = RANDOM_PORT)` com `management.server.port=0`): "Porta da API não expõe o actuator", "Só health e prometheus" e "Porta de gerenciamento configurável"
- [x] 6.4 Implementar, até 6.1 a 6.3 ficarem verdes: o `CircuitBreakerRegistry` e o `TaggedCircuitBreakerMetrics` em `BalanceCircuitBreakerConfig`, e `management.*` no `application.yaml` (porta `${MANAGEMENT_PORT:8082}`, exposição só de `health` e `prometheus`, probes e `show-details=never`)
- [x] 6.5 [Docker] Testes de integração (`IngestionMetricsIntegrationTest`): "Lag disponível com o consumer ativo" (a métrica real é `records.lag.max` por `client.id`, registrada na spec e no design) e o timer do listener registrando uma amostra por registro, inclusive "Falha também gera uma amostra". Os sete testes passaram contra o Redpanda remoto em 2026-10-01, via `make integration-test COMPOSE=.local/compose.sh`.
- [x] 6.6 [Docker] Medir o lag com as partições pausadas (circuito aberto) e registrar o resultado no `design.md` (R4): feito, e a métrica do cliente subestimou (0 contra 30 reais).

## 7. Health e credenciais (TDD)

- [x] 7.1 Testes vermelhos (`HealthProbesTest`, MockMvc com a porta de gerenciamento igual à da API): "Processo vivo", "Aplicação pronta", "Liveness independe do DynamoDB", "Liveness independe do Kafka", "Readiness continua UP com o DynamoDB fora do ar" (a consulta responde `503` com `Retry-After`), "Readiness continua UP com o Kafka fora do ar", "Probes não incluem indicadores de dependência" e "Corpo mínimo"
- [x] 7.2 Testes vermelhos de encerramento: "Encerramento muda o readiness" (um `ApplicationListener` captura a mudança para `REFUSING_TRAFFIC` antes de o servidor parar) e "Liveness segue UP durante o encerramento"
- [x] 7.3 Teste vermelho (`DynamoDbConfigTest`): "Credenciais vêm do ambiente" (propriedades de sistema `aws.accessKeyId` e `aws.secretAccessKey` resolvidas pelo provider do cliente) e "Sem credenciais a leitura falha de forma explícita" (um provider que falha resulta em `PermanentStorageException`, e a mensagem não contém credencial); "Nenhuma credencial no código" (nenhum `AwsBasicCredentials` no código de produção)
- [x] 7.4 Implementar, até 7.1 a 7.3 ficarem verdes: o grupo `readiness` só com o estado de disponibilidade, e `DefaultCredentialsProvider` no `DynamoDbConfig`, removendo `AwsBasicCredentials.create("local","local")`

## 8. Contêiner, compose e infra (TDD estático, e validação em Docker)

- [x] 8.1 Testes vermelhos estáticos (`DockerfileTest`, lendo `Dockerfile` do disco): "Nenhuma tag flutuante no Dockerfile", "Estágios separados", "Usuário numérico não root", "Flags padrão" (`JAVA_TOOL_OPTIONS` com `-XX:MaxRAMPercentage` e `-XX:+ExitOnOutOfMemoryError`), "ENTRYPOINT em forma exec" e `STOPSIGNAL SIGTERM`
- [x] 8.2 Testes vermelhos estáticos (`ComposeFileTest`, lendo `docker-compose.yml` com o YAML do Boot): "Nenhuma tag flutuante no compose", "Limite de memória no compose", "Grace period maior que o encerramento", "app espera os seeds", "Healthcheck do app" e "Porta de gerenciamento e formato de log por ambiente"
- [x] 8.3 Testes vermelhos estáticos dos scripts (`IngestionTopicsScriptTest`): "Fonte única dos tópicos" (o seed e o `Makefile` chamam o mesmo script; os nomes e as 6 partições aparecem só nele) e "Seed cria os tópicos de ingestão" (o script cria os dois tópicos, com `describe` antes, idempotente)
- [x] 8.4 Implementar, até 8.1 a 8.3 ficarem verdes: o `Dockerfile` (tags `21.0.12_8-jdk-noble` e `21.0.12_8-jre-noble`, usuário `10001`, `JAVA_TOOL_OPTIONS`, `ENTRYPOINT` exec, `STOPSIGNAL`), o `.dockerignore` (`.challenge`, `openspec`, `docs`, `.claude`), o `infra/redpanda/ingestion-topics.sh` chamado pelo `redpanda-seed`, o alvo `kafka-topics-ingestion` do `Makefile` chamando o mesmo script, e o serviço `app` do compose (`depends_on` com `service_completed_successfully`, healthcheck por `bash` e `/dev/tcp` no `readiness`, `mem_limit: 768m`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `MANAGEMENT_PORT` e as portas `8080` e `8082`)
- [x] 8.5 [Docker] Validação em contêiner, executando de verdade: `docker build --target runtime`, `docker run ... id -u` (UID `10001`), `JAVA_TOOL_OPTIONS` sobrescrita por ambiente ("Flags sobrescritas por ambiente"), "Processo não é root no contêiner", e `docker compose config` sem erro
- [x] 8.6 [Docker] "SIGTERM encerra sem perder o registro em andamento": `docker stop` com um registro em processamento; conferir que ele é gravado e confirmado e que o processo sai (código 143) dentro do `stop_grace_period`; registrar o resultado. Validado em 2026-10-01: thread dump confirmou `DefaultDynamoDbClient.putItem` e `TransactionEventListener.consume` em andamento enquanto o DynamoDB estava pausado; após SIGTERM e retomada do banco, snapshot persistido e offset 1/1 confirmado, lag 0, saída 143 em 3,257 s (limite 40 s). Tópico e grupos isolados, timeouts de escrita ampliados só no contêiner de validação.
- [x] 8.7 [Docker] "Stack inteira com um comando": `make up` numa máquina limpa; o `app` fica saudável, `make kafka-produce-transactions-events TOPIC=transacoes-financeiras-processadas COUNT=50` e `GET /balances/{accountId}` com um `account.id` do tópico responde `200`; conferir `/actuator/prometheus` (os quatro resultados, circuito, `http.server.requests`) e o log JSON com `traceId`

## 9. Documentação operacional

- [x] 9.1 Documentar no `README.md`: o endpoint de gerenciamento e as probes, as métricas e seus nomes, o formato do log e o `traceId`, as variáveis de ambiente novas (`MANAGEMENT_PORT`, `LOG_FORMAT`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `JAVA_TOOL_OPTIONS`), a decisão de o `readiness` não depender do DynamoDB, `make up` como único comando, e as evoluções documentadas (lag via `AdminClient`, exportação de traces, alertas)

## 10. Verificação

- [x] 10.1 `./gradlew check` verde (gate de cobertura ≥ 90%) e `make integration-test` verde [Docker]. Verificado em 2026-10-01 após as regressões de privacidade: 309 testes no gate, cobertura de instruções 95,2%; 61 testes de integração contra Docker remoto, incluindo o novo timer de erro da tarefa 6.5.
- [x] 10.2 Cabeçalho de comentário (Art. 6) em todo arquivo novo ou alterado: uma entrada por trecho (linhas, símbolo e porquê), `Spec:` nos testes e `Enunciado:` na última linha, em pt-BR, sem comentário no corpo; atualizar os cabeçalhos dos arquivos alterados, e os scripts `.sh` com `#` logo após o shebang
- [x] 10.3 Confirmar que o `HexagonalArchitectureTest` continua verde e que nenhum tipo de Micrometer, Actuator, OpenTelemetry ou Resilience4j atravessa um port nem entra em `domain` ou `application`
- [x] 10.4 Validar a implementação contra o `.challenge/enunciado.md` (Art. 11): percorrer *O que será avaliado → Production readiness* (logging, métricas, conteinerização), *Resiliência* e *Tratamento de cenários adversos*, e *Pense além do happy path* (mensagem duas vezes, dependência fora do ar); executar o que for observável (`curl` nas probes e no Prometheus, a mensagem duplicada incrementando `duplicate`, o DynamoDB parado deixando o `readiness` `UP` e a consulta `503`) [Docker]; registrar a tabela item do enunciado → evidência para o relatório de revisão e parar para perguntar se algo divergir

## 11. Revisão e contexto

- [x] 11.1 Independent agent review: seguir o procedimento do `CLAUDE.md` (subagente com contexto limpo via Agent, nunca fork; somente leitura; entrega de proposal, design, specs, tasks, diff, `CLAUDE.md` e `.challenge/enunciado.md`; achados bloqueante/ajuste/sugestão com `arquivo:linha`; no máximo 3 rodadas) e registrar rodadas, correções e rejeições para o usuário. Concluída em três rodadas, aprovação técnica sem bloqueantes nem ajustes; nenhum achado rejeitado. Registro em `review.md`.
- [x] 11.2 Apresentar ao usuário os comentários novos e alterados (`arquivo:linha` e texto) e só commitar depois do aval. Apresentados em `comment-review.md`; comentários aprovados pelo usuário em 2026-10-01; commit autorizado com exclusão da infraestrutura Docker.
- [x] 11.3 Atualizar `openspec/project.md` e `context:` do `config.yaml` (Regra 3 do `CLAUDE.md`), na mesma tarefa: dependências e o motivo; variáveis de ambiente novas; a porta de gerenciamento e as probes; `DuplicateIgnored` entre as decisões; a política de dados sensíveis; o `traceId` único; as tags fixas; as lacunas resolvidas (o app não esperava os seeds, rodava como root, tags flutuantes); a regra do cliente DynamoDB e das credenciais; validar o YAML com `openspec list --json`
