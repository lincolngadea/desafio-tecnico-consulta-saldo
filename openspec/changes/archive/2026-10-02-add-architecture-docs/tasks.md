## 1. Teste do README (TDD, vermelho primeiro)

- [x] 1.1 Criar `ReadmeTest` em `src/test/kotlin/br/com/itau/challenge/documentation/`, no padrão de `observability/ComposeFileTest.kt` (lê `README.md`, `Makefile`, `application.yaml` e `docker-compose.yml` do diretório de trabalho), com o cabeçalho do Art. 6 (`Spec:` e `Enunciado:`). Teste vermelho para o requisito "Seções obrigatórias do README": "Todas as seções existem", "As seções seguem a ordem do pedido" e "Instruções ao candidato removidas"
- [x] 1.2 Teste vermelho para "Visão da solução com fluxo e arquitetura hexagonal": "Dois diagramas Mermaid", "Diagramas só citam componentes reais" (cada nome de classe dos diagramas existe como tipo em `src/main/kotlin`) e "O parágrafo cita as três pontas do serviço"
- [x] 1.3 Teste vermelho para "Como rodar leva do zero à primeira consulta": "Passos na ordem", "Os alvos make citados existem", "A consulta documenta os cinco status", "A limitação do gerador está dita" e "Sem referência ao setup pessoal"
- [x] 1.4 Teste vermelho para "Decisões registradas como ADR curto": "Sete ADRs", "Cada ADR tem os três campos", "Cada ADR aponta para a decisão de origem" (a change citada existe em `openspec/changes/archive/`), "O ADR de escrita condicional traz a condição", "O ADR de DLQ vs pausa separa os dois destinos" e "Ambiguidades do enunciado reportadas"
- [x] 1.5 Teste vermelho para "Estratégia de testes com pirâmide e cobertura": "Pirâmide em dois níveis com o comando de cada um", "Cenários do enunciado mapeados para testes", "Classes de teste citadas existem" e "Cobertura documentada"
- [x] 1.6 Teste vermelho para "Resiliência e observabilidade com as métricas a olhar": "As cinco métricas citadas", "A ressalva do lag está dita" e "Readiness sem dependência externa explicada". "Os nomes de métrica são os do código" fica para a execução da tarefa 3.5, porque depende do serviço no ar
- [x] 1.7 Teste vermelho para "O que eu faria com mais tempo, com motivador": "Todo item tem motivador" e "Os itens mínimos estão presentes". "Nenhum item inventado" fica para a revisão independente (tarefa 5), porque compara prosa com os `design.md`
- [x] 1.8 Teste vermelho para "Como o repositório foi construído": "Pastas do openspec explicadas", "Changes arquivadas listadas" (lista do README igual às pastas de `openspec/changes/archive/`), "Links locais existem" e "O enunciado é citado e não linkado"
- [x] 1.9 Teste vermelho para "Referências do README existem no repositório": "Variáveis documentadas existem", "Variáveis do cliente de escrita documentadas", "Alvos do Makefile documentados existem" e "Alvos restritos ao hello sinalizados"
- [x] 1.10 Rodar `./gradlew test --tests '*ReadmeTest'` e confirmar que os testes falham contra o README atual, cada um pelo motivo esperado e não por erro do próprio teste

## 2. Reescrita do README (até o teste ficar verde)

- [x] 2.1 Levantar nos `design.md` arquivados e nas specs a fonte de cada fato antes de escrever (D1). Fato sem fonte, conferir no código, no `Makefile` ou no `docker-compose.yml`. Fato que nenhuma fonte sustente não entra e vira lacuna na revisão
- [x] 2.2 Remover do README o bloco "Instruções para o candidato", as camadas, o fluxo e a estrutura de pastas só do `hello` e as marcas de CI com URL relativa quebrada. Manter o aviso sobre os comentários de cabeçalho, a tabela de imagens Docker e a nota curta sobre o `hello`. Tarefas 1.1 e 1.3 (instruções removidas) passam a verde
- [x] 2.3 Escrever `Visão da solução`: um parágrafo e os dois diagramas Mermaid (fluxo de ingestão e de consulta, e arquitetura hexagonal com a regra `adapter → port ← application → domain`), com nomes de nó tirados do código. Tarefa 1.2 verde
- [x] 2.4 Escrever `Como rodar`: pré-requisitos, subir o ambiente, criar o tópico (`make kafka-topic-create NAME=transacoes-financeiras-processadas` e a nota de que o seed já cria o tópico e a `.DLT` com 6 partições), gerar eventos (`make kafka-produce-transactions-events`, com a limitação da conta aleatória e como publicar duplicado e fora de ordem no Redpanda Console com o payload de exemplo do enunciado), `curl` em `/balances/{accountId}`, tabela de status e URLs (API, Swagger, management, Console, DynamoDB admin). Tarefa 1.3 verde
- [x] 2.5 Escrever os sete ADRs (`ADR-1` a `ADR-7`) na ordem do design D2, cada um curto, com **Contexto**, **Decisão**, **Consequência** e **Detalhe** (`<change> design Dn`), mais a tabela das ambiguidades do enunciado (D6). Usar `DuplicateIgnored` e `StaleIgnored` como em OBS design D6. Tarefa 1.4 verde
- [x] 2.6 Escrever `Estratégia de testes`: pirâmide, tabela cenário do enunciado, classe e nível, gate de 90% do JaCoCo e o caminho do relatório. Citar só classes que existem. Tarefa 1.5 verde
- [x] 2.7 Escrever `Resiliência e observabilidade`: o que existe e a tabela das cinco métricas (nome, etiqueta, o que o valor indica, ressalva do lag parado). Tarefa 1.6 verde
- [x] 2.8 Escrever `O que eu faria com mais tempo` como tabela **Item** e **Motivador**, só com itens declarados nos `design.md` ou nas specs, incluindo a limitação do gerador de eventos (D8). Tarefa 1.7 verde
- [x] 2.9 Escrever `Como o repositório foi construído`: SDD com OpenSpec, papel de cada pasta de `openspec/`, ciclo de seis passos, as cinco changes arquivadas, `CLAUDE.md` como constituição, revisão independente e um commit por change. Citar o `enunciado.md` por seção, sem link. Tarefa 1.8 verde
- [x] 2.10 Corrigir a tabela de variáveis (incluir `BALANCE_TABLE_NAME` e os `DYNAMODB_*` do cliente de escrita) e a tabela de comandos (sinalizar `make db-scan` e `make http` como só do `hello`). Tarefa 1.9 verde
- [x] 2.11 Rodar `./gradlew test --tests '*ReadmeTest'` até todos os testes ficarem verdes, sem afrouxar nenhum teste para passar

## 3. Validação contra o enunciado (Art. 11)

- [x] 3.1 Percorrer *O que construir*, *Como começar* e *O que será avaliado* do `.challenge/enunciado.md` e montar a tabela item do enunciado, evidência (seção do README, teste ou comando executado). Item que o README não cobre vai como fora de escopo, com a change prevista
- [x] 3.2 Executar no host Docker do autor: subir o ambiente (`make up`), criar o tópico, gerar eventos, e conferir que o que o README diz sobre cada passo é o que acontece (tópico e `.DLT` com 6 partições, serviço `healthy`)
- [x] 3.3 Executar `curl` em `/balances/{accountId}` para uma conta com saldo (200), uma conta inexistente (404) e um id inválido (400), com o evento de exemplo do enunciado publicado à mão, e conferir corpo, status e `Cache-Control: no-store` contra o README
- [x] 3.4 Publicar um evento duplicado e um fora de ordem pelo procedimento descrito no README e conferir que o `balance_transactions_processed_total` registra `duplicate` e `stale_ignored`
- [x] 3.5 Abrir `/actuator/prometheus` na porta de management e conferir que cada métrica citada na seção de observabilidade existe com o mesmo nome (cenário "Os nomes de métrica são os do código")
- [x] 3.6 Se algum passo documentado não funcionar como escrito, corrigir o README e reexecutar. Se a causa for uma divergência entre `design.md`, spec e enunciado, **parar e perguntar** (Art. 11)

## 4. Verificação

- [x] 4.1 `./gradlew check` verde, com o gate de cobertura de 90% e o `HexagonalArchitectureTest` intactos, e nenhum arquivo de `src/main` alterado
- [x] 4.2 Cabeçalho do Art. 6 no `ReadmeTest`: um comentário `/* ... */` antes do `package`, em pt-BR, com uma entrada por trecho (linhas, símbolo e porquê), `Spec:` com os requisitos da spec e `Enunciado:` na última linha. Sem comentário no corpo. Conferir as linhas contra o arquivo final

## 5. Revisão independente

- [x] 5.1 Revisão por agente independente, conforme o `CLAUDE.md` (máximo 3 rodadas): pré-condição `./gradlew check` verde; subagente com contexto limpo (Agent, nunca `fork`), somente leitura; entregar o nome da change, os caminhos de `proposal.md`, `design.md`, `specs/` e `tasks.md`, o diff, `CLAUDE.md` e `.challenge/enunciado.md`, sem conclusões do autor. O revisor refaz por conta própria a conferência de que cada fato do README tem fonte, de que nenhum item de "O que eu faria com mais tempo" é inventado e a validação do Art. 11
- [x] 5.2 Corrigir todos os bloqueantes e ajustes, ou rejeitar com justificativa técnica, reexecutar `./gradlew check` e submeter o novo diff ao mesmo revisor (`SendMessage`), até uma rodada sem bloqueantes nem ajustes. Registrar rodadas, correções e rejeições

## 6. Contexto do projeto

- [x] 6.1 Revisar `openspec/project.md` e o `context:` do `config.yaml` (Regra 2 do `CLAUDE.md`). Atualizar só se um fato durável mudou (por exemplo, um comando ou variável corrigido, ou a lacuna do gerador de eventos), pelos critérios da Regra 3, e sempre os dois juntos, no mesmo commit. Validar o YAML com `openspec list --json`
- [x] 6.2 Concluir a change dependência `enforce-hexagonal-architecture` (código, guards, build, verificações reais, revisão, commit e arquivo) antes de retomar a entrega desta change; seguir a auditoria e `follow-up.md`, preservando as tarefas já realizadas
- [x] 6.3 Após a dependência concluída, reconciliar README e ReadmeTest com a arquitetura/composição realmente entregue, descoberta de contextos, infraestrutura neutra, inputs e Docker; ajustar histórico/contagem, nomes de changes além de add-*, regra de commits, itens futuros resolvidos e limites documentados, conforme `follow-up.md`
- [x] 6.4 Reexecutar os gates e comandos afetados da seção 3 no estado final, incluindo make test real e integração real; persistir em review.md a tabela item do enunciado → evidência, diferenciando execução nova de cache/resultados antigos
- [x] 6.5 Recuperar ou refazer o registro da revisão do Claude e submeter o diff final atualizado a revisão independente, seguindo o procedimento da seção 5; registrar rodadas, correções/rejeições e conferir contexto/config em sincronia depois da dependência

## 7. Entrega

- [x] 7.1 Separar os arquivos/hunks desta change dos trabalhos pendentes de outras changes e de infra, respeitando as exclusões de commit já autorizadas pelo usuário; validar o conteúdo exato do stage sem exigir que mudanças externas sejam commitadas para permitir a entrega de docs
- [x] 7.2 Apresentar ao usuário os comentários novos do `ReadmeTest` (`arquivo:linha` e texto) e aguardar o aval antes do commit
- [x] 7.3 Commit único em Conventional Commits, `docs(readme): ...` em pt-BR, e depois `archive` da change

## Retomada liberada — 2026-10-02

Dependência enforce-hexagonal-architecture commitada, sincronizada e arquivada em ../2026-10-02-enforce-hexagonal-architecture. Os ajustes mínimos para o arquivo (histórico, contagem volátil e reconhecimento de nomes) foram testados no check completo, com 374 casos e gate 95,3%, e pertencem a esta change de docs. Retomada liberada: seguir 6.3, 6.4, 6.5 e a seção 7; não presumir que a revisão da implementação cobre o novo diff de documentação. Nenhuma dessas etapas finais foi marcada concluída.

## Entrega concluída — 2026-10-02

39/39 tarefas concluídas. Cabeçalhos aprovados pelo usuário; revisão independente em duas rodadas sem pendências. Spec readme-documentation sincronizada (9 requisitos, 36 cenários). Commit docs(readme) criado; arquivo nesta pasta e registros finais consolidados no mesmo commit da change. Infraestrutura preexistente permanece fora do escopo.
