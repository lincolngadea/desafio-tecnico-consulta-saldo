## 1. Baseline e continuidade

- [x] 1.1 Auditar README, enunciado, mapa do starter kit, código, guards e trabalho do Claude; registrar tratado/pendente e evidências em `audit.md`
- [x] 1.2 Reexecutar `./gradlew check --rerun-tasks` com JDK 21 e inspecionar as entradas da task test; registrar 343 testes, 95,19% e a indisponibilidade atual do host Docker sem alegar execução de integração ou Docker
- [x] 1.3 Antes de implementar, registrar os arquivos/hunks que pertencem a esta change e os que já pertencem a Docker/observabilidade/docs; preservar a árvore atual e preparar verificação do conteúdo exato destinado ao commit

## 2. Regras arquiteturais (vermelho antes de refatorar)

- [x] 2.1 Fazer spike das APIs existentes de Konsist e criar testes negativos/positivos com fixtures usando a mesma política de produção: núcleo puro; Spring em application; Micrometer/Resilience4j no núcleo; imports com alias e referências totalmente qualificadas, sem falsos positivos em comentários/strings. Rodar o teste mais estrito no código atual e registrar o vermelho pelos quatro services anotados
- [x] 2.2 Criar testes vermelhos para adapter → application e adapter → composição; cenário válido com port e modelo de domínio; manter modelos/DTOs de tradução permitidos
- [x] 2.3 Criar provas negativas das regras internas existentes: domain → port, port → application, application → adapter e núcleo de balance → núcleo de hello; confirmar que não foram removidas ao ampliar o guard
- [x] 2.4 Criar teste vermelho de descoberta: hello e balance; terceiro contexto parcialmente formado em fixture sem editar lista; configuration/infrastructure fora da lista e falha quando o escopo esperado fica vazio
- [x] 2.5 Implementar os guards e a descoberta automática no teste/política arquitetural, preservando as regras existentes; manter o check estrito vermelho somente enquanto os services ainda violam o núcleo puro

## 3. Composição externa e configuração compartilhada (TDD)

- [x] 3.1 Escrever teste de wiring que prova uma implementação para cada um dos quatro ports de entrada e a resolução do contexto completo; criar o teste vermelho da composição externa por contexto, sem registrar services no núcleo
- [x] 3.2 Escrever teste vermelho que prova os casos de uso de balance recebendo os decorators primários de leitura/escrita, sem contornar circuit breakers; preservar a independência dos dois circuitos
- [x] 3.3 Retirar `@Service`/imports de Spring dos quatro services e criar BalanceUseCaseConfiguration/HelloUseCaseConfiguration fora das camadas de negócio; rodar todos os testes unitários existentes dos services (cenário de construção sem Spring) e fazer wiring/arquitetura passarem
- [x] 3.4 Criar teste vermelho de composição de balance com infraestrutura técnica comum sem componentes hello; conferir os testes existentes dos dois perfis de client como contrato de preservação
- [x] 3.5 Mover DynamoDbConfig/DynamoDbProperties para infrastructure.dynamodb, atualizar imports e componentes de teste, sem duplicar clients ou mudar nomes/qualifiers/Primary/limites; fazer o cenário de balance isolado e os testes de perfis ficarem verdes
- [x] 3.6 Rodar os testes de HTTP, ingestão, resiliência e o contexto completo; confirmar que o advice de balance continua global, sem mover exceções de domínio para infraestrutura

## 4. Entradas Gradle dos testes estáticos (TDD)

- [x] 4.1 Criar prova vermelha em uma cópia temporária do projeto: alterar só README depois de test verde deve invalidar test; não editar o README do usuário para simular a falha
- [x] 4.2 Criar prova vermelha parametrizada para alterações só em compose/Dockerfile/.dockerignore/Makefile/script de infra e criação/rename/remoção de fontes relevantes ou artefatos OpenSpec; repetir sem alteração para provar que UP-TO-DATE continua permitido
- [x] 4.3 Declarar os conjuntos dinâmicos de arquivos consumidos como inputs de test, incluindo fontes/inventários e links; excluir produtos de build/material pessoal e fazer as provas de invalidação passarem sem desativar o cache globalmente

## 5. Verificação dentro do Docker (vermelho antes do ajuste)

- [x] 5.1 Recuperar um host Docker e executar o comando do estágio test usado por `make test`; registrar o erro real de arquivos ausentes antes de ajustar o estágio, distinguindo-o de falha de rede/dependência do build
- [x] 5.2 Disponibilizar os arquivos necessários por COPY seletivo somente no estágio test e ajustar reinclusões em .dockerignore; manter exclusões de .local/.challenge/.claude/.codex/.git/credenciais e evitar COPY da raiz inteira
- [x] 5.3 Executar `make test` e um build do estágio test sem cache da verificação: todos os testes e o gate ≥90% passam sem DynamoDB/Kafka externos; não ignorar ReadmeTest ou os testes de infraestrutura
- [x] 5.4 Alterar só um documento em uma cópia de teste e reconstruir para provar que o passo Docker de verificação é invalidado; inspecionar test/runtime para provar arquivos necessários no primeiro e ausência de material editorial/pessoal no runtime

## 6. Validação do enunciado e das promessas do README

- [x] 6.1 Atualizar os cabeçalhos de todos os arquivos de código/teste/build alterados, com linhas reais, porquê, Spec nos testes e Enunciado válido; inventariar comentários para revisão manual
- [x] 6.2 Registrar a nova regra do usuário em CLAUDE.md e atualizar project.md/context juntos (composição externa, infraestrutura neutra, descoberta e inputs), removendo a exceção de Service e o diagrama adapter → application; validar YAML sem reescrever históricos arquivados
- [x] 6.3 Executar check novo com cobertura ≥90% e todas as provas arquiteturais; validar também a cópia/snapshot exata preparada para o commit sem depender de alterações não incluídas
- [x] 6.4 Executar integração real com DynamoDB Local/Redpanda, incluindo hello, perfis de client, concorrência, duplicata, fora de ordem, DECLINED, falhas de dependência e leitura HTTP; evitar app/testes competindo por grupo e restaurar o ambiente ao fim
- [x] 6.5 Executar make up, criação do tópico e publicação de 50 eventos; publicar o exemplo e suas variantes, consultar 200/400/404/503 com Retry-After, conferir OpenAPI/Swagger, métricas/probes e contrato hello; 500 sanitizado coberto por teste de contrato; registrar comando, resultado e tabela item do enunciado → evidência em review.md
- [x] 6.6 Conferir a lista de alegações/limites em audit.md com os resultados novos; entregar à change de docs as edições editoriais necessárias, incluindo nomes de changes enforce-..., histórico/contagem, itens de futuro resolvidos, regra de commits e limites honestos de desempenho/DNS

## 7. Revisão e entrega para a documentação

- [x] 7.1 Independent agent review: com check verde, subagente novo de contexto limpo, somente leitura, recebe artefatos, diff, CLAUDE.md e enunciado; refaz conformidade e verificação arquitetural. Corrigir/rever bloqueantes e ajustes, máximo 3 rodadas, e registrar resultados/rejeições sem depender da revisão antiga de docs
- [x] 7.2 Conferir e atualizar openspec/project.md e context de config.yaml somente pelos fatos duráveis que esta change justifica (Regra 3); manter ambos no mesmo commit e ressubmeter ao revisor se houver alteração depois da rodada aprovada
- [x] 7.3 Apresentar os comentários novos/alterados ao usuário e obter o aval antes do commit; confirmar stage por escopo sem incorporar as mudanças Docker/docs anteriores excluídas
- [x] 7.4 Commitar o escopo revisado em Conventional Commits, sincronizar e arquivar esta change concluída; coordenar a atualização do histórico na documentação aberta e revalidar links/testes antes/depois do arquivo
- [x] 7.5 Liberar a dependência de add-architecture-docs, atualizar a evidência de conclusão e retomar somente então seus ajustes finais, testes, revisão e entrega; não marcar a documentação concluída junto desta change

## Conclusão — 2026-10-02

32/32 tarefas concluídas. Escopo revisado commitado em Conventional Commits, specs hexagonal-architecture/repository-verification sincronizadas e change arquivada nesta pasta. Três rodadas de revisão, sem bloqueantes/ajustes pendentes; aval humano dos 25 cabeçalhos recebido.

Validação: workspace e Docker 374 testes; snapshot selecionado após o arquivo 323; integração independente real 61; zero falhas/erros/ignorados, gate 95,3%. Inputs/cache/documento e imagens test/runtime comprovados. Comandos e limites em review.md e independent-review-round-3.md.

A dependência de add-architecture-docs foi liberada e a retomada registrada em seus tasks.md/follow-up.md. Os ajustes mínimos de histórico/nomes foram coordenados e testados; a documentação continua aberta para sua reconciliação final, revisão, aval e entrega próprios. Infraestrutura anterior e documentação permanecem fora deste commit.
