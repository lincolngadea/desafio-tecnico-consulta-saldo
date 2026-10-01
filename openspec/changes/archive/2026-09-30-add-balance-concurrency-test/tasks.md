## 1. Dependência

- [x] 1.1 Conferir com `./gradlew dependencyInsight` que `kotlinx-coroutines-core` está no runtime dos testes e fora do classpath de compilação do `integrationTest`
- [x] 1.2 Declarar `org.jetbrains.kotlinx:kotlinx-coroutines-core` em `testImplementation` no `build.gradle.kts` e confirmar se o BOM do Spring Boot gerencia a versão; se não gerenciar, fixar 1.10.2

## 2. Teste de concorrência com coroutines (TDD)

- [x] 2.1 Teste vermelho: reescrever "Gravações concorrentes da mesma conta" em `DynamoDbBalanceIntegrationTest` com `runBlocking(Dispatchers.IO)`, N `async` em ordem embaralhada liberados por um `CompletableDeferred` e `awaitAll()`, verificando que nenhuma escrita falha e que a leitura devolve o snapshot de maior versão
- [x] 2.2 Provar que o teste detecta o defeito: trocar temporariamente a condição do `DynamoDbBalanceWriter` por uma gravação incondicional, confirmar que o teste fica vermelho e restaurar a condição
- [x] 2.3 Rodar o teste várias vezes seguidas contra o DynamoDB Local e confirmar que fica verde de forma estável com a condição restaurada

## 3. Verificação

- [x] 3.1 `./gradlew check` verde (gate de cobertura ≥ 90%) e `make integration-test` verde
- [x] 3.2 Atualizar o cabeçalho de comentário do `DynamoDbBalanceIntegrationTest` (Art. 6): linhas, símbolo e porquê das entradas alteradas, com o `Enunciado:` e o `Spec:` corretos

## 4. Revisão e contexto

- [x] 4.1 Independent agent review: seguir o procedimento do `CLAUDE.md` (subagente com contexto limpo via Agent, nunca fork; somente leitura; entrega de proposal, design, specs, tasks, diff, `CLAUDE.md` e `.challenge/enunciado.md`; achados bloqueante/ajuste/sugestão com `arquivo:linha`; no máximo 3 rodadas) e registrar rodadas, correções e rejeições para o usuário
- [x] 4.2 Apresentar ao usuário os comentários novos e alterados (`arquivo:linha` e texto) e só commitar depois do aval
- [x] 4.3 Atualizar `openspec/project.md` e `context:` do `config.yaml` com a dependência `kotlinx-coroutines-core` (só teste) e o motivo, conforme a Regra 3 do `CLAUDE.md`, e validar o YAML
