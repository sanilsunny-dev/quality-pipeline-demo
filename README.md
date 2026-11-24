# quality-pipeline-demo

A small Spring Boot customer API with integration tests that run against a **real PostgreSQL in Docker** (via Testcontainers) inside a **GitHub Actions** pipeline. The database container is created for the test run and torn down afterwards.

## What happens on every push / pull request

1. GitHub Actions starts an `ubuntu-latest` runner (Docker is preinstalled).
2. `mvn verify` compiles the code and runs the tests.
3. For `CustomerApiIT`, Testcontainers asks Docker to start `postgres:16-alpine` on a random port.
4. Spring Boot connects to it, and Flyway creates the schema from `V1__create_customer.sql`.
5. REST Assured calls the API (`POST /customers`, `GET /customers/{id}`), so data really goes into PostgreSQL. One test proves the real UNIQUE constraint is enforced (which H2 mocks can miss).
6. After the tests, Testcontainers removes the container. The `docker ps -a` step shows nothing left behind.
7. If any test fails, the build fails and the pull request cannot merge (quality gate).

## Run it locally

You need Java 21, Maven and Docker Desktop running.

```bash
mvn verify
```

## Push it to GitHub

```bash
git init
git add .
git commit -m "Quality pipeline demo: Testcontainers + PostgreSQL + GitHub Actions"
git branch -M main
git remote add origin https://github.com/<your-username>/quality-pipeline-demo.git
git push -u origin main
```

Then open the **Actions** tab on GitHub to watch the pipeline run.

## Files

| File | What it does |
|---|---|
| `pom.xml` | Spring Boot, PostgreSQL driver, Flyway, Testcontainers, REST Assured; Failsafe runs `*IT` tests |
| `src/main/java/.../CustomerApplication.java` | The API: entity, repository, controller |
| `src/main/resources/db/migration/V1__create_customer.sql` | Schema, applied by Flyway |
| `src/test/java/.../CustomerApiIT.java` | Integration tests against PostgreSQL in Docker |
| `.github/workflows/ci.yml` | The GitHub Actions pipeline |
