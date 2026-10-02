# Escopo preservado e conteúdo proposto

A árvore anterior à implementação foi copiada e teve os hashes registrados em `/tmp/hexagonal-implementation-baseline`, antes dos novos testes. O patch e o status anteriores também foram guardados. Nenhum arquivo de README, infra/compose/Makefile ou testes da documentação foi revertido.

## Pertence a enforce-hexagonal-architecture

- Os quatro services puros, duas configurações externas e seus testes de unicidade/composição/seleção dos decorators.
- Configuração/propriedades DynamoDB e seus testes movidos para infraestrutura neutra; imports dos testes de retry, timeout e integração ajustados.
- Política arquitetural, parser e catálogo de pacotes dos jars, fixtures e guard no escopo de produção.
- build.gradle.kts: parser já transitivo exposto ao compile de teste, classpath fornecido à política e inputs externos/dinâmicos declarados.
- CLAUDE.md e somente os hunks de arquitetura/testes dos dois contextos sincronizados (project.md/config.yaml).
- Dockerfile: somente cabeçalho e COPY seletivo do estágio test. .dockerignore: reinclusões necessárias ao estágio e exclusões privadas exigidas por esta change; o ignore editorial docs anterior fica fora.
- Artefatos, evidências e comentários desta change.

## Continua fora do commit proposto

- README e os três testes de documentação do Claude; artefatos de add-architecture-docs e sua retomada.
- Arquivo/sincronização anteriores de add-observability.
- Alterações anteriores de versões de imagens, usuário/runtime/JVM, portas/healthcheck, compose, Makefile, script de tópicos e seus três testes estáticos de infra.
- .codex e material pessoal.

O snapshot de revisão parte de HEAD e aplica somente esse escopo. O check do workspace inclui os testes pendentes de docs/infra; o check separado do snapshot prova que o código destinado ao commit não depende deles. As evidências operacionais Docker/compose são do workspace completo, preservado, e não prometem que a infraestrutura excluída foi commitada.
