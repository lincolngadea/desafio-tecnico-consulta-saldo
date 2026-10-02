# Escopo da entrega de documentação

## Incluído

- README.md e três arquivos em src/test/kotlin/br/com/itau/challenge/documentation/.
- Artefatos de add-architecture-docs, incluindo delta/main spec readme-documentation, relatórios e arquivo final.
- Somente o registro da existência do ReadmeTest no contexto do projeto, incluído em project.md/config.yaml juntos.

## Trabalho preexistente preservado

Continuam fora do commit desta change: runtime/imagens do Dockerfile, .dockerignore, Makefile, docker-compose.yml, infra/redpanda/ingestion-topics.sh, três testes estáticos de infraestrutura em observability, arquivo e sincronização preexistentes de add-observability, e demais hunks de project.md/config.yaml associados a infraestrutura/observabilidade. Nenhum fonte de produção muda.

Os comandos operacionais e testes completos verificam o workspace, incluindo essa infraestrutura pendente. Isso não significa que ela será incluída neste commit de docs. O snapshot só do stage pode diferir do workspace nos arquivos externos que README/ReadmeTest leem; a separação autorizada não depende de commitar esses trabalhos externos.

Antes do commit: conferir git diff --cached --check, lista de arquivos e hunks; apresentar os cabeçalhos ao usuário. Não publicar nem enviar mensagens externas nesta tarefa.

## Conferência do stage antes do aval

16 arquivos no stage: README, três testes de documentação, os artefatos desta change e dois hunks de contexto em par. git diff --cached --check passou; os bytes do README e dos três testes no index coincidem com o workspace revisado. Nenhum arquivo privado, fonte de produção ou mudança de infraestrutura foi incluído. O usuário autorizou a entrega em 2026-10-02. A sincronização acrescenta somente openspec/specs/readme-documentation/spec.md; o arquivo move os artefatos para archive/2026-10-02-add-architecture-docs, preservando o escopo.
