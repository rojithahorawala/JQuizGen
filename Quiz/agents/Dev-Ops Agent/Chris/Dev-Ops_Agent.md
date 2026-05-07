# JQuizGen — DevOps Document · V1.1
**AI-Powered Quiz Generator — Operational & Deployment Reference**

| Field | Value |
|-------|-------|
| Original Date | May 5, 2026 |
| Last Updated | May 6, 2026 |
| Phase | Active Development |
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

The application will **not start** without these four variables set in the runtime environment. They must never be committed to source control.

| Variable | Used As | Where Referenced |
|----------|---------|-----------------|
| `DB_URL` | JDBC connection string (no embedded credentials) | `application.properties` → `spring.datasource.url` |
| `DB_USER` | Database username | `application.properties` → `spring.datasource.username` |
| `DB_PASSWORD` | Database password | `application.properties` → `spring.datasource.password` |
| `ANTHROPIC_API_KEY` | Claude API auth key | `application.properties` → `claude.api.key` → `GeminiConfig.java` |

### Setting Variables Locally

```bash
export DB_URL=jdbc:postgresql://<host>/<dbname>?sslmode=require
export DB_USER=<username>
export DB_PASSWORD=<password>
export ANTHROPIC_API_KEY=sk-ant-...
```

> **Note 1:** Credentials are split into three separate variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`) rather than a single `DATABASE_URL` because the PostgreSQL JDBC driver rejects embedded-credentials URL format (`user:pass@host`).

> **Note 2:** The property key is `claude.api.key` and the config class is named `GeminiConfig` — this is a naming artifact from early development when Gemini was the planned AI provider. The live implementation calls the Anthropic Claude API.

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

Both require `DB_URL`, `DB_USER`, `DB_PASSWORD`, and `ANTHROPIC_API_KEY` to be exported in the environment first.

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

### Schema — V3 (current)

Managed by Flyway. Three migrations applied.

**Tables:**

| Table | Purpose |
|-------|---------|
| `users` | Accounts for both students and teachers, includes `role`, `failed_login_attempts`, `locked_until` |
| `quizzes` | Quiz metadata — title, creator, scope (PERSONAL/CLASS), status, question count |
| `questions` | Individual questions — type, correct answer key, points, order |
| `question_options` | Multiple-choice options linked to questions |
| `attempts` | Student quiz attempts — start/submit timestamps, score, status |
| `answers` | Per-question student answers — grading fields, `ai_feedback` (V2), UNIQUE `(attempt_id, question_id)` constraint (V3) |
| `generation_jobs` | Async AI generation tracking — job status, error codes, timestamps |

**Applied migrations:**

| File | Purpose |
|------|---------|
| `V1__init_schema.sql` | Initial full schema (7 tables) |
| `V2__add_ai_feedback.sql` | `ai_feedback TEXT` column on `answers` |
| `V3__unique_answer_per_attempt_question.sql` | Deduplicates answer rows; adds `UNIQUE(attempt_id, question_id)` constraint |

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
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Hibernate batch writes
spring.jpa.properties.hibernate.jdbc.batch_size=25
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.batch_versioned_data=true

# HikariCP connection pool
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=3
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=600000

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
spring.thymeleaf.cache=true
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

### Increment 11 — Performance Optimisations
- `spring.thymeleaf.cache=true` enabled (was `false`)
- Hibernate batch INSERTs/UPDATEs: `jdbc.batch_size=25`, `order_inserts=true`, `order_updates=true`
- HikariCP pool tuned: `maximum-pool-size=10`, `minimum-idle=3`, `connection-timeout=20000`
- `@BatchSize` added to `User`, `Quiz`, `Question` classes and `answers`/`options` collections to prevent N+1 queries
- `AttemptRepository` list queries replaced with explicit `JOIN FETCH` JPQL to load `quiz` and `student` eagerly
- `AttemptService.toSummaryDto()` introduced — list views (dashboard, results) skip loading answers; only the grading page loads full answer data via `toDto()`
- `answerRepository.saveAll()` used for batch answer INSERT on quiz submit
- `GradingService.gradeAutomatic()` uses `saveAll()` for batch UPDATE and avoids re-fetching already-loaded answer list

### Increment 12 — Question Type Selection
- Upload forms (student and teacher) now show checkboxes for `MULTIPLE_CHOICE`, `TRUE_FALSE`, `FREE_RESPONSE`
- JS validation prevents submission if no type selected
- `QuizGenerationService.generateQuizAsync()` accepts `List<String> questionTypes`
- `PromptBuilder.buildQuizPrompt()` dynamically adjusts question distribution and JSON examples based on selected types
- Distribution: single type = 100%; two types = ceiling/floor split; all three = 40/30/30 (MC/TF/FR)

### Increment 13 — Grading Bug Fixes
- `AnswerRepository.findFirstByAttemptIdAndQuestionId` replaces `findByAttemptIdAndQuestionId` — handles non-unique rows gracefully without throwing `IncorrectResultSizeDataAccessException`
- `V3__unique_answer_per_attempt_question.sql` migration: deduplicates existing rows, enforces DB-level uniqueness
- `AnswerDto` gains `maxPoints` field (11th record component) to drive slider max value
- Grading page slider: replaced `<input type="number">` with `<input type="range">` showing live point value
- Fixed Thymeleaf `th:attr` parsing failure: `oninput` with embedded `&quot;` entities replaced by `data-target` attribute + static JS handler
- `GradingService.doRecalculateScore()` private method introduced to avoid redundant DB fetch in `gradeAutomatic()`

---

## Outstanding Items

| Item | Status | Notes |
|------|--------|-------|
| Run the application | **Complete** | Server running on port 8080; credentials passed as `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ANTHROPIC_API_KEY` |
| Flyway checksum mismatch | Mitigated | `validate-on-migrate=false` prevents startup failure; do not edit already-applied migrations |
| V3 migration (unique constraint) | **Applied** | Duplicate answer rows removed; `UNIQUE(attempt_id, question_id)` enforced in DB |
| Testing suite | **Complete** | 60 tests · 0 failures — see Testing Creation Agent (Juliann) |
| `spring.thymeleaf.cache` | **Done** | Set to `true` for performance |
| CI/CD pipeline | Not started | No GitHub Actions or equivalent configured yet |
| Production deployment | Not started | No hosting target defined yet |

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

*JQuizGen DevOps Document · v1.1 · Updated May 6, 2026 · Dev-Ops Agent (Chris)*
