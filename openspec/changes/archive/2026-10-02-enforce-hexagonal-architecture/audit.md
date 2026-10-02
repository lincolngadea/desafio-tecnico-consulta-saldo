# Auditoria anterior à finalização do README

Data de referência: 2026-10-01, fuso America/Bahia. Escopo: instrução do usuário, README atual, `.challenge/enunciado.md`, código, build e trabalho iniciado pelo Claude. Este documento registra observações; não é um relatório de implementação ou de revisão independente.

## Continuidade do trabalho do Claude

- `add-architecture-docs` está ativa, com 32/35 tarefas marcadas. Proposal, design, spec e tarefas estão completos; README e três arquivos de teste de documentação já foram escritos.
- Foram preservados os arquivos existentes. As três tarefas de entrega continuam abertas. Não se presume que a revisão marcada nas tarefas esteja registrada: não há `review.md` nessa change; a documentação deve recuperar ou refazer e registrar as evidências antes do commit.
- `docs/starter-kit-architecture-map.md`, seção 2.3, já apontava os três problemas citados pelo usuário: adapter/application, Spring no núcleo e escopo limitado ao `hello`. Parte deles foi tratada em changes anteriores, como indica a matriz abaixo.
- O design de documentação já identificava que as entradas externas não são declaradas no Gradle. Essa pendência foi retomada nesta change, sem recomeçar a reescrita do README.

## Matriz de critérios e estado real

| Critério/fonte | Estado | Evidência atual | Tratamento |
|---|---|---|---|
| Adapter acessa caso de uso por port, sem importar `application` | Código atende; guard ausente | Nenhum import de `application` nos adapters de `src/main`; `HexagonalArchitectureTest.kt:28` não restringe o adapter | Nova change: proibir a dependência e provar que a regra detecta uma violação |
| `port` livre de Spring/frameworks | Atendido para o código atual; guard parcial | Nove arquivos de port sem imports externos ao projeto/JDK/Kotlin; teste cobre cinco namespaces | Nova change: proteger toda a fronteira, inclusive referências qualificadas e outros frameworks presentes na stack |
| `application` livre de Spring/frameworks | Pendente | `@Service` em `GreetingService`, `SaveGreetingTemplateService`, `GetBalanceService` e `ProcessTransactionService` | Nova change: retirar as anotações e compor serviços fora do núcleo |
| `domain` sem frameworks | Atendido no código atual | Quinze arquivos sem imports externos ao projeto/JDK/Kotlin; testes de arquitetura verdes | Preservar e ampliar as provas negativas |
| Ports não dependem de application/adapters; application não depende de adapters | Tratado | Regras existentes no teste de camadas; nenhum import contrário identificado | Preservar as regras e verificar regressões |
| Outros bounded contexts além de `hello` | Tratado para `balance`; cadastro futuro manual | Regra de camadas no pacote raiz e lista `hello`, `balance` em `boundedContexts()` | Nova change: descobrir contextos automaticamente e testar contexto adicional por fixture, sem criar funcionalidade fictícia |
| Infraestrutura usada por balance sem depender do exemplo hello | Parcial | Produção de balance não importa hello, mas ambos os clientes DynamoDB e suas propriedades são registrados dentro de hello; risco já documentado no design de repository D7 | Nova change: configuração técnica neutra, sem duplicar clientes; provar composição de balance sem carregar hello |
| API global de erros e limites de contexto | Comportamento já tratado | `ApiExceptionHandler` está em balance, lida com exceções de balance e tem advice global | Preservar; mover o advice inteiro para um pacote comum não é necessário para tirar a dependência de hello |
| Arquitetura desenhada/descritiva corresponde ao código | Pendente | README:64 diz que application não conhece framework; os quatro services contradizem isso. `project.md:133` ainda desenha adapter → application | Nova change corrige código/regras; documentação reconcilia texto e diagramas depois |
| Teste arquitetural detecta uma regra nova violada | Sem evidência suficiente | Os cinco testes existentes passam sem testar Spring em application ou adapter → application | Nova change: fixtures válidas e inválidas, incluindo aliases/referências qualificadas |
| Gradle invalida testes que leem arquivos externos | Pendente confirmado | Inspeção efetiva de `test.inputs.files` não inclui README, Makefile, Dockerfile, `.dockerignore`, compose, script de tópicos, diretório de changes ou fontes de integrationTest | Nova change: declarar conjuntos de entradas, incluindo a descoberta de novos arquivos |
| `make test` executa os mesmos testes do host com todos os arquivos disponíveis | Pendência identificada por análise estática; execução Docker não realizada | Dockerfile copia build/gradle/src, mas os testes leem README, Makefile, Dockerfile, compose, infra, links em http e OpenSpec; `.dockerignore` exclui openspec e http | Nova change: incluir entradas no estágio test e validar build real, sem desativar testes |
| API do enunciado: rota, UUID, cinco campos e formatos | Tratado | Testes de parser, controller e mapper verdes; evidência HTTP real no relatório arquivado de add-balance-query-api | Reexecutar após alterar a composição e depois na entrega da documentação |
| Duplicata, fora de ordem, concorrência, DECLINED | Tratado | Testes de domínio/repositório/ingestão verdes; 61 resultados de integração anteriores disponíveis | Reexecutar integração real após a mudança; não substituir por leitura de XML antigo |
| Dependência indisponível: retry, circuito, pausa, DLT e erro HTTP | Tratado com limites documentados | Specs e testes verdes; 503 real com Retry-After registrado na change da API; orçamento de DNS tem ressalva própria | Preservar e revalidar. Não prometer latência além do que o SDK/DNS garante |
| Production readiness: logs, métricas, probes e runtime | Tratado; parte da infraestrutura ainda não commitada | Change add-observability arquivada, testes e evidências presentes, Docker/compose modificados no workspace | Reexecutar smoke test; separar mudanças prévias de infraestrutura do escopo desta implementação |
| Pré-requisitos, comandos, variáveis, ADRs e links do README | Conteúdo e testes já tratados; execução ainda precisa acompanhar a nova mudança | 34 cenários de ReadmeTest passaram na execução atual; tarefas operacionais do Claude marcadas | Preservar texto; revalidar comandos finais e registrar evidências próprias depois da nova change |
| Histórico, contagem de capacidades e nomes de changes no README | Ajuste final pendente | README fixa onze capacidades; `ReadmeTest.kt:79` reconhece nomes apenas iniciados por `add-`; nova change usa `enforce-` | Em add-architecture-docs: reconciliar contagem/listas, aceitar nomes válidos sem limitar o verbo e evitar informação volátil hardcoded |
| Regra de commit e mistura de mudanças | Ajuste editorial/entrega pendente | README cita um commit por change seguido de commit de arquivo; artefatos de outras changes e infra permanecem pendentes | Em add-architecture-docs: tornar texto coerente com a prática autorizada e separar stage/commits por escopo; não exigir commit de infra como condição para planejar |
| Melhorias futuras do README | Documentadas; não são todas requisitos de implementação | Enunciado permite documentar patterns/algoritmos não implementados com motivador; tabela já faz isso | Reavaliar cada linha após a mudança, retirar configuração compartilhada da lista se resolvida; manter cache, auth, load test, histórico, OTLP e demais evoluções fora do escopo |
| Publicação pública e envio da conclusão | Não conferido nesta auditoria | Critérios de entrega externos ao código | Checklist final de entrega; não enviar mensagens nem alterar visibilidade nesta change |

## Verificações executadas nesta auditoria

- `JAVA_HOME=<JDK 21> ./gradlew check --rerun-tasks`: BUILD SUCCESSFUL, 7 tarefas executadas, 343 testes, zero falhas/erros/ignorados; cobertura de instruções 95,19%, gate de 90% aprovado. Log local: `/tmp/final-architecture-baseline-check.log`.
- Inventário de imports: 15 arquivos de domain e 9 de port sem dependências externas ao projeto/JDK/Kotlin; 4 arquivos de application dependem de `org.springframework.stereotype.Service`; adapters sem import de application. O inventário não substitui os futuros testes de referências totalmente qualificadas.
- Inspeção das entradas declaradas na task `test` via init script temporário externo ao repositório: os oito caminhos citados na matriz não constam como entradas próprias. Nenhum arquivo de build foi alterado para fazer essa inspeção.
- Leitura dos 61 XMLs de integração existentes: sem falhas, mas **não reexecutados nesta auditoria**.
- Tentativa de usar o host Docker existente: SSH recusou conexão antes da sincronização. Não houve execução de `make test`, `make up`, integração ou curls novos por essa via. O problema de arquivos ausentes no estágio test é uma conclusão da leitura do Dockerfile e dos testes, não um build Docker executado.

## Sequência para fechar as duas changes

1. Implementar `enforce-hexagonal-architecture` a partir de suas specs/tasks, com TDD, evidência negativa das regras e regressão funcional.
2. Validar host, Docker e integrações reais. Recuperar acesso à infraestrutura antes de dar essas validações como concluídas.
3. Revisar e commitar apenas o escopo da nova change; sincronizar e arquivar suas specs quando concluída. Coordenar a lista de changes no README ainda aberto para que ReadmeTest permaneça válido antes/depois do arquivo.
4. Retomar `add-architecture-docs`: ajustes finais de README/testes, tabela de conformidade atualizada, revisão independente após as mudanças, comentários apresentados ao usuário, commit e arquivo. A aprovação anterior do texto não cobre alterações futuras.

Nenhuma implementação, commit ou arquivamento é realizado por este planejamento. A nova regra de application puro vem do pedido do usuário e substitui a exceção histórica de `@Service`; o enunciado exige qualidade/aderência hexagonal, mas não contém essa proibição literal.

## Conferência após a implementação (2026-10-02)

A matriz acima preserva a observação anterior à mudança. As pendências de código foram tratadas: quatro services puros e composition root externo; adapters sem application/configuração; descoberta automática; configuração DynamoDB neutra; inputs Gradle; cópia seletiva Docker. As fixtures negativas passaram, incluindo DSLs qualificadas sem nome de tipo e controles positivos de comentários/strings/receivers e tipos do próprio núcleo.

A infraestrutura tornou-se acessível: make test e build test sem cache executados com gate aprovado; make integration-test real com 61 casos, zero falhas; make up recompilou a aplicação, tópico/50 eventos e smoke 200/400/404/503, duplicata/antigo/DECLINED, OpenAPI/Swagger servido, métricas/probes realizados. Evidências completas em review.md e verification/. O relatório distingue workspace completo e snapshot destinado ao commit, bem como serviço Swagger versus nova conferência visual.

Permanecem editoriais em add-architecture-docs: reconciliar história/capacidades/nome enforce-..., remover o item futuro DynamoDB resolvido e a alegação inadequada do advice, explicar a composição externa/descoberta/inputs e regra de commits. Não foram realizados benchmark/DNS blackhole/publicação/envio; evoluções futuras continuam com motivadores. A documentação e a infraestrutura previamente excluída não foram commitadas nem arquivadas por esta execução.
