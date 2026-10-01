## 1. Dependência e configuração

- [x] 1.1 Confirmar com `./gradlew dependencyInsight` que o BOM do Boot não gerencia o Resilience4j, e declarar `io.github.resilience4j:resilience4j-circuitbreaker:2.4.0` em `implementation` no `build.gradle.kts`
- [x] 1.2 Teste vermelho (`IngestionPropertiesTest` e `CircuitBreakerPropertiesTest`): "Valores padrão", "Limite de tentativas sobrescrito" e "O tempo máximo bloqueado cabe no max.poll.interval", para os parâmetros do consumer e do circuito
- [x] 1.3 Criar as `@ConfigurationProperties` `ingestion.*` e `balance.circuit-breaker.*`, acrescentar as propriedades com `${ENV:default}` ao `application.yaml` e as variáveis ao serviço `app` do `docker-compose.yml`, até 1.2 ficar verde

## 2. Caso de uso (TDD)

- [x] 2.1 Teste vermelho (`ProcessTransactionServiceTest`, com fake do `BalanceRepository` que honra o contrato): "Transação aprovada grava o snapshot", "Transação recusada também grava o snapshot", "Resultado Applied é devolvido", "Resultado StaleIgnored é devolvido sem erro" e "Falha do armazenamento não é engolida" (os cenários "Transação recusada também grava o snapshot" e "Valor da transação não altera o saldo gravado" só se demonstram a partir do JSON e ficam no teste do listener com o caso de uso real, em 5.3)
- [x] 2.2 Criar `ProcessedTransaction` (domínio, reusando os value objects), `fun interface ProcessTransactionUseCase` com KDoc do contrato e `ProcessTransactionService` (`@Service`), até 2.1 ficar verde

## 3. Circuit breaker no repositório (TDD)

- [x] 3.1 Criar `StorageUnavailableException`, subtipo de `BalanceStorageException`, e conferir que todo `when` sobre a sealed continua exaustivo e sem `else`
- [x] 3.2 Teste vermelho (`CircuitBreakerBalanceRepositoryTest`, fake do repositório): "Taxa de falhas acima do limiar abre o circuito", "Falha permanente não conta para o circuito", "Resultados esperados contam como sucesso", "Chamada com circuito aberto não alcança o DynamoDB", "A exceção não expõe tipo da biblioteca", "Sondas bem-sucedidas fecham o circuito" e "Sonda que falha reabre o circuito"
- [x] 3.3 Implementar `CircuitBreakerBalanceRepository` (Decorator) em `balance/adapter/output/resilience`, com a `@Configuration` que o expõe como o `BalanceRepository` injetado, até 3.2 ficar verde
- [x] 3.4 Teste: "Valores padrão" e "Valores sobrescritos" do circuito sobre a configuração criada em 1.3

## 4. Leitura do evento (TDD)

- [x] 4.1 Teste vermelho (mapper e DTO, com `JsonMapper` real): "Evento válido chama o caso de uso", "Valor monetário é lido sem perda de precisão", "Status desconhecido não invalida o evento", e as falhas "JSON malformado", "Campo inválido" e "Campo obrigatório ausente" resultando em `MalformedTransactionEventException` com a causa
- [x] 4.2 Criar o DTO em `adapter/input/kafka/dto`, o mapper para `ProcessedTransaction` e `MalformedTransactionEventException`, até 4.1 ficar verde

## 5. Consumer Kafka: commit, DLT, retry e pausa (TDD)

- [x] 5.1 Criar os tópicos de teste com `make kafka-topic-create NAME=transacoes-financeiras-processadas PARTITIONS=6` e `NAME=transacoes-financeiras-processadas.DLT PARTITIONS=6`; teste de integração "Criação pelo comando do kit" confere 6 partições nos dois (AdminClient)
- [x] 5.2 Spike com teste de integração vermelho (R1): validar o mecanismo "pausar o container, não confirmar o offset e reentregar o registro" com um error handler que decide por `StorageUnavailableException` e por retry esgotado; só seguir com 5.3 em diante com o mecanismo comprovado, e registrar no `design.md` qualquer ajuste
- [x] 5.3 Teste vermelho unitário do listener (fake do caso de uso, `Acknowledgment` espionado): "Offset confirmado depois da gravação", "Evento duplicado ou fora de ordem também é confirmado" e "Falha transitória não confirma o offset"
- [x] 5.4 Teste vermelho unitário da configuração de erro: decisão do recoverer (DLT vs pausa, `IngestionRecovererTest`; a classificação de quais exceções são tentadas de novo é provada pelos testes de integração), "Os intervalos crescem exponencialmente", "O jitter varia os intervalos" e "O limite de novas tentativas é respeitado"
- [x] 5.5 Testes de integração vermelhos contra o Redpanda: "JSON malformado vai para a DLT", "Campo inválido vai para a DLT", "Campo obrigatório ausente vai para a DLT", "Falha permanente do armazenamento vai para a DLT", "A DLT preserva payload e origem", "Evento inválido não trava o consumo", "Erro transitório não vai para a DLT", "Sucesso numa nova tentativa" e "Queda entre a gravação e o commit não perde nem corrompe"
- [x] 5.6 Testes de integração vermelhos de pausa: "Circuito aberto pausa o consumo", "Retries esgotados pausam o consumo", "Retomada após a pausa reentrega o registro", "Sonda bem-sucedida normaliza o consumo", "Sonda que falha pausa de novo", "Nenhum evento é perdido numa indisponibilidade" e "A pausa sobrevive a um rebalance"
- [x] 5.7 Testes de integração vermelhos de ciclo de vida: "Registro em andamento termina antes do encerramento", "Instância sai do grupo", "Retomada agendada é cancelada no encerramento" e "Partições revogadas durante o consumo"
- [x] 5.8 Implementar o listener (`@KafkaListener` com `Acknowledgment`), o `ConcurrentKafkaListenerContainerFactory` dedicado (`MANUAL_IMMEDIATE`, grupo próprio, concorrência), o `DefaultErrorHandler` com `DeadLetterPublishingRecoverer`, `ExponentialBackOff` com `maxAttempts` e jitter e `setCommitRecovered(true)`, e a pausa e retomada agendada, até 5.3 a 5.7 ficarem verdes
- [x] 5.9 Conferir que `GreetingTemplateConsumerIntegrationTest` e o consumer `hello` continuam verdes e sem mudança de comportamento

## 6. Documentação operacional

- [x] 6.1 Documentar no `README.md` os comandos de criação dos dois tópicos, as variáveis de ambiente novas e o comportamento da DLT e da pausa

## 7. Verificação

- [x] 7.1 `./gradlew check` verde (gate de cobertura ≥ 90%) e `make integration-test` verde
- [x] 7.2 Cabeçalho de comentário (Art. 6) em todo arquivo novo ou alterado: uma entrada por trecho (linhas, símbolo e porquê), `Spec:` nos testes e `Enunciado:` na última linha, em pt-BR, sem comentário no corpo
- [x] 7.3 Confirmar que `HexagonalArchitectureTest` cobre os pacotes novos (`adapter/output/resilience` incluído) e que nenhum tipo de Kafka, Jackson ou Resilience4j atravessa um port
- [x] 7.4 Validar a implementação contra o `.challenge/enunciado.md` (Art. 11): percorrer *Ingestão (input via Kafka)*, *Criando o tópico Kafka* e *O que será avaliado* (resiliência, concorrência, cenários adversos, production readiness), executar `make kafka-produce-transactions-events TOPIC=transacoes-financeiras-processadas COUNT=50` com a aplicação rodando e conferir o consumo, o snapshot no DynamoDB e a DLT; registrar a tabela item do enunciado → evidência para o relatório de revisão e parar para perguntar se algo divergir

## 8. Revisão e contexto

- [x] 8.1 Independent agent review: seguir o procedimento do `CLAUDE.md` (subagente com contexto limpo via Agent, nunca fork; somente leitura; entrega de proposal, design, specs, tasks, diff, `CLAUDE.md` e `.challenge/enunciado.md`; achados bloqueante/ajuste/sugestão com `arquivo:linha`; no máximo 3 rodadas) e registrar rodadas, correções e rejeições para o usuário
- [x] 8.2 Apresentar ao usuário os comentários novos e alterados (`arquivo:linha` e texto) e só commitar depois do aval
- [x] 8.3 Atualizar `openspec/project.md` e `context:` do `config.yaml` (Regra 3 do `CLAUDE.md`): dependência Resilience4j e o motivo, variáveis de ambiente, tópicos e partições, decisão sobre `DECLINED`, e a lacuna do consumer resolvida; validar o YAML
