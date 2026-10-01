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
- **Toda implementação é validada contra `.challenge/enunciado.md`** antes de ser dada como pronta (Art. 11, inegociável).
- **A constituição de qualidade de código é inegociável** (seção abaixo).
- **Toda implementação passa por revisão de um agente independente antes do commit** (seção abaixo).

## Constituição — qualidade de código (INEGOCIÁVEL)

Os Arts. 1 a 6 e 8 a 10 valem para todo código de produção **e de teste**. Os Arts. 8 a 10 valem também para os artefatos da change (`design.md`, specs) e para as dependências do `build.gradle.kts`, e o que eles mandam registrar vai no `design.md`. Uma alteração de código fora de uma change não tem `design.md`, então o registro vai na descrição do commit e no relatório de revisão. O Art. 7 vale para toda resposta ao usuário. O Art. 11 vale para toda implementação, antes de ela ser dada como pronta. Prazo, atalho ou conveniência não justificam violar um artigo.

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

O código continua se explicando sozinho quanto ao **quê**: antes de comentar o quê, **renomeie, extraia uma função ou crie um tipo**. O comentário existe para o que o código não consegue dizer: o **porquê** e a **rastreabilidade** até o enunciado. Ele fica **só no cabeçalho do arquivo**, para não se misturar com o código.

- **Um cabeçalho por arquivo:** todo arquivo com bloco criado ou alterado abre, antes do `package`, com **um** comentário `/* ... */` e nenhum comentário no corpo. Em script shell, o cabeçalho vem logo após o shebang, com linhas `#`. O cabeçalho traz:
  - uma **entrada por trecho** criado ou alterado, no formato `L<início>[-L<fim>] <símbolo>: <porquê>`. O porquê é uma razão real: a intenção, a decisão não óbvia ou a restrição de negócio que motivou a implementação. Para um tipo simples, a razão real é por que ele existe como tipo distinto (ex.: não ser confundido com outro UUID);
  - o **item do enunciado** que motivou a mudança, na **última linha**, no formato `Enunciado: <seção> → <item>`. Uma entrada cujo item difere do principal o traz no fim dela, como `Enunciado: <seção> → <item>`.
- **Linhas:** a numeração é a do arquivo final, já contando o cabeçalho. A entrada de tipo ou classe cita só a linha da declaração, e a de função ou trecho cita a faixa até a linha que o fecha (`)` ou `}`, quando houver). O símbolo ajuda a reencontrar o trecho quando as linhas se deslocam. Mudou o arquivo, atualize as linhas do cabeçalho.
- **Sem comentário no corpo:** nem por bloco, nem por linha, nem por unidade. A única exceção é o KDoc de contrato (retorno e exceções) na assinatura de ports e da API pública do domínio, quando a assinatura não basta. O cabeçalho do port aponta para ele.
- **Trivial**, e dispensado de cabeçalho: arquivo que só tem getter, delegação ou expressão única sem regra.
- **Item do enunciado:** o título da subseção ou o texto em negrito do bullet do `.challenge/enunciado.md`, sem o emoji (ex.: `Enunciado: O que será avaliado → Tratamento de concorrência`). Quando existir, pode-se citar o campo do contrato (ex.: `Enunciado: O que construir → Exposição (API REST) → Contrato de resposta → balance.amount`).
- **Sem item do enunciado** (ex.: infraestrutura de suporte): escreva `Enunciado: n/a (<change> design Dn)`, citando a decisão do `design.md` que a justifica. Nunca invente um item.
- **Referência ao `design.md`** sempre na forma `<change> design Dn` (ex.: `add-balance-repository design D1`), porque cada change tem o seu D1 e o arquivo muda de lugar depois do `archive`.
- **Testes:** o cabeçalho do arquivo traz, antes da linha `Enunciado:`, `Spec: <requisito>`, com o título do requisito da spec (vários, separados por `;`). Sem requisito de spec, use `Spec: n/a (<change> design Dn)`. Os nomes dos testes já descrevem o comportamento, então só entram no cabeçalho a classe e os pontos não óbvios.
- **Também permitidos no cabeçalho:**
  - referência a uma fonte externa (spec, documentação);
  - unidade ou formato que o tipo não expressa (ex.: µs).
- **Proibidos:**
  - comentário que repete o código (o quê);
  - comentário de ruído, que enche linha sem dar uma razão;
  - diário, changelog ou autoria (isso é papel do git);
  - código comentado;
  - banners;
  - `TODO`/`FIXME` em código entregue: vira task no OpenSpec ou item documentado no README.
- **Revisão manual:** o usuário revisa os comentários antes de cada commit (ver *Revisão manual dos comentários*, abaixo).
- **Comentário desatualizado é bug.** Mudou o código, revise o comentário.
- Escreva comentários **em português do Brasil (pt-BR)** e curtos. Identificadores e termos técnicos ficam como no código (`saveIfNewer`, `ConditionExpression`). Código, identificadores, mensagens de exceção e nomes de teste continuam em inglês.

### Art. 7 — Idioma das respostas

- **Todo resultado final de um prompt é devolvido em português do Brasil (pt-BR)**, inclusive resumos, relatórios de revisão e perguntas ao usuário.
- Continuam em inglês, porque são artefatos e não respostas: código, identificadores e o `type`/`scope` dos commits. Os comentários seguem o Art. 6 e ficam em pt-BR.

### Art. 8 — Carga cognitiva e coerência

Código é lido muito mais vezes do que escrito. Entre duas soluções que atendem à spec, escolha a que um humano entende mais rápido, mesmo que seja um pouco mais longa.

- **Baixa carga cognitiva:**
  - o código se lê de cima para baixo, sem truques e sem indireção que não se paga;
  - um conceito tem um único nome em todo o código, e um nome não serve a dois conceitos.
  - O revisor cita o trecho concreto (uma função que mistura níveis de abstração, como no Art. 3, ou um nome usado para dois conceitos), e não um critério abstrato.
- **Coerência com o codebase:** antes de escrever, leia o código existente, o do kit e o dos contextos vizinhos, para aprender o padrão em uso: nomes de variáveis, classes, arquivos e pacotes, estrutura dos testes, onde ficam as constantes e como as dependências são injetadas.
  - Siga esse padrão. Só diverge com motivo explícito, nunca por gosto.
  - Se o padrão existente violar um artigo desta constituição, o artigo prevalece, e a divergência é explícita, nunca silenciosa.

### Art. 9 — Design patterns

- **Antes de desenhar uma solução, verifique se já existe um pattern reconhecido para o problema** (Strategy, Factory, Adapter, Decorator, Specification, Repository, Result, Retry, Circuit Breaker etc.). Se existir e couber, use-o, e nomeie-o no `design.md` e, quando ajudar, no nome da classe.
- O pattern serve ao problema, e não o contrário. Só entra quando o problema existe hoje, e KISS e YAGNI (Art. 4) prevalecem sobre a elegância.
- O `design.md` registra o pattern escolhido ou por que nenhum coube.

### Art. 10 — Não reinventar a roda

- **Antes de implementar algo que não seja regra de negócio do domínio, procure a solução pronta**, nesta ordem: biblioteca padrão do Kotlin/JDK, Spring, AWS SDK e, por fim, uma biblioteca consolidada no ecossistema Kotlin/Spring. "Spring" inclui o Framework e os projetos do ecossistema (Spring Data, Spring Cloud AWS etc.). Se resolve o problema e é mantida e conhecida pela comunidade, use-a em vez de criar do zero.
- **Código próprio** é para a regra de negócio do domínio, ou para quando nenhuma alternativa adequada existe. O `design.md` registra a alternativa avaliada e por que foi descartada.
- **Dependência nova** precisa de manutenção ativa, maturidade e adoção ampla, e o custo de tê-la deve ser menor que o de implementar. O `design.md` registra a evidência mínima: versão, data da última release e uma linha sobre o custo de tê-la contra o de implementar. Ela é declarada no build, e o contexto do projeto (Regra 2 abaixo) registra só a dependência e o motivo.
- **O Art. 1 prevalece:** biblioteca de infraestrutura não entra no domínio, e uma que exija abrir mão da arquitetura (por exemplo, estado mutável no domínio) é adaptada na borda ou descartada.

### Art. 11 — Conformidade com o enunciado

- **O `.challenge/enunciado.md` é a fonte da verdade do resultado esperado.** Spec, design e código o interpretam, e uma interpretação pode estar errada. Por isso **toda implementação é validada contra o enunciado**, e não só contra a spec da change, antes de ser dada como pronta.
- **Como:** ao concluir uma implementação (de uma change ou fora dela), o autor percorre as seções do enunciado que ela toca (*O que construir*, contratos de payload e de resposta, *Como começar*, *O que será avaliado*) e confere **item por item** que o entregue faz o que está escrito: nomes de campo, tipos, formatos, comandos e critérios de avaliação.
- **A conferência é concreta:** cada item do enunciado tocado aponta para uma evidência (teste, arquivo ou comando executado). Quando o enunciado descreve um comportamento observável (rodar um comando, publicar o evento de exemplo, chamar um endpoint), a validação **executa** o comportamento, e não só lê o código.
- **Registro:** a tabela item do enunciado → evidência vai no relatório de revisão e, fora de uma change, na descrição do commit. Item que a change não cobre é listado como fora do escopo, com a change prevista para tratá-lo.
- **Divergência:** se a implementação, a spec ou o design divergirem do enunciado, **pare e pergunte** ao usuário. O enunciado prevalece, salvo decisão explícita do usuário registrada no `design.md`. Uma ambiguidade do enunciado vira decisão explícita e justificada no `design.md`, nunca suposição.
- **O revisor independente refaz a validação** por conta própria, a partir do enunciado, sem usar a tabela do autor.

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
   - (c) conformidade com o enunciado (Art. 11): contratos idênticos e cada item tocado atendido, validado por conta própria a partir do `.challenge/enunciado.md`, executando o comportamento observável quando o enunciado o descreve;
   - (d) as regras de trabalho;
   - (e) os comentários do Art. 6: todo arquivo alterado tem o cabeçalho, em pt-BR, com uma entrada por trecho (linhas e símbolo que batem com o arquivo, e o porquê real) e um item do enunciado que existe no `.challenge/enunciado.md` e tem relação com o código; sem comentário no corpo (salvo o KDoc de contrato), sem ruído nem o quê.
5. **Formato dos achados:** cada um traz `arquivo:linha`, a regra violada e uma severidade:
   - **bloqueante:** viola a constituição, a spec ou o contrato;
   - **ajuste:** qualidade abaixo do padrão;
   - **sugestão:** opcional.
6. **Correção e nova rodada:** o autor corrige todos os bloqueantes e ajustes, ou rejeita um achado com justificativa técnica explícita. Depois reexecuta `./gradlew check` e submete o novo diff. Prefira reusar o mesmo revisor (via `SendMessage`), para que ele verifique as correções sem reabrir o que já aprovou.
7. **Critério de saída:** uma rodada sem bloqueantes nem ajustes pendentes.
8. **Limite de 3 rodadas.** Se não convergir, pare e apresente ao usuário os pontos em divergência, com a posição do autor e a do revisor.
   - Faça o mesmo se o revisor mostrar que o **plano** (design ou spec) está errado: não desvie dele em silêncio; proponha a correção do artefato e peça uma decisão.

**Revisão manual dos comentários:** antes do commit, o autor apresenta ao usuário os comentários novos e alterados (`arquivo:linha` e texto). O commit só sai depois de o usuário revisá-los e dar o aval.

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
