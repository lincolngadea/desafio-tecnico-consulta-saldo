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
- **A constituição de qualidade de código é inegociável** (seção abaixo).
- **Toda implementação passa por revisão de um agente independente antes do commit** (seção abaixo).

## Constituição — qualidade de código (INEGOCIÁVEL)

Vale para todo código de produção **e de teste**. Prazo, atalho ou conveniência não justificam violar um artigo.

- Se cumprir um requisito exigir uma violação, **pare e pergunte** ao usuário. Não abra exceção por conta própria.
- Em conflito entre objetivos, prevalece esta ordem: **correção > clareza > simplicidade > desempenho**.

### Art. 1 — Clean Architecture

- **Regra de dependência:** as dependências apontam para dentro (`adapter → port ← application → domain`). O domínio não importa nada de fora dele: nem Spring, AWS SDK, Kafka ou Jackson, nem `application` ou `adapter`.
- **Regras de negócio vivem no domínio.** Entidades e value objects garantem suas invariantes na construção. A camada `application` só orquestra o caso de uso.
- **Adapters apenas traduzem** entre protocolo e domínio, sem decisão de negócio.
- **Tipos de tecnologia nunca atravessam um port.** Isso vale para DTOs, `AttributeValue`, `ConsumerRecord` e `ResponseEntity`.
- **Ports pertencem ao núcleo.** São definidos pela necessidade do caso de uso, não pela API da tecnologia.
- **Única exceção aceita:** `@Service` em `application`, padrão herdado do kit.

### Art. 2 — SOLID

| Princípio | Regra |
|-|-|
| **S**RP | Cada classe tem uma única razão para mudar. Um consumer não valida regra de negócio, e um repositório não decide o que é "mais recente". |
| **O**CP | Comportamento novo entra por uma nova implementação de port, ou por um novo tipo `sealed` com `when` exaustivo. Nunca por `if` de tipo espalhado no código existente. |
| **L**SP | Toda implementação de port honra o contrato inteiro, incluindo exceções e casos de ausência. Fakes de teste se comportam como a implementação real. |
| **I**SP | Ports pequenos e focados (`fun interface`, um por necessidade). Nenhum cliente depende de método que não usa. |
| **D**IP | O núcleo depende de ports injetados por construtor e nunca instancia infraestrutura. |

### Art. 3 — Clean Code

- **Nomes revelam intenção** na linguagem do domínio (ex.: `accountId`, `isNewerThan`).
  - Proibidos: nomes genéricos (`data`, `info`, `manager`, `helper`, `util`, `process`/`handle` sem complemento) e abreviações.
- **Funções pequenas**, que fazem uma coisa só, num único nível de abstração.
  - No máximo 3 parâmetros; acima disso, crie um tipo.
  - Sem parâmetro booleano de controle e sem efeito colateral escondido.
  - Respeite a separação entre comando e consulta.
- **Sem obsessão por primitivos no domínio.** Identificadores, dinheiro e instantes são value objects, validados na construção.
- **Imutável por padrão:** `val`, `data class` e coleções somente leitura. Nada de estado mutável compartilhado.
- **Erros com significado:**
  - Use exceções de domínio com mensagem útil.
  - Nunca engula exceção (`catch` vazio, ou só log sem decisão).
  - Não use exceção para o fluxo normal.
  - Ausência esperada é modelada no tipo (`?`).
- **Kotlin:**
  - `!!` é proibido em código de produção.
  - `lateinit` só onde o framework exigir.
  - `when` sobre tipos selados é exaustivo, sem `else`.
- **Sem números ou strings mágicos** (use constantes nomeadas), sem código morto, sem código comentado e sem código "para o futuro".
- **Regra do escoteiro:** deixe o código que você tocou mais limpo do que encontrou, dentro do escopo da change.

### Art. 4 — DRY, KISS, YAGNI

- **DRY:** cada regra de negócio tem **uma** representação autoritativa.
  - DRY trata de conhecimento, não de texto parecido. Não abstraia duplicação acidental; espere o terceiro caso (regra de três).
- **KISS:** a solução mais simples que atende à spec e ao enunciado.
- **YAGNI:** nada que a spec da change não peça. Sem generalização especulativa e sem extensão "para depois".
  - O que ficar de fora é **documentado**, nunca meio-implementado.

### Art. 5 — Testes são código de produção

- Seguem os artigos acima e os princípios F.I.R.S.T.: rápidos, independentes, repetíveis, autoverificáveis e escritos no momento certo.
- Um comportamento por teste. O nome descreve o comportamento (`should ... when ...`) e o corpo segue o padrão Arrange-Act-Assert.
- Testam o comportamento pela API pública, não os detalhes internos, e não têm lógica condicional.

### Art. 6 — Comentários (Clean Code)

O código deve se explicar sozinho. Um comentário admite que isso não foi possível, então antes de comentar tente **renomear, extrair uma função ou criar um tipo**.

- **Permitidos:**
  - o **porquê**: intenção, decisão não óbvia ou restrição de negócio;
  - aviso de consequência;
  - referência a uma fonte externa (spec, enunciado, documentação);
  - unidade ou formato que o tipo não expressa (ex.: µs);
  - KDoc de contrato em ports e na API pública do domínio, quando a assinatura não basta.
- **Proibidos:**
  - comentário que repete o código;
  - comentário de ruído ou obrigatório;
  - diário, changelog ou autoria (isso é papel do git);
  - código comentado;
  - banners;
  - `TODO`/`FIXME` em código entregue: vira task no OpenSpec ou item documentado no README.
- **Comentário desatualizado é bug.** Mudou o código, revise o comentário.
- Escreva comentários **em inglês**, como o código, curtos e junto do que explicam.

## Revisão por agente independente — MANDATÓRIA

Nenhuma implementação é dada como pronta, nem commitada, sem a revisão de **outro agente**. Autor e revisor são papéis distintos: quem implementou não se autoaprova.

**Quando:** ao concluir a implementação de cada change do OpenSpec, **antes do commit** e, portanto, antes do `archive`. Vale também para qualquer alteração de código feita fora de uma change.

**Como:**

1. **Pré-condição:** `./gradlew check` precisa estar verde. Código quebrado não vai para revisão.
2. **Dispare um subagente com contexto limpo:** use a ferramenta Agent, **nunca** `fork`, para que ele não herde o raciocínio do autor. O revisor é **somente leitura**: analisa e reporta, não edita.
3. **O que entregar ao revisor:**
   - o nome da change e os caminhos de `proposal.md`, `design.md`, `specs/` e `tasks.md`;
   - o diff da change;
   - as referências `CLAUDE.md` (esta constituição) e `.challenge/enunciado.md` (contratos).

   Não entregue suas conclusões nem justificativas.
4. **O que o revisor verifica:**
   - (a) coerência com o plano: cada requisito e cenário da spec está implementado e testado, e não há nada fora do escopo;
   - (b) cada artigo da constituição;
   - (c) contratos idênticos ao enunciado;
   - (d) as regras de trabalho.
5. **Formato dos achados:** cada um traz `arquivo:linha`, a regra violada e uma severidade:
   - **bloqueante:** viola a constituição, a spec ou o contrato;
   - **ajuste:** qualidade abaixo do padrão;
   - **sugestão:** opcional.
6. **Correção e nova rodada:** o autor corrige todos os bloqueantes e ajustes, ou rejeita um achado com justificativa técnica explícita. Depois reexecuta `./gradlew check` e submete o novo diff. Prefira reusar o mesmo revisor (via `SendMessage`), para que ele verifique as correções sem reabrir o que já aprovou.
7. **Critério de saída:** uma rodada sem bloqueantes nem ajustes pendentes.
8. **Limite de 3 rodadas.** Se não convergir, pare e apresente ao usuário os pontos em divergência, com a posição do autor e a do revisor.
   - Faça o mesmo se o revisor mostrar que o **plano** (design ou spec) está errado: não desvie dele em silêncio; proponha a correção do artefato e peça uma decisão.

**Registro:** ao final, informe ao usuário quantas rodadas houve, o que foi corrigido e o que foi rejeitado, com o motivo. A aprovação final é sempre do humano.

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
