## Why

O `README.md` nasceu como o do starter kit e foi remendado a cada change: ainda abre com as "Instruções para o candidato", descreve camadas, fluxo e estrutura de pastas só do contexto `hello` e não registra nenhuma decisão de arquitetura. O serviço já ingere, grava e expõe o saldo, mas quem avalia o repositório precisa abrir cinco `design.md` arquivados e onze specs para entender **por que** o modelo é um snapshot, por que a escrita é condicional, por que a falha de dependência pausa o consumer em vez de ir para a DLT.

O enunciado pontua modelagem de dados, concorrência, resiliência, testes e *production readiness* (*O que será avaliado*) e manda documentar o que não foi implementado, com os motivadores. Sem um README que reúna isso, esses critérios não se demonstram a quem lê só a raiz do repositório.

## What Changes

- **`README.md` reescrito** a partir das specs arquivadas em `openspec/specs/` e dos `design.md` das changes, com as seções:
  - visão da solução em um parágrafo, com diagramas Mermaid do fluxo e da arquitetura hexagonal;
  - como rodar: pré-requisitos, comandos `make`, criar o tópico, gerar eventos e chamar a API;
  - decisões em formato ADR curto (contexto, decisão, consequência): snapshot vs recálculo, modelagem do DynamoDB, escrita condicional, *at-least-once* com idempotência, DLQ vs pausa, `ConsistentRead` e ausência de cache;
  - estratégia de testes: pirâmide, cenários cobertos e relatório de cobertura;
  - resiliência e observabilidade: o que existe e quais métricas olhar;
  - "O que eu faria com mais tempo", cada item com o motivador;
  - como o repositório foi construído: *Spec-Driven Development* com OpenSpec (pasta `openspec/`).
- **Removido do README:** o bloco de instruções ao candidato e as seções que descrevem só o `hello` (camadas, fluxo, estrutura de pastas), que passam a ser uma nota curta sobre o exemplo do kit.
- **Corrigido no README:** a tabela de variáveis passa a incluir `BALANCE_TABLE_NAME` e os `DYNAMODB_*` do cliente de escrita, hoje omitidos, e a tabela de comandos do Makefile deixa claro que `make db-scan` e `make http` só tocam o `hello`.
- **Novo teste estático `ReadmeTest`** (em `src/test`, sem Docker, no `./gradlew check`) que protege o README contra desatualização: seções obrigatórias, estrutura de cada ADR, existência dos alvos `make` e das variáveis de ambiente que ele cita.
- Nenhum código de produção muda.

Fora do escopo, documentado no `design.md`: gerar a documentação por ferramenta (MkDocs, Antora, adr-tools), pasta `docs/adr/` separada, e detalhar o contexto `hello` do kit.

## Capabilities

### New Capabilities
- `readme-documentation`: o conteúdo mínimo e verificável do README (visão e diagramas, como rodar, ADRs, estratégia de testes, resiliência e observabilidade, melhorias futuras com motivador, processo SDD) e a garantia de que todo comando e toda variável que ele cita existem no repositório.

### Modified Capabilities
<!-- Nenhuma: nenhum requisito de comportamento do serviço muda. -->

## Impact

- **Código:** só teste. Novo `ReadmeTest` em `src/test/kotlin/br/com/itau/challenge/documentation/`, no padrão de `observability/ComposeFileTest.kt` (lê arquivos do repositório). Nada em `src/main`.
- **Documentação:** `README.md` reescrito. `openspec/project.md` e o `context:` do `config.yaml` só mudam se um fato do projeto mudar (Regra 2 do `CLAUDE.md`), e sempre juntos.
- **Dependências:** nenhuma. O `ReadmeTest` lê os arquivos como texto.
- **Specs:** uma capability nova.
- **Árvore de trabalho:** há alterações pendentes de outras changes e de infraestrutura. Separar os arquivos/hunks para o commit desta change levar só seu escopo, respeitando a exclusão de infra já solicitada pelo usuário; alterações externas podem continuar pendentes.
- **Dependência para a entrega:** concluir `enforce-hexagonal-architecture` antes do commit/archive desta change. A nova auditoria e `follow-up.md` determinam os ajustes finais de README/testes e a revalidação; o trabalho já realizado permanece preservado.
- **Fora do repositório:** o setup pessoal de Docker remoto do autor (`.local/`) não entra no README, porque não é requisito do projeto.
