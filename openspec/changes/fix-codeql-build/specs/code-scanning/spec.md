## Purpose

Garantir que a análise estática de segurança do CodeQL rode a cada push/PR e chegue ao fim: ela compila todo o código Kotlin do repositório de um jeito compatível com o extrator Kotlin e falha em tempo limitado em vez de ocupar o runner até o timeout da plataforma.

## ADDED Requirements

### Requirement: Análise compila o código com build explícito
O workflow do CodeQL MUST analisar `java-kotlin` com um build declarado no próprio workflow, sem depender da detecção automática de build. O build MUST compilar todas as árvores de fonte Kotlin versionadas: produção, testes unitários e testes de integração.

#### Scenario: Workflow não usa o autobuild
- **WHEN** o workflow do CodeQL é lido
- **THEN** a inicialização declara build manual para `java-kotlin` e nenhum passo usa a ação de autobuild

#### Scenario: Build cobre todas as árvores de fonte
- **WHEN** o passo de build do workflow do CodeQL é lido
- **THEN** o comando compila as classes de produção, de teste unitário e de teste de integração

### Requirement: Compilação compatível com o extrator Kotlin
O build da análise MUST compilar Kotlin no próprio processo do Gradle e com um único worker, porque o extrator Kotlin do CodeQL é single-threaded e trava a compilação feita fora de processo ou em paralelo. Essas configurações MUST valer só para a análise, sem alterar o build local, o `make test` nem os demais workflows.

#### Scenario: Compilação em processo com um único worker
- **WHEN** o passo de build do workflow do CodeQL é lido
- **THEN** o comando define a estratégia de compilação Kotlin em processo e limita o Gradle a um worker

#### Scenario: Build local não herda as configurações da análise
- **WHEN** o repositório é inspecionado fora do workflow do CodeQL
- **THEN** nenhuma configuração versionada do Gradle força a compilação Kotlin em processo ou limita os workers

### Requirement: Duração limitada da análise
O job do CodeQL MUST declarar um tempo máximo de execução muito menor que o limite padrão da plataforma, para que um novo travamento falhe de forma visível em vez de ocupar o runner por horas.

#### Scenario: Job declara timeout
- **WHEN** o workflow do CodeQL é lido
- **THEN** o job de análise declara `timeout-minutes` de no máximo 30

### Requirement: Análise conclui no GitHub
Uma execução do CodeQL disparada por push na branch `kotlin` MUST concluir com sucesso e publicar os resultados da categoria `java-kotlin`.

#### Scenario: Execução real conclui
- **WHEN** a change é enviada para a branch `kotlin` e o workflow do CodeQL roda no GitHub Actions
- **THEN** o job termina com sucesso dentro do timeout declarado e a análise da categoria `/language:java-kotlin` é publicada
