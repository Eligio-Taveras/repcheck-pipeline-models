# Component 2: `repcheck-pipeline-models` — Implementation Plan

**Date:** 2026-04-05
**Status:** Ready for execution

---

## Executive Summary

Component 2 defines the **inter-pipeline contract layer**: events, execution tracking, error handling, change detection, workflow orchestration schemas, and configuration. It's a publishable library consumed by every pipeline (Components 3–11). Unlike Component 1 (pure types), Component 2 introduces **behavioral types** — `RetryWrapper[F]` with Cats Effect, PureConfig auto-derivation, and status derivation logic with real business rules.

**Recommended agents: 6**, organized into 3 waves to respect dependency ordering.

---

## Pre-Work: Repository Scaffolding

**Owner action required (before agents start):** ✅ Complete
1. ~~Create the GitHub repo (`repcheck-pipeline-models`) — via `gh repo create` or GitHub UI~~
2. ~~Merge PR #4 on `repcheck-shared-models` (auto-tag git identity fix) so the published artifact version is clean~~

**Agent scaffolding (1 agent, ~15 min):**
1. Generate from `repcheck-g8` template
2. Trim dependencies to: Circe, Cats Effect, PureConfig (`pureconfig-generic-scala3`), Doobie, FS2 (test: ScalaTest, MockitoScala, cats-effect-testing)
3. Add `repcheck-shared-models` as a dependency (published artifact from Component 1)
4. Configure CI with the same pattern as `repcheck-shared-models` (auto-tag, publish, codecov)
5. Set root package to `repcheck.pipeline.models`
6. Create empty package directories for all sub-packages: `events`, `metadata`, `errors`, `changes`, `workflow/schema`, `workflow/state`, `launcher`, `config`, `constants`

---

## Wave 1: Independent Foundation Types (3 Agents in Parallel)

These areas have **zero inter-dependencies** — they can be built simultaneously.

---

### Agent 1: Events — Area 2.1 (Inter-Pipeline Communication)
**Package:** `repcheck.pipeline.models.events`

**Files to create:**
| File | Contents |
|------|----------|
| `PipelineEvent.scala` | Generic `PipelineEvent[T]` envelope case class |
| `EventTypes.scala` | Object with all event type string constants |
| `EventPayloads.scala` | All 10 event payload case classes |

**Implementation details:**
- `PipelineEvent[T]` is a pure case class with all fields required (no defaults):
  ```scala
  case class PipelineEvent[T](
    eventType: String,
    payload: T,
    timestamp: Instant,
    eventId: UUID,
    correlationId: UUID,
    source: String
  )
  ```
- **Effectful factory method** for constructing events (avoids side effects in case class):
  ```scala
  object PipelineEvent {
    def create[F[_]: Sync, T](
      eventType: String,
      payload: T,
      correlationId: UUID,
      source: String
    ): F[PipelineEvent[T]] = for {
      id <- Sync[F].delay(UUID.randomUUID())
      ts <- Sync[F].delay(Instant.now())
    } yield PipelineEvent(eventType, payload, ts, id, correlationId, source)
  }
  ```
- The case class itself stays pure — Circe deserialization constructs it directly from JSON fields
- Circe semi-auto codecs for `PipelineEvent[T]` need a `Codec[T]` in implicit scope — provide companion object codecs for each payload type
- `EventTypes` object: `val BillTextAvailable = "bill.text.available"`, etc. — 10 constants
- `VoteRecordedEvent.billId` is `Option[String]` because real-world votes without bill association exist: procedural votes (motions to adjourn, quorum calls), nomination confirmations, contempt resolutions, journal approval, Speaker elections, expulsion/censure votes

**Event payloads (10 case classes):**
1. `BillTextAvailableEvent(billId: String, congress: Int, textUrl: String, textFormat: String, versionCode: String, previousVersionCode: Option[String])`
2. `BillTextIngestedEvent(billId: String, versionId: UUID, congress: Int, versionCode: String, previousVersionCode: Option[String], committeeCode: Option[String])`
3. `DecompositionCompletedEvent(billId: String, versionId: UUID, conceptGroupCount: Int, sectionCount: Int)`
4. `VoteRecordedEvent(voteId: String, billId: Option[String], chamber: String, date: Instant, congress: Int, isUpdate: Boolean)`
5. `AnalysisCompletedEvent(billId: String, analysisId: UUID, topics: List[String], passesExecuted: List[Int], modelUsed: String)`
6. `UserProfileUpdatedEvent(userId: UUID, topicsChanged: List[String])`
7. `MemberUpdatedEvent(memberId: String)`
8. `ScoringUserRequestedEvent(userId: UUID, requestId: UUID, source: String)`
9. `ScoringUserCompletedEvent(userId: UUID, requestId: UUID, memberScoreCount: Int, status: String)`
10. `DailyIngestionStartEvent(date: String, congress: Int)`

**Test file:** `EventsSpec.scala`

**Required tests (minimum 25):**
- Round-trip JSON serialization for each of the 10 payloads
- Round-trip for `PipelineEvent[BillTextAvailableEvent]` (full envelope)
- `VoteRecordedEvent.billId` is `None` for procedural votes — test serialization with `None`
- `BillTextAvailableEvent.previousVersionCode` is `None` for first version — test
- Decode unknown `eventType` → `Left` with meaningful error
- Verify `EventTypes` constants have correct string values (test each constant)
- Verify `PipelineEvent` fields: `eventId` is UUID, `timestamp` is Instant, `correlationId` is UUID
- Negative: missing required field → decode failure with field name in error

**Error handling notes:**
- Circe decoders must produce clear error messages naming the missing/invalid field
- No `.get` calls anywhere — all Option handling via `fold`/`map`/`getOrElse`

---

### Agent 2: Error Handling & Retry — Area 2.3
**Package:** `repcheck.pipeline.models.errors`

**Files to create:**
| File | Contents |
|------|----------|
| `ErrorClass.scala` | `ErrorClass` enum (Transient, Systemic) with Circe codecs |
| `ErrorClassifier.scala` | `ErrorClassifier` trait + `DefaultErrorClassifier` + `HttpErrorClassifier` |
| `RetryConfig.scala` | `RetryConfig` case class with PureConfig auto-derivation |
| `RetryWrapper.scala` | `RetryWrapper[F[_]]` with exponential backoff |
| `DeadLetterMessage.scala` | `DeadLetterMessage` case class with Circe codecs |

**Implementation details:**
- `RetryWrapper[F[_]: Temporal: Logger]` — needs `Temporal` for `sleep`, a logging capability for retry attempts
- Backoff formula: `delay = min(initialBackoffMs * multiplier^attempt, maxBackoffMs)`
- On Transient error: retry up to `maxRetries`, log each attempt with `(attempt, maxRetries, delay, errorClass, message, correlationId)`
- On Systemic error: fail immediately, no retry
- After exhausting retries: raise the last error
- **Error factory pattern**: `withRetry` accepts an `errorFactory: (String, Throwable) => Throwable` parameter so each call site produces its own unique exception type. This ensures every retry-exhausted failure is distinguishable in logs and stack traces without `RetryWrapper` needing to know about subsystem-specific error types.
  ```scala
  def withRetry[A](
    operation: F[A],
    config: RetryConfig,
    classifier: ErrorClassifier,
    errorFactory: (String, Throwable) => Throwable,
    correlationId: UUID
  ): F[A]
  ```
  Example call site in a bills pipeline:
  ```scala
  retryWrapper.withRetry(
    fetchBillText(billId),
    config.retry,
    HttpErrorClassifier(extractStatus),
    (msg, cause) => BillTextFetchException(msg, cause),
    correlationId
  )
  ```
  When retries exhaust, `RetryWrapper` calls `errorFactory(s"Exhausted $maxRetries retries for ...", lastError)` and raises the result.

**`RetryConfig` defaults:**
```scala
case class RetryConfig(
  maxRetries: Int = 3,
  initialBackoffMs: Long = 10,
  maxBackoffMs: Long = 60000,
  backoffMultiplier: Double = 2.0
)
```

**Test file:** `ErrorHandlingSpec.scala`, `RetryWrapperSpec.scala`

**Required tests (minimum 20):**
- `DefaultErrorClassifier`: all errors → Systemic
- `HttpErrorClassifier`: 429 → Transient, 500 → Transient, 502 → Transient, 503 → Transient, 504 → Transient
- `HttpErrorClassifier`: 400 → Systemic, 401 → Systemic, 403 → Systemic, 404 → Systemic
- `HttpErrorClassifier`: unknown status code → Systemic (default)
- `RetryConfig` PureConfig round-trip (load from config string, verify defaults)
- `RetryWrapper`: Transient error retries 3 times then fails — verify attempt count
- `RetryWrapper`: Transient error succeeds on 2nd attempt — verify success
- `RetryWrapper`: Systemic error fails immediately — verify only 1 attempt
- `RetryWrapper`: backoff delay calculation — verify `min(10 * 2^attempt, 60000)` for attempts 0–15
- `RetryWrapper`: maxRetries = 0 → no retries, immediate failure
- `ErrorClass` Circe round-trip: `Transient` ↔ `"Transient"`, `Systemic` ↔ `"Systemic"`
- `DeadLetterMessage` Circe round-trip with all fields
- Negative: `RetryConfig` with negative maxRetries → validation error (if validated)
- Negative: unknown ErrorClass string → decode failure

**Error handling notes:**
- `RetryWrapper` must catch non-fatal exceptions only — use `cats.effect.MonadCancel` semantics
- The HTTP status code classification needs a way to extract status from the error — define a `StatusCodeExtractor` or have `HttpErrorClassifier` accept a function `Throwable => Option[Int]`
- `RetryWrapper` never creates its own exception types — the caller-supplied `errorFactory` is the only source of retry-exhaustion exceptions
- Test the error factory pattern: verify that the exception raised after exhaustion is the one produced by the factory, not a generic wrapper

**Additional tests for error factory pattern (add to the 20 minimum):**
- `RetryWrapper`: after exhausting retries, raised exception is the one from `errorFactory` (check type and message)
- `RetryWrapper`: `errorFactory` receives the last `Throwable` as `cause` — verify `getCause` chain
- `RetryWrapper`: two different call sites with different factories produce distinguishable exceptions

---

### Agent 3: Change Detection — Area 2.4
**Package:** `repcheck.pipeline.models.changes`

**Files to create:**
| File | Contents |
|------|----------|
| `ChangeDetectionStrategy.scala` | Enum: UpdateDateComparison, ExistenceCheck, FieldLevelDiff, AlwaysNew |
| `PersistenceStrategy.scala` | Enum: Upsert, UpsertWithHistory, AppendOnly |
| `ChangeDetectionResult.scala` | Generic `ChangeDetectionResult[T]` case class |
| `EntityChangeConfig.scala` | `EntityChangeConfig` case class |

**Implementation details:**
- `ChangeDetectionResult[T]`: `entity: T`, `entityId: String`, `entityType: String`, `changed: Boolean`, `strategy: ChangeDetectionStrategy`, `persistenceStrategy: PersistenceStrategy`, `emitEvent: Option[String]`
- `emitEvent` logic: `Some(eventType)` only when `changed == true` AND `config.eventType.isDefined`; `None` otherwise
- Both enums get Circe codecs and bidirectional string parsing (like Component 1 enum pattern)

**Test file:** `ChangeDetectionSpec.scala`

**Required tests (minimum 15):**
- Each `ChangeDetectionStrategy` variant Circe round-trip (4 tests)
- Each `PersistenceStrategy` variant Circe round-trip (3 tests)
- `ChangeDetectionResult` with `changed=true` + config has event → `emitEvent` is `Some`
- `ChangeDetectionResult` with `changed=true` + config has no event → `emitEvent` is `None`
- `ChangeDetectionResult` with `changed=false` + config has event → `emitEvent` is `None`
- `EntityChangeConfig` for each entity in the rules table (bill metadata, bill text, vote, member, amendment, analysis, score) — verify correct strategy/persistence/event combinations
- Full round-trip serialization of `ChangeDetectionResult[String]` (using String as a simple T)
- Negative: unknown strategy string → parse error
- Negative: unknown persistence string → parse error

---

## Wave 2: Types That Depend on Wave 1 (2 Agents in Parallel)

These areas reference enums and types defined in Wave 1.

---

### Agent 4: Pipeline Execution Tracking — Area 2.2
**Package:** `repcheck.pipeline.models.metadata`

**Depends on:** `ErrorClass` from Agent 2 (for `errorClass` field in `ProcessingResultDO`)

**Files to create:**
| File | Contents |
|------|----------|
| `PipelineStatus.scala` | Enum with 4 variants + Circe + Doobie |
| `ResultStatus.scala` | Enum with 3 variants + Circe + Doobie |
| `PipelineTrigger.scala` | Enum with 3 variants + Circe + Doobie |
| `PipelineRunDO.scala` | DO with Circe + Doobie Read/Write |
| `ProcessingResultDO.scala` | DO with Circe + Doobie Read/Write |
| `ProcessingResult.scala` | ADT: Succeeded/Failed/Skipped with helper methods and `toResultDO` |
| `PipelineRunSummary.scala` | Summary type with status derivation logic |

**Implementation details:**
- `ProcessingResult` is a sealed trait (not an enum) with three case classes:
  - `Succeeded(entityId: String, eventEmitted: Boolean = false)`
  - `Failed(entityId: String, reason: String)`
  - `Skipped(entityId: String, reason: String)`
- Helper methods: `isSucceeded`, `isFailed`, `isSkipped`
- `toResultDO(runId: UUID, correlationId: UUID): ProcessingResultDO` — maps variant to `ResultStatus` and extracts error info
- **Status derivation in `PipelineRunSummary`:**
  - All Succeeded → `Completed`
  - Mix of Succeeded + Failed → `CompletedWithErrors`
  - All Failed → `Failed`
  - Empty results → `Completed`
- `PipelineRunDO` maps to `pipeline_runs` table — has Doobie `Read`/`Write` via auto-derivation
- `ProcessingResultDO` maps to `processing_results` table — `errorClass` is `Option[String]` (stores "Transient" or "Systemic")

**Test file:** `PipelineTrackingSpec.scala`, `ProcessingResultSpec.scala`

**Required tests (minimum 25):**
- Each `PipelineStatus` variant: Circe round-trip, Doobie Read/Write via `implicitly`
- Each `ResultStatus` variant: Circe round-trip, Doobie Read/Write
- Each `PipelineTrigger` variant: Circe round-trip, Doobie Read/Write
- `ProcessingResult.Succeeded.toResultDO` → `resultStatus = Succeeded`, `errorMessage = None`, `errorClass = None`
- `ProcessingResult.Failed.toResultDO` → `resultStatus = Failed`, `errorMessage = Some(reason)`, `errorClass` populated
- `ProcessingResult.Skipped.toResultDO` → `resultStatus = Skipped`, `errorMessage = Some(reason)`, `errorClass = None`
- `isSucceeded`/`isFailed`/`isSkipped` helpers on each variant
- Status derivation: all succeeded → Completed
- Status derivation: mixed → CompletedWithErrors
- Status derivation: all failed → Failed
- Status derivation: empty → Completed
- Status derivation: 1 succeeded + 0 failed → Completed
- Status derivation: 0 succeeded + 1 failed → Failed
- `PipelineRunDO` Circe round-trip with all fields
- `PipelineRunDO` with `completedAt = None` (still running)
- `ProcessingResultDO` Circe round-trip
- Negative: unknown `PipelineStatus` string → decode error
- Negative: unknown `ResultStatus` string → decode error

---

### Agent 5: Workflow Schema + State + Launcher — Areas 2.5 + 2.6 + 2.7
**Package:** `repcheck.pipeline.models.workflow`, `repcheck.pipeline.models.workflow.schema`, `repcheck.pipeline.models.workflow.state`, `repcheck.pipeline.models.launcher`

**Depends on:** Event types from Agent 1 (for `triggeredBy` event type references)

**This is the largest work item. It includes three tightly coupled areas.**

**Files to create:**
| File | Package | Contents |
|------|---------|----------|
| `WorkflowDefinition.scala` | `workflow.schema` | Top-level definition + validation |
| `WorkflowStepDefinition.scala` | `workflow.schema` | Step with all config sub-types |
| `ContainerConfig.scala` | `workflow.schema` | Container config + SecretMount |
| `ResourceConfig.scala` | `workflow.schema` | CPU/memory/GPU config |
| `ExecutionConfig.scala` | `workflow.schema` | Timeout, retries, task count |
| `NetworkConfig.scala` | `workflow.schema` | VPC, Cloud SQL connections |
| `VolumeConfig.scala` | `workflow.schema` | GCS + in-memory volumes |
| `IdentityConfig.scala` | `workflow.schema` | Service account config |
| `HealthCheckConfig.scala` | `workflow.schema` | Probes config |
| `MetadataConfig.scala` | `workflow.schema` | Labels + annotations |
| `WorkflowRunStatus.scala` | `workflow.state` | Enum: Pending, Running, Completed, CompletedWithErrors, Failed |
| `WorkflowStepStatus.scala` | `workflow.state` | Enum: Pending, Running, Completed, Failed |
| `WorkflowRunDO.scala` | `workflow.state` | DO with Circe + Doobie |
| `WorkflowRunStepDO.scala` | `workflow.state` | DO with Circe + Doobie |
| `MacroContext.scala` | `launcher` | Context for macro resolution |
| `MacroResolver.scala` | `launcher` | `resolve(template, context): String` |
| `ResolvedStepConfig.scala` | `launcher` | Fully resolved step for launch |
| `LaunchResult.scala` | `launcher` | Success/failure of Cloud Run launch |
| `LauncherTraits.scala` | `launcher` | `MessagePuller`, `WorkflowResolver`, `DependencyChecker`, `JobLauncher` traits |

**Critical implementation details:**

**Validation in `WorkflowDefinition`:**
- Factory method returns `Either[String, WorkflowDefinition]`
- Validates: step names are unique, no circular dependencies (topological sort), all dependency step references exist in the workflow, config values in valid ranges (cpu in ["1","2","4","6","8"], memory parseable, timeout > 0, maxRetries 0–10, taskCount 1–10000)
- Circular dependency check: build adjacency graph from `step.dependencies`, detect cycles

**`MacroResolver.resolve`:**
- Replace `{{run_id}}` → runId.toString
- Replace `{{date}}` → ISO-8601 date string
- Replace `{{timestamp}}` → ISO-8601 timestamp
- Replace `{{message.fieldName}}` → lookup from `MacroContext.messagePayload` map
- **Unknown macros left unchanged** (not error, not empty — left as `{{unknown}}` for debugging)

**Workflow state status derivation:**
- Any step Running → workflow is Running
- All steps Completed → Completed
- Some Completed + some Failed → CompletedWithErrors
- All non-Pending are Failed → Failed
- All Pending → Pending

**Launcher traits** are pure interfaces (no implementation) — they define the contracts that Component 3 will implement.

**Test files:** `WorkflowSchemaSpec.scala`, `WorkflowStateSpec.scala`, `MacroResolverSpec.scala`, `LauncherTraitsSpec.scala`

**Required tests (minimum 40):**
- `WorkflowDefinition.apply` with valid steps → `Right`
- `WorkflowDefinition.apply` with circular deps → `Left` mentioning cycle
- `WorkflowDefinition.apply` with missing dep reference → `Left` mentioning missing step
- `WorkflowDefinition.apply` with duplicate step names → `Left`
- `WorkflowDefinition.apply` with invalid CPU → `Left`
- `WorkflowDefinition.apply` with maxRetries = -1 → `Left`
- `WorkflowDefinition.apply` with maxRetries = 11 → `Left`
- `WorkflowDefinition.apply` with taskCount = 0 → `Left`
- `WorkflowDefinition.apply` with valid defaults only (minimal step: just image) → `Right`
- Each config sub-type Circe round-trip (ContainerConfig, ResourceConfig, ExecutionConfig, NetworkConfig, VolumeConfig, IdentityConfig, HealthCheckConfig, MetadataConfig)
- `WorkflowRunStatus` all variants: Circe + Doobie
- `WorkflowStepStatus` all variants: Circe + Doobie
- `WorkflowRunDO` full Circe round-trip
- `WorkflowRunStepDO` full Circe round-trip with `originalMessage = Some(...)`
- `WorkflowRunStepDO` with `pipelineRunId = None` (step not yet launched)
- Workflow state derivation: all completed → Completed
- Workflow state derivation: mixed completed + failed → CompletedWithErrors
- Workflow state derivation: all pending → Pending
- Workflow state derivation: one running → Running
- `MacroResolver`: `{{run_id}}` replaced correctly
- `MacroResolver`: `{{date}}` replaced with ISO date
- `MacroResolver`: `{{timestamp}}` replaced with ISO timestamp
- `MacroResolver`: `{{message.billId}}` replaced from payload map
- `MacroResolver`: `{{message.missing}}` → left unchanged (key not in map)
- `MacroResolver`: `{{unknown_macro}}` → left unchanged
- `MacroResolver`: template with no macros → returned unchanged
- `MacroResolver`: template with multiple macros → all replaced
- `LaunchResult` with success + jobName
- `LaunchResult` with failure + errorMessage
- Negative: unknown `WorkflowRunStatus` string → decode error
- Negative: unknown `WorkflowStepStatus` string → decode error

---

## Wave 3: Configuration & Constants (1 Agent)

---

### Agent 6: Configuration + Constants — Areas 2.8 + 2.10
**Package:** `repcheck.pipeline.models.config`, `repcheck.pipeline.models.constants`

**Depends on:** Shared models enums (VoteType from Component 1), RetryConfig from Agent 2

**Files to create:**
| File | Package | Contents |
|------|---------|----------|
| `VoteWeights.scala` | `config` | Vote type → weight mapping with PureConfig |
| `CommitteeAttributionWeights.scala` | `config` | Committee role → weight mapping with PureConfig |
| `AnalysisPassConfig.scala` | `config` | Pass model selection + enable flags with PureConfig |
| `PipelineConfig.scala` | `config` | Top-level config aggregating all sub-configs |
| `Tables.scala` | `constants` | ~55 table name constants |
| `EventTypesReexport.scala` | `constants` | Re-export of event type constants from Area 2.1 |

**Implementation details:**
- All config classes use PureConfig auto-derivation with `derives ConfigReader`
- `VoteWeights.forVoteType(vt: VoteType): Double` — pattern match on VoteType enum, return configured weight
- `Tables` object: one `val` per table, e.g., `val Members = "members"`, `val Bills = "bills"` — **55 constants** matching the spec
- Every constant must match exactly what's in the acceptance criteria — the downstream pipelines reference these by name

**`PipelineConfig[T]` — generic over app-specific config:**

Not all pipelines need vote weights or analysis pass config. The bills pipeline needs `VoteWeights`; the scoring engine needs `AnalysisPassConfig`; the members pipeline needs neither. `PipelineConfig` is parameterized so each app declares only the config it actually uses:

```scala
case class PipelineConfig[T](
  parallelism: Int = 4,
  batchSize: Int = 100,
  pageSize: Int = 250,
  retry: RetryConfig = RetryConfig(),
  appConfig: T
)
```

Each consuming pipeline defines its own `T`:
```scala
// Bills pipeline
case class BillsPipelineAppConfig(
  voteWeights: VoteWeights = VoteWeights(),
  committeeWeights: CommitteeAttributionWeights = CommitteeAttributionWeights()
) derives ConfigReader

type BillsPipelineConfig = PipelineConfig[BillsPipelineAppConfig]

// Members pipeline — no domain-specific weights needed
case class MembersPipelineAppConfig(
  fetchBatchSize: Int = 50
) derives ConfigReader

type MembersPipelineConfig = PipelineConfig[MembersPipelineAppConfig]

// Scoring engine
case class ScoringAppConfig(
  analysisPass: AnalysisPassConfig = AnalysisPassConfig(),
  voteWeights: VoteWeights = VoteWeights()
) derives ConfigReader

type ScoringPipelineConfig = PipelineConfig[ScoringAppConfig]
```

PureConfig derivation for `PipelineConfig[T]` requires a `ConfigReader[T]` in implicit scope — each app's `T` provides this via `derives ConfigReader`. The library defines `PipelineConfig[T]` and all the building-block config types (`VoteWeights`, `CommitteeAttributionWeights`, `AnalysisPassConfig`, `RetryConfig`). Consuming apps compose them into their own `T`.

**Note:** The type aliases (e.g., `type BillsPipelineConfig = ...`) are defined in the consuming apps, NOT in this library. This library only provides `PipelineConfig[T]` and the individual config types.

**Config hierarchy — how values flow at runtime:**

This library defines config types with sensible defaults. Consuming applications (Components 3–11) override values at each layer:

```
Layer 1: Library defaults (Scala default params)
  └─ VoteWeights(passage = 1.0, cloture = 0.8, ...)
  └─ RetryConfig(maxRetries = 3, initialBackoffMs = 10, ...)
  └─ PipelineConfig common fields (parallelism = 4, batchSize = 100, ...)

Layer 2: Application reference.conf (in each pipeline's resources/)
  └─ pipeline.retry.max-retries = 5
  └─ pipeline.parallelism = 8
  └─ pipeline.app-config.vote-weights.passage = 1.0

Layer 3: Environment variables (set in Cloud Run Job YAML / workflow)
  └─ CONFIG_FORCE_pipeline_retry_max__retries=10
  └─ CONFIG_FORCE_pipeline_parallelism=16

Layer 4: JVM system properties (-D flags, highest priority)
  └─ -Dpipeline.retry.max-retries=1   # for local testing
```

PureConfig resolves these in standard Typesafe Config order: JVM props > env vars > application.conf > reference.conf > Scala defaults.

**Each consuming application bundles its own `application.conf`** that configures its `appConfig` block under the `pipeline` key. This library only defines the types and defaults — it does NOT ship an `application.conf` or `reference.conf` itself.

Example `application.conf` for a bills pipeline:
```hocon
pipeline {
  parallelism = 8
  batch-size = 200
  page-size = 250

  retry {
    max-retries = 5
    initial-backoff-ms = 10
    max-backoff-ms = 60000
    backoff-multiplier = 2.0
  }

  app-config {
    vote-weights {
      passage = 1.0
      cloture = 0.8
      amendment = 0.6
      procedural = 0.3
      nomination = 0.5
    }

    committee-weights {
      chair = 1.0
      ranking-member = 0.9
      member = 0.7
      ex-officio = 0.3
    }
  }
}
```

Example `application.conf` for a members pipeline (minimal `appConfig`):
```hocon
pipeline {
  parallelism = 4
  batch-size = 100

  retry {
    max-retries = 5
  }

  app-config {
    fetch-batch-size = 50
  }
}
```

**Test files:** `ConfigSpec.scala`, `TablesSpec.scala`

**Required tests (minimum 20):**
- `VoteWeights` default values match spec (passage=1.0, cloture=0.8, etc.)
- `VoteWeights.forVoteType` for each `VoteType` variant
- `VoteWeights` PureConfig: load from HOCON string → verify values
- `VoteWeights` PureConfig: load with overrides → verify custom values
- `CommitteeAttributionWeights` defaults match spec
- `CommitteeAttributionWeights` PureConfig round-trip
- `AnalysisPassConfig` defaults: haiku for pass1, sonnet for pass2, opus for pass3, all enabled
- `AnalysisPassConfig` PureConfig with one pass disabled
- `PipelineConfig[T]` PureConfig with a simple `T` (e.g., `case class TestAppConfig(x: Int)`) — verify common fields load with defaults
- `PipelineConfig[T]` PureConfig with custom parallelism + batchSize + `appConfig` populated
- `PipelineConfig[T]` with different `T` types — verify the same common fields parse regardless of `T`
- `Tables` object: verify at least 20 key constants match expected strings (Members = "members", Bills = "bills", Votes = "votes", etc.)
- `Tables` object: verify all 55 constants are unique (no duplicates)
- `Tables` object: verify no constant is empty string
- Negative: `PipelineConfig` with invalid HOCON → PureConfig error

---

## Docker Testing Strategy

Component 2 is primarily a **types-and-logic library** — most tests are pure unit tests requiring no external infrastructure. However, Doobie `Read`/`Write` derivations (used by DOs in Agents 4 and 5) benefit from integration testing against a real PostgreSQL-compatible database.

### Test Classification

| Category | What it covers | Infrastructure | Run with |
|----------|---------------|----------------|----------|
| **Unit tests** (Agents 1, 2, 3, 6) | Circe codecs, PureConfig loading, enum parsing, retry logic (cats-effect-testing `TestControl`), validation, macro resolution, change detection, error classification | None | `sbt test` |
| **Unit tests** (Agents 4, 5 — non-Doobie) | Circe codecs, status derivation logic, `ProcessingResult.toResultDO`, `isSucceeded`/`isFailed`/`isSkipped`, workflow validation, launcher traits | None | `sbt test` |
| **Doobie integration tests** (Agents 4, 5) | `Read`/`Write` instance derivation for DOs, round-trip through actual SQL columns, enum column mapping | AlloyDB Omni (Docker) | `sbt "testOnly -- -n com.repcheck.tags.E2ETest"` |

### AlloyDB Omni Docker Setup

For the Doobie integration tests, use AlloyDB Omni in Docker Compose:

```yaml
# docker-compose-test.yml
version: "3.8"
services:
  alloydb-omni:
    image: google/alloydbomni:15
    environment:
      POSTGRES_USER: repcheck_test
      POSTGRES_PASSWORD: test_password
      POSTGRES_DB: repcheck_test
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U repcheck_test"]
      interval: 5s
      timeout: 3s
      retries: 10
```

### Integration Test Pattern

Doobie integration tests follow this pattern:
1. Tagged with `taggedAs E2ETest` — excluded from `sbt test`, run separately
2. `beforeAll()`: create tables using DDL from acceptance criteria schemas
3. Each test: insert a DO via Doobie `Write`, read it back via `Read`, assert equality
4. `afterAll()`: drop tables, close transactor
5. Ephemeral namespace prefix (`test-{uuid}-`) on table names to allow parallel runs

### What Does NOT Need Docker

- **Circe round-trips** — pure JSON encoding/decoding, no DB needed
- **PureConfig loading** — reads from in-memory HOCON strings
- **RetryWrapper** — uses `cats.effect.testkit.TestControl` to simulate time without real sleeps
- **Workflow validation** — pure logic operating on case classes
- **MacroResolver** — pure string replacement
- **Error classification** — pure function mapping status codes to error classes
- **Status derivation** — pure function from `List[ProcessingResult]` to `PipelineStatus`

### CI Integration Tests (GitHub Actions)

Integration tests run in CI on every PR commit as a **separate job** from the unit test job. This keeps unit tests fast while still validating Doobie round-trips on every change.

```yaml
# In .github/workflows/ci.yml
jobs:
  build:
    # ... existing: compile, unit test, scalafmt, scalafix, coverage ...
    steps:
      - uses: actions/checkout@v4
      - name: Unit tests
        run: sbt coverage test coverageReport

  integration-test:
    runs-on: ubuntu-latest
    needs: build  # only run if unit tests pass
    services:
      alloydb-omni:
        image: google/alloydbomni:15
        env:
          POSTGRES_USER: repcheck_test
          POSTGRES_PASSWORD: test_password
          POSTGRES_DB: repcheck_test
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U repcheck_test"
          --health-interval 5s
          --health-timeout 3s
          --health-retries 10
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - name: Run integration tests
        env:
          TEST_DB_HOST: localhost
          TEST_DB_PORT: 5432
          TEST_DB_NAME: repcheck_test
          TEST_DB_USER: repcheck_test
          TEST_DB_PASSWORD: test_password
        run: sbt "testOnly -- -n com.repcheck.tags.E2ETest"
```

The `services` block gives GitHub Actions a managed Docker container. No `docker-compose` needed in CI — the `docker-compose-test.yml` is for local development only.

> **Guideline for agents**: If your test doesn't touch Doobie `Read`/`Write` against actual SQL, it's a unit test. Keep it fast and infrastructure-free.

---

## Execution Timeline

```
Pre-work (scaffold)     ████  (~15 min)
                            │
Wave 1 (Agents 1,2,3)      ████████████  (parallel, ~45 min)
                                        │
Merge + compile check                   ████  (~15 min)
                                            │
Wave 2 (Agents 4,5)                         ████████████████  (parallel, ~60 min)
                                                            │
Merge + compile check                                       ████  (~15 min)
                                                                │
Wave 3 (Agent 6)                                                ████████  (~30 min)
                                                                        │
Final merge + full test                                                 ████████  (~20 min)
                                                                                │
Audit #1                                                                        ████████
Fix + Audit #2                                                                          ████████
Push + PR                                                                                       ██
```

---

## Risk Areas & Mitigations

| Risk | Mitigation |
|------|-----------|
| `RetryWrapper[F]` needs Cats Effect `Temporal` — more complex than Component 1's pure types | Agent 2 gets explicit implementation guidance including error factory pattern; test with `IO` from cats-effect-testing |
| PureConfig auto-derivation in Scala 3 requires `derives ConfigReader` or explicit Reader — easy to get wrong | Agent 6 gets concrete HOCON test strings and a full example `application.conf` to load from |
| `WorkflowDefinition` validation (cycle detection) is non-trivial | Agent 5 gets pseudocode for topological sort; tests enumerate specific cycle and acyclic cases |
| Merge conflicts between Wave 1 agents (shared `build.sbt`, package objects) | Pre-work creates the full package structure with empty marker files |
| ~~WartRemover may flag `UUID.randomUUID()` as impure in default params~~ | **Resolved**: effectful `PipelineEvent.create[F[_]: Sync, T]` factory method keeps the case class pure; no default param side effects |
| 55 table constants is tedious and error-prone | Agent 6 gets the exact list from the spec; tests verify count and uniqueness |
| Doobie `Read`/`Write` derivation may not round-trip correctly for enums stored as strings | Doobie integration tests (tagged `E2ETest`) verify actual SQL round-trips against AlloyDB Omni in Docker |
| Config types defined in library may conflict with consuming app configs | Library ships NO `reference.conf` — only Scala defaults; consuming apps own their `application.conf` |
| `PipelineConfig[T]` PureConfig derivation requires `ConfigReader[T]` in scope — easy to forget | Tests use a concrete `TestAppConfig` to prove the pattern; doc examples show `derives ConfigReader` on every app config type |

---

## Success Criteria

- `sbt compile` passes with WartRemover + tpolecat (zero warnings under `-Werror`)
- `sbt test` — all unit tests pass, target **150+ tests** across all areas
- `sbt "testOnly -- -n com.repcheck.tags.E2ETest"` — all Doobie integration tests pass against AlloyDB Omni
- `sbt scalafmtCheckAll` — clean
- `sbt scalafixAll --check` — clean
- `sbt coverage test coverageReport` — **≥90% patch coverage**
- CI: unit test job + integration test job both green on PR
- Two audit passes against acceptance criteria (same pattern as Component 1)
- Published to GitHub Packages, consumable by downstream components
