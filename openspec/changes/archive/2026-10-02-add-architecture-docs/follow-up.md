# Retomada após a auditoria arquitetural

O trabalho iniciado pelo Claude permanece preservado: as 32 tarefas já concluídas não foram reabertas nem o README/testes foram reescritos por este planejamento. A entrega desta change passa a depender de `enforce-hexagonal-architecture`.

Fonte da auditoria e matriz completa: [`audit.md`](../2026-10-02-enforce-hexagonal-architecture/audit.md) da change arquivada.

## Ordem de conclusão

1. Concluir implementação, validações reais, revisão, commit e arquivo de enforce-hexagonal-architecture. Não finalizar docs antes disso.
2. Reconciliar README e testes com o resultado efetivamente entregue.
3. Executar gates e comandos documentados, persistir tabela de evidências e revisão atualizada.
4. Apresentar comentários novos/alterados ao usuário, obter aval, separar stage por escopo, commitar e arquivar esta change.

## Ajustes finais a conferir no README e nos testes

| Item | Ajuste/evidência de aceitação |
|---|---|
| Núcleo sem framework | Texto e diagramas coincidem com services puros e composition root externo; não declarar como exigência literal do enunciado o que é uma escolha do usuário |
| Adapter só acessa casos de uso por port | Explicar a regra protegida pelo teste, preservando a tradução via modelos de domínio |
| Cobertura dos bounded contexts | Indicar descoberta automática de hello, balance e futuros contextos; listar exemplos negativos que o guard rejeita |
| Infraestrutura técnica | Mostrar configuração DynamoDB neutra; remover essa pendência da tabela de futuro quando resolvida. Não afirmar que ApiExceptionHandler depende de hello |
| Verificação no host e Docker | Relatar inputs declarados e arquivos presentes no estágio test; make test realmente executado, sem pular testes ou aceitar resultado antigo do cache como prova nova |
| Inventário de changes/capacidades | Aceitar nomes válidos sem restringir a `add-`; incluir a nova change e reconciliar a contagem atual, evitando número fixo que envelhece |
| Arquivo da nova change | Coordenar sua linha no histórico ainda aberto antes do arquivo, para que ReadmeTest continue válido nos dois estados; identificar essa edição como trabalho de docs |
| Commit e preservação de infra | Tornar texto da regra de commits coerente com a prática autorizada; separar hunks/arquivos. Mudanças de infraestrutura excluídas pelo usuário podem continuar pendentes |
| Pré-requisitos e comandos | Reexecutar o caminho Docker e integração/JDK; conferir make up, criação de tópico, 50 eventos, publicação manual duplicada/antiga e consultas/métricas |
| Limites honestos | Manter explícitos generator aleatório, estimativas de capacidade, lag pausado, DNS, armazenamento local em memória e limites do Kafka/retention; não prometer garantias ilimitadas |
| Melhorias futuras | Manter somente pendências com fonte e motivador. A auditoria não pede implementar auth/cache/histórico/OTLP/carga como condição para terminar docs |
| Entrega do desafio | Checklist final de visibilidade/acesso público e exclusão do enunciado; ações externas de publicação/envio exigem instrução específica e não fazem parte desta retomada |

## Evidências e revisão

O check desta auditoria foi reexecutado com 343 testes e 95,19% de cobertura. Integração e Docker não foram reexecutados: o host recusou SSH. Não transportar esses gates como aprovação do estado posterior à refatoração.

As tarefas antigas de revisão estão marcadas, mas não existe review.md em add-architecture-docs. Recuperar o registro do Claude se disponível; caso contrário, refazer a revisão e registrar. Após as edições finais, a revisão deve cobrir o novo diff, independentemente da revisão anterior. Criar review.md com tabela enunciado → evidência, rodadas, correções/rejeições e limitações antes do commit.

## Entrega técnica da implementação

A implementação de enforce-hexagonal-architecture foi revisada, commitada, sincronizada e arquivada; a dependência está liberada para retomar a entrega desta change. Os arquivos de README e seus três testes foram preservados. Conferir review.md, scope.md e verification/ dessa change para os comandos e limites efetivos.

- Núcleo agora puro: quatro services sem Spring; configurações externas BalanceUseCaseConfiguration/HelloUseCaseConfiguration. Desenhar DI na raiz externa e o fluxo de chamadas via port.
- DynamoDbConfig/DynamoDbProperties ficam em infrastructure.dynamodb. Remover esse item da tabela de futuro; o advice continua global em balance e não é dependência de hello.
- Guard descobre todos os contextos pelas camadas, inclusive parciais; verificar imports/aliases/wildcards/FQN/DSLs, sem cadastrar hello/balance manualmente.
- Gradle rastreia documentos/infra/http/OpenSpec/fontes (incluindo integrationTest) e criação/rename/remoção; sem mudança permite UP-TO-DATE. Docker test recebe entradas por COPY explícito; builder/runtime separados.
- Os novos títulos de capacidade serão hexagonal-architecture e repository-verification após sincronização: calcular o inventário efetivo, sem congelar a contagem em onze. ReadmeTest deve reconhecer nomes válidos além de add- e a história deve incluir enforce-hexagonal-architecture antes do arquivo.
- Foram executados integração real (61 casos), make up/test, 50 eventos, exemplo/duplicata/fora de ordem/DECLINED, HTTP 200/400/404/503 Retry-After, OpenAPI, Swagger servido e métricas/probes. A resposta do exemplo literal é 2025-07-04T12:02:44.589-03:00, derivada do timestamp, conforme decisão existente da API; a data ilustrativa da resposta no enunciado é diferente.
- Não transportar isto como nova conferência visual de Swagger nem como benchmark/DNS blackhole. Gerador aleatório não exercita repetição na mesma conta; os eventos manuais e integração exercitam.
- Infra anterior de runtime/compose/imagens/Makefile/tópicos continua fora do commit proposto; resultados do workspace não significam que esses hunks foram incluídos.

## Coordenação do arquivo — 2026-10-02

As specs principais hexagonal-architecture/repository-verification foram criadas e a dependência movida para archive/2026-10-02-enforce-hexagonal-architecture; commit em preparação. Para manter o gate do workspace durante o arquivo, o README recebeu a linha de histórico e perdeu a contagem fixa; CHANGE_NAME agora aceita nomes de change sem restringir o verbo. O teste antes do ajuste editorial falhou no inventário de changes (1/34). Estes ajustes são desta change de docs, continuam fora do commit de arquitetura e ainda receberão revisão própria/aval do cabeçalho antes da entrega. Nenhuma tarefa editorial inteira foi marcada concluída por estes ajustes mínimos.

## Dependência concluída e retomada liberada

A implementação foi entregue em um único commit, com 32/32 tarefas e as duas capacidades sincronizadas; pasta ../2026-10-02-enforce-hexagonal-architecture. Evidências finais: check pós-arquivo 374 testes, snapshot de stage 323, integração independente 61 e cobertura 95,3%; relatório independente da rodada 3 sem achados. O histórico do README e o reconhecimento de nomes já acompanham o arquivo. A próxima etapa é completar 6.3, depois os gates/revisão de 6.4/6.5 e o aval/commit/archive de docs. Esta change permanece aberta; não transporta a aprovação dos 25 cabeçalhos da arquitetura para comentários novos de ReadmeTest.

## Situação final — 2026-10-02

Retomada concluída: reconciliação, gates reais, revisão independente em duas rodadas, aval humano, commit, sincronização e arquivo realizados. 39/39 tarefas; check pós-arquivo verde (374 casos, 95,3%). As seções anteriores registram a sequência da retomada e não representam pendências atuais. Infraestrutura preexistente permanece fora do commit de docs conforme scope.md.
