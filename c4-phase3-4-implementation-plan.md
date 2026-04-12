# Component 4 — Phase 3 & 4 Implementation Plan

## Context

Component 4 (Bills Pipeline) Phases 1-2 are complete: shared-models enums, pipeline-models events/constants, ingestion-common utilities, and bills-common repositories are all merged. Phases 3 and 4 implement the two remaining applications:

- **Phase 3**: `bill-text-availability-checker` — scans bills for new/updated text versions, emits `BillTextAvailableEvent`
- **Phase 4**: `bill-text-pipeline` — subscribes to events, downloads bill text, stores versions, emits `BillTextIngestedEvent`

Both apps live in `repcheck-data-ingestion`. Acceptance criteria define 15 test cases for the checker and 19 for the pipeline.

---

## PR Execution Plan

### Track A: Bill Text Availability Checker (Phase 3)

#### PR 1 — Phase 3: Build deps, config, errors
**Branch**: `feat/c4-checker-foundation`
**Files to create/modify**:
- `build.sbt` — add `doobie`, `pubSub`, `fs2` deps to `billTextAvailabilityChecker`
- `bill-text-availability-checker/src/main/scala/com/repcheck/bills/checker/config/BillTextCheckerConfig.scala` — PureConfig case class (parallelism, batchSize, retryConfig)
- `bill-text-availability-checker/src/main/scala/com/repcheck/bills/checker/errors/BillTextCheckerErrors.scala` — flat exceptions + ErrorClassifier

**Tests**:
- Config loading spec (valid, missing fields, defaults)
- ErrorClassifier spec (transient vs systemic classification)

**Coverage target**: 90%+ per file

---

#### PR 2 — Phase 3: TextVersionSelector + BillTextAvailabilityChecker core
**Branch**: `feat/c4-checker-core`
**Depends on**: PR 1 merged
**Files to create**:
- `bill-text-availability-checker/src/main/scala/com/repcheck/bills/checker/selection/TextVersionSelector.scala`
  - `selectBestVersion(versions: List[BillTextVersionDTO]): Option[BillTextVersionDTO]`
  - Format priority: FormattedText > FormattedXml > PDF (per acceptance criteria 04.5)
  - Within same format, prefer latest date
- `bill-text-availability-checker/src/main/scala/com/repcheck/bills/checker/pipeline/BillTextAvailabilityChecker.scala`
  - Constructor deps: `BillTextApiClient[F]`, `DoobieBillRepository`, `IngestionEventPublisher[F]`, `TransactionRunner[F]`, `BillTextCheckerConfig`, `Logger[F]`
  - `checkAll(correlationId: UUID): fs2.Stream[F, ProcessingResult]`
    1. Query `findBillsNeedingTextCheck()` via TransactionRunner
    2. For each bill, fetch text versions from API
    3. Select best version via TextVersionSelector
    4. Compare with current bill text fields (url, versionCode)
    5. If new/changed: publish `BillTextAvailableEvent`, emit Success
    6. If unchanged: emit Skipped
    7. On error: emit Failure with correlation context

**Tests** (15 acceptance criteria from 04.5):
- TextVersionSelector tests:
  - AC-04.5.1: Selects FormattedText over XML over PDF
  - AC-04.5.2: Selects latest date within same format
  - AC-04.5.3: Returns None for empty list
  - AC-04.5.4: Handles single version
- BillTextAvailabilityChecker tests:
  - AC-04.5.5: Emits event when new text version found
  - AC-04.5.6: Emits event when version upgraded (e.g., IH → ENR)
  - AC-04.5.7: Skips bill when text unchanged
  - AC-04.5.8: Handles API returning empty versions list
  - AC-04.5.9: Handles API error (transient → continue)
  - AC-04.5.10: Handles DB query error
  - AC-04.5.11: Handles event publish error
  - AC-04.5.12: Correct BillTextAvailableEvent fields (billId, congress, textUrl, textFormat, versionCode, previousVersionCode)
  - AC-04.5.13: Processes bills in parallel (config.parallelism)
  - AC-04.5.14: Logs correlation ID in all log lines
  - AC-04.5.15: Stream completes after all bills processed

**Coverage target**: 90%+ per file

---

#### PR 3 — Phase 3: IOApp wiring + PipelineExecutor
**Branch**: `feat/c4-checker-app`
**Depends on**: PR 2 merged
**Files to create**:
- `bill-text-availability-checker/src/main/scala/com/repcheck/bills/checker/app/BillTextAvailabilityCheckerApp.scala` — IOApp entry point
- `bill-text-availability-checker/src/main/scala/com/repcheck/bills/checker/app/BillTextCheckerPipeline.scala` — companion object with `run[F]` method (testability pattern)
  - Loads config, builds resources (Transactor, HTTP client, PubSub publisher)
  - Wires dependencies, calls `checker.checkAll()`, delegates to `PipelineExecutor.execute()`

**Tests**:
- `BillTextCheckerPipeline` companion object test: inject mock factories, verify wiring
- IOApp excluded from coverage (pure wiring, added to `coverageExcludedFiles`)

**Coverage target**: 90%+ for Pipeline companion; IOApp excluded

---

### Track B: Bill Text Pipeline (Phase 4) — Parallel with Track A

#### PR 4 — Phase 4: Build deps, config, errors
**Branch**: `feat/c4-pipeline-foundation`
**Can run in parallel with PR 1**
**Files to create/modify**:
- `build.sbt` — add `doobie`, `fs2` deps to `billTextPipeline`
- `bill-text-pipeline/src/main/scala/com/repcheck/bills/text/config/BillTextPipelineConfig.scala` — PureConfig case class (parallelism, downloadTimeout, retryConfig, maxContentSize)
- `bill-text-pipeline/src/main/scala/com/repcheck/bills/text/errors/BillTextPipelineErrors.scala` — flat exceptions + ErrorClassifier

**Tests**:
- Config loading spec
- ErrorClassifier spec

**Coverage target**: 90%+ per file

---

#### PR 5 — Phase 4: BillTextDownloader
**Branch**: `feat/c4-text-downloader`
**Depends on**: PR 4 merged
**Files to create**:
- `bill-text-pipeline/src/main/scala/com/repcheck/bills/text/download/BillTextDownloader.scala`
  - Constructor deps: `Client[F]`, `BillTextPipelineConfig`, `Logger[F]`
  - `download(url: String, format: FormatType): F[String]`
    - FormattedText (HTML): HTTP GET → strip HTML tags → plain text
    - FormattedXml: HTTP GET → parse XML → extract text content
    - PDF: HTTP GET → PDFBox extraction → plain text
  - Timeout per config
  - Content size validation

**Tests** (from 04.6 acceptance criteria):
- AC-04.6.1: Downloads and strips HTML successfully
- AC-04.6.2: Downloads and parses XML successfully
- AC-04.6.3: Downloads and extracts PDF text
- AC-04.6.4: Handles HTTP 404 (bill text removed)
- AC-04.6.5: Handles HTTP 500 (transient error)
- AC-04.6.6: Handles timeout
- AC-04.6.7: Handles content exceeding max size
- AC-04.6.8: Handles malformed HTML gracefully
- AC-04.6.9: Handles malformed XML gracefully

**Coverage target**: 90%+ per file

---

#### PR 6 — Phase 4: BillTextProcessor
**Branch**: `feat/c4-text-processor`
**Depends on**: PR 5 merged
**Files to create**:
- `bill-text-pipeline/src/main/scala/com/repcheck/bills/text/pipeline/BillTextProcessor.scala`
  - Constructor deps: `BillTextDownloader[F]`, `DoobieBillTextVersionRepository`, `IngestionEventPublisher[F]`, `TransactionRunner[F]`, `BillTextPipelineConfig`, `Logger[F]`
  - `processEvent(event: PipelineEvent[BillTextAvailableEvent], correlationId: UUID): F[ProcessingResult]`
    1. Download text via BillTextDownloader
    2. Build `BillTextVersionDO` (billId from event, content from download, no embedding yet)
    3. Call `storeAndUpdateBill()` via TransactionRunner → returns `Long` (DB id)
    4. Generate UUID for event correlation
    5. Publish `BillTextIngestedEvent` with versionId=UUID, DB id in metadata
    6. Return Success with timing
  - `streamAll(correlationId: UUID): fs2.Stream[F, ProcessingResult]` — subscribe to Pub/Sub topic, process each event

**Tests** (from 04.8 acceptance criteria):
- AC-04.8.1: Successfully processes event end-to-end (download → store → publish)
- AC-04.8.2: Handles download failure (transient → retry, systemic → fail)
- AC-04.8.3: Handles DB store failure
- AC-04.8.4: Handles event publish failure (after successful store)
- AC-04.8.5: Correct BillTextVersionDO fields populated
- AC-04.8.6: Correct BillTextIngestedEvent fields
- AC-04.8.7: storeAndUpdateBill updates both bill_text_versions AND bills table
- AC-04.8.8: Processes events in parallel (config.parallelism)
- AC-04.8.9: Logs correlation ID throughout
- AC-04.8.10: Handles duplicate events idempotently

**Coverage target**: 90%+ per file

---

#### PR 7 — Phase 4: IOApp wiring
**Branch**: `feat/c4-pipeline-app`
**Depends on**: PR 6 merged
**Files to create**:
- `bill-text-pipeline/src/main/scala/com/repcheck/bills/text/app/BillTextPipelineApp.scala` — IOApp entry point
- `bill-text-pipeline/src/main/scala/com/repcheck/bills/text/app/BillTextPipelinePipeline.scala` — companion object with `run[F]`
  - Loads config, builds resources (Transactor, HTTP client, PubSub subscriber+publisher)
  - Wires dependencies, calls `processor.streamAll()`, delegates to `PipelineExecutor.execute()`

**Tests**:
- Companion object wiring test with mock factories

**Coverage target**: 90%+ for companion; IOApp excluded

---

### Cross-Track: Integration & E2E

#### PR 8 — Inter-application integration tests
**Branch**: `feat/c4-integration-tests`
**Depends on**: PRs 3 and 7 both merged
**Files to create**:
- `bill-text-availability-checker/src/test/scala/com/repcheck/bills/checker/integration/CheckerIntegrationSpec.scala`
  - Docker PostgreSQL (AlloyDB Omni) + WireMock (Congress.gov API) + real Pub/Sub emulator
  - End-to-end: checker finds new text → emits event → verify event on topic
- `bill-text-pipeline/src/test/scala/com/repcheck/bills/text/integration/PipelineIntegrationSpec.scala`
  - Docker PostgreSQL + WireMock (bill text download) + Pub/Sub emulator
  - End-to-end: receive event → download text → store in DB → emit downstream event
- `bill-text-pipeline/src/test/scala/com/repcheck/bills/text/integration/CheckerToPipelineE2ESpec.scala`
  - Full flow: checker → Pub/Sub → pipeline → DB + downstream event
  - Tagged `E2ETest` (excluded from `sbt test`, run via `sbt "testOnly -- -n ..."`)

**Infrastructure**:
- AlloyDB Omni Docker container (local only, no cloud cost)
- Google Pub/Sub emulator (local)
- WireMock for Congress.gov API simulation

---

## Parallelization Strategy

```
Timeline:
  PR 1 (checker foundation) ──→ PR 2 (checker core) ──→ PR 3 (checker app) ──┐
  PR 4 (pipeline foundation) ─→ PR 5 (downloader) ──→ PR 6 (processor) ─→ PR 7 (pipeline app) ─→ PR 8 (integration)
                                                                               └──────────────────┘
  PR 1 & PR 4: parallel (independent foundations)
  PR 2 & PR 5: parallel (independent core logic)
  PR 8: after both tracks complete
```

## Validation Layers

1. **Function-level**: Unit tests per method with MockitoScala (90%+ coverage per file)
2. **Class-level**: Integration within each class (constructor wiring, error propagation)
3. **Inter-class**: Component tests verifying Checker↔Repository, Processor↔Downloader↔Repository
4. **Inter-application**: Checker emits event → Pipeline receives and processes (PR 8)
5. **E2E**: Full flow with real DB + Pub/Sub emulator + WireMock (PR 8, tagged E2ETest)

## Key Reusable Components (already exist)

| Component | Location | Used For |
|-----------|----------|----------|
| `DoobieBillRepository` | `bills-common` | `findBillsNeedingTextCheck()` |
| `DoobieBillTextVersionRepository` | `bills-common` | `storeAndUpdateBill()` |
| `BillTextApiClient` | `bill-text-availability-checker` (exists) | Fetch text versions from Congress.gov |
| `IngestionEventPublisher` | `ingestion-common` | Publish events to Pub/Sub |
| `PipelineExecutor` | `bill-metadata-pipeline/app` | Execute stream + logging pattern |
| `TransactionRunner` | `ingestion-common` | Lift ConnectionIO → F |
| `RetryWrapper` | `pipeline-models` | Retry with exponential backoff |
| `PipelineLoggerFactory` | `ingestion-common` | Structured logging |
| `TransactorResource` | `ingestion-common` | Build Doobie transactor |
| `Tables` constants | `pipeline-models` | Table name references |
| `DoobieEnumInstances` | `shared-models` | Get/Put for enum types |
| `BillTextAvailableEvent` | `pipeline-models` | Event payload |
| `BillTextIngestedEvent` | `pipeline-models` | Event payload |
| `ProcessingResult` | `pipeline-models` | Success/Failure/Skipped |

## Infrastructure (No Cloud DB Cost)

- **Database**: AlloyDB Omni Docker container (local) for integration tests; Cloud SQL PostgreSQL ($10/mo) for dev environment
- **Pub/Sub**: Google Pub/Sub emulator for local tests; real Pub/Sub in GCP dev project
- **Congress.gov API**: WireMock for tests; real API with rate limiting in dev
- **No AlloyDB cloud instance** — all DB testing via Docker

## Verification Plan

1. Each PR: `sbt compile && sbt test && sbt scalafmtCheckAll && sbt scalafixAll --check` (via `CreatePR`/`pushToPR`)
2. Each PR: `sbt coverage test coverageReport` — verify 90%+ patch coverage
3. PR 8: Run integration tests with Docker Compose (PostgreSQL + Pub/Sub emulator)
4. PR 8: Run E2E tests: `sbt "testOnly -- -n com.repcheck.tags.E2ETest"`
5. Final: Manual smoke test — run checker against dev DB, verify events appear, run pipeline, verify text stored
