# Validação da implementação

Change: enforce-hexagonal-architecture. Execução iniciada em 2026-10-01, continuada em 2026-10-02 (America/Bahia). Este registro distingue validação do autor e revisão independente; a aprovação humana dos comentários permanece obrigatória antes do commit.

## Vermelho → verde

| Prova | Resultado |
|---|---|
| Fixtures arquiteturais iniciais | 24 casos: 21 falharam com política vazia; depois 24/24 passaram |
| Guard contra o código anterior | Falhou exatamente nos quatro imports @Service de hello/balance; passou após composição externa |
| Composição explícita | Quatro testes falharam antes dos beans/pacote neutro; passaram após a migração, com unicidade por port e circuitos primários independentes |
| Referências qualificadas a funções | DSL Spring e função de composição falharam no guard anterior; catálogo de pacotes reais + PSI passou, com provas positivas de receivers e tipos qualificados do próprio núcleo |
| Cache Gradle anterior | 12 mudanças externas ficaram UP-TO-DATE, assim como a repetição sem mudança |
| Cache Gradle corrigido | As mesmas 12 mudanças reexecutaram test; repetição sem mudança preserva UP-TO-DATE. Prova inicial com fixtures, repetição final com ReadmeTest na cópia temporária |
| Docker anterior | Build real executou 343 testes e falhou em 51 por arquivos ausentes (README/infra); não foi erro de rede/dependência |
| Docker corrigido | make test executou os testes e gate, sem serviços externos; build adicional usou --no-cache-filter test |

Logs locais: `/tmp/hexagonal-policy-fixtures-red.log`, `/tmp/hexagonal-final-red.log`, `/tmp/hexagonal-qualified-functions-red.log`, `/tmp/hexagonal-functions-final.log`, `/tmp/hexagonal-inputs-{red,green}.log`, `/tmp/hexagonal-docker-{red,green,no-cache}.log`. Resultados JSON pequenos estão em verification/.

## Conferência do enunciado

| Item do enunciado | Evidência concreta |
|---|---|
| O que construir → Ingestão (input via Kafka), payload literal | Publicado o JSON literal do enunciado no tópico; consultado o saldo 183.12 BRL da conta do exemplo |
| O que construir → Exposição (API REST), cinco campos/UUID/Number/ISO 8601 | Smoke 200 da conta isolada: id, owner, balance.amount/currency e updated_at; formato Brasília com milissegundos preserva decisão existente da API, sem usar o valor ilustrativo incompatível com o timestamp do payload |
| O que será avaliado → Tratamento de concorrência | Integração real contra DynamoDB/Redpanda: 61 testes, zero falhas/erros/ignorados; testes de gravações concorrentes, duplicata e fora de ordem. Smoke duplicado/antigo preservou snapshot e DECLINED mais novo atualizou para 211.45 |
| O que será avaliado → Resiliência | Testes de circuitos independentes no wiring, timeouts/retry preservados, integração de pausa/retomada/DLT. DynamoDB parado de verdade: HTTP 503 problem+json, Retry-After 5, no-store; serviço restaurado ao fim |
| O que será avaliado → Testes / Tratamento de cenários adversos | Smoke UUID inválido 400, conta inexistente 404, traceId/no-store e 503; 500 sanitizado e formatos/URLs de borda pelos testes de contrato BalanceControllerTest |
| O que será avaliado → Qualidade de código | Guard de produção e fixtures, descoberta automática de contextos parciais, alias/wildcard/FQN, nenhuma dependência Spring no núcleo; composição externa e infraestrutura neutra. É a regra mais estrita pedida pelo usuário, não uma proibição literal adicional atribuída ao enunciado |
| O que será avaliado → Modelagem de dados no DynamoDB | Contrato de repository inalterado; integração real preserva GetItem consistente e gravação condicional/versionamento |
| Como começar → Criando o tópico Kafka | Comando do kit exercitado; tópico/DLT idempotentes com seis partições. make kafka-topics-ingestion e make integration-test executados de novo |
| Como começar → Gerando eventos de teste | make kafka-produce-transactions-events TOPIC=transacoes-financeiras-processadas COUNT=50: Done. Published 50 event(s), com offsets reais |
| Como começar → Consulte o README do starter-kit | make up refez a imagem e restaurou a aplicação; make test real com arquivos presentes, cache externo provado, integração real com JDK 21 |
| O que será avaliado → Production readiness | GET probes liveness/readiness UP, Prometheus com os cinco grupos de métricas descritos, circuito de leitura/escrita separados; runtime inspecionado para presença exclusiva do jar da aplicação e ausência de material pessoal/editorial |

## Gates e limites

- Integração real: 61 testes, 0 falhas/erros/ignorados, `/tmp/hexagonal-integration.log`. Aplicação parada durante os testes para evitar competição de consumers; make up depois a restaurou.
- Smoke: verification/smoke-results.json e `/tmp/hexagonal-smoke.log`, incluindo restauração de DynamoDB com exit 0. Nenhuma tabela/tópico foi apagada.
- OpenAPI respondeu 200 com 200/400/404/503/500 para a rota; Swagger HTML respondeu 200. Isto valida serviço/contrato e não é nova conferência visual da interface. A conferência visual já aceita pelo usuário na change da API é preservada.
- O gerador aleatório cria contas distintas, portanto não prova ordenação/concorrência na mesma conta: integração e eventos manuais cobrem isso. Não foi realizado benchmark, dimensionamento produtivo ou teste de DNS blackhole; os limites anteriores permanecem documentados.
- Visibilidade pública do repositório e envio de email estão fora do escopo; não foram executados.
- Check final do workspace: 374 testes, 0 falhas/erros/ignorados, cobertura 95,3%; `/tmp/hexagonal-final-host-check.log`.
- Snapshot selecionado partindo de HEAD: 323 testes, 0 falhas/erros/ignorados, cobertura 95,3%; `/tmp/hexagonal-snapshot-check.log`. A diferença de 51 testes é exatamente README (34) e infra anterior (17), preservados no workspace e fora do commit.
- O build Docker sem cache anterior passou com 370 testes e 95,3%. Após reforçar o guard para funções qualificadas e adicionar as quatro fixtures finais, a sincronização/build final falhou antes de acessar Docker: SSH Operation timed out. A árvore atual tem 374 testes; não se transporta o resultado anterior como resultado do último guard.
- Permanecem pendentes o Docker da árvore final, a prova de invalidação por documento numa cópia e a inspeção final test/runtime. Nenhum desses gates será marcado aprovado sem execução. O usuário foi informado e recebeu pedido de verificar a disponibilidade do host.

## Revisão independente

Rodada 1 executada por hexagonal_review com contexto limpo e somente leitura. Checks próprios: snapshot 323 e workspace 374 testes, ambos 95,3%; YAML/OpenSpec válido. Nenhum defeito funcional identificado. Três achados aceitos e corrigidos:

1. Bloqueante Art. 6: entrada de classe Read citava faixa. Agora cita declaração; invariantes têm entrada init separada com linhas reais.
2. Bloqueante Art. 6: referência principal de UseCaseCompositionTest combinava Qualidade de código/Resiliência. Agora tem item principal único e uma entrada específica para os circuitos.
3. Ajuste Art. 8: proposta dizia que dependências do build não mudavam. Agora distingue dependências de produção da declaração explícita do parser já transitivo no compile de teste.

Nenhum achado foi rejeitado. Além disso, a prova de composição isolada registra os adapters DynamoDB de produção, sem os fakes de portas ou componentes hello. Rodada 2 concluída: código e artefatos aprovados, sem bloqueantes/ajustes pendentes. O check próprio do snapshot atualizado passou com 323 testes e 95,3%; os quatro testes de composição passaram. Check do autor após as correções: 374 testes e 95,3%, `/tmp/hexagonal-round-two-gate.log`.

O revisor confirmou novamente o timeout SSH e não aprovou a parte operacional: gates Docker finais e repetição independente dos comportamentos reais permanecem pendentes. Reservar a terceira e última rodada para essas verificações quando o host estiver acessível. Nenhum achado foi rejeitado e nenhum arquivo foi editado pelo revisor.

## Entrega

Escopo aprovado entregue em Conventional Commits com specs sincronizadas e pasta arquivada. Gates Docker, revisão independente e aval dos comentários concluídos; add-architecture-docs continua aberta para sua reconciliação editorial e revisão final próprias.

## Nova tentativa de retomada — 2026-10-02

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew check`: BUILD SUCCESSFUL; test executada novamente, 374 testes, zero falhas/erros/ignorados, gate de instruções 95,3%. Log: `/tmp/hexagonal-resume-check.log`. Compile reaproveitado; não foi usado `--rerun-tasks`.
- `openspec validate enforce-hexagonal-architecture --strict` e `git diff --check`: aprovados.
- `.local/remote.sh "python --version"`: SSH para `192.168.15.200:22` terminou com `Operation timed out` (exit 255), tanto no sandbox quanto fora dele. Nenhum comando chegou ao Docker e nenhum comportamento remoto foi reexecutado nesta retomada.
- Não houve alterações de código, stage, commit ou archive. Gates 5.3/5.4 e revisão operacional 7.1 continuam pendentes; a terceira rodada permanece reservada à recuperação do host. A aprovação humana dos comentários ainda não foi solicitada nesta retomada, pois os gates necessários antes da entrega permanecem bloqueados.

## SSH recuperado, daemon pendente — 2026-10-02

O usuário informou a recuperação do SSH; a nova conexão executou comandos remotos. O erro mudou: o daemon Docker está indisponível, com pipe `docker_engine` inicialmente ausente e, após `docker desktop start`, pipe `dockerDesktopLinuxEngine` ausente. O start respondeu exit 0, mas `docker desktop status` respondeu exit 1 e o WSL listou `docker-desktop` como Stopped. `make test` e `compose ps` falharam antes de acessar o engine; nenhum teste Docker foi executado nesta tentativa.

Foi sincronizada a árvore sem material pessoal/credenciais e preparada uma pasta remota isolada, registrada em verification/docker-resume.json. As duas cópias locais da prova de cache têm 253 arquivos idênticos, exceto uma quebra de linha adicional no README da cópia document; o README do workspace permanece preservado. Upload: `/tmp/hexagonal-resume-upload.log`; tentativa make: `/tmp/hexagonal-docker-resume-make.log`. Para retomar, abrir Docker Desktop na sessão Windows e aguardar o engine. Gates 5.3/5.4 e rodada operacional continuam pendentes; não houve stage/commit/archive.

## Docker final recuperado — 2026-10-02

Tarefas 5.3 e 5.4 concluídas. O daemon voltou a responder; compose ps não listou serviços em execução antes do build. `make test`, com o executável Docker direcionado ao host remoto e contexto isolado, passou com 374 testes, zero falhas/erros/ignorados e gate 95,3%. Reconstrução com `--no-cache-filter test` reexecutou o check completo e passou com os mesmos resultados. As contagens foram conferidas nos XMLs de cada imagem, sem somar novamente os resumos dos testes aninhados.

Na cópia isolada, uma única quebra de linha adicional no README invalidou o COPY de documentos e o RUN do check, sem opção de forçar cache. Os cinco passos de construção da base (WORKDIR, cópias de build/Gradle/fontes e chmod) permaneceram CACHED. O novo check executou 374 testes e gate 95,3%. Comparação das duas tarballs: 253 arquivos e diferença exclusiva em README.md; fonte/build/regras da tarball original conferem com os 156 arquivos atuais correspondentes do workspace. O README do usuário não foi alterado.

As imagens test foram inspecionadas por containers temporários: inputs exigidos presentes e material pessoal ausente. O runtime foi construído e inspecionado: único arquivo de aplicação `/app/app.jar`, uid 10001, sem documentos, OpenSpec, fontes, testes ou diretórios pessoais em /app, /workspace ou raiz. Essas propriedades de usuário/runtime são do workspace preservado e não incorporam ao commit as alterações prévias de infraestrutura excluídas. Comandos, imagens e logs estão em verification/docker-final.json. O check local desta retomada passou com 374 testes e 95,3% (`/tmp/hexagonal-final-resume-host.log`). Revisão operacional independente e aval humano dos comentários ainda pendentes; sem commit/archive.

## Revisão independente concluída — rodada 3

Parecer completo preservado em independent-review-round-3.md, escrito pelo revisor com contexto limpo, sem alterações no workspace. Aprovado sem bloqueantes/ajustes/sugestões. Execuções próprias: snapshot 323 testes; make test e no-cache 374; integração real 61; todos sem falhas/erros/ignorados, gate 95,3%; 14 provas de inputs e smoke real HTTP/Kafka/resiliência aprovados. O revisor restaurou serviços e confirmou app healthy. Tarefas 5.3/5.4/7.1 concluídas.

Total de revisão: 3 rodadas. As correções da primeira rodada foram os dois cabeçalhos e a precisão da proposta sobre a declaração do parser já transitivo. Rodada 2 aprovou código/artefatos e manteve os gates operacionais pendentes; rodada 3 concluiu esses gates. Nenhum achado rejeitado. project.md/config.yaml não mudaram após a aprovação de código. Permanecem o aval humano dos 25 cabeçalhos selecionados, commit/sync/archive e liberação de docs; nenhuma tarefa de documentação foi concluída por associação.

## Aval humano e entrega

O usuário aprovou os 25 cabeçalhos apresentados em comment-review.md em 2026-10-02 (“pode confirmar”), autorizando concluir commit e arquivo. Stage conferido antes do aval: 45 arquivos, sem mudanças prévias de Docker/docs incluídas; código e cabeçalhos são os revisados. Somente operações de entrega e coordenação editorial permanecem.

## Coordenação editorial do arquivo

Specs sincronizadas por adição de hexagonal-architecture (6 requisitos) e repository-verification (2), sem editar as specs anteriores de observabilidade. A pasta foi movida para archive/2026-10-02-enforce-hexagonal-architecture. Revalidação antes do ajuste editorial: ReadmeTest com 34 casos, 33 aprovados e uma falha exatamente no inventário de changes arquivadas (`/tmp/hexagonal-archive-docs-red.log`). Ajustes mínimos no workspace: histórico inclui enforce-hexagonal-architecture, contagem volátil removida e CHANGE_NAME reconhece nomes em kebab-case entre backticks sem lista de verbos. README e ReadmeTest permanecem em add-architecture-docs e fora deste commit; comentários desse teste terão seu próprio aval na entrega de docs.

## Gates após o arquivo

O ajuste mínimo de histórico ficou verde no check completo: 374 testes, zero falhas/erros/ignorados, gate 95,3% (`/tmp/hexagonal-archive-check.log`). O snapshot exato extraído do stage, sem docs/infra excluídos, também passou: 323 testes, zero falhas/erros/ignorados, gate 95,3% (`/tmp/hexagonal-after-archive-snapshot-check.log`). As 13 specs do workspace passaram na validação estrita; os 8 requisitos adicionados nas duas specs principais são textualmente idênticos aos deltas revisados. Após o aval não houve alteração de código/build/contexto no escopo de arquitetura, nem dos 25 cabeçalhos aprovados.

## Entrega concluída

Commit de arquitetura registrado com o título `refactor(architecture): imponha núcleo puro e verificação reproduzível`; o registro foi consolidado para manter um único commit desta change. Tarefas 7.4/7.5 concluídas após o registro, incluindo liberação da dependência de docs. Nenhuma mudança de código/build/contexto ou cabeçalho foi feita após o aval. Os últimos ajustes deste relatório e do checklist registram somente a entrega. `preservation.json` registra a conferência anterior à coordenação editorial; as edições posteriores de README/ReadmeTest estão descritas acima, pertencem a docs e continuam fora do commit.
