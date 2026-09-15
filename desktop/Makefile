JAVA_HOME ?= /home/linuxbrew/.linuxbrew/opt/openjdk@8
PATH_WITH_JAVA := $(JAVA_HOME)/bin:$(PATH)
JAVA := $(JAVA_HOME)/bin/java
JAR := target/desktop-pedidos-1.0.0.jar
LIBS := target/lib/*

LAUNCH4J_VER := 3.50
LAUNCH4J_URL := https://downloads.sourceforge.net/project/launch4j/launch4j-3/$(LAUNCH4J_VER)/launch4j-$(LAUNCH4J_VER)-linux-x64.tgz
LAUNCH4J_TGZ := target/launch4j-$(LAUNCH4J_VER)-linux-x64.tgz
LAUNCH4J_TOOL := target/launch4j-tool

.PHONY: all build run exe dist clean

all: build

build:
	@export JAVA_HOME='$(JAVA_HOME)'; export PATH='$(PATH_WITH_JAVA)'; mvn -q -DskipTests package

run: build
	@$(JAVA) -cp $(JAR):$(LIBS) com.teste.desktop.OrderDesktopApp

exe: build
	@if [ ! -x $(LAUNCH4J_TOOL)/launch4jc ]; then \
		echo 'Baixando launch4j $(LAUNCH4J_VER)...'; \
		mkdir -p target; \
		curl -sL '$(LAUNCH4J_URL)' -o $(LAUNCH4J_TGZ); \
		rm -rf $(LAUNCH4J_TOOL) target/launch4j; \
		tar xzf $(LAUNCH4J_TGZ) -C target; \
		mv target/launch4j $(LAUNCH4J_TOOL); \
	fi
	@cp scripts/launch4j-config.xml target/l4j-config.xml
	@cd target && ./launch4j-tool/launch4jc l4j-config.xml && cd ..
	@ls -lh target/OrderDesktopApp.exe
	@echo "Exe gerado em: target/OrderDesktopApp.exe (usa jre/ embutida)"

dist: exe
	@rm -rf target/dist && mkdir -p target/dist
	@cp target/OrderDesktopApp.exe target/desktop-pedidos-1.0.0.jar target/dist/
	@cp -r target/lib target/dist/
	@echo 'Self-contained: JRE embutida em jre/. Sem dependencia de Java no Windows.' > target/dist/LEIA-ME.txt
	@if [ ! -f target/jre-win-8.zip ]; then \
		echo 'Baixando JRE 8 Windows x64 (Temurin)...'; \
		curl -sL 'https://api.adoptium.net/v3/binary/latest/8/ga/windows/x64/jre/hotspot/normal/eclipse' -o target/jre-win-8.zip; \
	fi
	@rm -rf target/dist/jre
	@python3 scripts/extract_jre.py target/jre-win-8.zip target/dist
	@rm -f target/OrderDesktopApp-dist.zip && cd target/dist && $(JAVA_HOME)/bin/jar cfM ../OrderDesktopApp-dist.zip . && cd ../..
	@echo 'Arquivo: target/OrderDesktopApp-dist.zip'
	@$(JAVA_HOME)/bin/jar tf target/OrderDesktopApp-dist.zip | head -6

clean:
	rm -rf target