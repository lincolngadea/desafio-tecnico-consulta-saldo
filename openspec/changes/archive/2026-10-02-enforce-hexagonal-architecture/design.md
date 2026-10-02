## Context

`add-architecture-docs` está com 32/35 tarefas concluídas. O trabalho do Claude é preservado: README reescrito e ReadmeTest com seus apoios. `audit.md` compara critérios, código e verificações existentes. O check foi reexecutado e passou com 343 testes e 95,19% de cobertura, apesar dos guards incompletos.

Há uma diferença entre fontes: `CLAUDE.md` e o contexto ainda permitem `@Service` em application; o README diz que o núcleo não conhece framework; o usuário pediu explicitamente ports e application livres de Spring. A mudança adota o requisito mais estrito solicitado, sem reinterpretar isso como uma obrigação literal do enunciado. Os contratos e as decisões de saldo já tomadas permanecem válidos.

## Goals / Non-Goals

**Goals:**
- Núcleo puro em hello e balance, com composição Spring explícita fora dele.
- Guard que impeça adapter → application e cubra todo contexto atual/futuro, com provas de que violações são rejeitadas.
- Infraestrutura DynamoDB compartilhada sem ser propriedade do exemplo hello.
- Testes de arquivos do repositório confiáveis tanto no host quanto no estágio Docker test.
- Entrega de evidências que permita concluir a change de documentação depois, sem perder seu trabalho.

**Non-Goals:**
- Criar um terceiro contexto funcional, remover hello, alterar os ports/contratos ou reescrever as regras de saldo.
- Introduzir framework de DI próprio, nova biblioteca arquitetural, eventos entre contextos ou shared kernel de domínio.
- Implementar toda a tabela de melhorias futuras: auth, cache, histórico, OTLP, dashboards, carga e DNS continuam com seus motivadores.
- Fechar, commitar ou arquivar add-architecture-docs nesta implementação.

## Decisions

### D1. Composition Root externo aos bounded contexts

Aplicar o pattern **Composition Root**: manter services como classes Kotlin comuns, com construtores existentes e implementação dos ports de entrada. Remover os quatro `@Service`; declarar beans Spring em `br.com.itau.challenge.configuration`, com configuração separada por contexto (`BalanceUseCaseConfiguration`, `HelloUseCaseConfiguration`). Os métodos recebem ports de saída e expõem os ports de entrada. Só a raiz de composição importa as implementações de application.

A divisão por contexto permite carregar a composição de balance sem exigir beans de hello. Mantêm-se a injeção por construtor, os fakes existentes e os decorators `@Primary` dos ports de saída. Tests devem verificar a seleção dos decorators, evitando que a migração conecte o caso de uso diretamente ao adapter DynamoDB.

Alternativas descartadas: conservar `@Service` (contradiz a instrução do usuário); registrar services em um pacote adapter (criaria a dependência proibida); container de DI próprio (reinventa o suporte já presente). Spring `@Configuration`/`@Bean`, já usado no repositório, resolve a composição. Nenhuma dependência nova.

### D2. Regras arquiteturais e descoberta de contextos

Reusar Konsist 0.17.3 já instalado e as regras existentes. Adicionar proibição de adapter depender de application e de framework no conjunto domain/port/application. Adapters podem usar modelos do domínio para traduzir transporte; a interação com casos de uso usa somente os ports. A regra não proíbe os imports técnicos necessários aos próprios adapters.

Descobrir contextos a partir das camadas sob `br.com.itau.challenge.<contexto>` nos fontes de produção. `configuration` e `infrastructure` são composição/infraestrutura externas, não contextos de negócio e não entram por engano na descoberta. A descoberta deve incluir um contexto parcialmente formado e detectar conjunto vazio em vez de passar por ausência de arquivos. Não manter whitelist manual hello/balance.

Para o núcleo, permitir dependências do JDK/Kotlin e do núcleo do próprio contexto; rejeitar dependências de adapters, da raiz de composição, de outro contexto e de frameworks (inclusive Spring, SDK, Kafka, Jackson, Resilience4j, Micrometer e Jakarta). Não tratar `javax.*` inteiro como biblioteca padrão: validar o namespace/tipo realmente usado. Imports com alias, wildcard e referências qualificadas não podem virar escape da regra.

Provar os guards com fixtures positivas/negativas por fonte, usando a mesma política que verifica produção, sem adicionar um contexto fictício em src/main. Fazer um spike curto das APIs de Konsist para essas referências antes de escolher a forma final de asserção. Evitar um parser Kotlin próprio e evitar regex sobre comentários/strings como única prova. Se a biblioteca não oferecer verificação suficiente, registrar a lacuna e avaliar a alternativa consolidada mínima no design antes de alterar dependências; nunca reduzir o cenário para fazer passar.

**Spike realizado:** as APIs públicas de Konsist fornecem escopo, pacotes e imports, mas sua árvore PSI é interna em Kotlin. Usar `KtPsiFactory`/`KtTreeVisitorVoid`, APIs públicas do parser Kotlin já transitivo de Konsist, sobre o texto de cada arquivo do escopo. Expor `kotlin-compiler-embeddable` em `testImplementation` sem nova versão/artifact de runtime; o BOM mantém 2.3.21. Não usar reflexão nem depender da implementação interna do Konsist. Fixtures e produção compartilham a mesma política. Para chamadas qualificadas de funções Kotlin sem nome de tipo, reconhecer os namespaces reais dos jars do classpath de teste com classpath real das dependências de teste fornecido pelo Gradle/`JarFile` do JDK (fallback `java.class.path` para execução direta), além da raiz do projeto; a PSI continua extraindo as referências. Fixtures provam a DSL `org.springframework.context.support.beans`, função de composição e uma cadeia comum de receivers. O projeto de parsing fica isolado; a API pública de inicialização requer opt-in `K1Deprecation` nesta versão do compiler, limitado ao helper de teste e registrado para a próxima atualização de Kotlin.

### D3. Configuração técnica do DynamoDB em pacote neutro

Mover `DynamoDbConfig` e `DynamoDbProperties` para `br.com.itau.challenge.infrastructure.dynamodb`; atualizar imports de testes e composição. Preservar os nomes `dynamoDbClient`/`readDynamoDbClient`, `@Primary`, qualifiers, prefixo de propriedades, env vars, timeouts, retry, credenciais e número de instâncias. Testar configuração de balance sem componentes hello, além do contexto completo.

Pattern: configuração/factory técnica já existente, sem novo compartilhamento de modelos de domínio. Manter dentro de hello deixa a dependência operacional que a auditoria identificou; criar um cliente por contexto quebra o perfil de acesso já decidido. Não mover `ApiExceptionHandler` para infraestrutura: ele traduz exceções de balance, já está fora de hello e seu comportamento global é coberto pelos testes. A alegação editorial de que ele precisa mudar para remover hello será reconciliada na change de docs.

### D4. Entradas externas da verificação declaradas no Gradle

Usar a API de inputs/files do Gradle, não `outputs.upToDateWhen { false }` nem `--rerun-tasks` permanente. Declarar na task test os arquivos e árvores que os testes leem diretamente: README/CLAUDE, Makefile, Dockerfile, `.dockerignore`, compose, arquivos relevantes de infra/http, OpenSpec e fontes usadas no inventário/arquitetura (incluindo integrationTest). Conjuntos devem acompanhar novos arquivos, renomes e remoções, sem depender apenas dos arquivos presentes no instante da configuração.

Não incluir build, .gradle, .local, .challenge ou conteúdo pessoal como entrada. Testar uma cópia temporária do projeto para alterar README/infra/artefatos sem tocar o trabalho do usuário. Uma alteração só em arquivo externo deve reexecutar test; uma repetição sem alterações continua elegível a UP-TO-DATE. Não adicionar Gradle TestKit/dependência antes de conferir se a infraestrutura de teste existente e execução do wrapper em diretório temporário bastam para os cenários.

### D5. Arquivos do repositório presentes somente no estágio Docker test

O Dockerfile atual só copia src e build/gradle para base; os testes estáticos exigem mais arquivos. Disponibilizar os arquivos da D4 por COPY explícito no estágio test, antes de `./gradlew check`. Ajustar exclusões/reinclusões de `.dockerignore` de forma seletiva para OpenSpec e `http/hello.http`, entre os demais alvos reais de links do README. Incluir `.dockerignore` como arquivo que o teste pode ler quando necessário.

Não copiar a raiz inteira com `COPY . .`. Nunca levar `.local`, `.challenge`, `.claude`, `.codex`, .git, credenciais ou relatórios ao build de teste. Manter o builder independente desses arquivos editoriais e o runtime copiando apenas o jar. Uma alteração no README invalida o passo de verificação no Docker, sem obrigar a regenerar o jar de produção.

Provar com build Docker real, incluindo execução sem cache do estágio test pelo menos uma vez. O host remoto usado anteriormente recusou SSH nesta auditoria; portanto o build ainda não tem resultado novo. Não substituir esse gate por um teste textual do Dockerfile ou marcar verde com base em cache anterior.

### D6. Duas changes, com responsabilidades e entrega em ordem

Esta change entrega código arquitetural, proteção de build e evidências. `add-architecture-docs` entrega a reconciliação final do README e seus testes: regra/diagramas, componentes externos, matrizes de cobertura, lista de changes/capacidades, reconhecimento de nomes como `enforce-...`, retirada dos itens resolvidos da tabela de futuro e ajuste do texto de commits. Atualizar lá a evidência de revisão, que está marcada nas tasks mas sem relatório persistido.

Adicionar agora somente a dependência e o checklist de retomada nos artefatos de docs; preservar as 32 tarefas concluídas. Quando a nova change estiver pronta, coordenar a linha de histórico no README ainda aberto antes de arquivar, para o teste que lista changes continuar válido; isso é trabalho editorial de docs, identificado como tal, não inclusão silenciosa no commit de arquitetura.

Durante a implementação, atualizar `CLAUDE.md` para retirar a exceção de `@Service`, e editar project.md/context conjuntamente para composição externa, descoberta automática e entradas da verificação. Não reescrever decisões históricas arquivadas como se nunca tivesse existido a exceção. Em commits, revisar stage por arquivo/hunk e considerar o estado já pendente de Docker/infra; preservar mudanças anteriores, sem squash de trabalhos não relacionados. A antiga tarefa 7.1 de docs deve tratar separação de escopos, sem exigir que alterações excluídas pelo usuário sejam commitadas antes.

### D7. Conformidade e critérios de saída

O enunciado exige a arquitetura do kit e os comportamentos de ingestão/consulta; não especifica annotations de DI. A instrução do usuário resolve a opção técnica por núcleo puro. Não há nova ambiguidade de payload ou resposta.

Após a refatoração: check novo com gate ≥90%, arquitetura com fixtures de rejeição, wiring completo e balance isolado, make test real e make integration-test real. Revalidar make up, tópico/50 eventos, publicação do exemplo, duplicado/fora de ordem/DECLINED, respostas 200/400/404/503 com Retry-After, 500 sanitizado por teste, OpenAPI/Swagger, métricas, readiness/liveness e comportamento hello. Registrar o que foi executado, reutilizado ou não executado. Não parar os mesmos consumers da aplicação e dos testes competindo pelo mesmo grupo; seguir o isolamento já implementado e restaurar a infraestrutura ao fim.

Validar o diff exato destinado ao commit, inclusive onde ReadmeTest ainda é trabalho não commitado de docs. Revisão independente limpa, até três rodadas, sem bloqueantes/ajustes; comentários novos/alterados apresentados ao usuário antes do commit. Depois a documentação reexecuta os gates afetados e recebe sua própria revisão após as edições finais.

## Risks / Trade-offs

- [Beans duplicados ou caso de uso sem bean] → Remover os estereótipos junto da nova configuração; teste de unicidade por port e de contexto completo.
- [Circuit breaker contornado no wiring] → Verificar que a injeção continua selecionando os decorators primários e repetir os testes de indisponibilidade.
- [Guard passa por escopo vazio ou ignora novo contexto] → Fixture adicional e asserções não vazias, sem nomes fixos de contexto.
- [Konsist não resolve alguma referência qualificada] → Spike e fixture antes da implementação; documentar alternativa em vez de relaxar a regra.
- [Imagem test leva material pessoal] → COPY seletivo e exclusões mantidas; inspeção real do estágio/runtime.
- [Resultado verde só no workspace com arquivos de docs ainda não commitados] → Verificar a versão destinada ao commit separadamente e registrar dependência editorial; não misturar os commits por conveniência.
- [Docker indisponível] → Concluir trabalho independente, manter gates reais pendentes até recuperar o host; não inventar resultados.

## Migration Plan

1. Registrar baseline/auditoria e a dependência de docs (este planejamento).
2. Escrever guards/fixtures vermelhos e testes de composição; migrar services e configuração DynamoDB até verde.
3. Escrever provas vermelhas de invalidação/contexto Docker; ajustar build até verde.
4. Atualizar regras/contexto em sincronia, executar regressão completa e revisão, obter aval dos comentários e commitar o escopo correto.
5. Sincronizar specs e arquivar esta change; retomar a documentação pelo checklist vinculado, sem arquivá-la antecipadamente.

Reversão: reverter o commit da implementação; sem migração de dados, mudança de schema ou alteração de contrato. Não reverter trabalho de Docker/docs anterior a esta change.

## Open Questions

- Nenhuma decisão de produto pendente: núcleo puro é instrução explícita do usuário. A disponibilidade do host Docker e o suporte de Konsist às referências dos fixtures são verificações operacionais/técnicas para a implementação, não motivos para inventar exceções.
