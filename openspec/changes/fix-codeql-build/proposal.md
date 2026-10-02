## Why

A primeira execução do CodeQL no projeto Kotlin/Gradle (run 37041862434, commit `39426fc`) está presa no passo *Autobuild* há horas. A execução anterior que passou era a versão Java do kit. No mesmo commit, *Build* (`assemble testClasses`) e *Test & Coverage* (`check`) terminaram verdes no Ubuntu, então o problema só aparece quando a compilação roda sob o rastreamento do CodeQL. Do jeito que está, toda push/PR em `kotlin` deixa o CodeQL ocupando o runner até o timeout padrão de 6 h, e a análise de segurança nunca chega a ser publicada.

O sintoma bate com o problema conhecido do extrator Kotlin do CodeQL: ele é single-threaded e trava a compilação Kotlin feita fora de processo ou em paralelo ([community discussion #151626](https://github.com/orgs/community/discussions/151626)). O Kotlin 2.3.21 está dentro da faixa suportada pelo CodeQL (1.8.0 a 2.4.20), então a versão foi descartada como causa.

## What Changes

- Trocar o `autobuild` do `codeql.yml` por `build-mode: manual`, com um passo de build explícito que compila todas as árvores de fonte Kotlin com o compilador em processo e um único worker Gradle.
- Limitar a duração do job do CodeQL com `timeout-minutes`, para que um novo travamento falhe em minutos e não ocupe o runner por 6 h.
- Cobrir a configuração do workflow com um teste estático, no padrão de `DockerfileTest`/`ComposeFileTest`, que roda no `./gradlew check`.
- Declarar `.github/` como input dos testes estáticos no Gradle e copiá-lo para o estágio Docker `test`, já que passa a ser lido por um teste.
- Validar com uma execução real do CodeQL concluída no GitHub. O log da execução presa já confirmou a causa: travamento em `:compileKotlin` por 2 h 32 min (design D5).

As configurações de compilação valem só no workflow do CodeQL. O build local, o `make test` e os demais workflows continuam como estão.

## Capabilities

### New Capabilities

- `code-scanning`: análise estática de segurança no CI, com build explícito compatível com o extrator Kotlin do CodeQL e duração limitada.

### Modified Capabilities

- `repository-verification`: os inputs declarados no Gradle e os arquivos copiados para o estágio Docker `test` passam a incluir os workflows em `.github/`.

## Impact

- **CI:** `.github/workflows/codeql.yml`. `build.yml`, `test.yml` e `docker.yml` não mudam.
- **Testes:** novo teste estático do workflow do CodeQL, com o SnakeYAML que já está no classpath de teste.
- **Build:** `build.gradle.kts` (inputs da task `test`) e estágio `test` do `Dockerfile`.
- **Sem mudança** em código de produção, contratos HTTP/Kafka, esquema DynamoDB, dependências ou no build local.
- **Validação externa:** exige push para o GitHub e acompanhamento de uma execução do CodeQL. O push depende de autorização do usuário.
