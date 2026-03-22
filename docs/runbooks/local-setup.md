# Runbook: Local Development Setup

This runbook covers setting up OrionETL for local development from scratch. Follow the steps in order.

---

## Requirements

Ensure the following tools are installed before proceeding:

| Tool | Minimum Version | Install Reference |
|---|---|---|
| **Java (JDK)** | 21 | [https://adoptium.net](https://adoptium.net) — use Temurin 21 or GraalVM 21 |
| **Maven** | 3.9+ | [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi) |
| **Docker Desktop** | 4.x+ | [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop) |
| **Git** | 2.40+ | Included with most OS package managers |

### Verify Requirements

```bash
java -version
# Should print: openjdk 21 ... (or similar)

mvn -version
# Should print: Apache Maven 3.9.x

docker --version
# Should print: Docker version 25.x or later

docker compose version
# Should print: Docker Compose version v2.x
```

---

## Step 1: Clone the Repository

```bash
git clone https://github.com/elyares/OrionETL.git
cd OrionETL
```

---

## Step 2: Configure Environment Variables

OrionETL uses environment variables for secrets and environment-specific configuration. A template is provided:

```bash
cp .env.example .env
```

Open `.env` in your editor and review/fill in the values:

```dotenv
# .env (local development — do NOT commit this file)

# PostgreSQL connection
POSTGRES_DB=orionetl_local
POSTGRES_USER=orionetl
POSTGRES_PASSWORD=orionetl_local_pass
POSTGRES_HOST=localhost
POSTGRES_PORT=5432

# Spring active profile
SPRING_PROFILES_ACTIVE=local

# Optional: API tokens for external sources (set if testing API-based pipelines)
CRM_API_TOKEN=
ECOMMERCE_API_TOKEN=
```

The `.env` file is in `.gitignore` and must **never** be committed to the repository.

---

## Step 3: Start the Database

Use Docker Compose to start PostgreSQL:

```bash
docker-compose up -d
```

Verify the container is running and healthy:

```bash
docker ps
# Should show: orionetl-db   Up (healthy)

# Optional: connect to PostgreSQL directly
docker exec -it orionetl-db psql -U orionetl -d orionetl_local
```

The `docker-compose.yml` configures:
- PostgreSQL 15 with the database name, user, and password from your `.env`.
- Health check: `pg_isready -U orionetl`.
- Persistent volume: `postgres_data` (data survives container restarts).
- Port `5432` exposed to `localhost`.

---

## Step 4: Run Flyway Migrations

Flyway migrations run **automatically** when the application starts. However, you can also run them manually:

```bash
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/orionetl_local \
  -Dflyway.user=orionetl \
  -Dflyway.password=orionetl_local_pass
```

Or via the Spring Boot Flyway goal:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--spring.flyway.target=current"
```

To verify migrations were applied:

```bash
docker exec -it orionetl-db psql -U orionetl -d orionetl_local \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Expected output (after first run):

```
 version |         description          | success
---------+------------------------------+---------
 1       | create etl schema            | t
 2       | add indexes                  | t
 3       | add audit table              | t
 4       | add metrics table            | t
```

---

## Step 5: Run the Application

Start OrionETL with the `local` Spring profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile activates:
- `application-local.yml` overrides: in-memory or local Docker database connection.
- Debug logging for ETL packages.
- Relaxed security (no auth required on API endpoints).
- Flyway runs automatically on startup.

### Expected Startup Output

```
  ___      _            _____ _____ _
 / _ \ _ __(_) ___  _ __|_   _|_   _| |
| | | | '__| |/ _ \| '_ \ | |   | | | |
| |_| | |  | | (_) | | | || |   | | | |_
 \___/|_|  |_|\___/|_| |_||_|   |_| |_(_)
                          OrionETL v1.0.0

2026-03-21 10:00:00 INFO  Spring Boot 3.x starting...
2026-03-21 10:00:01 INFO  Active profiles: local
2026-03-21 10:00:02 INFO  Flyway: Successfully applied 4 migrations (schema version: 4)
2026-03-21 10:00:03 INFO  Registered pipelines: [sales-daily, inventory-sync, customer-sync]
2026-03-21 10:00:04 INFO  Started EtlApplication in 4.123 seconds (JVM running for 4.8)
```

The application runs on `http://localhost:8080` by default. The port is configurable in `application-local.yml`.

---

## Step 6: Verify the Application is Healthy

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    }
  }
}
```

If you see `"status": "DOWN"` for `db`, verify:
1. Docker container is running: `docker ps`
2. Database credentials in `.env` match `application-local.yml`
3. Port 5432 is not blocked by a firewall or another process: `lsof -i :5432`

---

## Step 7: Verify Pipeline Registration

```bash
curl http://localhost:8080/api/v1/pipelines | python3 -m json.tool
```

You should see the registered pipelines listed:

```json
{
  "status": "ok",
  "data": [
    { "pipelineId": "sales-daily",    "name": "Sales Daily Pipeline",    "status": "ACTIVE" },
    { "pipelineId": "inventory-sync", "name": "Inventory Sync Pipeline", "status": "ACTIVE" },
    { "pipelineId": "customer-sync",  "name": "Customer Sync Pipeline",  "status": "ACTIVE" }
  ]
}
```

---

## Running Tests

OrionETL has three categories of tests:

### Unit Tests

Unit tests run without Docker and without a Spring context. Fast (< 30 seconds for the full suite).

```bash
mvn test -Dtest="unit/**"
```

Or run all tests (unit + integration) with:

```bash
mvn test
```

### Integration Tests

Integration tests use **Testcontainers** to spin up a real PostgreSQL container automatically. Docker must be running.

```bash
mvn test -Dtest="integration/**"
```

On the first run, Testcontainers downloads the PostgreSQL Docker image if not already cached. This may take a minute.

### End-to-End Tests

Full pipeline execution tests with real DB containers and sample data files.

```bash
mvn test -Dtest="e2e/**"
```

E2E tests use the sample data in `src/test/resources/` (CSV files, JSON files) and verify the complete 8-step flow.

### Run All Tests

```bash
mvn verify
```

This runs unit tests (`mvn test`) and integration/e2e tests (`mvn verify` activates the Failsafe plugin).

### Expected Test Output

```
[INFO] Tests run: 124, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Common Development Tasks

### Reset the Local Database

**Warning:** This destroys all local data.

```bash
./scripts/reset-db.sh
```

This script:
1. Stops Docker Compose.
2. Removes the `postgres_data` volume.
3. Starts Docker Compose again.
4. On next application start, Flyway re-applies all migrations from scratch.

### Run a Specific Pipeline Manually

```bash
curl -X POST http://localhost:8080/api/v1/pipelines/sales-daily/execute \
  -H "Content-Type: application/json" \
  -d '{"triggeredBy": "local:developer"}'
```

Or via the CLI script:

```bash
./scripts/run-pipeline.sh --pipeline sales-daily --env local --wait
```

### Enable SQL Logging

To see all SQL statements in the console, add this to `application-local.yml`:

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql: TRACE
```

Restart the application to apply.

### Hot Reload During Development

Spring Boot DevTools enables automatic restart when class files change. Add to `pom.xml` (dev scope):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

After adding, compile changes with `mvn compile` and the application restarts automatically.

---

## IDE Setup

### IntelliJ IDEA

1. Open project: **File → Open** → select `pom.xml` → open as project.
2. Set Project SDK to Java 21: **File → Project Structure → Project SDK**.
3. Enable annotation processing (for MapStruct and Lombok): **Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing**.
4. Install Lombok plugin if not already present: **Settings → Plugins → search "Lombok"**.
5. Run configurations: Create a Spring Boot run configuration with active profile `local`.

### VS Code

1. Install extensions: **Extension Pack for Java**, **Spring Boot Extension Pack**, **Lombok Annotations Support**.
2. Open the folder. VS Code auto-detects the Maven project.
3. Set `JAVA_HOME` to your JDK 21 path in `.vscode/settings.json`:
   ```json
   {
     "java.home": "/path/to/jdk-21"
   }
   ```
4. Use the Spring Boot Dashboard to start/stop the application.

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `Connection refused` to PostgreSQL on port 5432 | Verify `docker-compose up -d` ran successfully. Check `docker ps` for `orionetl-db` status. |
| `Flyway migration failed: checksum mismatch` | A migration file was modified after it was applied. Run `./scripts/reset-db.sh` to start fresh (dev only). |
| `No active pipeline found: sales-daily` | Pipeline YAML config not loaded. Check `application-local.yml` for the pipeline config path. Check application startup logs. |
| Testcontainers tests fail with `Could not find a valid Docker environment` | Docker Desktop is not running. Start Docker Desktop and re-run. |
| `Port 8080 already in use` | Another process is using port 8080. Stop it or change `server.port` in `application-local.yml`. |
| MapStruct generated mappers not found | Ensure annotation processing is enabled in IDE. Run `mvn compile` to trigger annotation processor. |
| `Lombok @Slf4j not recognized` | Lombok plugin not installed in IDE, or annotation processing not enabled. See IDE setup section. |
