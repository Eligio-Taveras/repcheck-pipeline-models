<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/acceptance-criteria/07-AMENDMENTS-PIPELINE.md -->

# Acceptance Criteria: Component 7 — Amendments Pipeline

Single SBT project (`amendments-pipeline`) within `repcheck-data-ingestion` that ingests amendments from Congress.gov JSON API. Single source, simple upsert, no history archival. **Depends on**: `repcheck-shared-models` (Component 1), `repcheck-pipeline-models` (Component 2), `ingestion-common` (Component 3).

---

## System Context

### One Project, One Source

| Project | Trigger | Responsibility | Publishes |
|---------|---------|---------------|-----------|
| `amendments-pipeline` | Scheduled (e.g., every 6 hours) | Fetch amendments from Congress.gov API, detect changes, upsert to AlloyDB, create placeholder members/bills for unknown sponsors and amended bills | Nothing |

**Simplest ingestion pipeline:**
- Single data source — Congress.gov JSON API only
- No history archival — `updateDate` comparison + upsert sufficient
- Single DO output — `AmendmentDetailDTO.toDO` produces one `AmendmentDO`
- Standard `ChangeDetector` from Component 3 §3.3

### End-to-End Data Flow

```
Cloud Scheduler
    |
    +-- amendments-pipeline (scheduled)
          |
          +-- Fetch amendment list from Congress.gov API (paginated)
          +-- For each amendment: detect change via updateDate comparison
          +-- For changed amendments: fetch detail endpoint
          +-- Convert AmendmentDetailDTO.toDO -> AmendmentDO
          +-- Create placeholder member for unknown sponsor (if sponsorBioguideId present)
          +-- Create placeholder bill for amended bill (if billId present)
          +-- Upsert AmendmentDO to AlloyDB
```

### No Events Emitted

Amendments pipeline is a **pure data recorder** — does not emit events. Bill re-analysis triggered by **new bill text**, not amendment recording:
1. Amendment adopted by Congress
2. Congress.gov publishes new text version of amended bill
3. `bill-text-availability-checker` detects new text via `updateDateIncludingText` change
4. Emits `bill.text.available` → `bill-text-pipeline` downloads text → emits `bill.text.ingested`
5. Bill Analysis Pipeline re-analyzes bill with new text

**`AmendmentRecordedEvent` removed:** Originally in Components 2 & 3; removed — amendments pipeline emits no events, bill re-analysis triggered by bill text path instead.

### Amendment Votes

Roll call votes on amendments flow through votes pipeline (Component 6), not amendments pipeline. `VoteDO` has `legislationType` and `legislationNumber` fields referencing the amendment. Amendments pipeline records only amendment *metadata* (sponsor, description, purpose, amended bill) — vote data from Component 6.

### No History Archival

Amendments do **not** use archive-before-overwrite pattern:
- Amendments rarely change substantively after initial recording
- `updateDate` comparison in `ChangeDetector` prevents redundant writes
- No downstream consumer depends on amendment history
- Future: adding history follows same `HistoryArchiver` pattern as bills/votes/members

### Amendment Types

Per Component 1 §1.8, `AmendmentType` enum:
- `HAMDT` — House amendment
- `SAMDT` — Senate amendment
- `SUAMDT` — Senate unprinted amendment

### Placeholder Entity Pattern

| Reference | Placeholder Type | Condition |
|-----------|-----------------|-----------|
| `sponsorBioguideId` | `MemberDO` placeholder | Only when `Some` — some amendments have no identified sponsor |
| Amended bill | `BillDO` placeholder | When amendment references specific bill via `amendedBill` |

Placeholders use `INSERT ... ON CONFLICT DO NOTHING` (Component 3 §3.6) — safe against concurrent ingestion.

### Congress.gov API Endpoints

| Endpoint | Returns | Used By |
|----------|---------|---------|
| `GET /amendment?congress={N}&...` | List of `AmendmentListItemDTO` | `fetchAll` (pagination) |
| `GET /amendment/{congress}/{type}/{number}` | `AmendmentDetailDTO` with sponsors, amended bill, latest action | `fetchDetail` |
| `GET /amendment/{congress}/{type}/{number}/actions` | Amendment actions timeline | Not used in initial implementation |
| `GET /amendment/{congress}/{type}/{number}/cosponsors` | Amendment cosponsors | Not used in initial implementation |
| `GET /amendment/{congress}/{type}/{number}/amendments` | Sub-amendments | Not used in initial implementation |
| `GET /amendment/{congress}/{type}/{number}/text` | Text versions (117th Congress+) | Not used in initial implementation |

**Minimal initial scope:** Only list and detail endpoints consumed. Actions, cosponsors, sub-amendments, text available in API but not consumed initially. API client extensible without architecture change.

---

## Implementation Areas

| Area | Status | Description |
|------|--------|-------------|
| 7.1 Amendments API Client | New | Extends `CongressGovPaginatedClient` for amendment list and detail endpoints |
| 7.2 Amendment Repository | New | Doobie repository for amendments table — upsert, queries |
| 7.3 Amendment Processing Pipeline | New | FS2 streaming pipeline: fetch → detect → placeholders → upsert |

## Component Routing Table

| Task | Area File |
|------|-----------|
| Congress.gov amendment list/detail API integration | [7.1 Amendments API Client](07-amendments-pipeline/07.1-amendments-api-client.md) |
| AlloyDB persistence for amendments | [7.2 Amendment Repository](07-amendments-pipeline/07.2-amendment-repository.md) |
| Streaming pipeline: fetch → detect → placeholders → upsert | [7.3 Amendment Processing Pipeline](07-amendments-pipeline/07.3-amendment-processing-pipeline.md) |

---

## Cross-Cutting Concerns

### SBT Module Structure

```
repcheck-data-ingestion/
└── amendments-pipeline/             (Cloud Run Job)
    └── repcheck.ingestion.amendments
        ├── api
        │   └── AmendmentsApiClient          (7.1)
        ├── repository
        │   └── AmendmentRepository          (7.2)
        ├── pipeline
        │   └── AmendmentProcessor           (7.3)
        ├── app
        │   └── AmendmentPipelineApp         (IOApp entry point — pure wiring)
        └── errors
            ├── AmendmentFetchFailed         (7.1)
            └── AmendmentUpsertFailed        (7.2)
```

**No shared module needed** — single project, all classes in one SBT module. Application entry point (`AmendmentPipelineApp`) follows standard IOApp + PureConfig + `PipelineBootstrap` pattern from Component 3 §3.7. Pure wiring — no area file needed.

### Dependencies

```
amendments-pipeline
├── ingestion-common                 (internal SBT dependency — Component 3)
│   ├── CongressGovPaginatedClient   (API base)
│   ├── ChangeDetector               (change detection)
│   ├── PlaceholderCreator           (cross-entity refs)
│   ├── TransactorResource           (DB connection)
│   ├── UpsertHelper                 (SQL generation)
│   ├── PipelineBootstrap            (config, runId)
│   └── WorkflowStateUpdater         (step tracking)
├── repcheck-shared-models           (published artifact — Component 1)
│   ├── AmendmentListItemDTO, AmendmentDetailDTO
│   ├── AmendmentDO
│   ├── AmendmentType (HAMDT, SAMDT, SUAMDT)
│   └── HasPlaceholder[MemberDO], HasPlaceholder[BillDO]
└── repcheck-pipeline-models         (published artifact — Component 2)
    ├── ProcessingResult, PipelineRunSummary
    └── Tables (Amendments)
```

### Testing Strategy

| Test Type | Scope | Infrastructure |
|-----------|-------|---------------|
| Unit tests | Processor logic, change detection integration | MockitoScala |
| WireMock tests | `AmendmentsApiClient` pagination, detail fetching, error classification | WireMock |
| Integration tests | `AmendmentRepository` (CRUD, upsert, conflict handling) | `DockerPostgresSpec` |
| Pipeline integration | Full pipeline flow: API → detect → placeholders → upsert | WireMock + DockerPostgresSpec |