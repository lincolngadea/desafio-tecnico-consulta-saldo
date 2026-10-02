# Validação final da documentação — 2026-10-02

A revisão antiga do Claude estava indicada em tasks.md, mas nenhum relatório recuperável foi encontrado nesta change. Não é usada como aprovação deste diff. A retomada refaz a revisão independente com contexto limpo, somente leitura, conforme CLAUDE.md.

## Enunciado → evidência

| Item do enunciado | Evidência na documentação e verificação |
|-|-|
| O que construir → Ingestão (input via Kafka), payload literal | README, Fluxo e Como rodar; smoke publica o exemplo literal, duplicata e versão antiga, consulta snapshot e contadores; integração real de ingestão e persistência |
| O que construir → Exposição (API REST), request e contrato de resposta | README, Como rodar; smoke confere UUID, id/owner/balance/updated_at, 200/400/404, no-store e problem+json; integração ponta a ponta |
| Pense além do happy path → mesma mensagem, dados inválidos, dependência fora | ADR-3/4/5/6/7, tabela HTTP; integração de DLT/duplicata/ordem/retry/pausa; smoke com DynamoDB pausado para 503/Retry-After |
| Como começar → Pré-requisito e Consulte o README do starter-kit | Pré-requisitos Docker/Compose/make/WSL2/JDK21, Stack, comandos, cobertura e testes estáticos do ReadmeTest |
| Como começar → Criando o tópico Kafka e Gerando eventos de teste | make up, criação idempotente/partições, make kafka-produce-transactions-events COUNT=50; ressalva da conta aleatória e publicação manual |
| O que será avaliado → Modelagem de dados no DynamoDB | ADR-1/2/3, snapshot por conta, PK/sem SK/GSI; testes DynamoDB reais |
| O que será avaliado → Tratamento de concorrência | ADR-3/4; integração com 32 escritas e matriz de comparação de SnapshotVersion |
| O que será avaliado → Resiliência | ADR-5/6, circuitos/timeout/retry/pausa; integração e smoke de indisponibilidade |
| O que será avaliado → Testes | Pirâmide, cenário/classe, check e integração; cobertura ≥90% |
| O que será avaliado → Qualidade de código | Diagrama hexagonal, composição externa, infraestrutura neutra, descoberta automática e exemplos negativos do guard; arquitetura no check |
| O que será avaliado → Tratamento de cenários adversos | Tabela de 200/400/404/503/500; testes HTTP, contratos de erro e no-store; 500 exercitado por teste, sem injetar falha inesperada no serviço real |
| O que será avaliado → Production readiness | Resiliência/observabilidade; probes, cinco famílias de métricas, tracing/log JSON, imagem; limitações honestas de DNS, lag, retenção e ambiente local |
| O que será avaliado → Pattern/algoritmo não implementado com motivador | Tabela de melhorias futuras e origens conferidas pelo revisor independente |
| Como entregar → Kotlin, repositório público e não incluir o enunciado | Kotlin no repositório; testes proíbem link ao enunciado; stage exclui .challenge. Verificação de acesso público em guia anônima e envio de email são ações externas fora desta change, sem execução ou alegação de conclusão |

## Gates da retomada

Resultados da dependência arquivada são históricos e não substituem esta execução. Logs da retomada têm prefixo /tmp/architecture-docs-. O Docker usa snapshot próprio itau-docs-final-20261002, sem arquivos privados, e override local para o host remoto; o README contém somente o caminho padrão do projeto.

| Gate/comando | Resultado desta retomada | Execução/cache |
|-|-|-|
| JDK21 ./gradlew check | 374 casos, zero falha/erro/skip, cobertura 95,3% | test executou; compilação/resources sem alteração puderam ser UP-TO-DATE |
| make test (Docker remoto, snapshot próprio) | 374 casos e gate 95,3% | RUN check executou, não CACHED; base reutilizada onde os inputs não mudaram |
| make up | app e seeds no snapshot atual, app restaurada após integração | build de runtime e seeds executados; camadas base válidas reaproveitadas |
| make integration-test | 61 casos, zero falha/erro/skip | integrationTest executou contra DynamoDB Local/Redpanda reais; app parada para evitar disputa dos consumers e restaurada ao final |
| Criação de tópico e 50 eventos | tópico existente respondeu TOPIC_ALREADY_EXISTS; 50 publicados e 50 applied | comandos reais; sucesso esperado de operação idempotente explicado no README |
| Payload literal e resposta | saldo 183,12 BRL; updated_at derivado do evento: 2025-07-04T12:02:44.589-03:00 | publicação e HTTP reais |
| Duplicata e fora de ordem | saldo preservado; duplicate +1 e stale_ignored +1 | publicação manual via rpk; não é evidência de clique no Console |
| Falha DynamoDB | 503, Retry-After 5, no-store, problem+json; probes UP; ~0,66 s neste ensaio | container pausado e despausado; snapshot preservado, recuperação confirmada |
| OpenAPI, Swagger e métricas | cinco status e UUID no schema; HTML/JS servidos; cinco famílias e probes em 8082 | consultas HTTP reais; sem avaliação visual |
| openspec validate add-architecture-docs --strict | válida | executado nesta retomada |

Detalhes sem payload/credenciais em verification/results.json. Logs completos locais em /tmp/architecture-docs-{check,make-test,up,integration,smoke}.log. Resultados operacionais continuam válidos após correções de prosa: nenhum código, contrato, composição ou comando mudou; os testes de documentos são reexecutados sobre o texto final.

## Limites de escopo

Ver scope.md. Os gates completos usam o workspace com a infraestrutura preexistente excluída do commit de docs. Não equivalem a validar um checkout limpo contendo somente este stage. Não houve benchmark, DNS blackhole, conferência visual de Swagger ou publicação pela UI do Console; o smoke publica manualmente os mesmos bytes pelo protocolo Kafka (rpk), e confere OpenAPI/HTML/assets por HTTP. Não foi publicado o repositório nem enviado email.

## Revisão independente da retomada

Revisor: /root/docs_final_review, criado com contexto limpo e somente leitura. Duas rodadas; a rodada inicial teve uma interrupção por limite de uso antes do parecer completo e foi retomada no mesmo agente.

| Rodada | Achado | Tratamento |
|-|-|-|
| 1 | README ADR-4 afirmava que toda reentrega/rebalance é DuplicateIgnored | Corrigido: igualdade com a versão atual é duplicata; versão superada é StaleIgnored; ambas preservam idempotência |
| 1 | README confundia lag máximo do consumer com lag por partição | Corrigido: famílias separadas; scrape independente motivou ressalva sobre partition/NaN/ausência de amostra agregada válida |
| 2 | Diff final do stage, cabeçalhos, contexto, contratos e fontes | Nenhum bloqueante ou ajuste pendente; check final verde em 29 s, cobertura 95,3% |

Nenhum achado rejeitado. A sugestão de explicitar o comportamento real do exportador de lag foi incorporada. O revisor conferiu Arts. 1–11, requisitos, ADRs e melhorias futuras, componentes, links, alvos, variáveis e os três cabeçalhos. Conferiu por conta própria o payload literal, HTTP 200/400/404 e no-store/problem+json, updated_at, readiness UP, OpenAPI UUID/cinco status, cinco famílias de métricas, validação estrita OpenSpec e stage sem whitespace errors. Leu as evidências do autor para Docker/integração/smoke; não as tratou como execução própria.

Limites do revisor: não mutou infraestrutura nem publicou eventos; não executou testes concorrentes, publicação/email, avaliação visual de Swagger/Console, carga ou DNS blackhole. A aprovação humana dos três cabeçalhos foi recebida em 2026-10-02, junto da autorização para realizar o commit. Nenhuma aprovação anterior dos cabeçalhos da change de arquitetura foi reutilizada.

## Fechamento autorizado

Aval dos três cabeçalhos e autorização de commit recebidos em 2026-10-02. A sincronização criou somente readme-documentation, com 9 requisitos e 36 cenários idênticos à delta; as 14 specs principais passaram na validação estrita. Nenhum código/header mudou depois da revisão; somente estado de entrega no README e registros do processo foram atualizados. O commit foi criado e a change arquivada; arquivo e registros finais são consolidados no mesmo commit. As 39 tarefas estão concluídas.

Conferência pós-arquivo: ./gradlew check executou novamente e terminou verde em 23 s (374 casos, zero falha/erro/skip, cobertura 95,3%). Inventário OpenSpec sem changes ativas; YAML do arquivo preservado; 39/39 tarefas e igualdade integral dos 9 requisitos/36 cenários conferidas.
