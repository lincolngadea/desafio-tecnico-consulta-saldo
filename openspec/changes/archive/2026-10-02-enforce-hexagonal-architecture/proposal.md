## Why

Antes de finalizar `add-architecture-docs`, a arquitetura documentada precisa corresponder à implementação e a verificações que realmente detectem violações. A auditoria encontrou quatro services com Spring em `application`, guards incompletos e testes de documentação/infraestrutura sem todas as entradas necessárias no Gradle e no build Docker.

## What Changes

- Tornar `domain`, `port` e `application` livres de frameworks nos contextos `hello` e `balance`; remover `@Service` dos quatro services e fazer a composição Spring fora dessas camadas, expondo os casos de uso pelos ports.
- Proibir dependências de adapters para `application` e detectar automaticamente todos os bounded contexts reais, incluindo novos contextos sem cadastro manual; adicionar provas negativas das regras arquiteturais.
- Mover a configuração técnica compartilhada do DynamoDB para fora de `hello`, preservando os dois perfis de cliente, seus nomes, qualificadores, parâmetros e comportamento.
- Declarar as entradas externas consumidas pelos testes estáticos, para uma alteração só em documentação ou infraestrutura invalidar o resultado do Gradle.
- Disponibilizar essas entradas somente no estágio Docker de testes, preservando uma imagem de runtime sem documentação, testes, arquivos pessoais ou segredos.
- Registrar evidências de regressão do HTTP, ingestão, armazenamento, observabilidade e do `hello`; atualizar a regra arquitetural em `CLAUDE.md`, `openspec/project.md` e `context:` conjuntamente durante a implementação.
- Manter `add-architecture-docs` aberta e dependente desta change. A reconciliação final do README, a revisão dos comentários de documentação e seu commit/archive continuam naquela change.

Não há mudança no contrato HTTP/Kafka, nas regras de saldo, nas dependências de produção ou no esquema DynamoDB. O build de teste expõe explicitamente kotlin-compiler-embeddable, já transitivo de Konsist e gerenciado pelo BOM, para acessar o parser público; não adiciona artifact nem versão ao runtime existente. A substituição da exceção histórica de `@Service` no núcleo segue a instrução explícita do usuário; não se apresenta essa regra mais estrita como uma exigência literal do enunciado.

## Capabilities

### New Capabilities

- `hexagonal-architecture`: núcleo sem framework, acesso aos casos de uso por ports, composição externa, cobertura de todos os contextos e infraestrutura compartilhada independente do exemplo `hello`.
- `repository-verification`: testes que leem o repositório com entradas declaradas no Gradle e disponíveis no estágio Docker de testes.

### Modified Capabilities

Nenhuma: os requisitos de comportamento já especificados permanecem válidos; muda a organização interna e a confiabilidade da verificação.

## Impact

- **Produção:** quatro services, nova composição externa e movimentação de `DynamoDbConfig`/`DynamoDbProperties`; sem alteração funcional planejada.
- **Testes:** `HexagonalArchitectureTest`, testes de composição/independência dos contextos e referências à configuração DynamoDB movida. Reusar Konsist, seu parser Kotlin transitivo, JUnit, Spring de teste e a biblioteca padrão existentes.
- **Build:** `build.gradle.kts`, estágio `test` do `Dockerfile` e regras seletivas em `.dockerignore`; executar tanto o check no host quanto o caminho documentado `make test`.
- **Documentação de regras:** `CLAUDE.md`, `openspec/project.md` e `openspec/config.yaml`. Os ajustes editoriais finais de README e seus testes continuam pertencendo a `add-architecture-docs`.
- **Preservação:** alterações Docker já pendentes e trabalho do Claude não serão incorporados silenciosamente ao commit. A implementação deverá separar alterações prévias de cada change e validar o conteúdo exato a ser commitado.
- **Fonte e auditoria:** `audit.md` contém a matriz já tratado/pendente, as evidências e os critérios para retomar a documentação.
