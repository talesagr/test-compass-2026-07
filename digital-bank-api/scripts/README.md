# Scripts k6 (carga HTTP)

Requisitos: [k6](https://k6.io/docs/get-started/installation/) instalado e API em execução (por defeito `http://localhost:8080`), com base PostgreSQL alinhada ao seed Flyway (Alice, Bob, Charlie).

Variável opcional: `BASE_URL` (ex.: `export BASE_URL=http://127.0.0.1:8080`).

## Cenários (`scripts/k6/`)

| Ficheiro | Descrição |
|----------|-----------|
| `01-accounts-paging.js` | Carga em `GET /accounts` (leitura, seguro prolongado). |
| `02-transfers-concurrent.js` | Ramp + pico de `POST /transfers` Alice→Bob, chave idempotência única por pedido (consome saldo; repetir exige reset da BD). |
| `03-transfers-idempotency-storm.js` | Muitos VUs com a **mesma** `Idempotency-Key` e corpo fixo (stress do caminho idempotente; espera 201/503). |
| `04-transfers-pingpong.js` | Por iteração: transferência Alice→Bob e Bob→Alice com chaves distintas (montante pequeno, alternância). |

Variáveis k6 úteis: `VUS`, `DURATION`, `RAMP_TARGET`, `PEAK_DURATION`, `TRANSFER_AMOUNT`, `SHARED_IDEMPOTENCY_KEY` (só no storm).

## Wrappers

```bash
chmod +x scripts/*.sh
./scripts/run-k6-accounts.sh
./scripts/run-k6-transfers-concurrent.sh
./scripts/run-k6-transfers-idempotency-storm.sh
./scripts/run-k6-transfers-pingpong.sh
./scripts/run-k6-all.sh
```

Direto com k6:

```bash
BASE_URL=http://localhost:8080 k6 run scripts/k6/02-transfers-concurrent.js
```

## Nota

Carga HTTP complementa os testes JVM em `./mvnw test -Pwith-integration` (Testcontainers); não os substitui.

Os scripts de transferência tratam **201** como sucesso e **409** (saldo insuficiente) ou **503** (storm / indisponibilidade transitória) como resultado aceite onde aplicável. Usam `http.setResponseCallback(http.expectedStatuses(...))` para o k6 não contar esses códigos em `http_req_failed`; o threshold `http_req_failed` nos scripts 02–04 aplica-se só a respostas inesperadas (ex. 5xx). Garante que estás a correr a cópia atual destes ficheiros (checks com um único predicado `201 || 409` no 02).
