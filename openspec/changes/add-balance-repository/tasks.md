## 1. Estrutura, arquitetura e dependências

- [x] 1.1 Estender o `HexagonalArchitectureTest` (teste primeiro) para cobrir `br.com.itau.challenge.balance..` com as mesmas regras de camada e de domínio sem Spring, e criar os pacotes do contexto `balance`
- [x] 1.2 Declarar `software.amazon.awssdk:apache5-client` (BOM 2.46.7) no `build.gradle.kts` e confirmar com `./gradlew dependencies` que é o HTTP client síncrono que o `dynamodb` já traz em runtime (o `apache-client` não é, e foi descartado)

## 2. Domínio: value objects e ordem das versões (TDD)

- [x] 2.1 Teste vermelho: "Moeda fora da ISO 4217 é rejeitada", "Casas decimais acima das da moeda são rejeitadas", "Valor monetário é normalizado para as casas da moeda" e "Saldo negativo é aceito" (`MoneyTest`)
- [x] 2.2 Implementar `Money` (`BigDecimal` + `java.util.Currency`) e a exceção de domínio correspondente até os testes de 2.1 ficarem verdes
- [x] 2.3 Teste vermelho: "Timestamp do evento não positivo é rejeitado" (`EventTimestampTest`)
- [x] 2.4 Implementar `EventTimestamp` (µs) e a exceção de domínio; implementar `AccountId`, `OwnerId` e `TransactionId` (`UUID`)
- [x] 2.5 Teste vermelho: "Timestamp maior é mais recente", "Empate de timestamp desempatado pelo id da transação" (incluindo um par em que `UUID.compareTo` e a ordem textual discordam) e "Mesmo evento não é mais recente que si mesmo" (`SnapshotVersionTest`)
- [x] 2.6 Implementar `SnapshotVersion : Comparable` (timestamp, depois forma canônica textual do id), `BalanceSnapshot` e `SnapshotSaveResult` (sealed: `Applied`, `StaleIgnored`)

## 3. Ports de saída e contrato de falhas

- [x] 3.1 Criar `fun interface BalanceRepository` (`saveIfNewer`) e `fun interface BalanceProvider` (`findByAccountId`, retorno anulável), com KDoc do contrato: ordem, resultado tipado e exceções
- [x] 3.2 Criar a sealed `BalanceStorageException` com `TransientStorageException` e `PermanentStorageException` em `balance/port/output`, preservando a causa

## 4. Cliente DynamoDB: timeouts e tentativas explícitos (TDD)

- [x] 4.1 Teste vermelho: "Cliente configurado com os valores declarados" (`DynamoDbConfigTest`, verificando `serviceClientConfiguration().overrideConfiguration()`)
- [x] 4.2 Criar a `@ConfigurationProperties` `dynamodb.*` (endpoint, região, timeouts e máximo de tentativas) e aplicá-la em `DynamoDbConfig` com `ApacheHttpClient` (connection/socket) e `ClientOverrideConfiguration` (api-call-attempt/api-call, estratégia `standard` com max attempts)
- [x] 4.3 Adicionar as propriedades com `${ENV:default}` ao `application.yaml` e as variáveis de ambiente ao serviço `app` do `docker-compose.yml`

## 5. Adapter DynamoDB: gravação condicional (TDD)

- [x] 5.1 Teste unitário vermelho (`DynamoDbBalanceWriterTest`, mock + `ArgumentCaptor`): o `PutItem` leva a tabela configurada, os atributos do item (D1) e a `ConditionExpression` com os valores da versão; `ConditionalCheckFailedException` resulta em `StaleIgnored` e sucesso resulta em `Applied`
- [x] 5.2 Implementar `DynamoDbBalanceWriter.saveIfNewer` até 5.1 ficar verde
- [x] 5.3 Criar a tabela `AccountBalances` (PK `accountId` S, `PAY_PER_REQUEST`) em `infra/dynamodb/seed.sh`, idempotente, com `BALANCE_TABLE_NAME` no `dynamodb-seed` e no `app` do compose
- [x] 5.4 Teste de integração vermelho (`DynamoDbBalanceIntegrationTest`, cliente criado pelo factory de produção, ids `UUID` e limpeza em `@AfterEach`): "Primeiro snapshot da conta", "Evento mais novo substitui o snapshot", "Evento mais antigo chega depois (fora de ordem)", "Mensagem duplicada", "Empate de timestamp com id de transação maior", "Empate de timestamp com id de transação menor" e "Um item por conta"
- [x] 5.5 Teste de integração: "Armazenamento concorda com a ordem do domínio" (matriz de pares comparando o resultado com `SnapshotVersion.compareTo`)
- [x] 5.6 Teste de integração: "Gravações concorrentes da mesma conta" (versões embaralhadas gravadas em paralelo; a leitura final é a maior versão)
- [x] 5.7 Ajustar a implementação até 5.4–5.6 ficarem verdes com `make integration-test`

## 6. Adapter DynamoDB: leitura por chave (TDD)

- [x] 6.1 Teste unitário vermelho: "Leitura fortemente consistente" (`GetItem` com a chave `accountId` e `ConsistentRead=true`), "Conta inexistente" (item ausente devolve nulo) e "Item armazenado malformado" (atributo ausente ou moeda inválida resulta em `PermanentStorageException`)
- [x] 6.2 Implementar `DynamoDbBalanceProvider.findByAccountId` até 6.1 ficar verde
- [x] 6.3 Teste de integração: "Conta com snapshot" (todos os campos idênticos, incluindo a escala do saldo após a normalização de números do DynamoDB) e "Conta inexistente"

## 7. Classificação de falhas (TDD)

- [x] 7.1 Teste unitário vermelho, para gravação e leitura: "Throttling é transitório", "Erro 5xx é transitório", "Timeout é transitório", "Falha de rede é transitória", "Erro 4xx de requisição ou configuração é permanente" e "Causa original preservada"
- [x] 7.2 Implementar a tradução de exceções do SDK no adapter (D5) até 7.1 ficar verde
- [x] 7.3 Teste vermelho: "DynamoDB que não responde" (cliente real do factory, com timeouts curtos, contra um `ServerSocket` local que aceita e não responde; falha transitória dentro do timeout da chamada total)
- [x] 7.4 Ajustar até 7.3 ficar verde

## 8. Verificação

- [x] 8.1 `./gradlew check` verde (gate de cobertura ≥ 90%) e `make integration-test` verde
- [x] 8.2 Conferir que nenhum tipo do SDK aparece em `balance/domain` ou `balance/port`, e que não há `!!`, `TODO` nem números mágicos no código novo

## 9. Revisão e contexto

- [x] 9.1 Independent agent review: seguir o procedimento do `CLAUDE.md` (subagente com contexto limpo via Agent, nunca fork; somente leitura; entrega de proposal, design, specs, tasks, diff, `CLAUDE.md` e `.challenge/enunciado.md`; achados bloqueante/ajuste/sugestão com `arquivo:linha`; no máximo 3 rodadas) e registrar rodadas, correções e rejeições para o usuário
- [x] 9.2 Atualizar `openspec/project.md` e `context:` do `config.yaml` com o que passa na Regra 3 do `CLAUDE.md`: tabela `AccountBalances` e `BALANCE_TABLE_NAME`, variáveis de timeout e tentativas do DynamoDB, contexto `balance` coberto pelo teste de arquitetura, ambiguidades resolvidas (`updated_at` e desempate) e lacunas do template resolvidas (seed com tabela única); validar com `openspec list --json`

## 10. Refinamento pelos Arts. 8 a 10 da constituição

- [x] 10.1 Auditar a change contra carga cognitiva e coerência (Art. 8), design patterns (Art. 9) e não reinventar a roda (Art. 10)
- [x] 10.2 Separar o adapter em `DynamoDbBalanceWriter` (`BalanceRepository`) e `DynamoDbBalanceProvider` (`BalanceProvider`), como o kit faz com `...Writer` e `...Provider`: testes unitários divididos primeiro (fixtures compartilhadas em `BalanceStorageFixtures`), depois o código; teste de integração renomeado para `DynamoDbBalanceIntegrationTest`, como o do kit
- [x] 10.3 Registrar no `design.md` (D9) os patterns aplicados, as bibliotecas avaliadas e a coerência com o kit, e no contexto do projeto a dependência `apache5-client`
- [x] 10.4 Nova rodada da revisão independente e reexecução de `./gradlew check` e `make integration-test`

