## Context

O `codeql.yml` atual tem quatro passos: `checkout`, `setup-java` (Temurin 21), `codeql-action/init` com `languages: java-kotlin`, `codeql-action/autobuild` e `codeql-action/analyze`. Não há `gradle.properties` versionado, e o build Gradle usa os padrões: compilação Kotlin no *Kotlin daemon* e workers em paralelo. Os outros workflows (`build.yml`, `test.yml`) compilam e testam o mesmo commit no Ubuntu sem problema, então o travamento só acontece com o rastreamento do CodeQL ligado (ver proposal.md, *Why*).

Fatos externos que sustentam o design:

- O CodeQL suporta Kotlin de 1.8.0 a 2.4.20 ([supported languages](https://codeql.github.com/docs/codeql-overview/supported-languages-and-frameworks/)). O 2.3.21 do projeto está dentro da faixa.
- `build-mode: none` não analisa Kotlin. Kotlin exige `autobuild` ou `manual` ([compiled languages](https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/codeql-code-scanning-for-compiled-languages)).
- O extrator Kotlin é single-threaded. Com Java 21, a compilação Kotlin sob o CodeQL trava até o timeout de 6 h, e o problema foi resolvido com `kotlin.compiler.execution.strategy=in-process` e `org.gradle.workers.max=1` ([community discussion #151626](https://github.com/orgs/community/discussions/151626)).

## Goals / Non-Goals

**Goals:**
- O CodeQL conclui a análise `java-kotlin` em minutos, a cada push/PR.
- Um travamento futuro falha dentro de um limite curto e visível.
- A configuração do workflow fica protegida por teste, como já acontece com Dockerfile e compose.

**Non-Goals:**
- Mudar queries, a categoria da análise ou os gatilhos do CodeQL.
- Otimizar o tempo dos demais workflows ou adicionar cache ao CodeQL.
- Mudar o build local, o `make test` ou o `build.yml`/`test.yml`.

## Decisions

### D1. `build-mode: manual` em vez de `autobuild`

O passo `autobuild` sai do workflow. O `init` passa a declarar `build-mode: manual`, e um passo `run` executa o build.

- **Por quê:** o autobuild não permite passar parâmetros ao Gradle e decide sozinho quais tasks rodar. Com o build manual, o comando que o CodeQL rastreia fica explícito, versionado e testável.
- **Alternativa descartada:** manter o autobuild e colocar as propriedades num `gradle.properties` versionado, que o autobuild leria. Isso mudaria também o build local e o `make test`, deixando a compilação de todo desenvolvedor single-threaded por causa de uma limitação que só existe no CodeQL (viola o Goal de não afetar o build local).
- **Alternativa descartada:** `build-mode: none`. Ele não analisa Kotlin, e todo o código do projeto é Kotlin.

### D2. Configurações do extrator passadas na linha de comando do build

O passo de build usa `-Pkotlin.compiler.execution.strategy=in-process` e `--max-workers=1`.

- `kotlin.compiler.execution.strategy` é uma propriedade Gradle do Kotlin Gradle Plugin. Passada por `-P`, vale só para essa invocação. Com `in-process`, o compilador Kotlin roda dentro do processo do Gradle, onde o rastreamento do CodeQL o alcança, e não num *Kotlin daemon* separado.
- `--max-workers=1` é o equivalente de linha de comando de `org.gradle.workers.max=1` e serializa as compilações, como o extrator single-threaded exige.
- `"-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g"` dimensiona o heap do processo do Gradle. Com `in-process`, o compilador Kotlin e o extrator do CodeQL rodam nesse processo, e o padrão de 512 MiB de heap e 384 MiB de metaspace esgotou na execução real (D5). O `org.gradle.jvmargs` passado por `-D` na linha de comando vale só para essa invocação, e com `--no-daemon` o Gradle cria o daemon de uso único com esses argumentos. O runner `ubuntu-latest` tem 16 GB de RAM em repositório público, que é o caso exigido pelo enunciado, e 7 GB em privado. Nos dois casos, 4 GiB de heap cabem.
- **Alternativa descartada:** `GRADLE_OPTS`/`JAVA_TOOL_OPTIONS` no `env` do job. Isso passaria propriedades de sistema da JVM, e não propriedades Gradle, e ainda as aplicaria a todo processo Java do job, inclusive aos do próprio CodeQL. Pelo mesmo motivo, o heap vai em `org.gradle.jvmargs` do comando, e não no `env`.
- **Alternativa descartada:** `org.gradle.jvmargs` num `gradle.properties` versionado. Isso aumentaria também o heap do build local e do `make test`, que não precisam dele.

### D3. Comando de build: `./gradlew testClasses integrationTestClasses --no-daemon`

- `testClasses` depende de `classes`, então o comando compila as três árvores de fonte (`main`, `test`, `integrationTest`), que é o que o CodeQL analisa no build manual. Não roda testes nem empacota o jar, ao contrário de `check`/`assemble`, que não acrescentam código para analisar.
- `--no-daemon` segue o padrão dos outros workflows e evita um daemon que sobreviva ao passo. Com ele, o Gradle cria um daemon de uso único como processo filho do comando, e o rastreamento do CodeQL acompanha processos filhos. Com `in-process` (D2), a compilação Kotlin acontece dentro desse processo.
- **Sem `gradle/actions/setup-gradle`:** os outros workflows usam essa ação por causa do cache. Aqui o cache é um risco: uma task Kotlin restaurada do cache (`FROM-CACHE`) não compila, e o CodeQL não extrai o que não foi compilado. Essa é uma divergência explícita do padrão dos outros workflows. O build cache do Gradle não está ligado no projeto, e o checkout é limpo, então toda task compila.

### D4. `timeout-minutes: 30` no job

Na versão Java do kit, o Autobuild do CodeQL levava cerca de 1 minuto, e o build manual completo leva cerca de 40 s localmente. Mesmo com o extrator single-threaded, 30 minutos sobram com folga e cortam um travamento 12 vezes antes do limite padrão de 6 h.

### D5. Causa confirmada pelo log da run presa

A API de download de logs responde `403 Must have admin rights to Repository`, e o usuário não tem admin no repositório. Por isso o usuário chegou a decidir seguir sem o log. Logo depois, ele cancelou a run e forneceu o link de log bruto do job, e a causa foi confirmada antes de alterar o workflow.

Evidência do log da run 37041862434 (CodeQL 2.27.1):

```
17:37:52 [autobuild] > ./gradlew --no-daemon ... testClasses
17:38:30 [autobuild] > Task :compileKotlin
20:10:39 ##[error]The operation was canceled.
```

- O autobuild rodou `clean` (concluído em 57 s) e depois `testClasses`. Ele travou em `:compileKotlin` por 2 h 32 min, até o cancelamento.
- Não há `OutOfMemoryError`, erro de toolchain nem erro do extrator. Isso confirma a compilação Kotlin presa sob o extrator, como no relato público.
- O autobuild compila só `testClasses`. O D3 inclui também `integrationTestClasses`, para que a análise cubra todo o código versionado.

A execução real depois do push (task 4.6) continua sendo a validação final. Se ela falhar ou atingir o timeout de 30 min (D4), o log da nova execução é analisado e o plano é revisto com o usuário antes do archive.

**Primeira execução real, commit `045170c`:** o travamento acabou, e o job terminou em cerca de 3 min, mas o build falhou por memória em `:compileKotlin`:

```
20:49:01 > Task :compileKotlin
The currently configured max heap space is '512 MiB' and the configured max metaspace is '384 MiB'.
Gradle build daemon has been stopped: since the JVM garbage collector is thrashing
```

Com `in-process`, o compilador e o extrator rodam no processo do Gradle, que tem o heap padrão. O travamento original provavelmente era o mesmo esgotamento dentro do Kotlin daemon, que não tem esse encerramento e por isso ficava preso, mas isso é uma inferência que o log não confirma. O plano foi revisto com o usuário, que aprovou aumentar o heap só no comando da análise (D2).

### D6. Teste estático `CodeQlWorkflowTest`

- Lê `.github/workflows/codeql.yml` com o SnakeYAML do Boot (`org.yaml.snakeyaml.Yaml`), como o `ComposeFileTest`, e verifica cada cenário de arquivo da spec `code-scanning`: build manual sem autobuild, comando que compila as três árvores, compilação em processo com um worker, heap do Gradle dimensionado para o extrator e `timeout-minutes` ≤ 30.
- O cenário *Build local não herda as configurações da análise* é verificado no mesmo teste: o repositório não tem `gradle.properties` com essas chaves.
- O cenário *Execução real conclui* não tem teste automatizado. Ele é validado pela execução no GitHub (task de validação) e registrado no relatório de revisão.
- **Pacote:** `br.com.itau.challenge.codescanning`, com o nome da capability. Os testes estáticos de infraestrutura existentes ficam em `observability` porque vieram da change `add-observability`. O CodeQL não é observabilidade, então colocá-lo ali usaria um nome para dois conceitos (Art. 8). Essa é a divergência explícita do padrão.

### D7. `.github/` nos inputs do Gradle e no estágio Docker `test`

O teste novo lê `.github/workflows/codeql.yml`, então pelo requisito de `repository-verification`:

- a task `test` declara `fileTree(".github")` entre os inputs `repositoryInventories` do `build.gradle.kts`;
- o estágio `test` do `Dockerfile` ganha `COPY .github .github`. O `.dockerignore` já não exclui `.github`, e o estágio `runtime` continua copiando só o jar.

O teste também lê o `gradle.properties` (cenário *Build local não herda as configurações da análise*), que hoje não existe:

- a task `test` declara `gradle.properties` entre os inputs `repositoryDocuments`. O Gradle aceita um input ausente e trata a criação do arquivo como mudança, então criar o arquivo invalida o teste;
- o estágio `base` do `Dockerfile` copia `gradle.propertie[s]` junto com os scripts do build. O glob copia o arquivo só se ele existir, e o Docker não falha quando ele está ausente, porque a instrução tem outras fontes. Ele fica no `base`, e não só no `test`, porque o arquivo configura o próprio build. Assim o build e o teste no contêiner veem o mesmo arquivo que o host, e o `make test` não passa com um `gradle.properties` que o `./gradlew check` local rejeitaria.

### D8. Comentários (Art. 6)

- `codeql.yml` ganha cabeçalho em linhas `#`, como o `docker-compose.yml`, com uma entrada por trecho alterado (`init`, passo de build, `timeout-minutes`).
- O CodeQL não corresponde a nenhum item do enunciado. A linha final é `Enunciado: n/a (fix-codeql-build design D1)`, e o teste usa `Spec: <requisitos de code-scanning>` com o mesmo `Enunciado: n/a`.
- `build.gradle.kts` e `Dockerfile` atualizam as entradas e as linhas de cabeçalho que mudarem.

### D9. Segundo commit da mesma change (decisão do usuário)

O commit `045170c` desta change já foi publicado na `kotlin` quando a execução real revelou a falta de heap. A regra é um commit por change, e havia duas saídas:

- um segundo commit com a correção do heap;
- reescrever o `045170c` com amend e force push.

O usuário escolheu o **segundo commit**, para não reescrever o histórico de uma branch publicada. É uma exceção explícita à regra, válida só para esta change.

## Conformidade, padrões e coerência

- **Art. 1 (Clean Architecture):** nenhuma camada de produção é tocada. Não há port, value object ou adapter envolvido.
- **Arts. 3 e 5:** o teste segue o padrão `should ... when ...`, com AAA e um comportamento por teste, como `DockerfileTest`/`ComposeFileTest`. Valores como `30` e os nomes das propriedades ficam em constantes nomeadas.
- **Art. 4 (KISS/YAGNI):** a mudança troca só o necessário no workflow. Não adiciona cache, matriz nem queries.
- **Art. 9 (patterns):** nenhum design pattern cabe. É configuração de CI, e não há variação de comportamento a encapsular.
- **Art. 10:** a solução usa os recursos do próprio CodeQL (`build-mode: manual`) e do Kotlin Gradle Plugin (`kotlin.compiler.execution.strategy`). O teste usa o SnakeYAML que já está no classpath. Não há dependência nova.
- **Art. 11:** o enunciado não trata de CI nem de análise estática. A change não toca contrato, comando de *Como começar* nem critério de *O que será avaliado*. A validação executável é a execução real do CodeQL no GitHub (spec `code-scanning`, *Análise conclui no GitHub*).
- **Coerência:** segue o padrão dos workflows existentes (`actions/checkout@v7`, `setup-java@v5` Temurin 21, `--no-daemon`) e o dos testes estáticos de infraestrutura. As divergências são duas e estão justificadas: sem `setup-gradle` (D3) e um pacote novo de teste (D6).

## Risks / Trade-offs

- [As configurações não bastam e a compilação continua presa] → D5: o timeout de 30 min limita o custo, e o log da nova execução orienta a revisão do plano com o usuário antes do archive.
- [Compilação serial deixa a análise mais lenta] → o código é pequeno. O custo esperado é de poucos minutos, contra uma análise que hoje não termina.
- [O timeout de 30 min é curto demais se o projeto crescer] → a falha fica visível e o valor é ajustado numa change futura, sem impacto em outros workflows.
- [O workflow só é validado de verdade no GitHub] → o teste estático protege a configuração no `check`. A validação final exige push, que depende de autorização do usuário.

## Migration Plan

1. Cancelar a run 37041862434 presa pela interface do GitHub (ação do usuário, que tem as credenciais).
2. Fazer o commit e o push da change na branch `kotlin`, com autorização do usuário.
3. Acompanhar a nova execução do CodeQL até a conclusão e registrar a evidência.
4. **Rollback:** reverter o commit volta ao autobuild, sem efeito em outros workflows ou no build local.
