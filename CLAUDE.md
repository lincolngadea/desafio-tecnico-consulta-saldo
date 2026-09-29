# CLAUDE.md

Desafio técnico Itaú — **consulta de saldo** sobre o `itau-code-challange-starter-kit` (Kotlin, Spring Boot 4, hexagonal, DynamoDB Local, Redpanda).
Contexto completo do projeto (stack, arquitetura, convenções, comandos, testes, domínio): **`openspec/project.md`**.

## Regras de trabalho — MANDATÓRIAS

Detalhes em `openspec/project.md` → *Working Rules*.

- **TDD:** todo cenário das specs do OpenSpec vira teste **antes** do código de produção.
- **Dinheiro em `BigDecimal`** de ponta a ponta.
- **Domínio sem dependência de framework.**
- **Um commit por change do OpenSpec.**
- **Contratos (payload, request, response) vêm de `.challenge/enunciado.md`.** Confira os nomes de campo e os formatos lá e nunca os invente.

## Commits

**Conventional Commits 1.0.0** é obrigatório: `<type>(<scope>)!: <descrição>`, com a mensagem em **português** (`type`/`scope` em inglês). Tipos, escopos e formato estão em `openspec/project.md` → *Git Workflow*.

## Contexto do projeto — regras MANDATÓRIAS

Existem duas representações do contexto do projeto:

| Arquivo | Papel | Consumido por |
|-|-|-|
| `openspec/project.md` | Versão completa (pt-BR), fonte de verdade | Humanos e agentes que precisam do detalhe |
| `context:` em `openspec/config.yaml` | Versão condensada (en) dos mesmos fatos | Injetada pelo OpenSpec em toda proposta, design, spec e task |

### Regra 1 — Sincronia obrigatória

Toda alteração em `openspec/project.md` **deve** ser replicada no `context:` de `openspec/config.yaml`, e vice-versa, **na mesma tarefa e no mesmo commit**.

- Os dois devem conter o **mesmo conjunto de fatos**. O `context:` pode ser mais conciso na forma, mas nunca pode omitir uma regra ou restrição, contradizer o `project.md` ou conter um fato que não esteja nele.
- Antes de concluir qualquer tarefa que tenha tocado um dos dois, compare-os e corrija divergências.
- Após editar o `config.yaml`, valide que ele continua sendo YAML válido (ex.: `openspec list --json`).

### Regra 2 — Evolução contínua junto com o código

À medida que a implementação evolui, `project.md` e `context:` **devem** ser atualizados no mesmo passo em que a mudança acontece, e não depois. Revise-os ao final de cada change do OpenSpec (antes do `archive`) e sempre que uma destas situações ocorrer:

- Dependência, versão, serviço de infra, tópico Kafka, tabela DynamoDB ou variável de ambiente adicionada, removida ou alterada.
- Novo bounded context, nova camada/padrão arquitetural ou exceção a uma regra existente.
- Convenção de código, de testes ou de commits nova ou alterada.
- Comando do `Makefile` novo, removido ou com comportamento alterado.
- Regra de domínio, requisito não funcional ou critério de avaliação novo ou alterado (seções *Domain Context*, *Non-Functional Requirements* e *Evaluation Criteria*).
- Lacuna conhecida resolvida (remover da lista) ou descoberta (adicionar).

### Regra 3 — Cautela: só entra o que justifica existir

Esses arquivos existem para dar ao agente o **menor conjunto de informações de alto sinal** que ele precisa para agir corretamente e que **não** consegue obter de forma barata lendo o código. Esse é o princípio de *context engineering* da Anthropic: contexto é um recurso finito, e cada token irrelevante compete por atenção com os relevantes. Antes de adicionar qualquer coisa, a informação precisa passar em **todos** os critérios:

1. **Muda o comportamento do agente.** Sem ela, o agente provavelmente erraria ou violaria uma convenção.
2. **É durável.** Continua verdadeira além da tarefa atual (nada de estado temporário, TODOs ou "em andamento").
3. **É de amplo alcance.** Vale para o projeto ou para um contexto inteiro, não para uma classe específica.
4. **Não é derivável de forma barata** do código, do `git log`, do `README.md` ou das specs em `openspec/specs/`.

**Não** adicionar: listas de classes ou arquivos, detalhes de implementação, histórico de mudanças, requisitos de uma change (esses vão em `openspec/specs/`), explicações que o código já deixa claras, ou texto duplicado.

Prefira **editar ou remover** a acrescentar. Tirar informação obsoleta é tão obrigatório quanto incluir informação nova. Se uma atualização não passar nos critérios, não faça a atualização.
