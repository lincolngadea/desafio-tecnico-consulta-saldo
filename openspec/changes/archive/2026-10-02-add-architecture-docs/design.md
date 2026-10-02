## Context

- O `README.md` tem 462 linhas e três origens misturadas: o template do kit (instruções ao candidato, camadas, fluxo e estrutura de pastas do `hello`), os remendos de cada change (endpoint `/balances`, mensageria de transações, variáveis, observabilidade) e nada de decisão de arquitetura. As seções do `hello` contradizem o produto: o fluxo de dados mostrado é `greeting-templates` para `/hello`.
- As decisões existem, mas dispersas: cinco `design.md` em `openspec/changes/archive/` (`add-balance-repository`, `add-balance-concurrency-test`, `add-transaction-ingestion`, `add-balance-query-api`, `add-observability`) e onze specs em `openspec/specs/`. Para citá-las, este documento usa as siglas **REPO**, **CONC**, **INGEST**, **API** e **OBS**, seguidas do número da decisão (`REPO design D2`).
- O enunciado (*O que será avaliado*) pontua modelagem no DynamoDB, concorrência, resiliência, testes, qualidade de código e *production readiness*, e traz a cláusula: "Não teve tempo suficiente para implementar algum pattern ou algoritmo? Sem problema, apenas documente o que poderia ser implementado com os motivadores". O enunciado não impõe conteúdo ao README do candidato, só diz que o README do kit traz stack, arquitetura, comandos, como rodar e como testar.
- O `.challenge/enunciado.md` não é versionado (`.challenge/` está no `.gitignore`, e o próprio enunciado pede para não incluí-lo no repositório). O README só pode citá-lo por seção e item, nunca linká-lo.
- Já existe um padrão para teste estático que lê arquivos do repositório: `src/test/kotlin/br/com/itau/challenge/observability/ComposeFileTest.kt` (lê `docker-compose.yml` e `application.yaml`, roda no `./gradlew check`, não precisa de Docker).

## Goals / Non-Goals

**Goals:**
- Um README que um avaliador lê de cima para baixo e entende a solução, roda, e vê o porquê de cada decisão sem abrir `openspec/`.
- Cada fato do README rastreável a uma spec, a um `design.md` ou ao código, e cada comando e variável citados existentes no repositório.
- Sete ADRs curtos com a mesma forma (contexto, decisão, consequência) e a ligação para o `design.md` de origem.
- A cláusula do enunciado atendida: tudo o que ficou de fora listado com o motivador.
- Um teste que impeça o README de voltar a apontar para comando, alvo `make` ou variável que não existe.

**Non-Goals:**
- Mudar comportamento do serviço, código de produção, Makefile, `Dockerfile` ou `docker-compose.yml`.
- Gerar site de documentação ou publicar em outro lugar (MkDocs, Antora, GitHub Pages).
- Detalhar o contexto `hello` do kit além de uma nota curta.
- Verificar a prosa do README por teste. O teste cobre só o que é mecânico (D5).
- Reabrir decisão já tomada nos `design.md`. O README as reporta (D6).

## Decisions

### D1. Fonte do conteúdo

- **Escolha:** o README é derivado das specs de `openspec/specs/` e dos `design.md` arquivados. Um fato que essas fontes não tragam (comando do Makefile, porta, nome de classe de teste) é conferido no código, no `Makefile` ou no `docker-compose.yml` antes de entrar. Se nenhuma fonte o sustentar, não entra, e vira lacuna registrada na tarefa de revisão, nunca suposição.
- **Por que:** o pedido manda partir dessas fontes, e o Art. 11 não aceita um README que descreva um comportamento que ninguém executou.
- **Terminologia (Art. 8):** o README usa `DuplicateIgnored` e `StaleIgnored` como decidido em OBS design D6. O texto de INGEST design D2, que chama a reentrega de `StaleIgnored`, é anterior a essa decisão e não é copiado.

### D2. ADRs dentro do README, em formato curto

- **Escolha:** os sete ADRs ficam numa seção do próprio README, cada um com os três campos **Contexto**, **Decisão** e **Consequência** e uma linha **Detalhe** com a decisão de origem (`add-balance-repository design D2`, sempre nessa forma, porque o arquivo muda de lugar depois do `archive`).
- **Alternativa descartada:** uma pasta `docs/adr/` com um arquivo por decisão (formato de Michael Nygard). Fragmenta a leitura do avaliador em sete arquivos, e os `design.md` já são o registro longo, com alternativas e riscos. O ADR do README é o resumo que aponta para ele (DRY de conhecimento: o porquê completo vive no `design.md`).
- **Tamanho:** o ADR resume em poucas linhas e não copia a justificativa inteira. Quem quiser a discussão completa segue o link.
- **Conteúdo por ADR** (todos com fonte nos `design.md`):

| ADR | Decisão a registrar | Detalhe |
|-|-|-|
| Snapshot vs recálculo | O serviço guarda o último snapshot por conta e nunca recalcula, porque o evento já traz o saldo. Tabela de histórico descartada. | REPO D6, D8; CONC D3 |
| Modelagem do DynamoDB | Tabela `AccountBalances`, chave de partição `accountId`, sem chave de ordenação e sem índice secundário. | REPO D1 |
| Escrita condicional | `PutItem` com `ConditionExpression` sobre `SnapshotVersion` (maior `timestamp`, empate pelo maior id em texto minúsculo), resultado tipado, `DuplicateIgnored` separado de `StaleIgnored`. | REPO D2, D3; OBS D6 |
| *At-least-once* e idempotência | `MANUAL_IMMEDIATE`, offset confirmado só depois da escrita ou da DLT, idempotência pela condição estrita, sem exactly-once e sem tabela de deduplicação. | INGEST D2, D8, D9 |
| DLQ vs pausa | A DLT só recebe erro permanente. Falha da dependência pausa as partições e deixa o Kafka como buffer. | INGEST D4, D6 |
| `ConsistentRead` | `GetItem` fortemente consistente, ao custo de 2x de RCU, com o limite honesto de que o atraso dominante é o lag do consumer. | REPO D4; API D8 |
| Ausência de cache | Nenhum cache de saldo, e `Cache-Control: no-store` por um filtro, não por interceptor. | API D8 |

### D3. Diagramas em Mermaid

- **Escolha:** dois diagramas em blocos ```` ```mermaid ````: o **fluxo** (Kafka, listener, caso de uso, DynamoDB; HTTP, caso de uso, DynamoDB) e a **arquitetura hexagonal** (`adapter → port ← application → domain`, com os adapters de entrada e de saída). O README já usa Mermaid, e o GitHub o renderiza.
- **Alternativa descartada:** imagem gerada (PNG/SVG). Desatualiza em silêncio, não aparece no diff e exige uma ferramenta para regenerar.
- **Fidelidade:** os nomes dos nós saem do código real (`TransactionEventListener`, `ProcessTransactionService`, `BalanceRepository`, `DynamoDbBalanceWriter`, `GetBalanceService`, `BalanceController`, `BalanceProvider`, `CircuitBreaker*`). O diagrama não inventa componente.

### D4. Pattern e ferramenta (Arts. 9 e 10)

- **Pattern (Art. 9):** o ADR é o pattern de registro de decisão. Nenhum outro pattern cabe: não há problema de software a resolver aqui, só de comunicação.
- **Ferramenta pronta (Art. 10):** biblioteca padrão e Spring não se aplicam a documentação. Avaliadas e descartadas: `adr-tools` (gera arquivos separados, ver D2), MkDocs e Antora (geram um site que ninguém pediu e acrescentam um build e uma dependência fora do ecossistema Kotlin/Spring), e o plugin `springdoc`, que já documenta a API em `/swagger-ui.html` e que o README apenas referencia. Nenhuma dependência nova.

### D5. `ReadmeTest`: teste estático do que é mecânico

- **Escolha:** `ReadmeTest` em `src/test/kotlin/br/com/itau/challenge/documentation/`, no padrão de `ComposeFileTest`: lê `README.md`, `Makefile`, `application.yaml`, `docker-compose.yml` e `Dockerfile` do diretório de trabalho, e usa dois apoios no mesmo pacote, para o teste não carregar a lógica de leitura (Art. 3): `MarkdownText` (funções de extensão que separam o texto em seções, tabelas, blocos de código e links) e `RepositoryFacts` (o que o repositório tem: alvos do `Makefile`, tipos do código, classes de teste, changes e fontes de variáveis), roda no `./gradlew check` e não precisa de Docker. Um comportamento por teste, `should ... when ...`, Arrange-Act-Assert (Art. 5).
- **O que verifica:**
  - as seções obrigatórias existem, pelos títulos;
  - os sete ADRs existem e cada um tem **Contexto**, **Decisão** e **Consequência**;
  - os dois diagramas Mermaid existem;
  - todo `make <alvo>` citado em bloco de código existe como alvo no `Makefile`;
  - toda variável de ambiente listada na tabela de variáveis existe no `application.yaml`, no `docker-compose.yml` ou no `Dockerfile` (`JAVA_TOOL_OPTIONS` só existe no último);
  - todo item de "O que eu faria com mais tempo" traz um motivador (célula não vazia);
  - todo nome de classe dos diagramas existe como tipo em `src/main/kotlin`, e toda classe de teste citada existe em `src/test` ou `src/integrationTest`;
  - todo link relativo resolve para um arquivo ou pasta que existe, toda change arquivada em `openspec/changes/archive/` aparece no README, e toda change que o README cita existe em `openspec/changes/archive/` ou em `openspec/changes/` (assim o teste passa antes e depois do `archive` desta change);
  - o README não cita o setup pessoal (`.local/`) nem linka o `enunciado.md`.
- **Fica para a execução e a revisão** (não são mecânicos): que os nomes de métrica batem com `/actuator/prometheus` (tarefa 3.5) e que nenhum item de "O que eu faria com mais tempo" é inventado (revisão independente).
- **O que não verifica:** a prosa, a correção técnica de uma justificativa ou a qualidade do texto. Isso é da revisão independente.
- **Por que um teste e não só revisão:** o README já ficou desatualizado uma vez (fluxo do `hello` num produto de saldo). Um alvo `make` renomeado quebraria o README em silêncio, e o custo do teste é baixo.
- **Dependências:** nenhuma. Os arquivos são lidos como texto: o `application.yaml` não precisa ser interpretado como YAML, basta procurar o nome da variável.
- **Mantém simples (KISS):** expressões regulares curtas sobre o texto. Sem parser de Markdown.

### D6. Ambiguidades do enunciado já decididas: o README as reporta, não as reabre

A nota da transcrição do enunciado lista seis ambiguidades. Cinco são decisões de design e já têm decisão nos `design.md`, e o README as lista numa tabela curta com a decisão e a origem. A sexta, não versionar o enunciado, é regra do `CLAUDE.md` e não é decisão de design; o README só a respeita, sem linkar o arquivo:

| Ambiguidade | Decisão | Origem |
|-|-|-|
| Rota no plural | `GET /balances/{accountId}` | API D2 |
| `updated_at` vs `timestamp` | `updated_at` é o `transaction.timestamp` do evento que gerou o snapshot | REPO D8 |
| Status para conta inexistente | `404`; `400` para `accountId` que não é UUID | API D3 |
| Evento rejeitado atualiza o snapshot? | Sim, `DECLINED` segue a mesma regra, e só `updated_at` avança | INGEST D1 |
| Empate de `timestamp` | Maior id de transação, em texto minúsculo | REPO D2 |

Nenhuma decisão nova. Se a escrita do README revelar divergência entre um `design.md` e o enunciado, a regra do Art. 11 vale: parar e perguntar.

### D7. Estrutura do README e o que sai

- **Ordem:** a do pedido (visão, como rodar, decisões, testes, resiliência e observabilidade, melhorias, processo), seguida das duas referências que já existem e estão certas (variáveis de ambiente e comandos do Makefile).
- **Sai:** o bloco "Instruções para o candidato", as camadas, o fluxo e a estrutura de pastas só do `hello`, e as marcas de CI com URL relativa quebrada.
- **Fica:** o aviso sobre os comentários de cabeçalho (Art. 6) e a tabela de imagens Docker, e a tabela de variáveis, corrigida (D1).
- **`hello`:** uma nota curta diz que é o exemplo do kit e que não faz parte da solução. Detalhá-lo seria descrever código que sai do repositório (`REPO D7`).
- **Idioma:** pt-BR (Art. 7). Nomes de código, comandos e métricas ficam como no código.

### D8. Lacuna que o README documenta em vez de esconder

O gerador `make kafka-produce-transactions-events` usa um `account.id` aleatório por evento, então nenhuma conta recebe um segundo evento e o gerador não demonstra duplicado nem fora de ordem. O README diz isso e indica como demonstrar: publicar à mão no Redpanda Console com o payload de exemplo do enunciado. Esses cenários estão cobertos pelos testes de integração. Mudar o gerador fica fora do escopo e entra em "O que eu faria com mais tempo".

### Respeito à constituição

- **Camadas, ports e value objects (Art. 1):** nenhuma. A change não toca `src/main`.
- **Testes (Art. 5):** `ReadmeTest` segue F.I.R.S.T. e o padrão de `ComposeFileTest` (Art. 8). É independente de Docker e repetível.
- **Comentários (Art. 6):** o `ReadmeTest` abre com o cabeçalho único, em pt-BR, com `Spec: <requisito>` e `Enunciado: Como começar → Consulte o README do starter-kit`, o item em que o enunciado diz o que o README deve conter. O README é Markdown e não é código, então o Art. 6 não se aplica a ele.
- **YAGNI (Art. 4):** nenhum gerador, nenhum site, nenhuma pasta nova de documentação. O que ficou de fora é documentado, não meio implementado.
- **Contexto do projeto (Regras 1 a 3):** `project.md` e `config.yaml` só mudam se um fato mudar, e sempre juntos. A tabela de variáveis do README segue o `application.yaml`, que é a fonte, e não o inverso.

### D9. Dependência da auditoria arquitetural antes da entrega

A pedido do usuário, a finalização depende agora de `enforce-hexagonal-architecture`. A auditoria está em `audit.md` dessa change e o checklist editorial em `follow-up.md` desta. A auditoria inicial encontrou Spring em application, lacunas no guard e inputs ausentes no Gradle/Docker; esses pontos foram resolvidos pela dependência arquivada em 2026-10-02. Esses ajustes de código/build pertencem à nova change; a reconciliação final do README/testes e a revisão atualizada continuam nesta. As 32 tarefas concluídas pelo Claude são preservadas, com novas tarefas de retomada antes da entrega. A dependência precisa estar concluída antes do commit/archive de docs; não reescrever o README para encobrir as lacunas.

## Risks / Trade-offs

- [O README documenta um comando que não funciona] → A validação do Art. 11 executa cada comando (subir o ambiente, criar o tópico, gerar eventos, chamar a API, ler `/actuator/prometheus`) e o `ReadmeTest` garante que alvos e variáveis existem. O autor não tem Docker local, então os comandos rodam no host Docker que o autor já usa, e as tarefas que dependem dele ficam abertas até haver um host disponível.
- [O README fica longo demais e ninguém o lê] → ADR de poucas linhas com link para o `design.md`, tabelas em vez de prosa para o que é lista, e o índice no topo.
- [Um fato do README diverge de um `design.md` mais recente] → D1 manda conferir no código e usar a decisão mais recente (caso `DuplicateIgnored`). O revisor independente refaz a conferência a partir do enunciado.
- [`ReadmeTest` frágil, que quebra por reescrita de título] → Os títulos que ele exige são parte da spec desta change (requisito "Seções obrigatórias"). Mudar um deles é mudar a spec.
- [O teste estático dá falsa sensação de que o README está certo] → Ele é declarado como cobertura só do mecânico (D5). A correção da prosa é da revisão.
- [O Gradle reaproveita o resultado do `test` depois de uma edição só no README, porque os arquivos não são inputs declarados] → Constatado anteriormente e confirmado pela inspeção de inputs na auditoria. A correção foi entregue em `enforce-hexagonal-architecture`, com inputs declarados e arquivos no estágio Docker test. A retomada valida o estado final e distingue execução nova de cache.
- [Árvore de trabalho com mudanças de outras changes] → Separar stage por arquivos/hunks e respeitar exclusões anteriores; esta change só entra no commit com seu escopo. Não exigir commit de alterações externas como condição para retomar docs.

## Migration Plan

1. Escrever `ReadmeTest` e vê-lo falhar contra o README atual.
2. Reescrever o README por seção até o teste passar.
3. Executar os comandos documentados e conferir métricas e respostas.
4. `./gradlew check` verde, revisão independente, aval do usuário aos comentários, commit e `archive`.

Reversão: `git revert` do commit. Não há estado, migração nem dependência a desfazer.

## Open Questions

- Nenhuma que bloqueie. Os itens de "O que eu faria com mais tempo" saem dos `design.md` com o motivador declarado ali; um item sem motivador na fonte não entra.
