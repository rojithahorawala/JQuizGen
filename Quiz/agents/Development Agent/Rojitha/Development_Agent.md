# JQuizGen — Development Document · V1.1
**AI-Powered Quiz Generator — Implementation Reference**

| Field | Value |
|-------|-------|
| Original Date | May 5, 2026 |
| Last Updated | May 6, 2026 |
| Phase | Active Development |
| Agent | Development Agent (Rojitha) |
| Tool | Claude Code (claude-sonnet-4-6) |
| Model | claude-haiku-4-5-20251001 (AI generation) |

> This document is generated and updated incrementally with the application. Each section reflects the actual implemented code — not the planned architecture.

---

## Project Structure

```
com.quizgen
├── JQuizGenApplication.java          # Entry point
├── auth/
│   ├── AuthController.java           # GET/POST /auth/login, /auth/register
│   ├── AuthService.java              # User registration logic
│   └── CustomUserDetailsService.java # Spring Security user loading + lockout check
├── user/
│   ├── User.java                     # @Entity — users table
│   ├── UserRepository.java           # findByEmail, existsByEmail
│   └── UserRole.java                 # STUDENT, TEACHER
├── config/
│   ├── SecurityConfig.java           # Route protection, login/logout, session config
│   ├── GeminiConfig.java             # Claude API WebClient bean
│   └── AsyncConfig.java              # ThreadPoolTaskExecutor (core 4, max 8, queue 100)
├── ai/
│   ├── GeminiClient.java             # Claude API HTTP calls (prompt caching)
│   ├── PromptBuilder.java            # Constructs quiz generation prompt
│   ├── ResponseParser.java           # Parses Claude JSON → ParsedQuestion list
│   ├── ParsedQuestion.java           # Record: type, text, options, correctAnswer, points
│   ├── GenerationJob.java            # @Entity — generation_jobs table
│   ├── GenerationJobRepository.java
│   ├── JobStatus.java                # PENDING, PROCESSING, READY, FAILED
│   ├── AIFeedbackService.java        # Synchronous Claude feedback for wrong MC/TF answers
│   ├── QuizGenerationService.java    # Interface (accepts questionTypes list)
│   ├── QuizGenerationServiceImpl.java# Reads files, creates job, fires async executor
│   └── QuizGenerationAsyncExecutor.java # @Async — extract → prompt → call AI → save quiz
├── quiz/
│   ├── Quiz.java                     # @Entity — quizzes table
│   ├── Question.java                 # @Entity — questions table
│   ├── QuestionOption.java           # @Entity — question_options table
│   ├── QuizRepository.java           # Custom queries for scope/status filtering
│   ├── QuestionRepository.java
│   ├── QuestionOptionRepository.java
│   ├── QuizScope.java                # PERSONAL, UNIVERSAL
│   ├── QuizStatus.java               # GENERATING, READY, ACTIVE, COMPLETED
│   ├── QuestionType.java             # MULTIPLE_CHOICE, TRUE_FALSE, FREE_RESPONSE
│   ├── QuizService.java              # DTO-returning service, @PreAuthorize guarded
│   ├── QuizController.java           # /quiz/** — status polling, take, submit, complete
│   ├── StudentController.java        # /student/** — dashboard, upload, attempt detail
│   ├── TeacherController.java        # /teacher/** — dashboard, upload, grade, results
│   └── RootController.java           # Redirects / → /auth/login
├── attempt/
│   ├── Attempt.java                  # @Entity — attempts table
│   ├── Answer.java                   # @Entity — answers table
│   ├── AttemptRepository.java        # Custom queries for grading queue, history
│   ├── AnswerRepository.java
│   ├── AttemptStatus.java            # IN_PROGRESS, SUBMITTED, GRADED
│   └── AttemptService.java           # startAttempt, submitAttempt, getAttempt, history
├── grading/
│   └── GradingService.java           # gradeAutomatic (MC/TF), submitManualGrade, recalculateScore
├── result/
│   └── ResultService.java            # Thin wrapper — delegates to AttemptService
├── file/
│   ├── FileTextExtractor.java        # Interface: extractText(MultipartFile)
│   ├── TikaFileTextExtractor.java    # Apache Tika implementation — 8 formats
│   └── InMemoryMultipartFile.java    # Utility: wraps byte[] as MultipartFile for async boundary
└── common/
    ├── AppException.java             # Base unchecked exception with errorCode field
    ├── ValidationException.java      # HTTP 400
    ├── AuthenticationException.java  # HTTP 401
    ├── AuthorizationException.java   # HTTP 403
    ├── ResourceNotFoundException.java# HTTP 404
    ├── FileProcessingException.java  # HTTP 422
    ├── AIServiceException.java       # HTTP 502
    ├── SystemException.java          # HTTP 500
    ├── ErrorCodes.java               # AUTH-*, FILE-*, AI-*, QUIZ-*, GRADE-*, SYS-* constants
    ├── GlobalExceptionHandler.java   # @ControllerAdvice → error/error.html view
    ├── CustomErrorController.java    # Servlet-level error fallback
    ├── RegisterRequest.java          # Form binding: email, username, password, role
    ├── QuizDto.java                  # Immutable record for quiz data transfer
    ├── QuestionDto.java
    ├── QuestionOptionDto.java
    ├── AttemptDto.java
    ├── AnswerDto.java
    ├── GenerationJobDto.java         # Record: jobId
    └── GenerationStatusDto.java      # Record: status, quizId, errorCode
```

---

## Route Map

### Auth (`/auth`)

| Method | Path | Handler | Access |
|--------|------|---------|--------|
| GET | `/auth/login` | `AuthController#loginPage` | Public |
| POST | `/auth/login` | Spring Security | Public |
| GET | `/auth/register` | `AuthController#registerPage` | Public |
| POST | `/auth/register` | `AuthController#register` | Public |
| POST | `/auth/logout` | Spring Security | Authenticated |

### Quiz (`/quiz`)

| Method | Path | Handler | Access |
|--------|------|---------|--------|
| GET | `/quiz/status/{jobId}` | `QuizController#getStatus` | Authenticated (JSON) |
| GET | `/quiz/generating/{jobId}` | `QuizController#generatingPage` | Authenticated |
| GET | `/quiz/take/{quizId}` | `QuizController#takeQuiz` | STUDENT only |
| POST | `/quiz/submit/{attemptId}` | `QuizController#submitQuiz` | STUDENT only |
| GET | `/quiz/complete/{attemptId}` | `QuizController#completePage` | Authenticated |

### Student (`/student`)

| Method | Path | Handler | Access |
|--------|------|---------|--------|
| GET | `/student/dashboard` | `StudentController#dashboard` | STUDENT |
| GET | `/student/upload` | `StudentController#uploadPage` | STUDENT |
| POST | `/student/upload` | `StudentController#upload` | STUDENT |
| GET | `/student/attempts/{attemptId}` | `StudentController#attemptDetail` | STUDENT (own only) |

### Teacher (`/teacher`)

| Method | Path | Handler | Access |
|--------|------|---------|--------|
| GET | `/teacher/dashboard` | `TeacherController#dashboard` | TEACHER |
| GET | `/teacher/upload` | `TeacherController#uploadPage` | TEACHER |
| POST | `/teacher/upload` | `TeacherController#upload` | TEACHER |
| GET | `/teacher/grade` | `TeacherController#gradePage` | TEACHER |
| POST | `/teacher/grade/{attemptId}/question/{questionId}` | `TeacherController#submitGrade` | TEACHER |
| GET | `/teacher/quiz/{quizId}` | `TeacherController#viewQuiz` | TEACHER |
| GET | `/teacher/results/{quizId}` | `TeacherController#results` | TEACHER |

---

## Domain Model

### Entities & Relationships

```
User (1) ──────────────────── (many) Quiz
  │ id, email, username,               │ id, title, scope, status,
  │ passwordHash, role,                │ questionCount, createdAt
  │ failedLoginAttempts,               │
  │ lockedUntil, createdAt             │
  │                            (1) ────┘
  │                            │
  │                     (many) Question
  │                            │ id, questionText, questionType,
  │                            │ correctAnswer, points, orderIndex
  │                            │
  │                     (many) QuestionOption
  │                            │ id, optionText, isCorrect
  │
  └─── (many) Attempt ──────── (1) Quiz
         │ id, startedAt,
         │ submittedAt, score,
         │ status
         │
    (many) Answer ─── (1) Question
           │ id, answerText,
           │ isCorrect, pointsAwarded
  │
  └─── (many) GenerationJob ── (1) Quiz
         id, status, errorCode,
         createdAt, completedAt
```

### Enums

| Enum | Values |
|------|--------|
| `UserRole` | `STUDENT`, `TEACHER` |
| `QuizScope` | `PERSONAL` (student upload), `UNIVERSAL` (teacher upload — visible to all students) |
| `QuizStatus` | `GENERATING`, `READY`, `ACTIVE`, `COMPLETED` |
| `QuestionType` | `MULTIPLE_CHOICE`, `TRUE_FALSE`, `FREE_RESPONSE` |
| `AttemptStatus` | `IN_PROGRESS`, `SUBMITTED`, `GRADED` |
| `JobStatus` | `PENDING`, `PROCESSING`, `READY`, `FAILED` |

---

## Key Implementation Details

### 1. Authentication & Security

**`CustomUserDetailsService`** — Spring Security loads users by email (not username). Before returning `UserDetails`, it checks `lockedUntil`: if the timestamp is in the future, it throws `LockedException`, blocking login without reaching the password check.

**`SecurityConfig`** — Role-based redirect on login success: Teachers go to `/teacher/dashboard`, Students to `/student/dashboard`. Implemented via a custom `AuthenticationSuccessHandler` that inspects the `GrantedAuthority` list.

**Session config** — Maximum 1 concurrent session per user (`maximumSessions(1)`). A second login from another browser expires the first session and redirects to `/auth/login?expired=true`.

**CSRF** — Enabled globally; explicitly ignored for `/api/**` to allow the status polling endpoint to work without a CSRF token from JavaScript.

---

### 2. Quiz Generation Pipeline

The generation pipeline crosses an async boundary. The key challenge was that Spring's `@Async` runs in a different thread where the `MultipartFile` stream is no longer readable. This was solved by eagerly reading file bytes before the async call:

**`QuizGenerationServiceImpl`** — reads all file bytes into `List<byte[]>` while still in the request thread, then passes raw bytes to the async executor.

**`QuizGenerationAsyncExecutor`** — reconstructs `MultipartFile` objects from raw bytes using `InMemoryMultipartFile`, then runs the full pipeline:

```
1. job.status = PROCESSING
2. For each file: TikaFileTextExtractor.extractText() → combined text
3. PromptBuilder.buildQuizPrompt(text, questionCount) → prompt string
4. GeminiClient.generateContent(prompt) → Claude API call
5. ResponseParser.parse(response) → List<ParsedQuestion>
6. saveQuizWithQuestions() → Quiz persisted with Questions + QuestionOptions
7. job.status = READY, job.quiz = savedQuiz
```

On any exception, `job.status = FAILED` and `job.errorCode` is set based on the exception type.

---

### 3. Prompt Engineering

**`PromptBuilder`** — question count is clamped to 4–25. Distribution is calculated dynamically based on which types are selected:
- All 3 types: MC 40%, TF 30%, FR remainder
- 2 types: ceiling/floor split; MC gets the extra question when present
- 1 type: 100% of that type

The user selects which types to generate via checkboxes on the upload form (all three selected by default). A `List<String> questionTypes` parameter flows through `QuizGenerationService → QuizGenerationAsyncExecutor → PromptBuilder`. JS validation on the form prevents submission with no types selected.

The prompt explicitly instructs Claude to return only valid JSON (no markdown), ignore instructions embedded in uploaded content (prompt injection mitigation), and use the exact schema required by `ResponseParser`. JSON examples in the prompt use `String.join(",\n", ...)` to avoid trailing commas when only a subset of types is requested.

**`ResponseParser`** — handles the case where Claude wraps its response in markdown code fences (` ```json ... ``` `) by stripping them before parsing. This is a real-world robustness fix — models sometimes add fences even when told not to.

---

### 4. AI Client — Naming Note

The class is named `GeminiClient` and the config class is `GeminiConfig`, but the implementation calls the **Anthropic Claude API** (`api.anthropic.com/v1/messages`). This is a naming artifact from early development when Gemini was the intended provider. The actual API key env var is `ANTHROPIC_API_KEY`.

**Prompt caching** is enabled via the `anthropic-beta: prompt-caching-2024-07-31` header. The system prompt is sent with `"cache_control": {"type": "ephemeral"}`, reducing token costs on repeated calls to the same endpoint.

---

### 5. File Parsing

**`TikaFileTextExtractor`** — uses Apache Tika's `AutoDetectParser` which identifies the file type from its content (not just the extension). Allowed extensions are whitelisted: `pdf, docx, doc, csv, xls, xlsx, txt, md`. `BodyContentHandler(-1)` is used with no size limit to handle large files.

**`InMemoryMultipartFile`** — a custom `MultipartFile` implementation that wraps a `byte[]`. Needed because `MultipartFile` is a request-scoped object that becomes invalid after the HTTP request completes, but the async processing happens after that point.

---

### 6. Grading

**`GradingService.gradeAutomatic()`** — called automatically on `submitAttempt()`. Iterates all answers; for `MULTIPLE_CHOICE` and `TRUE_FALSE`, compares `answer.answerText` to `question.correctAnswer` (case-insensitive). Sets `isCorrect` and `pointsAwarded`.

**`recalculateScore()`** — called after both auto-grading and manual grading. Checks whether any `FREE_RESPONSE` answers still have `pointsAwarded == null`. If all answers are graded, computes the percentage score using `BigDecimal` with `HALF_UP` rounding and transitions the attempt to `AttemptStatus.GRADED`.

**Manual grading** — `submitManualGrade()` is `@PreAuthorize("hasRole('TEACHER')")`. Takes `pointsAwarded`; sets `isCorrect = (pointsAwarded > 0)`. Then recalculates the overall score.

**`findFirstByAttemptIdAndQuestionId`** — the repository method for locating an answer by attempt+question uses `findFirst` rather than the plain derived-query form. This prevents `IncorrectResultSizeDataAccessException` if duplicate rows existed before the V3 UNIQUE constraint was applied. The V3 migration deduplicates historical rows and the constraint prevents new duplicates.

**Score recalculation** — `doRecalculateScore(Long attemptId, List<Answer> answers)` accepts an already-loaded answer list to avoid a redundant DB fetch when called from `gradeAutomatic()`. The `recalculateScore(Long)` protected method re-fetches answers from DB and delegates to `doRecalculateScore`, used by `submitManualGrade`.

**Grading UI** — the teacher grading page uses `<input type="range">` (slider) instead of a number input. The slider's `data-target` attribute stores the ID of the live-value `<span>`, and the `oninput` handler reads `this.dataset.target` to update it. This avoids embedding quoted strings inside Thymeleaf `th:attr` expressions, which caused a `TemplateProcessingException`.

---

### 7. Performance — N+1 Prevention & Batch Writes

**`@BatchSize`** annotations on entity classes and collections tell Hibernate to load lazy proxies in batches instead of one-by-one. Applied to `User` (25), `Quiz` (25), `Question` (50), and the `answers` (50) and `options` (100) collections.

**JOIN FETCH queries** in `AttemptRepository` — all three list queries use explicit JPQL with `JOIN FETCH a.quiz JOIN FETCH a.student` so that quiz and student data is loaded in a single SQL join, not via separate proxy loads.

**`toSummaryDto()` vs `toDto()`** — list views (student dashboard, teacher results) call `toSummaryDto()` which returns an empty `List.of()` for answers, avoiding loading answer data that those views don't display. The grading queue uses `toDto()` which loads full answer data.

**Batch writes** — `submitAttempt()` uses `answerRepository.saveAll()` for one batch INSERT per quiz submission. `gradeAutomatic()` collects all modified answers then calls `saveAll()` for one batch UPDATE.

### 8. QuizService — Data Access Patterns

`QuizService` uses `@PreAuthorize` at the method level rather than only at the controller level, providing a second layer of enforcement:

| Method | Guard |
|--------|-------|
| `getQuizById` | `isAuthenticated()` |
| `getAvailableQuizzesForStudent` | none (called from STUDENT-guarded controller) |
| `getQuizzesByTeacher` | `hasRole('TEACHER')` |
| `getPersonalQuizzesByStudent` | `hasRole('STUDENT')` |

Entity → DTO mapping is done inside the service — controllers never see `@Entity` objects directly (per architecture contract).

---

### 9. AttemptService — Circular Dependency Resolution

`AttemptService` depends on `GradingService`, and `GradingService` uses `AttemptRepository`. This creates a potential circular bean dependency. Resolved by annotating the `GradingService` constructor parameter in `AttemptService` with `@Lazy` — Spring creates a proxy and injects it lazily on first use.

---

### 10. Error Handling

**`GlobalExceptionHandler`** — `@ControllerAdvice` catches all `AppException` subclasses and returns the `error/error.html` Thymeleaf view with `status`, `errorCode`, and `errorMessage` model attributes.

**Spring Security exceptions** (`AccessDeniedException`, `AuthenticationException`) are explicitly re-thrown in the catch-all `handleGeneral` handler — Spring Security must handle these itself to trigger its redirect logic (login page, 403 page). Catching and swallowing them would break the security flow.

---

## Development Decisions Log

| Decision | What | Why |
|----------|------|-----|
| File bytes read before `@Async` | `List<byte[]>` passed across async boundary | `MultipartFile` stream closes after HTTP request; bytes must be captured synchronously |
| `@Lazy` on `GradingService` in `AttemptService` | Circular dependency break | `AttemptService` → `GradingService` → `AnswerRepository` → no cycle to `AttemptService`, but Spring's eager init sees it as circular |
| `validate-on-migrate=false` | Flyway property | `V1__init_schema.sql` was edited after first apply; this prevents startup failure during development |
| `BodyContentHandler(-1)` | Tika no size limit | Default handler has a 100,000 char cap — large documents would be silently truncated |
| Re-throw Spring Security exceptions in catch-all | `GlobalExceptionHandler` | Spring Security's redirect chain breaks if these are caught and converted to `ModelAndView` |
| Prompt caching | `cache_control: ephemeral` on system prompt | Reduces Claude API token costs on repeated generation calls |
| `InMemoryMultipartFile` | Custom `MultipartFile` | Allows passing file content across thread boundary without filesystem writes |
| `@OrderBy("orderIndex ASC")` on `Quiz.questions` | JPA annotation | Ensures questions are always returned in authored order without explicit sorting in service layer |
| `BigDecimal` with `HALF_UP` for scores | `recalculateScore()` | Avoids floating-point rounding errors on percentage calculations |
| `@BatchSize` on entities and collections | Hibernate annotation | Batches lazy proxy loads so N attempts → 1 batch query, not N queries |
| `JOIN FETCH` on all `AttemptRepository` list queries | JPQL | Eliminates separate proxy loads for `quiz` and `student` on every attempt in a list |
| `toSummaryDto()` skips answer loading | `AttemptService` | Dashboard/results list views don't display answers; skipping them avoids loading hundreds of rows |
| `findFirstByAttemptIdAndQuestionId` | `AnswerRepository` | Returns first match safely; plain `findBy...` throws if duplicate rows exist (pre-V3 data) |
| `data-target` attribute on grade slider | `teacher/grade.html` | Stores dynamic element ID in a plain `data-*` attribute; avoids embedding quotes inside `th:attr` Thymeleaf expressions which causes `TemplateProcessingException` |
| `String.join(",\n", exampleLines)` in `PromptBuilder` | Prompt construction | Prevents trailing commas in JSON examples when only a subset of question types is selected |
| Split DB credentials (`DB_URL`, `DB_USER`, `DB_PASSWORD`) | `application.properties` | PostgreSQL JDBC driver rejects embedded-credential URL format (`user:pass@host`) |

---

## Templates

All views use Thymeleaf with a shared `layout.html` fragment.

| Template | Route | Role |
|----------|-------|------|
| `auth/login.html` | `/auth/login` | Public |
| `auth/register.html` | `/auth/register` | Public |
| `student/dashboard.html` | `/student/dashboard` | STUDENT |
| `student/upload.html` | `/student/upload` | STUDENT |
| `student/attempt-detail.html` | `/student/attempts/{id}` | STUDENT |
| `quiz/generating.html` | `/quiz/generating/{jobId}` | Authenticated |
| `quiz/take.html` | `/quiz/take/{quizId}` | STUDENT |
| `quiz/complete.html` | `/quiz/complete/{attemptId}` | Authenticated |
| `teacher/dashboard.html` | `/teacher/dashboard` | TEACHER |
| `teacher/upload.html` | `/teacher/upload` | TEACHER |
| `teacher/grade.html` | `/teacher/grade` | TEACHER |
| `teacher/quiz-detail.html` | `/teacher/quiz/{quizId}` | TEACHER |
| `teacher/results.html` | `/teacher/results/{quizId}` | TEACHER |
| `error/error.html` | (all errors) | Any |
| `fragments/layout.html` | (shared fragment) | — |

---

## Current State

| Component | Status |
|-----------|--------|
| Auth (register / login / logout) | Complete |
| Role-based routing | Complete |
| Account lockout | Schema ready — lockout enforcement in `CustomUserDetailsService`; lockout-on-failure trigger not yet wired to login failure handler |
| File upload + Tika extraction | Complete |
| Claude API integration + prompt caching | Complete |
| Async generation with job polling | Complete |
| Question type selection (MC / TF / FR checkboxes) | **Complete** |
| Quiz persistence (MC, TF, FR) | Complete |
| Auto-grading (MC, TF) | Complete |
| Manual grading (FR) with slider UI | **Complete** |
| Score calculation | Complete |
| Student dashboard | Complete |
| Teacher dashboard | Complete |
| Error handling + error page | Complete |
| Logging (3 appenders) | Complete |
| Performance optimisations (batch, pool, cache) | **Complete** |
| Database running | **Complete** — credentials passed as `DB_URL`, `DB_USER`, `DB_PASSWORD` |
| Application startup | **Complete** — server running on port 8080 |
| Tests | **Complete** — 60 tests · 0 failures |

---

## Outstanding Implementation Notes

- **Account lockout trigger**: `failed_login_attempts` and `locked_until` columns exist in the DB and `CustomUserDetailsService` checks `locked_until` on login. However, the logic to *increment* `failed_login_attempts` and set `locked_until` on a failed password attempt has not yet been wired into the authentication failure handler. This is the remaining piece of the brute-force protection.

- **Quiz scope visibility**: `UNIVERSAL` quizzes (teacher-created) appear in the student dashboard under "Available Quizzes". `PERSONAL` quizzes (student-created) are only visible to the student who created them — teachers cannot see them. This privacy rule is enforced at the query level in `QuizRepository`.

- **Quiz status state machine**: Quizzes are saved with `QuizStatus.READY` immediately when generation completes. The `GENERATING`, `ACTIVE`, and `COMPLETED` statuses exist in the enum and schema but transitions for `ACTIVE` and `COMPLETED` are not yet implemented in the service layer.

---

*JQuizGen Development Document · v1.1 · Updated May 6, 2026 · Development Agent (Rojitha)*
