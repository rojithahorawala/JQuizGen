# JQuizGen — Architecture Document · V1.0
**AI-Powered Quiz Generator — Web Application**

| Field | Value |
|-------|-------|
| Date | April 24, 2026 |
| Phase | Architecture |
| Stack | Java · Spring Boot · Thymeleaf · PostgreSQL |
| AI | Google Gemini 2.0 Flash |
| Standard | ISO/IEC 25010 |

---

## 1. Architecturally Significant Requirements (ASRs)

Requirements selected per ISO/IEC 25010. These are the constraints that are hardest to change once development begins.

| ASR | Quality Attribute | Why It's Hard to Change |
|-----|-------------------|------------------------|
| Role-based access (Student vs Teacher) | Security | Touches every layer — auth, routing, data visibility |
| AI-generated questions from uploaded files | Functional Suitability | Determines the entire processing pipeline |
| File format support (PDF, DOCX, CSV, XLS/XLSX, TXT, MD) | Compatibility | Parsing library choice locks in format handling |
| Auto-grading vs manual grading | Functional Suitability | Two distinct workflows that diverge at data model level |
| Max 3 file uploads per session, 10MB each | Reliability / Security | Enforced at multiple layers: UI, backend, storage |
| PostgreSQL persistence via Neon (free cloud) | Portability | Schema design and ORM strategy depend on this |
| Free-tier AI API (Gemini 2.0 Flash) | Performance Efficiency | Rate limits (15 RPM / 1M tokens/day) affect async strategy |
| Scores, attempt count, and date stored | Functional Suitability | Core audit data — schema must be correct from the start |

---

## 2. Framework Validation & Module Structure

### Core Technology Stack

| Layer | Technology | Reason |
|-------|------------|--------|
| Framework | Spring Boot 3.x | Industry-standard Java web, auto-config, wide ecosystem |
| Frontend | Thymeleaf + Bootstrap 5 | Server-rendered, stays in Java, no separate frontend build |
| Security | Spring Security | Native RBAC, CSRF, session management |
| ORM | Spring Data JPA + Hibernate | PostgreSQL abstraction, migrations via Flyway |
| File Parsing | Apache Tika | Single library handles all 6 formats |
| AI Integration | Google Gemini 2.0 Flash (REST) | Free tier, large context window, structured JSON output |
| Logging | SLF4J + Logback | Spring Boot default, flexible output formats |
| DB Migrations | Flyway | Versioned, reproducible schema changes |
| Database | PostgreSQL via Neon | Free cloud PostgreSQL, serverless, dev/prod branching |
| Build Tool | Maven | Standard, excellent Spring Boot integration |

### Module Structure

```
com.quizgen
├── auth/       # Login, registration, session, roles
├── user/       # User profiles (Student, Teacher)
├── file/       # Upload handling, validation, text extraction
├── ai/         # Gemini client, prompt engineering, response parsing
├── quiz/       # Quiz entity, question types, quiz state machine
├── attempt/    # Student attempts, answer submission
├── grading/    # Auto-grader (MC/T/F), free-response grading queue
├── result/     # Score storage, attempt history
├── common/     # DTOs, exceptions, error codes, utilities
└── config/     # Security config, app config, Gemini config
```

---

## 3. Error Handling Strategy

Strategy: predefined error codes + centralized `@ControllerAdvice` handler + separate log file for unknown system errors.

### Exception Hierarchy

```
AppException (base, unchecked)
├── ValidationException       → HTTP 400
├── AuthenticationException   → HTTP 401
├── AuthorizationException    → HTTP 403
├── ResourceNotFoundException → HTTP 404
├── FileProcessingException   → HTTP 422
├── AIServiceException        → HTTP 502
└── SystemException           → HTTP 500
```

### Predefined Error Codes

| Code | Message |
|------|---------|
| AUTH-001 | Invalid credentials |
| AUTH-002 | Insufficient permissions |
| AUTH-003 | Session expired |
| FILE-001 | Unsupported file type |
| FILE-002 | File exceeds 10MB size limit |
| FILE-003 | Maximum of 3 files per upload exceeded |
| FILE-004 | File content could not be parsed |
| AI-001 | AI service unavailable |
| AI-002 | AI response format invalid |
| AI-003 | AI rate limit reached (free tier) |
| QUIZ-001 | Quiz not found |
| QUIZ-002 | No questions could be generated |
| QUIZ-003 | Quiz already completed |
| GRADE-001 | Attempt already submitted |
| GRADE-002 | Free response not yet graded |
| SYS-001 | Unexpected system error (logged separately) |

### Structured Error Response

```json
{
  "code": "FILE-003",
  "message": "You may upload a maximum of 3 files.",
  "timestamp": "2026-04-24T10:00:00Z"
}
```

---

## 4. Logging Strategy

Tooling: SLF4J + Logback. JSON format in production, plain text in development. Unknown system errors routed to a separate `errors.log`.

| Event | Level | Example Log Entry |
|-------|-------|-------------------|
| User registered | INFO | `USER_REGISTERED userId=42 role=STUDENT` |
| User login / logout | INFO | `USER_LOGIN userId=42` |
| File uploaded | INFO | `FILE_UPLOADED userId=42 fileName=notes.pdf size=1.2MB` |
| File parsed successfully | INFO | `FILE_PARSED fileName=notes.pdf chars=8420` |
| File parse failed | ERROR | `FILE_PARSE_FAILED fileName=notes.pdf error=...` |
| AI quiz generation started | INFO | `QUIZ_GENERATION_STARTED userId=42 fileCount=2` |
| AI quiz generated | INFO | `QUIZ_GENERATED quizId=7 questions=15 duration=3.2s` |
| AI generation failed | ERROR | `QUIZ_GENERATION_FAILED userId=42 errorCode=AI001` |
| Student starts attempt | INFO | `ATTEMPT_STARTED attemptId=99 quizId=7 userId=42` |
| Student submits quiz | INFO | `ATTEMPT_SUBMITTED attemptId=99 score=85` |
| Teacher grades free response | INFO | `FREE_RESPONSE_GRADED attemptId=99 teacherId=5` |
| Unknown system error | ERROR | Full stack trace → `errors.log` |

---

## 5. GUI — Usability, Learnability & Recognizability

### Teacher Dashboard

| Element | Description |
|---------|-------------|
| Upload Panel | Drag-and-drop zone, max 3 files, real-time progress bar |
| Active Quizzes List | Quiz name, attempt statistics, creation date |
| Grading Queue | Badge count of pending free-response submissions |
| Student Results View | Per-quiz breakdown of student scores |

### Student Dashboard

| Element | Description |
|---------|-------------|
| Available Quizzes | Teacher-assigned quizzes, prominently featured |
| My Quizzes | Self-generated quizzes from personal uploads |
| History Table | Quiz name, score, date, attempt count |
| Upload Panel | Personal quiz generation from own notes |

### Usability Principles

- Primary action always one click from dashboard (Start Quiz, Upload File, Grade Response)
- Role-specific navigation — students never see grading tools
- File upload shows real-time status: **Uploading → Parsing → Generating → Ready**
- Quiz attempt shows progress indicator (e.g. Question 3 of 12)
- Answers auto-saved on navigation — no lost work
- Responsive layout — works on tablet (classroom use)

---

## 6. Brand & UX — JQuizGen

### Color Palette (Material Design 3)

| Hex | Role |
|-----|------|
| `#1A73E8` | Primary |
| `#1557B0` | Primary Dark |
| `#34A853` | Secondary |
| `#FBBC04` | Accent |
| `#EA4335` | Error |
| `#F8F9FA` | Background |
| `#202124` | Text Primary |
| `#5F6368` | Text Secondary |

### Typography

| Role | Font | Size / Weight |
|------|------|---------------|
| Heading | Google Sans / Roboto | 24px / 600 |
| Subheading | Google Sans / Roboto | 18px / 500 |
| Body | Roboto | 14px / 400 |
| Label | Roboto | 12px / 500 |
| Code / Monospace | Roboto Mono | 13px / 400 |

### Component Guidelines (Bootstrap 5)

- **Cards** — quiz items, upload zones, result summaries
- **Pill badges** — question type labels (MC, T/F, Free Response)
- **Progress bars** — generation status and quiz attempt progress
- **Toasts** — non-blocking success/error notifications
- **Modal** — confirmation dialog on quiz submission

---

## 7. Layer Interface Contracts

### Layering Rule

```
HTTP Request
    ↓
@Controller / @RestController   (receives RequestDTO, returns ResponseDTO)
    ↓
@Service                        (business logic, operates on domain objects)
    ↓
@Repository (JPA)               (operates on @Entity objects)
    ↓
PostgreSQL (Neon)
```

### Interface Rules

- Controllers never call repositories directly
- Services never return `@Entity` objects to controllers — always map to DTOs
- DTOs are immutable records (Java 17+)
- AI module hidden behind a single `QuizGenerationService` interface
- File parsing hidden behind a single `FileTextExtractor` interface

### Key Service Interfaces

```java
public interface QuizGenerationService {
    QuizGenerationResult generateQuiz(List<MultipartFile> files, QuizConfig config);
}

public interface FileTextExtractor {
    String extractText(MultipartFile file) throws FileProcessingException;
}

public interface GradingService {
    AutoGradeResult gradeAutomatic(QuizAttempt attempt);
    void submitManualGrade(Long attemptId, Long questionId, int score, String feedback);
}
```

### Async Generation Flow

```
POST /quiz/generate
    → @Async QuizGenerationService.generateQuiz()
    → returns jobId immediately

GET /quiz/status/{jobId}    ← client polls every 2 seconds
    → returns GenerationStatus { PENDING | PROCESSING | READY | FAILED }
```

---

## 8. Security, Privacy & Compliance

**Authentication:** Spring Security session-based (CSRF protection built-in, natural fit for Thymeleaf forms).

### Role Permissions

| Role | Permissions |
|------|-------------|
| `ROLE_STUDENT` | Own uploads, own attempts, teacher-assigned quizzes, own history |
| `ROLE_TEACHER` | All student results, grading queue, universal quiz management |

### Security Controls

| Threat | Control |
|--------|---------|
| Unauthorized access | Spring Security `@PreAuthorize` on all service methods |
| File upload abuse | Whitelist extensions + Apache Tika content-type verification (not just extension) |
| SQL injection | JPA parameterized queries — no raw SQL |
| XSS | Thymeleaf auto-escapes all output by default |
| CSRF | Spring Security CSRF tokens on all forms |
| AI prompt injection via files | Sanitize extracted text before inserting into Gemini prompt |
| Brute force login | Account lockout after 5 failed attempts |
| API key exposure | Gemini key stored in environment variables only — never in source code |

### Privacy Rules

- Student quiz results visible only to that student and teachers
- Student-uploaded (personal) quizzes are **never** visible to teachers
- Uploaded file content is processed in memory and never persisted to disk
- No sharing of file content between users

---

## 9. Additional Architectural Decisions

| Concern | Decision | Rationale |
|---------|----------|-----------|
| File Storage | None — in-memory only | Files only needed for text extraction; no persistent storage required |
| Async AI Generation | Spring `@Async` + polling endpoint | Gemini takes 3–8s; user sees status spinner, not a frozen page |
| Gemini Rate Limiting | In-memory queue + backoff | Free tier: 15 RPM — queue prevents API errors under concurrent load |
| Database Migrations | Flyway versioned scripts | Schema changes tracked, reproducible, safe for Neon/PostgreSQL |
| Quiz State Machine | `GENERATING → READY → ACTIVE → COMPLETED` | Prevents students accessing mid-generation quizzes; simplifies UI logic |
| Dev vs Prod DB | Neon branching (dev branch / main branch) | Free feature of Neon — no extra cost, clean environment separation |

---

## Finalized Architecture Summary

| | |
|--|--|
| **APP NAME** | JQuizGen |
| **FRAMEWORK** | Spring Boot 3.x |
| **FRONTEND** | Thymeleaf + Bootstrap 5 |
| **SECURITY** | Spring Security (session-based) |
| **ORM** | Spring Data JPA + Hibernate |
| **MIGRATIONS** | Flyway |
| **DATABASE** | PostgreSQL via Neon (free cloud) |
| **FILE STORAGE** | None — in-memory processing only |
| **FILE PARSING** | Apache Tika (all 6 formats) |
| **AI GENERATION** | Google Gemini 2.0 Flash |
| **ASYNC STRATEGY** | Spring @Async + status polling |
| **LOGGING** | SLF4J + Logback (JSON in prod) |
| **BUILD TOOL** | Maven |
| **MAX FILES** | 3 per upload |
| **MAX FILE SIZE** | 10MB per file |
| **ERROR STRATEGY** | Predefined codes + @ControllerAdvice |

---

*JQuizGen Architecture Document · v1.0 · Generated April 24, 2026 · Architecture Agent (Svarun)*
