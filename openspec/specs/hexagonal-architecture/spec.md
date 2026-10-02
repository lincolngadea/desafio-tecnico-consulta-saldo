# hexagonal-architecture Specification

## Purpose
Manter o núcleo dos bounded contexts livre de frameworks, com acesso por ports, composição externa e verificações arquiteturais que detectam violações.

## Requirements

### Requirement: Núcleo livre de frameworks em cada contexto
As camadas `domain`, `port` e `application` de cada bounded context MUST depender somente do JDK/Kotlin e do núcleo do próprio contexto, sem Spring, SDK AWS, Kafka, Jackson, Resilience4j, Micrometer, Jakarta ou outros frameworks. A verificação MUST considerar imports e referências qualificadas, sem exceção para `@Service` em application.

#### Scenario: Núcleo puro permanece válido
- **WHEN** o núcleo usa modelos, ports e classes Kotlin comuns do próprio contexto, sem frameworks
- **THEN** a verificação arquitetural aceita o contexto

#### Scenario: Spring em application é rejeitado
- **WHEN** uma classe de application usa `org.springframework.stereotype.Service`
- **THEN** a verificação falha e identifica a classe/dependência, em hello ou balance

#### Scenario: Framework adicional no núcleo é rejeitado
- **WHEN** um arquivo de port, application ou domain referencia Micrometer ou Resilience4j
- **THEN** a verificação falha, mesmo sendo namespaces ausentes da lista antiga de frameworks

#### Scenario: Alias ou referência qualificada não contorna a regra
- **WHEN** uma classe do núcleo usa um framework com import alias ou referência totalmente qualificada sem import
- **THEN** a mesma política rejeita a dependência e não produz falso resultado por textos em comentários ou strings

### Requirement: Adapters acessam casos de uso por ports
Um adapter MUST acessar casos de uso por ports e MUST NOT depender de tipos de `application` ou da raiz de composição. O uso de modelos do domínio para tradução de transporte MUST continuar permitido.

#### Scenario: Adapter depende de implementação de application
- **WHEN** um adapter referencia diretamente um service de application
- **THEN** a verificação falha apontando a dependência proibida

#### Scenario: Adapter usa port e modelos do domínio
- **WHEN** o adapter recebe um port de entrada e traduz DTOs para modelos do domínio
- **THEN** a verificação aceita o fluxo sem exigir o service concreto

### Requirement: Dependências internas apontam para dentro do próprio contexto
Domain MUST NOT depender de port, application, adapters ou configuração. Port MUST NOT depender de application, adapters ou configuração. Application MUST NOT depender de adapters ou configuração. Essas camadas MUST NOT depender do núcleo de outro bounded context.

#### Scenario: Domain depende de camada externa
- **WHEN** um modelo do domínio referencia um port do mesmo contexto
- **THEN** a verificação rejeita a dependência

#### Scenario: Port depende de application
- **WHEN** um port referencia uma implementação de application
- **THEN** a verificação rejeita a dependência

#### Scenario: Application depende de adapter
- **WHEN** um service referencia um adapter de persistência
- **THEN** a verificação rejeita a dependência

#### Scenario: Núcleos de contextos diferentes são acoplados
- **WHEN** o núcleo de balance referencia um tipo do núcleo de hello
- **THEN** a verificação rejeita a dependência entre contextos

### Requirement: Verificação descobre todos os bounded contexts
A verificação MUST descobrir os bounded contexts a partir das camadas nos fontes de produção, incluindo contextos parcialmente formados, sem manter uma lista manual. MUST distinguir infraestrutura/composição de contexto de negócio e MUST falhar se a descoberta ou um escopo esperado ficar vazio.

#### Scenario: Hello e balance são cobertos
- **WHEN** a verificação analisa o repositório atual
- **THEN** ambos os contextos são descobertos e têm todas as camadas existentes verificadas

#### Scenario: Novo contexto é coberto sem editar uma lista
- **WHEN** uma fixture adiciona um terceiro contexto com uma violação, mesmo contendo inicialmente só uma camada de núcleo
- **THEN** a descoberta inclui esse contexto e a verificação rejeita a violação sem cadastro manual

#### Scenario: Composição externa não vira contexto de negócio
- **WHEN** há configurações técnicas em `configuration` e `infrastructure`, além dos contextos reais
- **THEN** esses pacotes não são classificados como bounded contexts e a verificação não passa por um conjunto de contextos vazio

### Requirement: Composição Spring é externa ao núcleo
Os quatro services existentes MUST ser classes Kotlin comuns. A raiz de composição fora de domain/port/application/adapter MUST registrar cada caso de uso por seu port de entrada, receber ports de saída por construtor/factory e preservar as implementações decoradas primárias. A configuração de cada contexto MUST poder ser carregada separadamente.

#### Scenario: Cada port de entrada tem uma implementação
- **WHEN** o contexto completo da aplicação inicia
- **THEN** há uma implementação de produção para cada um dos quatro ports de entrada, sem beans duplicados ou dependências ausentes

#### Scenario: Wiring preserva os decorators de armazenamento
- **WHEN** os casos de uso de balance recebem os ports de saída
- **THEN** escrita e leitura continuam passando pelos circuit breakers primários independentes

#### Scenario: Services podem ser construídos sem Spring
- **WHEN** os quatro services são instanciados com fakes dos ports de saída nos testes unitários
- **THEN** seus comportamentos existentes permanecem válidos sem annotations ou tipos de framework nas classes

### Requirement: Infraestrutura DynamoDB compartilhada não pertence a hello
A configuração e as propriedades compartilhadas do DynamoDB MUST ficar em um pacote técnico neutro. A mudança MUST preservar nomes e quantidade dos clients, qualificadores, `@Primary`, propriedades, timeouts, retry e credenciais. Balance MUST poder usar essa infraestrutura sem carregar os componentes hello.

#### Scenario: Balance funciona sem componentes hello
- **WHEN** a configuração técnica comum e a composição de balance são carregadas sem registrar componentes do exemplo hello
- **THEN** os casos de uso de balance são resolvidos com seus ports de saída sem dependência de configuração dentro de hello

#### Scenario: Perfis DynamoDB não mudam na movimentação
- **WHEN** a configuração é movida para o pacote neutro
- **THEN** continuam existindo os mesmos dois clients por perfil, com a escrita primária e a leitura qualificada, mantendo os limites e as configurações já testados
