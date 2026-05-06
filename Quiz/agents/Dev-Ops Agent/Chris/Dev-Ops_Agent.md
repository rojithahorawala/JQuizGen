# JQuizGen — DevOps Document · V1.0
**AI-Powered Quiz Generator — Operational & Deployment Reference**

| Field | Value |
|-------|-------|
| Date | May 5, 2026 |
| Phase | Development → Pre-Deploy |
| Agent | Dev-Ops Agent (Chris) |
| Tool | Claude Code (claude-sonnet-4-6) |

> This document is generated and updated incrementally alongside the application — each section reflects the actual state of the codebase at each development stage.

---

## Tech Stack — DevOps View

| Concern | Technology | Version |
|---------|------------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.5 |
| Build Tool | Maven | 3.x |
| Database | PostgreSQL via Neon (cloud) | — |
| DB Migrations | Flyway | (Spring Boot managed) |
| AI Service | Anthropic Claude API | `claude-haiku-4-5-20251001` |
| File Parsing | Apache Tika | 2.9.2 |
| HTTP Client | Spring WebFlux (WebClient) | — |
| Logging | SLF4J + Logback | — |
| Frontend | Thymeleaf + Bootstrap 5 | — |
| Version Control | Git | main branch |

---

## Environment Variables

The application will **not start** without these two variables set in the runtime environment. They must never be committed to source control.

| Variable | Used As | Where Referenced |
|----------|---------|-----------------|
| `DATABASE_URL` | PostgreSQL JDBC connection string | `application.properties` → `spring.datasource.url` |
| `ANTHROPIC_API_KEY` | Claude API auth key | `application.properties` → `claude.api.key` → `GeminiConfig.java` |

### Setting Variables Locally

```bash
export DATABASE_URL=jdbc:postgresql://<host>/<dbname>?user=<user>&password=<pass>&sslmode=require
export ANTHROPIC_API_KEY=sk-ant-...
```

> **Note:** The property key is `claude.api.key` and the config class is named `GeminiConfig` — this is a naming artifact from early development when Gemini was the planned AI provider. The live implementation calls the Anthropic Claude API.

### .gitignore Coverage

The following are correctly excluded from version control:

```
target/        # compiled output
logs/          # runtime log files
*.class
*.jar / *.war
*.env / .env   # environment variable files
.idea/ / .vscode/
.DS_Store
```

---

## Build

### Standard Commands

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package (produces target/jquizgen-0.0.1-SNAPSHOT.jar)
mvn package

# Skip tests during package
mvn package -DskipTests

# Clean build artifacts
mvn clean

# Full clean build
mvn clean package
```

### Run the Application

```bash
# Via Maven (development)
mvn spring-boot:run

# Via JAR (staging / production)
java -jar target/jquizgen-0.0.1-SNAPSHOT.jar
```

Both require `DATABASE_URL` and `ANTHROPIC_API_KEY` to be exported in the environment first.

### Key Maven Plugin

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

Produces a self-contained executable JAR with embedded Tomcat — no external servlet container required.

---

## Database — Neon PostgreSQL

### Connection

- Provider: [Neon](https://neon.tech) (free-tier cloud PostgreSQL)
- Driver: `org.postgresql.Driver`
- Connection supplied entirely via `DATABASE_URL` environment variable
- SSL required (Neon enforces `sslmode=require`)
- Hibernate dialect: `PostgreSQLDialect`
- DDL auto: `validate` — Hibernate only validates the schema, never modifies it

### Neon Branching Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Production database |
| `dev` | Development / testing — free Neon feature, isolated from prod |

### Schema — V1 (current)

Managed by Flyway. Migration file: `src/main/resources/db/migration/V1__init_schema.sql`

**Tables:**

| Table | Purpose |
|-------|---------|
| `users` | Accounts for both students and teachers, includes `role`, `failed_login_attempts`, `locked_until` |
| `quizzes` | Quiz metadata — title, creator, scope (PERSONAL/CLASS), status, question count |
| `questions` | Individual questions — type, correct answer key, points, order |
| `question_options` | Multiple-choice options linked to questions |
| `attempts` | Student quiz attempts — start/submit timestamps, score, status |
| `answers` | Per-question student answers within an attempt — grading fields included |
| `generation_jobs` | Async AI generation tracking — job status, error codes, timestamps |

### Flyway Configuration

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.validate-on-migrate=false
```

> `validate-on-migrate=false` is set intentionally to prevent startup failures if the migration checksum changes during development.

#### ⚠ Known Issue — Checksum Mismatch (Resolved in config)
Early errors in `errors.log` showed a Flyway checksum mismatch on V1 after the migration file was edited post-apply. This is prevented going forward by `validate-on-migrate=false`. For any future schema changes, **always create a new versioned migration** (e.g. `V2__add_column.sql`) — never edit an already-applied migration file.

---

## AI Service — Claude API

### Configuration (`GeminiConfig.java`)

```java
WebClient.builder()
    .baseUrl("https://api.anthropic.com")
    .defaultHeader("x-api-key", apiKey)           // from ANTHROPIC_API_KEY
    .defaultHeader("anthropic-version", "2023-06-01")
    .defaultHeader("anthropic-beta", "prompt-caching-2024-07-31")
    .defaultHeader("Content-Type", "application/json")
    .build();
```

### Model

```properties
claude.api.model=claude-haiku-4-5-20251001
```

Haiku is used for cost and speed — appropriate for quiz generation at free/low-cost tier.

### Prompt Caching

The system prompt (`"You are an expert educational quiz generator..."`) is sent with `cache_control: ephemeral`, enabling Anthropic prompt caching to reduce token costs on repeated calls.

### Async Generation Flow

```
POST /quiz/generate
  → QuizGenerationAsyncExecutor (@Async)
  → GeminiClient.generateContent(prompt)
  → ResponseParser parses JSON
  → Quiz saved to DB with status READY
  → GenerationJob updated to COMPLETED

GET /quiz/status/{jobId}      ← frontend polls every 2 seconds
  → returns { PENDING | PROCESSING | READY | FAILED }
```

### Thread Pool (`AsyncConfig.java`)

| Parameter | Value |
|-----------|-------|
| Core pool size | 4 |
| Max pool size | 8 |
| Queue capacity | 100 |
| Thread name prefix | `async-` |

Configured via `application.properties` and `AsyncConfig.java`. Handles concurrent AI generation jobs without blocking the main request thread.

---

## Logging

### Appenders (`logback-spring.xml`)

| Appender | Output | Filter |
|----------|--------|--------|
| `CONSOLE` | stdout | All levels ≥ INFO |
| `FILE` | `logs/jquizgen.log` | All levels ≥ INFO, 30-day rolling |
| `ERRORS` | `logs/errors.log` | ERROR only |

### Rolling Policy

- `jquizgen.log` → rolls daily: `logs/jquizgen.%d{yyyy-MM-dd}.log`
- `errors.log` → rolls daily: `logs/errors.%d{yyyy-MM-dd}.log`
- `maxHistory=30` on the main log (30 days retained)

### Log Levels (`application.properties`)

```properties
logging.level.com.quizgen=INFO
logging.level.org.springframework.security=WARN
logging.level.org.flywaydb=INFO
```

### Key Log Events

| Event | Level | Pattern |
|-------|-------|---------|
| Startup failure | ERROR | `errors.log` — full stack trace |
| Flyway migration | INFO | Flyway output on startup |
| Claude API call | INFO | `Claude API call successful, received N chars` |
| Claude API error | ERROR | `Claude API HTTP error: STATUS - BODY` |
| Security events | WARN | Spring Security filter chain |

> `logs/` is in `.gitignore` — log files are runtime artifacts and must not be committed.

---

## Security — Operational Notes

| Control | Implementation |
|---------|---------------|
| Password hashing | BCrypt (`BCryptPasswordEncoder`) |
| Session timeout | 30 minutes (`server.servlet.session.timeout=30m`) |
| Max concurrent sessions | 1 per user — additional login expires old session |
| CSRF | Enabled on all routes except `/api/**` |
| Brute force | `failed_login_attempts` + `locked_until` columns in `users` table |
| API key | Stored in env var only — never in source or logs |
| File upload cap | 10MB per file, 30MB per request (multipart config) |

---

## Application Configuration Reference

```properties
# Database
spring.datasource.url=${DATABASE_URL}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.validate-on-migrate=false

# File Upload
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=30MB
spring.servlet.multipart.enabled=true

# AI
claude.api.key=${ANTHROPIC_API_KEY}
claude.api.model=claude-haiku-4-5-20251001

# Async Thread Pool
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=8
spring.task.execution.pool.queue-capacity=100
spring.task.execution.thread-name-prefix=async-

# Session
server.servlet.session.timeout=30m

# Logging
logging.file.name=logs/jquizgen.log
logging.level.com.quizgen=INFO
logging.level.org.springframework.security=WARN
logging.level.org.flywaydb=INFO

# Thymeleaf
spring.thymeleaf.cache=false
spring.thymeleaf.encoding=UTF-8
```

---

## Development Increments — Claude Code Sessions

Each increment below reflects a stage of development completed with the Claude Code Development Agent.

### Increment 1 — Project Scaffold
- Spring Boot 3.2.5 project initialized via Maven
- `pom.xml` configured with all dependencies: Web, Thymeleaf, Security, JPA, Flyway, Tika, WebFlux, Validation, PostgreSQL, Jackson
- Base package `com.quizgen` established
- `JQuizGenApplication.java` entry point created

### Increment 2 — Database Schema
- Flyway enabled with `V1__init_schema.sql`
- 7 tables created: `users`, `quizzes`, `questions`, `question_options`, `attempts`, `answers`, `generation_jobs`
- Schema designed to support both auto-grading (MC/T/F) and manual grading (free response)
- `users` table includes brute-force lockout columns (`failed_login_attempts`, `locked_until`)

### Increment 3 — Security Layer
- `SecurityConfig.java`: role-based route protection (`/teacher/**`, `/student/**`)
- `CustomUserDetailsService.java`: loads users from DB for Spring Security
- BCrypt password encoding
- Role-based redirect on login success (Teacher → `/teacher/dashboard`, Student → `/student/dashboard`)
- Single concurrent session enforcement
- CSRF enabled (excluded on `/api/**` for polling endpoints)

### Increment 4 — Domain Model & Repositories
- JPA entities: `User`, `Quiz`, `Question`, `QuestionOption`, `Attempt`, `Answer`, `GenerationJob`
- Enums: `UserRole`, `QuizStatus`, `QuizScope`, `QuestionType`, `AttemptStatus`, `JobStatus`
- Spring Data repositories for all entities
- DTO layer in `common/` package (immutable records per architecture spec)

### Increment 5 — AI Integration
- `GeminiConfig.java`: WebClient configured for Anthropic Claude API (named Gemini — naming artifact)
- `GeminiClient.java`: Claude API call with prompt caching (`cache_control: ephemeral` on system prompt)
- `PromptBuilder.java`: constructs quiz generation prompt from extracted file text
- `ResponseParser.java`: parses Claude JSON response into `ParsedQuestion` objects
- `QuizGenerationServiceImpl.java`: orchestrates full generation pipeline
- `QuizGenerationAsyncExecutor.java`: `@Async` wrapper that updates `GenerationJob` status

### Increment 6 — File Processing
- `TikaFileTextExtractor.java`: Apache Tika parses PDF, DOCX, CSV, XLS/XLSX, TXT, MD
- `InMemoryMultipartFile.java`: utility for in-memory file handling (never written to disk)
- File content sanitized before being inserted into AI prompt (prompt injection mitigation)

### Increment 7 — Controllers & Templates
- `AuthController.java`: login, register, logout
- `QuizController.java`: generate, status polling, take quiz, submit
- `StudentController.java`: student dashboard, upload, attempt history
- `TeacherController.java`: teacher dashboard, results, grading queue
- `RootController.java`: root redirect
- Thymeleaf templates for all views in `templates/` (auth, student, teacher, quiz, error)
- `layout.html` fragment for shared navigation

### Increment 8 — Grading & Results
- `GradingService.java`: auto-grades MC and T/F questions; queues free response for teacher review
- `ResultService.java`: calculates and stores final scores on attempt submission
- `AttemptService.java`: manages attempt lifecycle (start → in-progress → submitted)

### Increment 9 — Error Handling & Logging
- Full exception hierarchy under `AppException`: Validation, Authentication, Authorization, ResourceNotFound, FileProcessing, AIService, System
- Predefined error codes in `ErrorCodes.java` (AUTH-*, FILE-*, AI-*, QUIZ-*, GRADE-*, SYS-*)
- `GlobalExceptionHandler.java`: `@ControllerAdvice` maps exceptions to HTTP status + error view
- `CustomErrorController.java`: handles servlet-level errors
- `logback-spring.xml`: three-appender setup (CONSOLE, FILE, ERRORS) with daily rolling

### Increment 10 — Agent Documentation Structure
- `agents/` folder created at project root
- Subfolders per agent, each with a student owner subfolder:
  - `Architecture Agent/Svarun/`
  - `Development Agent/Rojitha/`
  - `Testing Creation Agent/Juliann/`
  - `Dev-Ops Agent/Chris/`
- `Architecture_Agent.md` populated from architecture PDF (v1.0)
- `Dev-Ops_Agent.md` generated from codebase state (this document)

---

## Outstanding Items

| Item | Status | Notes |
|------|--------|-------|
| Run the application | Blocked | `DATABASE_URL` and `ANTHROPIC_API_KEY` not set in local environment |
| Flyway checksum mismatch | Mitigated | `validate-on-migrate=false` prevents startup failure; do not edit applied migrations |
| CI/CD pipeline | Not started | No GitHub Actions or equivalent configured yet |
| Production deployment | Not started | No hosting target defined yet |
| Testing suite | In progress | Testing Creation Agent (Juliann) — targeted for today |
| `spring.thymeleaf.cache=false` | Dev-only | Must be set to `true` in production for performance |

---

## Future CI/CD Recommendations

When a pipeline is configured, the following stages are recommended:

```
1. mvn test               → unit + integration tests (requires test DB)
2. mvn package -DskipTests → build JAR artifact
3. flyway migrate          → apply pending migrations to target DB
4. java -jar *.jar         → deploy with env vars injected by CI secrets
```

Secrets (`DATABASE_URL`, `ANTHROPIC_API_KEY`) should be stored in the CI/CD platform's secret store (e.g. GitHub Actions Secrets, Render environment config) — never in the repository.

---

*JQuizGen DevOps Document · v1.0 · Generated May 5, 2026 · Dev-Ops Agent (Chris)*
