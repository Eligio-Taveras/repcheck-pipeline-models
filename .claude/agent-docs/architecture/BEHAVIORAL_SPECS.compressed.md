<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/BEHAVIORAL_SPECS.md -->

# RepCheck Behavioral Specifications

Supplement to SYSTEM_DESIGN.md. Explicit rules for pipeline behavior, entity linking, scoring logic, event emission, and workflow execution.

---

## 1. Change Detection & Persistence Strategy

Per-entity rules for detecting and handling changes from Congress.gov:

| Entity | Natural Key | Change Detection | Persistence Strategy | Event Trigger |
|--------|------------|-----------------|---------------------|---------------|
| Bill | `{congress}-{billType}-{number}` | `updateDate` from API > stored `updateDate` | Upsert (overwrite) in AlloyDB | Emit `bill.text.available` when `updateDate` changed AND `textUrl` non-null |
| Vote | `{congress}-{chamber}-{rollNumber}` | `updateDate` from API > stored `updateDate`, or doesn't exist | Upsert with history: archive prior to `vote_history`, overwrite main row. Update `stance_materialization_status.has_votes` | Emit `vote.recorded` on insert OR when positions changed |
| Member | `{bioguideId}` | Any field differs | Upsert (overwrite) in AlloyDB | No event |
| Amendment | `{congress}-{amendmentType}-{number}` | `updateDate` from API > stored `updateDate` | Upsert (overwrite) in AlloyDB | No event |
| Analysis | `{billId}-{passNumber}-{version}` | N/A (always new version) | Insert new version (append-only). Update `stance_materialization_status.has_analysis` and `all_passes_completed` | Emit `analysis.completed` after final pass |

### Key Rules

- **No field-by-field diffing** — compare `updateDate` timestamps (bills/amendments) or existence + position comparison (votes)
- **Votes are diffed and upserted** — prior version saved to `vote_history`; scoring uses latest only; history is audit trail
- **Analysis is append-only** — re-analyzing creates new version; preserves full audit trail
- **`bill.text.available` emits on every re-ingest** — enables re-analysis when bill text updates
- **Scoring is decoupled from data-change events** — `vote.recorded` and `analysis.completed` update DB status flags. Stance materialization readiness determined by scheduled DB scanner (§3.2), not event consumption

---

## 2. Join Keys & Entity Linking

### Foreign Key Relationships

```
BILL.billId       ← VOTE.billId              (many votes per bill)
BILL.billId       ← AMENDMENT.billId          (many amendments per bill)
BILL.billId       ← ANALYSIS.billId           (many analysis versions per bill)
VOTE.voteId       ← VOTE_POSITION.voteId      (many positions per vote)
MEMBER.memberId   ← VOTE_POSITION.memberId    (many positions per member)
USER.userId       ← PREFERENCE.userId          (many preferences per user)
USER.userId + MEMBER.memberId → SCORE          (composite key)
```

### Congress Scoping Rules

- Bills, votes, amendments scoped to congress number
- Members span multiple congresses (same `bioguideId` across terms)
- **Scoring is perpetual** — legislator's votes from ALL congresses contribute to aggregate score
- **Two score tiers maintained:**
  - **Aggregate score**: across all congresses served (lifetime alignment)
  - **Per-congress score**: scoped to single congress
- `congress` field on bills/votes partitions per-congress scores; aggregate spans all

### Vote-to-Bill Linkage

- Congress.gov API includes `bill` object in vote response: `billId`, `congress`, `type`, `number`
- Stored as `VOTE.billId` FK to `BILL.billId`
- Procedural votes (no bill attached): `billId` is null — stored but excluded from alignment scoring

### Vote Type Detection & Significance

Each vote has distinct legislative meaning communicated to users:

| Vote Type | `question` Pattern | Legislative Meaning | User-Facing Explanation |
|-----------|------------------------|---------------------|------------------------|
| Committee | "Reported favorably" / "Ordered to be reported" | Advances out of committee to full chamber | "Voted to advance this bill from committee for consideration by the full [House/Senate]" |
| Cloture | "On Cloture" / "On the Cloture Motion" | Ends debate / breaks filibuster (60 votes) | "Voted to end debate on this bill, allowing it to proceed to a final vote" |
| Floor passage | "On Passage" / "On Motion to Suspend the Rules and Pass" | Approves bill in one chamber | "Voted to pass this bill in the [House/Senate]" |
| Amendment | "On Agreeing to the Amendment" | Modifies bill text before passage | "Voted on a proposed change to the bill's text" |
| Conference report | "On Agreeing to the Conference Report" | Approves final reconciled version | "Voted to approve the final version of this bill after both chambers agreed on the text" |
| Motion to recommit | "On Motion to Recommit" | Sends bill back to committee (often blocks passage) | "Voted to send this bill back to committee" |
| Veto override | "On Overriding the Veto" | Overrides presidential veto (2/3 majority) | "Voted to override the President's veto of this bill" |

### Scoring Weight by Vote Type

Vote weight configurable in scoring config:

```hocon
scoring.vote-weights {
  passage = 1.0
  conference-report = 1.0
  cloture = 0.8
  veto-override = 0.9
  amendment = 0.5
  committee = 0.4
  motion-to-recommit = 0.6
}
```

Scoring LLM receives vote type and weight as context — weights guide prompt emphasis, not hard multipliers. Unknown patterns default to 0.5 and flagged for manual review.

### Vote Type Detection

Determined from `question` field in Congress.gov response. Pattern matched against known question text (see table above). Stored as `VOTE.voteType` enum: `Passage | ConferenceReport | Cloture | VetoOverride | Amendment | Committee | Recommit | Other`.

### Bill Text Decomposition

Large bills cannot fit within single LLM context. Before analysis passes run, large bills undergo decomposition.

#### Decomposition Steps

1. **Text parsing / section identification** (Ollama sidecar) — Ollama instance running as Cloud Run sidecar reads bill text (plain text, PDF-extracted, or XML) and identifies logical sections: boundaries, headings, numbering. For XML: structural elements provide hints; for plain text: legislative formatting conventions. Each section becomes `BillTextSectionDO` row. Cost: minimal (Ollama runs locally).

2. **In-process section embedding** (DJL + ONNX Runtime) — embed each section using sentence-transformer (e.g., `all-MiniLM-L6-v2`, ~80MB) loaded via DJL with ONNX Runtime backend. Produces 384-dimensional dense vector per section for clustering. No external API. Throughput: hundreds of sections/second on CPU.

3. **Semantic clustering** (Smile ML library) — cluster embedding vectors using k-means or DBSCAN to produce concept groups. Sections about "transportation funding" cluster together regardless of position. Cluster count determined dynamically (target 5-20 groups for large bill, min 2 sections/group). Deterministic and free.

4. **LLM-assisted simplification** (Haiku API, only external cost) — for each concept group, call LLM to produce coherent summary using decomposition prompts from `repcheck-prompt-engine-bills` (`concept-simplification` profile). Cost: ~$0.001/group, 10-20 groups = $0.01-0.02.

5. **Persist to AlloyDB:**
   - `bill_text_sections` — section content, identifier, heading, ordinal index, embedding
   - `bill_concept_groups` — simplified text, title, embedding
   - `bill_concept_group_sections` — junction linking sections to groups
   - Tied to `bill_text_versions.version_id`, immutable, reusable across re-analyses
   - 1536-dim embeddings for semantic search generated separately

#### Ollama Sidecar Architecture

- **Model**: Small, fast model for text parsing (e.g., Llama 3.2 1B). Only identifies section boundaries and extracts headings.
- **Communication**: HTTP API on localhost (`http://localhost:11434`)
- **Lifecycle**: Starts with Cloud Run Job, shares network namespace
- **Cost**: Runs on Cloud Run Job's CPU/memory — no per-token API charges
- **Why not in-process?** Text parsing requires instruction-following LLM; Ollama provides lightweight sidecar without external API costs

**Why embedding-based clustering instead of LLM classification?** — LLM-based approach requires one call per section (~200 for large bill). Embedding-based clustering achieves same grouping at negligible cost via in-process semantic similarity. `section-classification` profile in Component 8 available as fallback.

**Key rules:**
- Short bills fitting context window skip decomposition (raw text used directly)
- bill-analysis-pipeline (Component 10) owns decomposition orchestration
- Component 8 provides only LLM-assisted simplification prompts
- Results (sections, concept groups) persisted to AlloyDB, tied to text version, reusable across re-analyses
- DJL embedding model (`all-MiniLM-L6-v2`) bundled in image (~80MB), produces 384-dim vectors for clustering only (NOT 1536-dim pgvector stored for semantic search)
- Smile clustering library runs in-process on JVM
- Ollama sidecar model pulled at container build, cached in image

### Embedding Generation for Semantic Search

Bill text and analysis outputs vectorized (pgvector, 1536 dimensions) for semantic search in two distinct layers:

**Bill text layer** (tied to text version, immutable, reusable):

| Source | Text Field | Target Table / Column | DO | Enables |
|--------|-----------|----------------------|-----|---------|
| Parsed sections | `BillTextSectionDO.content` | `bill_text_sections.embedding` | `BillTextSectionDO` | "Find sections across all bills about broadband funding" |
| Concept group summaries | `BillConceptGroupDO.simplifiedText` | `bill_concept_groups.embedding` | `BillConceptGroupDO` | "Find concept groups similar to this across all bills" |
| Full text version | `BillTextVersionDO.content` | `bill_text_versions.embedding` | `BillTextVersionDO` | "Find bills with similar full text" |

**Analysis layer** (tied to analysis run, one set per analysis):

| Source | Text Field | Target Table / Column | DO | Enables |
|--------|-----------|----------------------|-----------|---------|
| Pass 1 overall summary | `BillSummaryOutput.summary` | `bill_analyses.embedding` | `BillAnalysisDO` | "Find bills similar to this one" |
| Pass 1 per-concept summaries | `ConceptSummaryResult.summary` | `bill_concept_summaries.embedding` | `BillConceptSummaryDO` | "Find analysis results for similar concepts" |
| Pass 1 topic classifications | `TopicScore.topic` values | `bill_subjects.embedding` | `BillSubjectDO` | Semantic topic similarity |
| Pass 2 pork findings | `PorkFinding.description` | `bill_findings.embedding` | `BillFindingDO` | "Find similar earmark/rider patterns" |
| Pass 2 impact analysis | `ImpactItem.description` | `bill_findings.embedding` | `BillFindingDO` | "Find bills with similar impact on this group" |
| Amendment descriptions | `AmendmentSummary.description` | `amendment_findings.embedding` | `AmendmentFindingDO` | Amendment semantic search |

**Key rules:**
- Embedding model configurable per deployment (e.g., OpenAI `text-embedding-3-small`, 1536 dimensions)
- Generated after each pass completes, before pipeline moves to next pass
- HNSW indexes with cosine distance pre-defined on all embedding columns
- Failures non-fatal — results persisted, bill won't appear in semantic search until regenerated

### AlloyDB Schema — Bill Text & Analysis

**Bill text layer** (tied to text version, immutable, reusable):

- **`bill_text_sections`** — One row per structural section. Stores content, identifier (e.g., "Sec. 101"), heading, ordinal index, `embedding`. Linked to `bill_text_versions.version_id`.
- **`bill_concept_groups`** — One row per concept group. Stores `simplified_text` (Haiku summary), `title`, `embedding`. Linked to `bill_text_versions.version_id`.
- **`bill_concept_group_sections`** — Junction linking sections to groups.

**Analysis layer** (tied to analysis run):

- **`bill_analyses`** — One row per analysis run. Keyed by `analysis_id` (UUID). Stores Pass 1 overall summary (`summary`, `reading_level`, `key_points`), topic tags (`topics TEXT[]`), per-pass model names (`pass1_model`, `pass2_model`, `pass3_model`), `pass_completed` (1, 2, or 3), `embedding`.
- **`bill_concept_summaries`** — One row per concept group per analysis. References group via `concept_group_id` FK. Stores per-concept Pass 1 results (`summary`, `reading_level`, `key_points`, `topics`), `embedding`. Linked to `bill_analyses.analysis_id`.
- **`bill_analysis_topics`** — One row per topic per analysis. Stores `TopicClassificationOutput` with `confidence`. `concept_group_id` nullable (NULL for bill-wide, set for per-concept).
- **`bill_findings`** — One row per finding. Discriminated by `finding_type_id` FK. Columns: `concept_group_id` (nullable), `severity`, `confidence`, `affected_section`, `affected_group` (nullable), `embedding`.
- **`bill_fiscal_estimates`** — One row per analysis run. Stores `FiscalEstimateOutput`: `estimated_cost`, `timeframe`, `confidence`, `assumptions TEXT[]`.
- **`amendment_findings`** — Same pattern as `bill_findings`. Columns: `severity`, `confidence`, `affected_section`.

**Key rules:**
- `analysisId` = auto-generated UUID
- Latest analysis = `WHERE bill_id = ? ORDER BY analyzed_at DESC LIMIT 1`
- All rows for one analysis share `analysis_id` across `bill_analyses`, `bill_concept_summaries`, `bill_analysis_topics`, `bill_findings`, `bill_fiscal_estimates`
- Concept groups and sections shared across analysis runs — belong to text version
- Structured outputs (`Pass1Output`, `Pass2Output`) NOT stored as JSONB — decomposed into normalized tables by Component 10 after each pass
- Finding types: `finding_types` lookup with seeded values: `topic_extraction`, `bill_summary`, `policy_analysis`, `stance_detection`, `impact_analysis`, `fiscal_estimate`, `pork`, `rider`, `lobbying`, `constitutional`

### Vote Position Query Pattern

- **Fetch all positions for a vote**: `SELECT * FROM vote_positions WHERE vote_id = ?`
- **Fetch member's position on vote**: `SELECT * FROM vote_positions WHERE vote_id = ? AND member_id = ?`
- **Fetch member's votes on bill**: `SELECT * FROM votes WHERE bill_id = ?` then join `vote_positions ON vote_id AND member_id = ?`
- Secondary index on `(bill_id)` in `votes`; PK `(vote_id, member_id)` in `vote_positions`

---

## 3. Batch Scoring Architecture

Scoring decoupled from data-change events. Combination of scheduled jobs and DB polling determines when data is ready:

| Process | Trigger | Input | Output |
|---------|---------|-------|--------|
| A. Pairing Validator (§11.6) | Scheduled + user location change + `member.updated` | `users`, `members`, `member_terms` | `user_legislator_pairings` |
| B. Ingestion Pipelines | Event-driven (Components 4/6/7/10) | Congress.gov API | bills, votes, amendments, findings + `stance_materialization_status` |
| C. Stance Materializer (§11.9) | Scheduled scanner (polls DB) | `vote_positions`, `bill_findings`, `stance_materialization_status` | `member_bill_stances` + `member_bill_stance_topics` |
| D. User-Bill Alignment (§11.10) | Scheduled | `users`, `bill_findings`, `member_bill_stance_topics` | `user_bill_alignments`, `user_amendment_alignments` |
| E. User-Member Scoring (§11.7, §11.8) | Scheduled + ad-hoc | `user_legislator_pairings`, `member_bill_stance_topics`, `user_bill_alignments` | `scores`, `score_topics`, `score_history` |
| F. Score Refresh Notifier (§11.11) | On ad-hoc completion | scoring results | `scoring.user.completed` event |

### 3.1 User-Legislator Pairings

- **At signup**: Look up user's state/district → query `member_terms` for current legislators (`end_year IS NULL OR end_year >= current year`) → insert `user_legislator_pairings`
- **Scheduled validation**: Scan all users, validate each pairing against current `member_terms`, add new, remove stale
- **On user location change**: `validateForUser(userId)` recomputes pairings
- **On `member.updated`**: Check if terms changed, update affected pairings

### 3.2 Stance Materialization

No Pub/Sub event subscription. Pipelines record status in DB when votes/analysis arrive. Scheduled scanner polls DB:

**Status tracking (by ingestion pipelines):**
- **Votes pipeline**: On position insert/update, upsert `stance_materialization_status` with `has_votes = true, votes_updated_at = NOW()`
- **Analysis pipeline**: After `completeAnalysisRun()`, upsert with `has_analysis = true, all_passes_completed = true, analysis_completed_at = NOW()`

**Scanner query:**
```sql
SELECT bill_id FROM stance_materialization_status
WHERE has_votes = true
  AND all_passes_completed = true
  AND (stances_materialized_at IS NULL
       OR stances_materialized_at < GREATEST(votes_updated_at, analysis_completed_at))
```

**Materialization per bill:**
1. Fetch `vote_positions` for bill
2. Fetch analysis topics + stance findings from `bill_findings`
3. For each (member, vote, topic): compute stance direction, generate per-topic reasoning (LLM), generate reasoning embedding (DJL/ONNX), link to finding
4. Upsert `member_bill_stances` parent row
5. Delete + insert `member_bill_stance_topics` children
6. Update `stances_materialized_at = NOW()`
7. Update `users.last_stance_change_at` for all paired users of affected members

**Key rules:**
- Eliminates dual-event coordination — no race condition waiting for both events
- Scanner runs on schedule (e.g., every 15 minutes), processes all qualifying bills in batch
- Materialization idempotent — re-run produces same result if data unchanged

### 3.3 User-Bill Alignment

Scheduled job pre-computes per-bill, per-topic alignment between user stances and bill stance findings:

- **Changed-bill optimization**: Only process bills where `stances_materialized_at` updated since last alignment run
- **Changed-user optimization**: Only process users whose stances changed
- For each (user, bill) with overlapping topics: compute alignment score, generate reasoning (LLM), generate reasoning embedding (DJL/ONNX)
- Upsert results to `user_bill_alignments` / `user_amendment_alignments`

### 3.4 User-Member Scoring

Scoring runs on schedule and triggered ad-hoc per user:

**Scheduled mode:**
1. Query users where `last_stance_change_at > last_scoring_run_at` for any paired legislator
2. For each qualifying user: `scoreUser(userId, correlationId)`

**Ad-hoc mode:**
1. Subscribe to `scoring.user.requested` events
2. For each: `scoreUser(event.userId, event.requestId)`
3. Publish `scoring.user.completed` on completion

**Phased scoring (`scoreUser`):**
1. Read pairings → get legislators
2. Aggregate user stances (§11.1)
3. **Phase 1 — Numeric scores (parallel)**: For each legislator: read profile (§11.2), read user-bill alignments, calculate alignment (§11.3), check no-overlap → persist all numeric scores (immediately visible to frontend)
4. **Phase 2 — LLM explanations (parallel)**: For each legislator: fetch evidence (§11.4), generate LLM explanation, generate reasoning embedding → persist explanations

Two-phase approach ensures numeric scores visible immediately. LLM explanation generation (slower, more expensive) does not block score visibility.

### 3.5 Ad-Hoc Scoring

Request/reply pattern for on-demand user scoring:

1. API Server (or scheduler) publishes `ScoringUserRequestedEvent(userId, requestId, source)` to Pub/Sub
2. Scoring Pipeline subscribes, processes user via `scoreUser`
3. On completion, Score Refresh Notifier publishes `ScoringUserCompletedEvent(userId, requestId, memberScoreCount, status)`
4. API Server reads return queue, notifies user

### 3.6 Skip-Unchanged Optimization

- `users.last_stance_change_at` — updated by stance materializer when any paired legislator's stances change, and by Q&A response submission
- `stance_materialization_status.stances_materialized_at` — updated per bill when stances materialized
- Scoring checks: for each user, have any paired legislators had stance changes since last scoring run? If not, skip.

### Score Structure (Two Tiers + History)

Fully normalized tables:

- **`scores`** — Current score per (userId, memberId). Overwritten on re-score. Contains `aggregate_score`, `status`, `reasoning`, `reasoning_embedding`.
- **`score_topics`** — Per-topic scores for current run.
- **`score_congress`** — Per-congress aggregate scores.
- **`score_congress_topics`** — Per-congress per-topic scores.
- **`score_history`** — Append-only audit trail. Contains `trigger_event` (`"scheduled"` or `"ad-hoc"`), `reasoning`, `reasoning_embedding`.
- **`score_history_congress`**, **`score_history_congress_topics`**, **`score_history_highlights`** — History detail tables.

### Scoring Computation Rules

- Each run computes BOTH aggregate and per-congress scores
- **Aggregate** = weighted combination of all per-congress scores (more recent weighted higher)
- **Per-congress** = based only on votes/analyses from specific congress
- Latest scores overwrite `scores` table for fast frontend reads
- Each run appends to `score_history` (enables "alignment over time" trend charts)
- Always uses latest bill and vote versions (historical versions not scored)
- History records what score WAS at each point — not re-scored independently

---

## 4. Event Emission — Precise Conditions

| Event | Emitted By | Condition | Payload |
|-------|-----------|-----------|---------|
| `bill.text.available` | bills-pipeline | `textUrl` non-null AND `updateDate` newer than stored | `{ billId, congress, textUrl, textFormat }` |
| `vote.recorded` | votes-pipeline | New insert OR positions changed on upsert. Also updates `stance_materialization_status.has_votes` | `{ voteId, billId, chamber, date, congress, isUpdate: boolean }` |
| `analysis.completed` | bill-analysis-pipeline | Final pass completes. Also updates `stance_materialization_status` | `{ billId, analysisId, topics[], passesExecuted, modelUsed }` |
| `user.profile.updated` | api-server | User submits new Q&A responses differing from stored (no-op if identical resubmission) | `{ userId, topicsChanged[] }` |
| `scoring.user.requested` | scoring scheduler / API server | Scheduled enqueue or user ad-hoc request | `{ userId, requestId, source }` |
| `scoring.user.completed` | scoring pipeline (§11.11) | Ad-hoc run completes | `{ userId, requestId, memberScoreCount, status }` |

### Event Ordering & Idempotency

- **Pub/Sub does NOT guarantee ordering** — all pipelines must be idempotent
- `vote.recorded` and `analysis.completed` update DB status flags. Stance materialization readiness determined by DB scanner (§3.2), not event consumption. Eliminates race condition where `vote.recorded` arrives before analysis exists.
- Duplicate events safe — all pipelines use upsert semantics. Next scheduled run self-corrects.
- `scoring.user.requested` idempotent — re-scoring same user produces same output if data unchanged.

### Dead-Letter Policy

- Max retries: 3 for processing failures
- Failed messages moved to dead-letter topic
- Triggers GCP Monitoring alert per topic
- Recovery: manual replay after root cause fixed
- No automatic retry from dead-letter
- No requeue-on-missing-dependency — stance materialization handles convergence via DB polling (§3.2)

---

## 5. Workflow Execution Rules

### Snapshot Semantics

- Each workflow run creates fresh snapshot (point-in-time copy of relevant AlloyDB data to GCS)
- NOT shared across concurrent runs
- Downstream steps read from run's snapshot, not live AlloyDB
- Snapshot path passed via environment variable `SNAPSHOT_PATH` to each Cloud Run Job
- Exception: pipeline run status writes go directly to AlloyDB

### Step Completion Criteria

- Step **complete** when Cloud Run Job exits code 0
- Partial failures (some items failed, some succeeded) = step **succeeds** if exit code 0
- Failed items logged in `ProcessingResult`; step not retried for individual item failures
- Step **failed** only if Job exits non-zero code (systemic failure)

### Error Escalation

- If step N fails, all steps depending on N **skipped** (not attempted)
- Workflow marked "completed with errors" (not "failed" — data from completed steps valid)
- No workflow-level retry — individual steps re-triggerable manually via orchestrator

### Parallel Step Execution

- Steps whose dependencies all met CAN run in parallel
- Orchestrator launches all ready steps concurrently
- Example: after snapshot step completes, all 4 ingestion pipelines (bills, votes, members, amendments) launch simultaneously
- Parallelism limited by Cloud Run Job concurrency quota

### Event Payload Propagation

- Orchestrator does NOT pass event payloads between steps
- Each step reads input from AlloyDB or snapshot independently
- Pub/Sub events (`bill.text.available`, `vote.recorded`, etc.) are for **event-driven path**, not orchestrated batch path
- Two paths coexist: orchestrator handles scheduled batch; Pub/Sub handles real-time triggered scoring

### Workflow Versioning

- In-flight runs continue on workflow version they started with
- New runs pick up latest version from GCS
- No hot-reload during run