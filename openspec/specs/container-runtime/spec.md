# container-runtime Specification

## Purpose
Execução da aplicação em contêiner com imagem mínima, configuração por ambiente e encerramento gracioso.

## Requirements
### Requirement: Imagens com versão fixa
O `Dockerfile` e o `docker-compose.yml` MUST referenciar toda imagem por uma versão fixa e completa (por exemplo `eclipse-temurin:21.0.12_8-jre-noble`), e MUST NOT usar `latest` nem uma tag flutuante de versão maior (por exemplo `21-jre`).

#### Scenario: Nenhuma tag flutuante no Dockerfile
- **WHEN** as instruções `FROM` do `Dockerfile` são lidas
- **THEN** cada imagem tem tag com versão completa, e nenhuma é `latest` nem só a versão maior

#### Scenario: Nenhuma tag flutuante no compose
- **WHEN** as imagens do `docker-compose.yml` são lidas
- **THEN** cada uma tem tag com versão fixa

### Requirement: Imagem multi-stage mínima
A imagem de execução MUST ser construída em estágios: o estágio de execução MUST conter só o JRE e o jar da aplicação, sem o JDK, o Gradle, o código-fonte nem o cache de build.

#### Scenario: Estágios separados
- **WHEN** o `Dockerfile` é lido
- **THEN** há estágios de build e de execução distintos, e o estágio de execução parte de uma imagem JRE e copia só o jar do estágio de build

### Requirement: Processo sem privilégio de root
A imagem de execução MUST rodar o processo com um usuário não root, com UID numérico fixo (para o orquestrador poder verificar `runAsNonRoot`).

#### Scenario: Usuário numérico não root
- **WHEN** o `Dockerfile` é lido
- **THEN** o estágio de execução termina com `USER` de UID numérico diferente de 0

#### Scenario: Processo não é root no contêiner
- **WHEN** o contêiner da aplicação executa `id -u`
- **THEN** o resultado é o UID configurado, diferente de 0

### Requirement: JVM ajustada para contêiner
A imagem de execução MUST definir flags de memória da JVM para contêiner por `JAVA_TOOL_OPTIONS`, incluindo percentual máximo de heap sobre o limite do contêiner (`-XX:MaxRAMPercentage`) e `-XX:+ExitOnOutOfMemoryError`, para o orquestrador reiniciar um processo sem memória. As flags MUST poder ser sobrescritas por variável de ambiente, sem reconstruir a imagem. O contêiner MUST ter um limite de memória no compose, para as flags terem efeito.

#### Scenario: Flags padrão
- **WHEN** o `Dockerfile` é lido
- **THEN** `JAVA_TOOL_OPTIONS` define `-XX:MaxRAMPercentage` e `-XX:+ExitOnOutOfMemoryError`

#### Scenario: Flags sobrescritas por ambiente
- **WHEN** o contêiner sobe com `JAVA_TOOL_OPTIONS` definida
- **THEN** a JVM usa o valor do ambiente

#### Scenario: Limite de memória no compose
- **WHEN** o serviço `app` do compose é lido
- **THEN** ele declara um limite de memória

### Requirement: Encerramento por SIGTERM
O processo da aplicação MUST ser o PID 1 do contêiner (forma exec do `ENTRYPOINT`, sem shell intermediário), para o `SIGTERM` do orquestrador chegar à JVM e acionar o encerramento gracioso: o servidor web termina as requisições em andamento, o consumer termina o registro em andamento e sai do grupo. O `stop_grace_period` do serviço MUST ser maior que o tempo máximo do encerramento gracioso configurado.

#### Scenario: ENTRYPOINT em forma exec
- **WHEN** o `Dockerfile` é lido
- **THEN** o `ENTRYPOINT` está na forma de lista e chama `java` diretamente, e `STOPSIGNAL` é `SIGTERM`

#### Scenario: Grace period maior que o encerramento
- **WHEN** o compose e o `application.yaml` são lidos
- **THEN** `stop_grace_period` é maior que `spring.lifecycle.timeout-per-shutdown-phase`

#### Scenario: SIGTERM encerra sem perder o registro em andamento
- **WHEN** o contêiner recebe `SIGTERM` com um registro em processamento
- **THEN** o registro é gravado e confirmado antes de o processo sair, e o processo sai dentro do `stop_grace_period` com código 143 (a JVM terminada por `SIGTERM`; 137 seria um `SIGKILL` por estouro do prazo)

### Requirement: Configuração só por variável de ambiente
Toda configuração que varia entre ambientes (endpoints, tópicos, tempos, limites, credenciais, formato de log, porta de gerenciamento) MUST poder ser definida por variável de ambiente, sem alterar código nem a imagem. As credenciais AWS MUST vir da cadeia padrão do SDK (variáveis `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY`), e MUST NOT estar escritas no código.

#### Scenario: Credenciais vêm do ambiente
- **WHEN** a aplicação sobe com `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` definidas
- **THEN** o cliente DynamoDB usa essas credenciais

#### Scenario: Sem credenciais a leitura falha de forma explícita
- **WHEN** a aplicação sobe sem credenciais AWS no ambiente e uma consulta chega
- **THEN** a resposta é um erro explícito (`500`), e a causa é registrada no log sem o valor de nenhuma credencial

#### Scenario: Nenhuma credencial no código
- **WHEN** o código-fonte de produção é inspecionado
- **THEN** não há credencial AWS escrita nele

#### Scenario: Porta de gerenciamento e formato de log por ambiente
- **WHEN** `MANAGEMENT_PORT` e o formato de log são definidos no ambiente
- **THEN** a aplicação os usa

### Requirement: Serviço app sobe com um comando
O `docker-compose.yml` MUST conter o serviço `app`, e `make up` MUST subir a stack inteira funcional, sem passo manual: o `app` MUST esperar o término com sucesso dos seeds do DynamoDB e do Redpanda, e os seeds MUST criar a tabela `AccountBalances` e os tópicos `transacoes-financeiras-processadas` e `transacoes-financeiras-processadas.DLT` com 6 partições cada. A criação dos tópicos MUST ter uma única fonte (um script de `infra/redpanda`), usada pelo seed e por `make kafka-topics-ingestion`. O `app` MUST ter um healthcheck que use o `readiness`.

#### Scenario: app espera os seeds
- **WHEN** o serviço `app` do compose é lido
- **THEN** ele depende dos seeds do DynamoDB e do Redpanda com a condição de término com sucesso

#### Scenario: Seed cria os tópicos de ingestão
- **WHEN** o seed do Redpanda executa
- **THEN** existem o tópico de transações e a DLT, com 6 partições cada, sem erro se já existirem

#### Scenario: Fonte única dos tópicos
- **WHEN** o seed e `make kafka-topics-ingestion` são lidos
- **THEN** ambos chamam o mesmo script de criação, e os nomes e as partições aparecem em um só lugar

#### Scenario: Stack inteira com um comando
- **WHEN** `make up` é executado numa máquina com Docker
- **THEN** o `app` fica saudável e `GET /balances/{accountId}` de uma conta com evento publicado responde `200`

#### Scenario: Healthcheck do app
- **WHEN** o serviço `app` do compose é lido
- **THEN** ele declara um healthcheck que consulta o `readiness` na porta de gerenciamento
