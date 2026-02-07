# J2ME TinyLLM Build Configuration

# Project settings
PROJECT_NAME = TinyLLM
MIDLET_CLASS = tinyllm.LLMMidlet
SRC_DIR = src
BUILD_DIR = build
DIST_DIR = dist

# J2ME SDK paths (inside container)
WTK_HOME = /opt/j2me/wtk
JAVA_HOME = /opt/j2me/jdk1.8.0_202

# J2ME API classpath - includes MIDP 2.0, CLDC 1.1, and networking support
CLASSPATH = $(WTK_HOME)/lib/midpapi20.jar:$(WTK_HOME)/lib/cldcapi11.jar:$(WTK_HOME)/lib/jsr75.jar

# Output files
JAR_FILE = $(DIST_DIR)/$(PROJECT_NAME).jar
JAD_FILE = $(DIST_DIR)/$(PROJECT_NAME).jad

# Docker configuration
DOCKER_IMAGE = ghcr.io/mgruszkiewicz/docker-j2me-build:latest
# Auto-detect non-x86 architectures (ARM64/Apple Silicon)
HOST_ARCH := $(shell uname -m)
DOCKER_PLATFORM := $(if $(filter-out x86_64 amd64,$(HOST_ARCH)),--platform linux/amd64,)
DOCKER_RUN = docker run --rm $(DOCKER_PLATFORM) -v "$$(pwd):/workspace" $(DOCKER_IMAGE)

.PHONY: all clean build docker-build shell info

# Default target
all: docker-build

clean:
	@echo "Cleaning build artifacts..."
	rm -rf $(BUILD_DIR) $(DIST_DIR)

build:
	@echo "=========================================="
	@echo "Building $(PROJECT_NAME)..."
	@echo "=========================================="
	@mkdir -p $(BUILD_DIR) $(DIST_DIR)
	@echo "Compiling Java sources..."
	$(JAVA_HOME)/bin/javac -bootclasspath $(CLASSPATH) \
		-source 1.3 -target 1.3 \
		-d $(BUILD_DIR) \
		$(shell find $(SRC_DIR) -name "*.java")
	@echo "Preverifying classes..."
	$(WTK_HOME)/bin/preverify -classpath $(CLASSPATH) -d $(BUILD_DIR) $(BUILD_DIR)
	@echo "Creating JAR archive with manifest..."
	cd $(BUILD_DIR) && \
		echo "MIDlet-Name: $(PROJECT_NAME)" > manifest.mf && \
		echo "MIDlet-Version: 1.0.0" >> manifest.mf && \
		echo "MIDlet-Vendor: TinyLLM" >> manifest.mf && \
		echo "MIDlet-1: $(PROJECT_NAME),,$(MIDLET_CLASS)" >> manifest.mf && \
		echo "MicroEdition-Configuration: CLDC-1.1" >> manifest.mf && \
		echo "MicroEdition-Profile: MIDP-2.0" >> manifest.mf && \
		echo "MIDlet-Permissions: javax.microedition.io.Connector.http" >> manifest.mf && \
		echo "" >> manifest.mf && \
		$(JAVA_HOME)/bin/jar cvfm ../$(JAR_FILE) manifest.mf .
	@echo "Generating JAD descriptor..."
	@echo "MIDlet-Name: $(PROJECT_NAME)" > $(JAD_FILE)
	@echo "MIDlet-Version: 1.0.0" >> $(JAD_FILE)
	@echo "MIDlet-Vendor: TinyLLM" >> $(JAD_FILE)
	@echo "MIDlet-1: $(PROJECT_NAME),,$(MIDLET_CLASS)" >> $(JAD_FILE)
	@echo "MicroEdition-Configuration: CLDC-1.1" >> $(JAD_FILE)
	@echo "MicroEdition-Profile: MIDP-2.0" >> $(JAD_FILE)
	@echo "MIDlet-Jar-URL: $(PROJECT_NAME).jar" >> $(JAD_FILE)
	@echo "MIDlet-Jar-Size: $(shell stat -c%s $(JAR_FILE))" >> $(JAD_FILE)
	@echo "MIDlet-Permissions: javax.microedition.io.Connector.http" >> $(JAD_FILE)
	@echo ""
	@echo "=========================================="
	@echo "Build complete!"
	@echo "Output files:"
	@echo "  - $(JAR_FILE)"
	@echo "  - $(JAD_FILE)"
	@echo "=========================================="

# Docker-based build (runs make inside container)
docker-build:
	@echo "Building with Docker container..."
	$(DOCKER_RUN) make build

# Show project info
info:
	@echo "Project: $(PROJECT_NAME)"
	@echo "Main class: $(MIDLET_CLASS)"
	@echo "Source files:"
	@find $(SRC_DIR) -name "*.java" | sed 's/^/  - /'
	@echo ""
	@echo "To build: make docker-build"

# Interactive shell for debugging
shell:
	@echo "Starting interactive shell in container..."
	$(DOCKER_RUN) -it bash

# Quick test compile (without preverify/jar)
test-compile:
	@echo "Testing compilation..."
	@mkdir -p $(BUILD_DIR)
	$(DOCKER_RUN) $(JAVA_HOME)/bin/javac -bootclasspath $(CLASSPATH) \
		-source 1.3 -target 1.3 \
		-d $(BUILD_DIR) \
		$(shell find $(SRC_DIR) -name "*.java")
	@echo "Compilation successful!"
