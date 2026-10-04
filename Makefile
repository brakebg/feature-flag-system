# Feature Flag Service (spec 10.1, 11.3). `make verify` is the single source of truth.
SHELL := /bin/bash
VERSION := $(shell cat VERSION)
GIT_COMMIT := $(shell git rev-parse HEAD 2>/dev/null)
SHORT_SHA := $(shell git rev-parse --short HEAD 2>/dev/null)
JAVA_HOME_21 := $(shell node scripts/lib/java-env.mjs 2>/dev/null)
export FF_VERSION := $(VERSION)
export GIT_COMMIT

.PHONY: up down build-images test lint format verify verify-fast verify-all tools

## Local stack (spec 10.3): UI http://localhost:3000, login admin / admin123.
up:
	docker compose build --pull
	docker tag feature-flag-backend:$(VERSION) feature-flag-backend:sha-$(SHORT_SHA)
	docker tag feature-flag-ui:$(VERSION) feature-flag-ui:sha-$(SHORT_SHA)
	docker compose up -d

down:
	docker compose down

build-images:
	docker compose build --pull
	docker tag feature-flag-backend:$(VERSION) feature-flag-backend:sha-$(SHORT_SHA)
	docker tag feature-flag-ui:$(VERSION) feature-flag-ui:sha-$(SHORT_SHA)

test:
	cd backend && JAVA_HOME=$(JAVA_HOME_21) ./mvnw -B -ntp verify
	cd frontend && npm test

lint:
	cd backend && JAVA_HOME=$(JAVA_HOME_21) ./mvnw -B -ntp -q spotless:check
	cd frontend && npm run lint && npm run format:check

format:
	cd backend && JAVA_HOME=$(JAVA_HOME_21) ./mvnw -B -ntp -q spotless:apply
	cd frontend && npm run format

tools:
	scripts/install-tools.sh

## Quality gates (spec 11.3). Report: build/verify-report.md
verify:
	node scripts/verify.mjs verify

verify-all:
	node scripts/verify.mjs all

verify-fast:
	node scripts/verify.mjs fast
