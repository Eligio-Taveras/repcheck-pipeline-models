<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/acceptance-criteria/02-PIPELINE-MODELS.md -->

# repcheck-pipeline-models — Compressed Context

## System Overview

**Purpose:** Shared operational library for pipeline infrastructure — events, job metadata, error classification, change detection, workflow definitions, launcher execution model, constants.

**Used by:** data-ingestion, llm-analysis, scoring-engine pipelines + Launcher app. NOT used by API server or shared-models.

**Depends on:** `repcheck-shared-models` (domain enums: Chamber, VoteType, FindingType).

---

## Pipeline Architecture

### Scheduled Jobs (Independent Crons)

| Job | Purpose | Publishes |
|-----|---------|-----------|
| Member Profile Pipeline | Pull/detect/archive/upsert member profiles | `member.updated` |
| LIS Mapping Refresher | Refresh senator-lookup.xml LIS mappings | `member.updated` (mapped senators) |
| Committees Refresh | Pull committee membership | Nothing |
| Bill Metadata Ingestion | Upsert bills table | Nothing |
| Bill Text Availability Checker | Scan tracked bills for new text versions | `bill.text.available` |
| Votes Ingestion | Pull votes for recorded bills | `vote.recorded` |
| Amendments Ingestion | Pull amendments for recorded bills | `amendment.recorded` |

### Event-Driven Pipelines (Launcher-Launched)

| Event | Launches | Publishes |
|-------|----------|-----------|
| `bill.text.available` | Bill Text Ingestion Pipeline | `bill.text.ingested` |
| `bill.text.ingested` | Bill Analysis Pipeline (LLM) | `analysis.completed` |
| `analysis.completed` | Analysis Pipeline (stance status update) | Nothing (polled) |
| `vote.recorded` | Votes Pipeline (stance status update) | Nothing (polled) |
| `amendment.recorded` | Amendment & Bill Re-analysis Pipeline | `analysis.completed` |
| `member.updated` | Pairing Validator | Nothing |
| `scoring.user.requested` | Scoring Pipeline | `scoring.user.completed` |

### Launcher Execution Flow

```
Cloud Scheduler → Launcher (Cloud Run Job)
  1. Pull Pub/Sub messages
  2. Read workflow definition from GCS per message
  3. Look up requested step
  4. Check workflow_run_steps: all dependencies completed?
  5. Extract image, args, env, resources, networking from step
  6. Resolve macros ({{run_id}}, {{date}}, {{message.billId}}, etc.)
  7. Launch target Cloud Run Job via Jobs API
```

### Application Execution Flow

1. Receive `run_id` and args from Launcher
2. Look up workflow state in `workflow_run_steps` for that `run_id`
3. Update step status → `running`
4. Do work (ingest, analyze, score, etc.)
5. Update step status → `completed`
6. Publish event(s) to Pub/Sub for downstream steps
7. On failure: increment `retry_count`, requeue Pub/Sub message, update status. After 3 retries: set status → `failed`, pipeline stops.

### Placeholder Entity Pattern

When a pipeline ingests data referencing a non-existent entity (e.g., bill with unknown `sponsorBioguideId`), create a **placeholder row** with only natural key populated (all other fields null/default). This ensures FK validity. Owning pipeline fills full data later via upsert — ChangeDetector diffs placeholder vs. full entity and updates all fields. Applies across all entity types, implemented in `ingestion-common` (Component 3 §3.6).

### Service Account Boundaries

| Service Account | Used By | Access |
|----------------|---------|--------|
| Launcher SA | Launcher, Daily Upload Initializer | Pub/Sub (pull), GCS (read workflows), Cloud Run (launch), AlloyDB (read-only workflow state) |
| Ingestion SA | bills, votes, members, amendments pipelines | Congress.gov API, senate.gov XML, clerk.house.gov XML, AlloyDB (r/w), Pub/Sub (publish) |
| Analysis SA | bill-analysis, amendment & bill re-analysis pipelines | AlloyDB (r/w), Pub/Sub (publish), GCS (read prompts), LLM API keys |
| Scoring SA | scoring-pipeline, score-cache | AlloyDB (r/w), Pub/Sub (publish), GCS (read snapshots) |

All pipeline SAs: AlloyDB write to `workflow_run_steps`, Pub/Sub publish for events.

### Alerting

Infrastructure-driven, not application code. Applications update `workflow_run_steps.status` → `failed` on retry exhaustion. Cloud Monitoring policies detect `failed` status → PagerDuty (free tier, up to 5 users).

---

## Implementation Areas

| Area | Status | Description |
|------|--------|-------------|
| 2.1 Inter-Pipeline Communication | New | Pub/Sub event envelope, typed payloads, event routing |
| 2.2 Pipeline Execution Tracking | New | Run metadata, per-item processing results, status enums |
| 2.3 Error Handling & Retry | New | Error classification, retry wrapper, dead-letter handling |
| 2.4 Change Detection | New | Detection strategies, persistence strategies, event emission rules |
| 2.5 Workflow Definition Schema | New | GCS-stored workflow spec: steps, container config, resources, networking, volumes, identity, health checks, macros |
| 2.6 Workflow Execution State | New | DB-stored runtime state: workflow_runs, workflow_run_steps, status transitions, retry tracking, message storage |
| 2.7 Launcher Execution Model | New | Message pull, dependency resolution, macro resolution, Cloud Run Job invocation |
| 2.8 Pipeline Configuration | New | Vote weights, committee attribution weights, analysis pass config |
| 2.10 Constants | New | Table names, event type strings |

---

## Cross-Cutting Acceptance Criteria

**Package Structure:** `repcheck.pipeline.models` + sub-packages: `events`, `metadata`, `errors`, `changes`, `workflow`, `workflow.schema`, `workflow.state`, `launcher`, `config`, `constants`.

**Build:** Published to GitHub Packages. Depends on `repcheck-shared-models`. Dependencies: Circe (semi-auto), Cats Effect (RetryWrapper needs Temporal[F]), PureConfig (auto-derivation), Doobie (Read/Write for DOs), FS2 (streaming types).

**Database Migrations:**
- New: `workflow_runs`, `workflow_run_steps` tables.
- Add `reasoning TEXT` to `score_history`.
- New (Component 1): `bill_text_versions`, `committees`, `committee_members`, `bill_committee_referrals`.
- New (Component 5): `member_history`, `member_term_history` (archive-before-overwrite).

**Infrastructure (Terraform):**
- Four service accounts: Launcher SA (read-only DB), Ingestion SA, Analysis SA, Scoring SA.
- Cloud Monitoring alerting on `workflow_run_steps.status = 'failed'`.
- PagerDuty integration via Cloud Monitoring (free tier).
- Secret Manager for API keys, DB credentials — referenced in workflow defs, mounted by Cloud Run.

**Code Quality:**
- Pass `sbt compile` with WartRemover + tpolecat.
- Pass `sbt scalafmtCheckAll` and `sbt scalafixAll --check`.
- Test coverage > 90% (Codecov patch coverage enforced).
- No `@nowarn` or `@SuppressWarnings`.
- Curly brace syntax only (no Scala 3 braceless).

**Documentation:** ScalaDoc on all public classes; cross-reference BEHAVIORAL_SPECS.md or SYSTEM_DESIGN.md.