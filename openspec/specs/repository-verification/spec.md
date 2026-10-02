# repository-verification Specification

## Purpose
Garantir que a verificação dos arquivos do repositório seja executada com inputs completos e resultados corretamente invalidados, tanto no Gradle quanto no Docker.

## Requirements

### Requirement: Testes de arquivos do repositório declaram suas entradas
A tarefa Gradle que executa os testes estáticos MUST declarar os arquivos e árvores consumidos por esses testes como inputs, incluindo descoberta de arquivos novos/renomeados/removidos. Uma alteração relevante MUST invalidar o resultado anterior; a ausência de mudanças MUST continuar permitindo reaproveitamento. Inputs MUST NOT incluir diretórios pessoais, segredos ou produtos do build.

#### Scenario: Mudança somente no README reexecuta a verificação
- **WHEN** test terminou e apenas o conteúdo do README é alterado numa cópia de teste
- **THEN** a próxima execução não reutiliza o resultado anterior de test e verifica o novo conteúdo

#### Scenario: Mudança somente em infraestrutura reexecuta a verificação
- **WHEN** apenas compose, Dockerfile, `.dockerignore`, Makefile ou um script lido pelos testes é alterado
- **THEN** test é invalidada e os testes estáticos verificam a alteração

#### Scenario: Descoberta de fontes e artefatos acompanha novos arquivos
- **WHEN** um arquivo de fonte relevante ou artefato OpenSpec é criado, renomeado ou removido
- **THEN** a próxima execução reavalia os fatos do repositório em vez de manter uma lista de inputs anterior

#### Scenario: Sem mudanças o resultado continua reutilizável
- **WHEN** test é executada novamente sem mudanças em suas entradas
- **THEN** continua elegível a UP-TO-DATE sem um desvio permanente que force todos os testes a rodar

### Requirement: Estágio Docker test tem os arquivos consumidos pela verificação
O estágio Docker `test` MUST disponibilizar os arquivos necessários aos testes estáticos, incluindo README, regras, build/compose, scripts, fontes, alvos de links locais e artefatos OpenSpec. MUST executar o check e o gate de cobertura sem pular testes ou exigir serviços externos. A imagem de runtime MUST continuar contendo somente os artefatos de execução, sem material pessoal ou documentação/testes copiados por essa change.

#### Scenario: Verificação completa roda no estágio test
- **WHEN** o comando Docker usado por `make test` é executado a partir do repositório, sem cache do estágio de verificação
- **THEN** todos os testes estáticos e unitários executam sem erro por arquivo ausente e o gate de cobertura mínimo de 90% é aplicado

#### Scenario: Documento alterado invalida o passo Docker de testes
- **WHEN** apenas um documento consumido pelos testes muda após um build test bem-sucedido
- **THEN** o passo de verificação no estágio test é reexecutado com o conteúdo atualizado

#### Scenario: Runtime permanece separado das entradas de teste
- **WHEN** as imagens test e runtime são inspecionadas após o build
- **THEN** test contém as entradas necessárias e runtime não contém README, OpenSpec, fontes de teste, `.local`, `.challenge`, `.claude`, `.codex` ou credenciais pessoais
