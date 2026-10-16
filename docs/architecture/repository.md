# Repository layout and local development

From spec 001 (`docs/SPEC.md`) section 10, 10.1, 10.4. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

## 10. Repository, build and run

One Git repository with two independently built and deployed apps (`backend/`, `frontend/`) plus a root `docker-compose.yml` that runs everything locally with one command.

### 10.1 Layout

```text
feature-flag-service/
├── backend/
│   ├── mvnw, pom.xml, Dockerfile
│   └── src/{main,test}/java/com/example/featureflags/...
│       src/main/resources/{application.yml, application-dev.yml, application-prod.yml, db/migration/}
├── frontend/
│   ├── package.json, vite.config.ts, tsconfig.json, Dockerfile, nginx.conf
│   ├── src/...
│   └── e2e/            # Playwright tests
├── docker-compose.yml  # postgres + backend + frontend
├── .github/workflows/ci.yml
├── Makefile            # make up | down | test | lint
└── README.md
```

### 10.4 Local development

- Backend: `./mvnw spring-boot:run` with `dev` profile (needs `docker compose up postgres`).
- Frontend: `npm ci && npm run dev` (Vite on 5173, proxy to 8080).
- Scripts in `package.json`: `dev`, `build`, `preview`, `test`, `test:e2e`, `lint`, `typecheck`, `format`.
