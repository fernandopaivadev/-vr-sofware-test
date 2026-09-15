JAVA_HOME ?= /home/linuxbrew/.linuxbrew/opt/openjdk@8
PATH_WITH_JAVA := $(JAVA_HOME)/bin:$(PATH)
JAVA := $(JAVA_HOME)/bin/java
JAR := target/backend-pedidos-1.0.0.jar
LOGFILE := /tmp/backend-pedidos.log
PIDFILE := /tmp/backend-pedidos.pid
URL := http://localhost:8080/api/pedidos

.PHONY: all build run test stop clean

all: build

build:
	@export JAVA_HOME='$(JAVA_HOME)'; export PATH='$(PATH_WITH_JAVA)'; mvn -q -DskipTests package

run: build
	@if [ -f $(PIDFILE) ] && kill -0 $$(cat $(PIDFILE)) 2>/dev/null; then \
		echo "backend ja esta rodando (pid $$(cat $(PIDFILE)))"; \
	else \
		$(JAVA) -jar $(JAR) > $(LOGFILE) 2>&1 & echo $$! > $(PIDFILE); \
		echo "backend iniciado, pid $$(cat $(PIDFILE)); log em $(LOGFILE)"; \
	fi

test: build
	@if [ ! -f $(PIDFILE) ] || ! kill -0 $$(cat $(PIDFILE)) 2>/dev/null; then \
		echo "backend nao esta rodando. execute: make run"; exit 1; \
	fi
	@set -e; \
	id=$$(cat /proc/sys/kernel/random/uuid); \
	echo "==> POST /api/pedidos (pedido valido)"; \
	code=$$(curl -s -o /tmp/opencode/pedido.json -w '%{http_code}' -X POST $(URL) \
	  -H 'Content-Type: application/json' \
	  -d "{\"id\":\"$$id\",\"produto\":\"Teclado\",\"quantidade\":3}"); \
	echo "HTTP $$code"; \
	[ "$$code" = "202" ] || { echo "FALHOU: esperava 202"; exit 1; }; \
	echo "==> POST invalido (quantidade 0, produto vazio)"; \
	code=$$(curl -s -o /tmp/opencode/erro.json -w '%{http_code}' -X POST $(URL) \
	  -H 'Content-Type: application/json' \
	  -d "{\"id\":\"$$id\",\"produto\":\"\",\"quantidade\":0}"); \
	echo "HTTP $$code"; \
	[ "$$code" = "400" ] || { echo "FALHOU: esperava 400"; exit 1; }; \
	echo "==> await 6s e GET status"; \
	sleep 6; \
	status=$$(curl -s $(URL)/status/$$id | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])"); \
	echo "status: $$status"; \
	[ "$$status" = "SUCESSO" -o "$$status" = "FALHA" ] || { echo "FALHOU: status inesperado"; exit 1; }; \
	echo "OK: batch smoke test passou"

stop:
	@if [ -f $(PIDFILE) ] && kill -0 $$(cat $(PIDFILE)) 2>/dev/null; then \
		kill $$(cat $(PIDFILE)); rm -f $(PIDFILE); echo "backend parado"; \
	else \
		echo "backend nao esta rodando"; \
	fi

clean:
	rm -rf target