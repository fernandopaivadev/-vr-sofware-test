# Sistema de Pedidos Desktop Assíncrono

Teste prático (Java Pleno/Sênior): aplicação desktop **Java Swing** que envia pedidos a um serviço **Spring Boot**, processados de forma assíncrona via **RabbitMQ**, com retorno de status exibido em tempo real na GUI.

## Stack

| Camada       | Tecnologia                                   |
|--------------|----------------------------------------------|
| Backend      | Java 8, Spring Boot 2.7, Spring AMQP, Jackson |
| Desktop      | Java 8, Java Swing, `HttpURLConnection`      |
| Mensageria   | RabbitMQ                                     |
| Build        | Maven + Make                                 |

## Arquitetura

```
┌──────────────────┐   POST /api/pedidos    ┌──────────────────────┐
│  Desktop (Swing) │ ──────────────────────▶│    Spring Boot       │
│                  │   202 Accepted + id    │  /api/pedidos        │
│                  │ ◀──────────────────────│                      │
│                  │   GET /status/{id}     │  pedidos.entrada.*   │
│                  │ ──────────────────────▶│  (fila) ─┐           │
└──────────────────┘                        └──────────┼───────────┘
                    polling a cada 4s                    │ consume
                                                         ▼
                                             processamento 1–3s,
                                             20% de chance de falha
                                                         │
                                   ┌─────────┬────────────┼───────────┐
                                   │         ▼            ▼            │
                    success  pedidos.status.sucesso.*   pedidos.status.*
                    falha     pedidos.status.falha.*     → DLQ
```

Fluxo:
1. Desktop envia `Pedido` (id UUID, produto, quantidade) ao `POST /api/pedidos`.
2. Backend valida (quantidade > 0, produto não vazio → **HTTP 400**) e, se ok, publica na fila `pedidos.entrada.fernando` e responde **HTTP 202**.
3. Consumidor processa: dorme 1–3s e ~20% das vezes lança falha.
   - Sucesso → publica status `SUCESSO` em `pedidos.status.sucesso.fernando`.
   - Falha → publica status `FALHA` (com `mensagemErro`) em `pedidos.status.falha.fernando` e rejeita a mensagem original (sem requeue) → vai para a **DLQ** `pedidos.entrada.fernando.dlq`.
4. Desktop faz polling (`GET /api/pedidos/status/{id}`) a cada 4s e atualiza a tabela na EDT.

## Estrutura

```
.
├── backend/                  # serviço Spring Boot
│   └── src/main/java/com/teste/orders/
│       ├── OrderApplication.java
│       ├── config/RabbitConfig.java      # filas, DLQ, converters
│       ├── domain/                       # Order, OrderStatus, ...
│       ├── service/OrderProcessor.java   # consumidor assíncrono
│       └── web/OrderController.java      # REST
└── desktop/                  # GUI Swing
    ├── scripts/launch4j-config.xml       # config do .exe
    ├── scripts/extract_jre.py            # extrai JRE p/ bundle
    └── src/main/java/com/teste/desktop/OrderDesktopApp.java
```

## Pré-requisitos

- Java 8 (JRE/JDK) e Maven
- RabbitMQ (Docker ou servidor) — ver abaixo

## Como rodar

### 1. RabbitMQ (Docker)

```bash
docker run -d --name teste-rabbit -p 5672:5672 -p 15672:15672 rabbitmq:3.12-management-alpine
# Management UI: http://localhost:15672  (guest/guest)
```

> O backend usa `localhost:5672` com `guest/guest`. Para usar outro broker, edite `spring.rabbitmq.*` em `backend/src/main/resources/application.yml`.

### 2. Backend

```bash
make -C backend run      # compila e inicia em background (log: /tmp/backend-pedidos.log)
make -C backend test     # smoke test E2E (202, 400, status)
make -C backend stop     # para o serviço
```

Sem `make`:

```bash
cd backend && mvn -DskipTests package
java -jar target/backend-pedidos-1.0.0.jar
```

### 3. Desktop

```bash
make -C desktop run       # abre a GUI (precisa de display)
```

## Endpoints

| Método | Rota                        | Descrição                                  |
|--------|-----------------------------|--------------------------------------------|
| POST   | `/api/pedidos`              | Cria pedido; 202 = aceito, 400 = inválido  |
| GET    | `/api/pedidos/status/{id}`  | Status atual do pedido (`OrderStatus`)     |

Payload exemplo:

```json
{"id":"3d104a0c-055f-45ae-a854-220f7b9599b8","produto":"Teclado","quantidade":3}
```

## Fila RabbitMQ

| Fila                                 | Papel                                   |
|--------------------------------------|------------------------------------------|
| `pedidos.entrada.fernando`           | entrada de pedidos (com DLQ associada)   |
| `pedidos.entrada.fernando.dlq`       | dead letter queue (processamento falhou) |
| `pedidos.status.sucesso.fernando`    | status de sucesso                        |
| `pedidos.status.falha.fernando`      | status de falha (com `mensagemErro`)     |

A fila de entrada declara `x-dead-letter-exchange` + `x-dead-letter-routing-key`, e o listener usa ack manual (`AcknowledgeMode.MANUAL`): sucesso → `basicAck`; falha → `basicReject(requeue=false)` (mensagem original vai para a DLQ).

## Gerar o `.exe` Windows (desktop)

```bash
make -C desktop exe     # gera target/OrderDesktopApp.exe (launch4j 3.50)
make -C desktop dist    # zip self-contained com JRE 8 embutida + libs
```

`make dist` produz `desktop/target/OrderDesktopApp-dist.zip` (~40 MB) com:
```
OrderDesktopApp.exe
desktop-pedidos-1.0.0.jar
lib/
jre/
LEIA-ME.txt
```

Basta extrair no Windows e clicar em `OrderDesktopApp.exe` — não precisa Java instalado (usa `jre/` ao lado).

> Aviso: `.exe` sem assinatura pode gerar falso-positivo de antivírus.

### Desktop no Windows + backend no WSL2 (NAT)

O WSL2 em modo NAT tem rede própria: `localhost` do Windows **não** enxerga o backend do WSL automaticamente (e, se a porta 8080 do Windows já estiver ocupada — ex.: Chrome DevTools/CEF — o forward automático também falha).

Solução recomendada — apontar o desktop para o IP do WSL:

1. No WSL: `hostname -I` → anote o IP (ex.: `172.19.20.21`).
2. Ao iniciar o desktop no Windows, defina a URL do backend:

```bat
set PEDIDOS_BASE_URL=http://172.19.20.21:8080
OrderDesktopApp.exe
```

3. O backend deve estar rodando no WSL (bind `0.0.0.0`, padrão).

Alternativas equivalentes: liberar a porta 8080 no Windows para usar `localhost` (forward WSL2), `netsh interface portproxy`, ou `networkingMode=mirrored` no `.wslconfig`.

## Notas de implementação

- Estado em memória: `ConcurrentHashMap<UUID, OrderStatus>` (sem banco).
- Serialização JSON com `Jackson2JsonMessageConverter` + módulo `JavaTime` (datas em ISO-8601).
- GUI: polling com `java.util.Timer`; toda atualização na Event Dispatch Thread via `SwingUtilities.invokeLater()`.
- Erros de conexão/validação exibidos de forma amigável via `JOptionPane`.