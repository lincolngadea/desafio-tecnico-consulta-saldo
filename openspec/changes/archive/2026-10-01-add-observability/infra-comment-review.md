# Cabeçalhos complementares de infraestrutura — 2026-10-02

Os cabeçalhos abaixo foram completados na revisão de fechamento. O comportamento operacional foi preservado. Aprovados pelo usuário em 2026-10-02: "pode commitar" (CLAUDE.md, Art. 6). Os cabeçalhos do script e dos três testes não mudaram e já foram aprovados no registro original.

## .dockerignore:1

```text
# L10-L11 http: mantém o alvo local usado pelos links do README.
# L18-L22 openspec: somente contexto, changes e specs participam do inventário e dos links dos testes.
# L24-L28 exclusões privadas: material pessoal e credenciais não participam de nenhuma imagem de teste ou runtime.
# L29 docs: documentação auxiliar não participa das imagens; os inputs usados pelos testes são copiados explicitamente.
# Enunciado: O que será avaliado → Production readiness
```

## Dockerfile:1

```text
# syntax=docker/dockerfile:1
# L10 base e L28 runtime: tags completas tornam as imagens reprodutíveis sem acompanhar uma versão flutuante
#     (add-observability design D8).
# L17-L22 test: documentos invalidam a verificação sem entrar no builder/runtime
#     (enforce-hexagonal-architecture design D5). Enunciado: O que será avaliado → Testes
# L30-L37 runtime: usuário sem root limita privilégios; flags limitam a JVM ao contêiner e a forma exec entrega
#     SIGTERM à JVM para o encerramento gracioso (add-observability design D8).
# Enunciado: O que será avaliado → Production readiness
```

## Makefile:1

```text
# L84-L85 kafka-topics-ingestion: seed e alvo make compartilham o script para não duplicar os tópicos nem as partições
#     (add-observability design D10).
# Enunciado: Como começar → Criando o tópico Kafka
```

## docker-compose.yml:1

```text
# L12-L16 app: porta de gerenciamento separada e orçamento de memória tornam probes e limites operacionais explícitos.
# L32-L34 app.environment: credenciais fictícias servem apenas ao DynamoDB Local; gerenciamento segue a porta publicada.
# L52-L70 app: seeds completos evitam corrida na subida; readiness sinaliza quando o serviço pode receber tráfego.
# L133 redpanda-seed: o mesmo script idempotente usado pelo make cria os tópicos antes de iniciar a aplicação.
# Enunciado: O que será avaliado → Production readiness
```
