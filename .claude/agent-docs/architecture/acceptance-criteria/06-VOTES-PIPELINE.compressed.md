<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/acceptance-criteria/06-VOTES-PIPELINE.md -->

# Acceptance Criteria: Component 6 — Votes Pipeline

Single SBT project within `repcheck-data-ingestion` that ingests roll call votes from both chambers. House votes from Congress.gov JSON API; Senate votes from senate.gov XML feeds. Both map to unified DTOs with shared storage, history, and event emission logic. **Depends on**: `repcheck-shared-models` (Component 1), `repcheck-pipeline-models` (Component 2), `ingestion-common` (Component 3).

---

## System Context

### One Project, Two Sources

| Project | Trigger | Responsibility | Publishes |
|---------|---------|---------------|-----------|
| `votes-pipeline` | Scheduled (e.g., every 2 hours) | Fetch roll call votes from Congress.gov (House) and senate.gov (Senate), detect changes, archive history, persist to AlloyDB, create placeholder members for unknown voters | `vote.recorded` |

Single project handles both chambers; two data sources converge on unified DTO types. Change detection, history archival, storage, and event emission are shared.

### End-to-End Data Flow

```
Cloud Scheduler
    |
    +-- votes-pipeline (scheduled)
          |
          +-- House votes path:
          |     +-- Fetch vote list from Congress.gov API (paginated)
          |     +-- For each vote: detect change via updateDate comparison
          |     +-- For changed votes: fetch members endpoint (positions)
          |     +-- Convert HouseVoteMembers JSON to unified VoteMembersDTO
          |
          +-- Senate votes path:
          |     +-- Fetch roll call vote XML from senate.gov
          |     +-- Parse SenateVoteXmlDTO
          |     +-- Resolve LIS member IDs to bioguide IDs via lis_member_mapping table
          |     +-- Convert to unified VoteMembersDTO
          |
          +-- Shared path (both chambers):
                +-- Convert VoteMembersDTO.toDO -> VoteConversionResult (VoteDO + positions)
                +-- Detect position changes via VoteChangeDetector
                +-- Archive old vote + positions to history tables
                +-- Upsert vote metadata + positions to AlloyDB
                +-- Create placeholder members for unknown voters
                +-- Emit vote.recorded event (new votes, or updates with position changes)
```

### Downstream Consumers

```
vote.recorded event -> Scoring Engine (downstream)
  +-- Extract all memberIds from vote positions
  +-- Find users whose representatives include those members
  +-- Re-score affected (userId, memberId) pairs
  +-- If bill has no analysis yet, requeue with backoff
```

### History & Archival Strategy

Archive-before-overwrite pattern (BEHAVIORAL_SPECS §1):

| Table | History Table | Archive Trigger |
|-------|--------------|-----------------|
| `votes` | `vote_history` | Vote metadata or positions changed |
| `vote_positions` | `vote_history_positions` | Positions archived alongside vote metadata (shared `history_id`) |

`VoteHistoryArchiver` copies current state to history tables with shared `history_id` UUID before upsert overwrites. History table DOs (`VoteHistoryDO`, `VoteHistoryPositionDO`) defined in Component 1 §1.2; code lives here.

### Vote Change Detection

Differs from generic `ChangeDetector` (Component 3 §3.3):

- **Generic**: Compares `updateDate` timestamps, then field-by-field diffing via `Product` reflection (bills, members, amendments).
- **`VoteChangeDetector`**: Fast-path skip if `updateDate` unchanged; if dates differ, checks whether vote **positions** actually changed by comparing incoming against stored positions. Event emission depends on position changes, not metadata changes.

Lives in votes-pipeline (not `ingestion-common`) due to vote-specific logic.

### Event Payload

**`VoteRecordedEvent`** (Component 2 §2.1): `voteId`, `billId: Option[String]`, `chamber`, `date`, `congress`, `isUpdate`

**Emission conditions** (BEHAVIORAL_SPECS §4):
- **New vote**: emit with `isUpdate = false`
- **Updated vote (positions changed)**: emit with `isUpdate = true`
- **Updated vote (metadata only)**: upsert & archive, but do **not** emit (scoring is position-driven)

### Vote Type Classification

From `question` field:

| Vote Type | `question` Pattern |
|-----------|-------------------|
| `Passage` | "On Passage" / "On Motion to Suspend the Rules and Pass" |
| `ConferenceReport` | "On Agreeing to the Conference Report" |
| `Cloture` | "On Cloture" / "On the Cloture Motion" |
| `VetoOverride` | "On Overriding the Veto" |
| `Amendment` | "On Agreeing to the Amendment" |
| `Committee` | "Reported favorably" / "Ordered to be reported" |
| `Recommit` | "On Motion to Recommit" |
| `Other` | *(no pattern match)* |

Stored in `VoteDO.voteType`; passed in event for downstream scoring. `VoteType` enum and `fromQuestion` parser in Component 1 §1.8. Unknown patterns default to `Other` (warn-level log).

### Placeholder Member Resolution

When vote references member (via `bioguideId` in House, resolved `lisMemberId` in Senate) not in `members` table, votes-pipeline creates placeholder rows via `PlaceholderCreator` (Component 3 §3.6). Members-pipeline (Component 5) fills in full data later.

### Senate LIS Resolution

Senate vote XML uses `lisMemberId` (e.g., "S428"), not `bioguideId`:

1. Query `lis_member_mapping` table for each `lisMemberId`
2. If mapping exists: use `bioguideId` from mapping
3. If missing: skip position, log warn with unresolved `lisMemberId`, continue. Vote persists with resolved positions; unresolved tracked in `ProcessingResult`. Single unmapped senator should not block entire vote.

### Bill Linkage

- House vote responses include `bill` object: `billId`, `congress`, `type`, `number`
- Senate vote XML includes legislation references (same fields)
- Stored as `VoteDO.billId` (FK to `bills` table, `Option[String]`)
- Votes-pipeline creates placeholder bill rows if bill not yet in `bills` table
- Procedural votes (null `billId`) stored but excluded from alignment scoring

---

## Implementation Areas

| Area | Status | Description |
|------|--------|-------------|
| 6.1 House Votes API Client | Migrate + Extend | Extends `CongressGovPaginatedClient` for House vote list and members endpoints |
| 6.2 Senate Vote XML Client | New | Fetches and parses roll call vote XML from senate.gov |
| 6.3 Vote Repository & History | New | Doobie repositories for votes, positions — with archive-before-overwrite |
| 6.4 Vote Change Detection | New | Position-aware change detection: `VoteChangeDetector` |
| 6.5 Vote Processing Pipeline | New | FS2 streaming pipeline: fetch both chambers -> detect -> archive -> upsert -> placeholders -> events |

## Component Routing Table

| Task | Area File |
|------|-----------|
| Congress.gov House vote list/members API integration | [6.1 House Votes API Client](06-votes-pipeline/06.1-house-votes-api-client.md) |
| Senate.gov roll call vote XML fetch and parsing | [6.2 Senate Vote XML Client](06-votes-pipeline/06.2-senate-vote-xml-client.md) |
| AlloyDB persistence for votes, positions + history archival | [6.3 Vote Repository & History](06-votes-pipeline/06.3-vote-repository-history.md) |
| Position-aware vote change detection | [6.4 Vote Change Detection](06-votes-pipeline/06.4-vote-change-detection.md) |
| Full vote processing pipeline: fetch -> detect -> archive -> upsert -> events | [6.5 Vote Processing Pipeline](06-votes-pipeline/06.5-vote-processing-pipeline.md) |

---

## Cross-Cutting Concerns

### Package Structure

```
repcheck.ingestion.votes
+-- api
|   +-- HouseVotesApiClient               (6.1)
|   +-- SenateVoteXmlClient               (6.2)
+-- repository
|   +-- VoteRepository                    (6.3)
|   +-- VotePositionRepository            (6.3)
|   +-- VoteHistoryArchiver               (6.3)
+-- detection
|   +-- VoteChangeDetector                (6.4)
+-- pipeline
|   +-- VoteProcessor                     (6.5)
+-- errors
    +-- HouseVoteFetchFailed              (6.1)
    +-- SenateVoteFetchFailed             (6.2)
    +-- VoteUpsertFailed                  (6.3)
    +-- VoteArchiveFailed                 (6.3)
    +-- LisResolutionFailed              (6.2)
```

Application entry point (`VotesPipelineApp`): IOApp + PureConfig + `PipelineBootstrap` pattern (Component 3 §3.7). No area file — pure wiring.

### Dependencies

```
votes-pipeline
+-- ingestion-common                     (internal SBT dependency -- Component 3)
|   +-- CongressGovPaginatedClient       (API base, House votes)
|   +-- XmlFeedClient                    (XML parsing, Senate votes)
|   +-- IngestionEventPublisher          (event emission)
|   +-- PlaceholderCreator               (unknown members/bills refs)
|   +-- TransactorResource               (DB connection)
|   +-- UpsertHelper                     (SQL generation)
|   +-- PipelineBootstrap                (config, runId)
|   +-- WorkflowStateUpdater             (step tracking)
|   +-- RetryWrapper                     (operation retry)
+-- repcheck-shared-models               (published artifact -- Component 1)
|   +-- VoteListItemDTO, VoteDetailDTO, VoteMembersDTO, VoteResultDTO
|   +-- SenateVoteXmlDTO, SenateVoteMemberXmlDTO
|   +-- VoteDO, VotePositionDO, VoteHistoryDO, VoteHistoryPositionDO
|   +-- VoteType (enum), VotePartyTotalDTO
|   +-- HasPlaceholder[MemberDO], HasPlaceholder[BillDO]
+-- repcheck-pipeline-models             (published artifact -- Component 2)
    +-- VoteRecordedEvent
    +-- ProcessingResult, PipelineRunSummary
    +-- Tables (Votes, VotePositions, VoteHistory, VoteHistoryPositions)
```

Votes-pipeline does NOT depend on `bills-common` (Component 4) or members repositories (Component 5). References members/bills only via `PlaceholderCreator` + `EntityRepository`.

### Testing Strategy

| Test Type | Scope | Infrastructure |
|-----------|-------|---------------|
| Unit tests | VoteChangeDetector, vote type parsing, LIS resolution logic, processor flow | MockitoScala |
| WireMock tests | `HouseVotesApiClient` (JSON), `SenateVoteXmlClient` (XML) | WireMock |
| Integration tests | All repositories (CRUD, upsert, history archival, position replacement) | `DockerPostgresSpec` |
| Pipeline integration | Full pipeline flow (both chambers) | WireMock + DockerPostgresSpec + mock Pub/Sub |

### Migration Checklist

1. Remove `vote-ingestion` module (legacy vote fetching/storage logic)
2. Remove vote-related API clients from `gov-apis`
3. Entity-specific DTOs/DOs already migrated to `shared-models` (Component 1)
4. Shared infrastructure already migrated to `ingestion-common` (Component 3)