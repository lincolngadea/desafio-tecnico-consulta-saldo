# Cabeçalhos para revisão humana

Os três arquivos abaixo são novos nesta change. Cabeçalhos aprovados pelo usuário em 2026-10-02: "pode confirmar as validacões e realizar o commit das mudanças" (CLAUDE.md, Art. 6).

## src/test/kotlin/br/com/itau/challenge/documentation/MarkdownText.kt:1

```kotlin
/*
 * L28 MarkdownSection: tipo próprio para a seção, para o teste não indexar listas de strings por posição.
 * L30 MarkdownTable: tipo próprio para a tabela, com cabeçalho e linhas separados, pelo mesmo motivo.
 * L32-L44 sectionsAtLevel: ignora linhas dentro de bloco de código, porque um `## ` de exemplo
 *     não é título de seção.
 * L54-L55 makeTargets: só olha trechos de código: na prosa "make" é uma palavra comum
 *     ("o make já vem no Linux").
 * L61-L67 tables: uma tabela só começa onde a linha seguinte é o separador `|-|-|`,
 *     para não confundir com um `|` solto.
 *
 * Spec: Seções obrigatórias do README; Visão da solução com fluxo e arquitetura hexagonal; Como rodar leva do zero à
 *     primeira consulta; Decisões registradas como ADR curto; Estratégia de testes com pirâmide e cobertura;
 *     Resiliência e observabilidade com as métricas a olhar; O que eu faria com mais tempo, com motivador; Como o
 *     repositório foi construído; Referências do README existem no repositório
 * Enunciado: Como começar → Consulte o README do starter-kit
 */
```

## src/test/kotlin/br/com/itau/challenge/documentation/ReadmeTest.kt:1

```kotlin
/*
 * L90 ReadmeTest: protege o README contra desatualização conferindo só o que é mecânico (seções, ADRs,
 *     diagramas, alvos `make`, variáveis, classes e changes citadas); a prosa fica para a revisão independente.
 * L266-L275 should map each enunciado scenario: casa a linha pela primeira coluna da tabela de cenários, porque a
 *     tabela de níveis cita as mesmas palavras na descrição e daria falso positivo.
 * L82 CHANGE_NAME: reconhece nomes de change sem restringir o verbo, para o inventário acompanhar novos
 *     arquivos sem cadastro manual de prefixos (add-architecture-docs design D9).
 * L83 DESIGN_LINK: aceita decisões de changes com qualquer verbo, mantendo a validação do destino e de Dn.
 *
 * Spec: Seções obrigatórias do README; Visão da solução com fluxo e arquitetura hexagonal; Como rodar leva do zero à
 *     primeira consulta; Decisões registradas como ADR curto; Estratégia de testes com pirâmide e cobertura;
 *     Resiliência e observabilidade com as métricas a olhar; O que eu faria com mais tempo, com motivador; Como o
 *     repositório foi construído; Referências do README existem no repositório
 * Enunciado: Como começar → Consulte o README do starter-kit
 */
```

## src/test/kotlin/br/com/itau/challenge/documentation/RepositoryFacts.kt:1

```kotlin
/*
 * L21 RepositoryFacts: reúne do repositório o que o README cita, para o `ReadmeTest`
 *     comparar o texto com o que existe.
 * L34-L36 archivedChanges: tira o prefixo de data da pasta, porque o README cita a change só pelo
 *     nome.
 * L38 activeChanges: aceita também a change ainda não arquivada, para o teste passar antes e
 *     depois do `archive`.
 *
 * Spec: Como o repositório foi construído; Referências do README existem no repositório
 * Enunciado: Como começar → Consulte o README do starter-kit
 */
```
