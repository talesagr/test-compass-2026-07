# Digital Bank API

API REST (Spring Boot 4, Java 21, Maven) para contas bancárias, transferências transacionais com bloqueio ordenado de contas, extrato em ledger, notificação após commit e documentação OpenAPI (Swagger UI).

## Pré-requisitos

- JDK 21
- Maven (ou `./mvnw`)
- Docker (para PostgreSQL local)

## Execução local

1. Subir o banco:

```bash
docker compose up -d
```

2. Aplicação (Flyway aplica `V1__init.sql` e `V2__seed.sql` com três contas de exemplo):

```bash
./mvnw spring-boot:run
```

3. Swagger UI: `http://localhost:8080/swagger-ui.html`  
   OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Contas seed (PostgreSQL)

| ID | Titular | Saldo inicial |
|----|---------|----------------|
| `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` | Alice | 1000.00 |
| `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb` | Bob | 500.00 |
| `cccccccc-cccc-cccc-cccc-cccccccccccc` | Charlie | 250.50 |

## Endpoints

| Método | Caminho | Descrição |
|--------|---------|-----------|
| GET | `/accounts` | Lista contas paginadas (`page`, `size`, `sort`; padrão `size=20`, ordenação `createdAt` descendente) |
| GET | `/accounts/{id}` | Detalhe e saldo |
| POST | `/accounts` | Cria conta (`name`, `initialBalance` ≥ 0) |
| POST | `/transfers` | Transferência (`fromAccountId`, `toAccountId`, `amount` > 0). Resposta assíncrona no servlet (execução no pool `transfer-api-*`). Cabeçalho opcional `Idempotency-Key` (até 255 caracteres) para deduplicação. |
| GET | `/accounts/{id}/movements` | Movimentos paginados (`page`, `size`) |

Erros: `400` validação de bean (`MethodArgumentNotValidException`) ou regra de aplicação explícita (`BadRequestException`); `404` conta inexistente; `409` saldo insuficiente ou reutilização de `Idempotency-Key` com corpo diferente (vide `title` no Problem Detail); `422` mesma conta origem/destino; `502` **`IllegalArgumentException` genérica** (tratada como falha de componente externo, sem ecoar a mensagem da exceção); `503` tempo esgotado aguardando a mesma chave concluir outra requisição ou timeout ao obter lock pessimista em conta (5s); `500` falha inesperada (incl. erros assíncronos não mapeados). Corpo de erro em `application/problem+json` quando aplicável.

## Testes

### Rápidos (padrão, sem Docker para integração)

```bash
./mvnw test
```

Por padrão o Surefire **exclui** testes com a anotação `@Tag("integration")` (Testcontainers). Assim a suíte continua rápida em ambientes sem Docker.

- **TransferService**: JUnit 5 + Mockito (saldo insuficiente, mesma conta, conta ausente, fluxo feliz, `Idempotency-Key`).
- **DigitalBankApiApplicationTests**: sobe o contexto com perfil `test` (H2 em memória, Flyway desligado, `ddl-auto=create-drop`).

### Integração + concorrência (PostgreSQL real via Testcontainers)

Requer **Docker** em execução.

```bash
./mvnw test -Pwith-integration
```

Sobe um `postgres:16-alpine` efêmero, aplica Flyway (`V1`–`V3`), e executa `TransferConcurrencyPostgresIntegrationTest` (`@Tag("integration")`), que cobre:

- muitas transferências **Alice → Bob** em paralelo (saldo final e conservação do somatório das contas);
- **ping-pong** Alice ↔ Bob com a mesma dupla de contas em muitas threads (comportamento sob contenção; saldos voltam ao esperado);
- **mesma `Idempotency-Key`** em muitas threads (uma única movimentação de valor, demais *replays*);
- **superexigência de saldo** (parte das threads recebe `InsufficientBalanceException`, soma dos saldos inalterada);
- **stress** com dezenas de threads e alternância de direção (detecção de deadlock / conclusão estável).

O perfil Maven `with-integration` redefine `surefire.excludedGroups` para um valor inexistente, de modo que **todos** os testes (unitários + integração) rodem na mesma invocação. Com Docker ativo, o total esperado é **15** testes (10 rápidos + 5 de concorrência). Classes só com sufixo `IT` não entram no Surefire por padrão (convenção Maven Failsafe); por isso a classe de integração usa o sufixo `IntegrationTest`.

Para rodar **apenas** a classe de integração:  
`./mvnw test -Pwith-integration -Dtest=TransferConcurrencyPostgresIntegrationTest`

Cada método de integração faz **reset** das tabelas financeiras no `@BeforeEach` para isolar cenários.

### Carga HTTP (k6)

Com a API no ar (`./mvnw spring-boot:run` + Postgres), ver `scripts/README.md` e os wrappers `scripts/run-k6-*.sh` para stress contra `http://localhost:8080` (variável `BASE_URL`).

## Arquitetura e decisões

- **Camadas**: `api` (REST + DTOs), `application` (serviços, eventos, porta de notificação), `domain.model` (entidades JPA), `infrastructure` (repositórios Spring Data, adaptador de notificação).
- **Transferência**: uma transação `@Transactional`; contas carregadas com `PESSIMISTIC_WRITE` sempre na **ordem crescente de UUID** para evitar deadlock entre transferências A→B e B→A.
- **Ledger**: duas linhas por transferência (`DEBIT` na origem, `CREDIT` no destino), ligadas ao `transfer_id`.
- **Notificação**: `NotificationPort` com implementação que persiste em `notifications` e registra log; disparo via `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("notifyTaskExecutor")` num **`ThreadPoolTaskExecutor`** dedicado (core 4, max 16, fila 200, threads `notify-*`, encerramento ordenado) para não bloquear a thread HTTP nem usar o executor implícito de fila ilimitada.
- **POST /transfers**: o caso de uso corre num **`transferApiExecutor`** (pool `transfer-api-*`, fila limitada) via `CompletableFuture`, libertando o worker HTTP do servlet durante locks e esperas de idempotência; entre tentativas de idempotência usa-se o agendador **`idempotencyWaitScheduler`** (threads `idem-wait-*`) em vez de `Thread.sleep` na thread do caso de uso.
- **Schema**: Flyway em `src/main/resources/db/migration` com `ddl-auto=validate` no perfil default (inclui `transfer_idempotency` a partir de `V3__transfer_idempotency.sql`).

### Por que `record` (DTOs, resultado e eventos)

Vários tipos são **`record`** do Java (16+): DTOs de entrada/saída da API (`CreateAccountRequest`, `TransferRequest`, `AccountResponse`, `TransferResponse`, `MovementResponse`), o retorno do caso de uso **`TransferResult`**, o evento **`TransferCompletedEvent`**, e o valor **`TransferIdempotencyRow`** na infraestrutura.

**Simplicidade**: o compilador gera construtor canônico, acessores, `equals`, `hashCode` e `toString` — evita dezenas de linhas repetitivas (Lombok ou classes “só getters”) para tipos que não passam de um conjunto fixo de campos.

**Clareza de intenção**: um `record` comunica imediatamente que o tipo é um **objeto valor imutável** (dados congelados no momento da criação), distinto de **entidades JPA** com identidade persistente, mutação de saldo e ciclo de vida gerenciado pelo ORM. Isso reduz ambiguidade na leitura do código: “aqui é payload / resultado / evento”, “ali é persistência”.

**Alinhamento ao Java 21 e ao ecossistema Spring**: records integram validação (`jakarta.validation`) em DTOs de request, serialização JSON e uso em `ApplicationEventPublisher` sem cerimônia extra.

### Por que saldo e valores usam `BigDecimal`

Nesta API o saldo e os montantes são modelados com **`java.math.BigDecimal`** e persistidos como **`NUMERIC`** no PostgreSQL pelos seguintes motivos:

- **Precisão decimal exata**: evita erros de arredondamento inerentes a `float`/`double`, inaceitáveis em dinheiro somado ao longo do tempo.
- **Alinhamento ao banco**: `NUMERIC(19,2)` (ou equivalente) é o padrão usual para valores monetários em SQL; o tipo espelha a regra de negócio sem “surpresas” de binário IEEE-754.
- **Regras financeiras**: comparações (`compareTo`), soma e subtração ficam determinísticas no domínio, o que simplifica validações (por exemplo, saldo insuficiente) e o ledger.

O JSON da API usa **números com ponto decimal** (por exemplo `17.90`), que é o formato natural de número em JSON e integra bem com OpenAPI e clientes modernos.

### Arredondamento monetário (`setScale(2, HALF_UP)`)

No domínio, montantes são normalizados para **exatamente duas casas decimais** antes de persistir e de comparar (serviço de transferência e criação de conta), alinhados ao `NUMERIC(19,2)` do banco.

**Decisão:** usar `RoundingMode.HALF_UP` ao fixar a escala.

**Justificativa:**

- **Contrato com o SQL:** o schema armazena duas casas decimais; sem normalização explícita, `BigDecimal` com escalas diferentes pode gerar comparações e idempotência mais difíceis de raciocinar, mesmo quando o valor numérico é o mesmo.
- **Regra explícita em empates:** quando o dígito a descartar é exatamente “meio” (ex.: …5 na casa seguinte), `HALF_UP` **sempre arredonda para cima** — comportamento determinístico e fácil de explicar em documentação e suporte.
- **Não é regra legal universal:** em alguns contextos (agregações massivas ou normas específicas) prefere-se `HALF_EVEN` (*banker’s rounding*) para reduzir viés. Esta base priorizou simplicidade e previsibilidade; se o produto exigir outra norma, o modo de arredondamento deve ser revisto em conjunto com compliance.

A camada HTTP reforça o contrato com **`@Digits(integer = 17, fraction = 2)`** nos DTOs de montante, reduzindo entradas com precisão incompatível com o modelo.

### Alternativas comuns no mercado (não usadas neste projeto)

**Inteiro em menor unidade (centavos), sem vírgula** — padrão frequente em empresas financeiras no Brasil (e em gateways): o valor `1790` representa **R$ 17,90** (17 reais e 90 centavos). Vantagens: aritmética inteira, sem ambiguidade de separador decimal, bom desempenho e contratos que forçam o cliente a pensar em “centavos”. Desvantagens: exige convenção explícita (escala 10^2), cuidado com overflow em agregações muito grandes e camada extra para exibição humana (formatação em reais).

**`String` com vírgula local (ex.: `"17,90"`)** — aparece em integrações legadas, telas ou arquivos onde o formato é textual. O fluxo típico é: receber `String`, **normalizar/parsear** para um tipo numérico interno (quase sempre `BigDecimal` após interpretar locale e vírgula), executar a regra de negócio e, na saída, **voltar a `String`** quando o contrato ou o canal exige formato fixo ou controle fino de zeros e separadores. Desvantagens: mais validação, risco de inconsistência se cada sistema assumir um locale diferente, e custo de parse em alto volume.

Esta base escolheu **`BigDecimal` + JSON numérico** como equilíbrio entre clareza na API REST, segurança aritmética e mapeamento direto ao SQL. Migrar para **centavos como `long`/`int`** na API ou aceitar **montantes como `String`** seria uma decisão de contrato (breaking change) documentada e alinhada a gateways ou frontends existentes.

### Idempotência HTTP (`Idempotency-Key`)

O `POST /transfers` aceita o cabeçalho opcional **`Idempotency-Key`**. O servidor grava a chave junto de `fromAccountId`, `toAccountId` e `amount` na tabela `transfer_idempotency` (via `INSERT ... ON CONFLICT DO NOTHING` na mesma transação da transferência) e, após sucesso, associa o `transfer_id`.

**Por que usar**

- **Retentativas de rede**: clientes HTTP (mobile, browser, jobs) podem repetir o mesmo `POST` após timeout ou `5xx` sem saber se o débito ocorreu; sem idempotência, cada retentativa pode gerar outra transferência.
- **Duplo clique / duplo envio**: o usuário ou um *retry* automático dispara duas requisições idênticas em curto intervalo.
- **Semântica “no máximo uma vez” por chave**: a mesma chave com o **mesmo** corpo devolve o **mesmo** resultado (mesmo `transfer_id` e dados da transferência) sem mover dinheiro de novo e **sem** disparar novamente a notificação pós-commit. A mesma chave com **corpo diferente** responde `409` com `title` **Idempotency key conflict**, para evitar que um cliente “reaproveite” uma chave com outra intenção.
- **Alinhamento a práticas de APIs financeiras** (Stripe, Adyen, bancos): expor idempotência no protocolo HTTP é o padrão mais simples quando não há fila com deduplicação externa.

**Limitações documentadas**: requisições concorrentes com a mesma chave fazem a perdedora aguardar brevemente até a outra concluir; se o limite de espera for atingido, a API responde `503`. Linhas com `transfer_id` nulo por falha entre *claim* e conclusão são improváveis na mesma transação, mas falhas operacionais exóticas podem exigir limpeza manual ou job de saneamento em produção.

## Repositório público

**GitHub:** [https://github.com/talesagr/test-compass-2026-07](https://github.com/talesagr/test-compass-2026-07)

## Melhorias futuras sugeridas
- Autenticação, multimoeda, filas externas (Kafka/RabbitMQ), observabilidade.
- Melhorar o sistema de notificação, hoje em dia é apenas uma simulação de BD + log

