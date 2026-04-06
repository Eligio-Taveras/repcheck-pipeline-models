<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/acceptance-criteria/11-SCORING-ENGINE.md -->

# Component 11 — Scoring Engine

## System Context

**Alignment scoring engine** — computes legislator-user fit scoped to representative relationships (user scored only against their own representatives). Six coordinating processes:

```
Process A: Pairing Validator (§11.6) — scheduled + user location change + member.updated
    ↓ persists user_legislator_pairings

Process B: Ingestion Pipelines (Components 4/6/7/10)
    ↓ writes bills, votes, amendments, findings
    ↓ updates stance_materialization_status (has_votes / has_analysis)

Process C: Stance Materializer (§11.9) — scheduled DB scanner
    ↓ polls stance_materialization_status for bills with both votes + analysis
    ↓ writes member_bill_stances + member_bill_stance_topics (per-topic with reasoning/embedding)

Process D: User-Bill Alignment (§11.10) — scheduled
    ↓ processes changed bills + changed users
    ↓ writes user_bill_alignments, user_amendment_alignments

Process E: User-Member Scoring (§11.7, §11.8) — scheduled + ad-hoc
    ↓ Phase 1: numeric scores (parallel) → persisted immediately
    ↓ Phase 2: LLM explanations (parallel) → persisted after scores

Process F: Score Refresh Notifier (§11.11)
    ↓ publishes scoring.user.completed for ad-hoc requests
```

**Three SBT projects**: `score-cache` (publishable library for score reads/writes), `scoring-pipeline` (Cloud Run Job for user-member alignment scoring), `stance-materializer` (Cloud Run Job for bill stance materialization and user-bill alignment).

**Dependencies**: Component 1 (shared-models), Component 2 (pipeline-models), Component 9 (prompt-engine-users), Component 10 (llm-adapter, SemanticEmbeddingService).

### Scoring Flow

**Step 1 — User stance aggregation (§11.1)**

User stances from Q&A responses using pre-tagged question bank:
- **Multiple-choice** → algorithmic (deterministic): Per-topic `stanceScore = Σ(agreeDir × multiplier × weight) / Σ(weight)`, bounded [-1.0, +1.0]. `agreeStance_direction = +1.0` if "Progressive", `-1.0` if "Conservative".
- **Custom fill-in** → LLM batch call via Component 9 `preference-interpretation` profile (stance adjustments only, no importance signal).
- **No LLM for pure multiple-choice flows** — common case requires zero LLM calls.
- **Topic importance** — explicit user weights from "Prioritize your topics" screen (stored `user_topic_priorities`), NOT derived from answer frequency.

Output: `UserTopicProfile` — list of `(topic, stanceScore[-1.0 to +1.0], importance[0.0-1.0])`.

**Step 2 — Legislator profile construction (§11.2)**

Query pre-materialized `member_bill_stance_topics` (no LLM, no finding lookups):
1. Per user-cared topic: fetch `member_bill_stance_topics` rows
2. Each row: `stanceDirection`, `reasoning`, `reasoningEmbedding`, `findingId`
3. Contribution = `stanceDirection × voteWeight` (per BEHAVIORAL_SPECS.md §2)
4. Average across topic votes → `stanceScore` per topic

**Step 3 — Alignment score calculation + LLM explanation (§11.3, §11.4)**

- **Algorithmic**: Per-topic alignment = `1.0 - abs(userStance - legislatorStance) / 2.0`. Weighted average by user importance → **authoritative score** in `scores.aggregate_score`.
- **LLM explanation**: Sonnet/Haiku call via Component 9 → `AlignmentScoreOutput`. LLM's `overallScore` for context only, NOT stored. Reasoning in both `scores.reasoning` and `score_history.reasoning`. Evidence from pre-materialized `member_bill_stance_topics` and `user_bill_alignments`.

### Score Storage (Two Tiers)

**Current scores** (overwritten on re-score):

| Table | Key | Content |
|-------|-----|---------|
| `scores` | (user_id, member_id) | `aggregate_score`, `status`, `last_updated`, `llm_model`, `total_bills`, `total_votes`, `non_overlapping_topics`, `reasoning`, `reasoning_embedding` |
| `score_topics` | (user_id, member_id, topic) | `score` per topic |
| `score_congress` | (user_id, member_id, congress) | `overall_score`, `bills_considered`, `votes_analyzed` per congress |
| `score_congress_topics` | (user_id, member_id, congress, topic) | `score` per congress per topic |

**Score history** (append-only):

| Table | Key | Content |
|-------|-----|---------|
| `score_history` | score_id (UUID) | `aggregate_score`, `status`, `trigger_event` ("scheduled" / "ad-hoc"), `reasoning`, `reasoning_embedding` |
| `score_history_congress` | (score_id, congress) | `overall_score`, `bills_considered`, `votes_analyzed` |
| `score_history_congress_topics` | (score_id, congress, topic) | `score` |
| `score_history_highlights` | (score_id, bill_id, topic) | `stance`, `vote`, `alignment` |

### Persistence Mapping

| Table | Written By | When |
|-------|-----------|------|
| `user_legislator_pairings` | `PairingValidator` | At signup, scheduled validation, user location change |
| `stance_materialization_status` | Votes pipeline, Analysis pipeline | On vote position change, analysis completion |
| `member_bill_stances` + `member_bill_stance_topics` | `StanceMaterializer` | Scheduled scanner finds bills with both votes + analysis |
| `user_bill_alignments` + `user_amendment_alignments` | `UserBillAligner` | Scheduled, changed bills and users |
| `scores` + `score_topics` + `score_congress` + `score_congress_topics` | `ScorePersister.upsertScore` | After each scoring run |
| `score_history` + sub-tables | `ScorePersister.appendHistory` | After each scoring run (append) |

### Event Contracts

| Event | Direction | Payload |
|-------|-----------|---------|
| `member.updated` | **Consumes** (Pairing Validator) | `{ memberId }` |
| `user.profile.updated` | **Consumes** (Pairing Validator + User-Bill Alignment) | `{ userId, topicsChanged[] }` |
| `scoring.user.requested` | **Consumes** (Scoring Pipeline) | `{ userId, requestId, source }` |
| `scoring.user.completed` | **Emits** (Score Refresh Notifier) | `{ userId, requestId, memberScoreCount, status }` |

> Scoring engine does NOT consume `analysis.completed` or `vote.recorded`. Those pipelines update `stance_materialization_status` in DB. Stance materializer polls DB for readiness.

---

## Implementation Areas

| Area | Project | Description |
|------|---------|-------------|
| 11.1 User Stance Aggregation | `scoring-pipeline` | Extracts per-topic stance and importance from Q&A responses. Algorithmic for multiple-choice. LLM batch call for custom fill-in via Component 9. |
| 11.2 Legislator Profile Construction | `scoring-pipeline` | Queries pre-materialized `member_bill_stance_topics` → per-topic stance profile per legislator. |
| 11.3 Alignment Score Calculator | `scoring-pipeline` | Pure algorithmic per-topic alignment: linear distance on [-1.0, +1.0] stance scale, weighted by user importance. |
| 11.4 Score Explainer | `scoring-pipeline` | LLM explanation layer via Component 9 + Component 10 llm-adapter. Evidence from pre-materialized tables. Returns `AlignmentScoreOutput`. Non-fatal on failure. |
| 11.5 Score Persistence | `score-cache` | AlloyDB score repository. Upserts current scores, appends history. |
| 11.6 Pairing Validator | `scoring-pipeline` | Persists `user_legislator_pairings` at signup. Scheduled validation. Handles user location change and `member.updated` events. |
| 11.7 Scoring Orchestrator | `scoring-pipeline` | Per-user orchestration: reads pairings → aggregates stances → scores all legislators (Phase 1: numeric, Phase 2: LLM explanation). |
| 11.8 Scoring Pipeline Entry Point | `scoring-pipeline` | Cloud Run Job entry point. Scheduled + ad-hoc modes. Subscribes to `scoring.user.requested`. |
| 11.9 Stance Materializer | `stance-materializer` | Scheduled DB scanner. Polls `stance_materialization_status` for ready bills. Materializes per-topic stances with reasoning/embedding. |
| 11.10 User-Bill Alignment | `stance-materializer` | Scheduled job. Pre-computes per-bill, per-topic alignment between users and bills. Writes `user_bill_alignments`. |
| 11.11 Score Refresh Notifier | `scoring-pipeline` | Publishes `scoring.user.completed` event on ad-hoc completion. |

---

## Package Structure

```
repcheck-scoring-engine/
├── score-cache/
│   └── repcheck.scoring.cache
│       ├── repository
│       │   ├── ScorePersister
│       │   └── ScoreReader
│       ├── config
│       │   └── ScoreCacheConfig
│       └── errors
│           └── ScorePersistenceFailed
│
├── scoring-pipeline/
│   └── repcheck.scoring.pipeline
│       ├── aggregation
│       │   ├── UserStanceAggregator
│       │   ├── UserTopicProfile
│       │   └── TopicStanceEntry
│       ├── profile
│       │   ├── LegislatorProfileBuilder
│       │   ├── LegislatorTopicProfile
│       │   ├── LegislatorTopicEntry
│       │   └── VoteWeightConfig
│       ├── scoring
│       │   ├── AlignmentScoreCalculator
│       │   ├── PerTopicAlignment
│       │   ├── AlignmentCalculationResult
│       │   ├── ScoreExplainer
│       │   ├── AlignmentEvidenceFetcher
│       │   └── ScoringContext
│       ├── pairing
│       │   └── PairingValidator
│       ├── orchestration
│       │   ├── ScoringOrchestrator
│       │   └── ScoringResult
│       ├── notification
│       │   └── ScoreRefreshNotifier
│       ├── config
│       │   └── ScoringPipelineConfig
│       ├── app
│       │   └── ScoringPipelineApp
│       └── errors
│           ├── ScoringFailed
│           ├── StanceExtractionFailed
│           ├── ProfileBuildFailed
│           └── ExplanationFailed
│
└── stance-materializer/
    └── repcheck.scoring.materializer
        ├── scanner
        │   └── StanceMaterializationScanner
        ├── materializer
        │   └── StanceMaterializer
        ├── alignment
        │   └── UserBillAligner
        ├── config
        │   └── StanceMaterializerConfig
        ├── app
        │   └── StanceMaterializerApp
        └── errors
            ├── MaterializationFailed
            └── AlignmentFailed
```

---

## Dependencies

```
score-cache (publishable library)
├── repcheck-shared-models (Component 1 — score DOs §1.5)
├── repcheck-pipeline-models (Component 2 — Tables constants §2.10)
└── Doobie

scoring-pipeline (Cloud Run Job)
├── score-cache
├── repcheck-shared-models (Component 1 — DOs §1.5, output schemas §1.6)
├── repcheck-pipeline-models (Component 2 — events §2.1, pipeline tracking)
├── repcheck-prompt-engine-users (Component 9 — UserPromptAssembler, scoring profiles)
├── repcheck-llm-analysis (llm-adapter) (Component 10 — LlmProvider[F], LlmRetryWrapper)
├── repcheck-llm-analysis (in-process-ml) (Component 10 — SemanticEmbeddingService)
├── Doobie
├── Google Cloud Pub/Sub SDK
└── http4s Ember

stance-materializer (Cloud Run Job)
├── repcheck-shared-models (Component 1 — DOs §1.5, §1.9)
├── repcheck-pipeline-models (Component 2 — Tables constants)
├── repcheck-llm-analysis (llm-adapter) (Component 10)
├── repcheck-llm-analysis (in-process-ml) (Component 10)
├── repcheck-prompt-engine-users (Component 9)
├── Doobie
└── http4s Ember
```

---

## Cross-Component Updates

| Component | Update | Reason |
|-----------|--------|--------|
| **Component 1 §1.5** | Add DOs: `UserLegislatorPairingDO`, `MemberBillStanceTopicDO`, `UserBillAlignmentDO`, `UserAmendmentAlignmentDO`, `StanceMaterializationStatusDO` | New tables for batch scoring |
| **Component 1 §1.5** | Update `ScoreDO` with `status`, `nonOverlappingTopics`, `reasoning`, `reasoningEmbedding` | Score status and explanation |
| **Component 1 §1.5** | Update `ScoreHistoryDO` with `status`, `reasoningEmbedding` | History audit trail |
| **Component 2 §2.1** | Add `ScoringUserRequestedEvent`, `ScoringUserCompletedEvent`; update consumers for `VoteRecordedEvent`, `AnalysisCompletedEvent`, `UserProfileUpdatedEvent`, `MemberUpdatedEvent` | Batch scoring replaces event-driven |
| **Component 2 §2.10** | Add `Tables` constants for `user_legislator_pairings`, `member_bill_stance_topics`, `user_bill_alignments`, `user_amendment_alignments`, `stance_materialization_status` | New tables |
| **BEHAVIORAL_SPECS.md §3** | Rewrite "Incremental Scoring" → "Batch Scoring Architecture" | Architecture change |
| **Votes pipeline §6.5** | Add `stance_materialization_status` upsert after vote position persistence | Votes pipeline updates status |
| **Analysis pipeline §10.10** | Add `stance_materialization_status` upsert after analysis completion | Analysis pipeline updates status |

---

## Testing Strategy

| Test Type | Scope | Infrastructure |
|-----------|-------|---------------|
| Unit | `UserStanceAggregator` MC extraction formula, importance normalization | MockitoScala |
| Unit | `AlignmentScoreCalculator` formula, edge cases | No infrastructure |
| Unit | `LegislatorProfileBuilder` stance profile from pre-materialized data | MockitoScala |
| Unit | `PairingValidator` creation and validation logic | MockitoScala |
| Unit | `ScoringOrchestrator` step coordination, phased scoring | MockitoScala |
| Unit | `StanceMaterializationScanner` query logic | MockitoScala |
| WireMock | LLM API calls from `ScoreExplainer` (request structure, profile selection) | WireMock |
| WireMock | LLM API calls from `StanceMaterializer` (stance reasoning generation) | WireMock |
| Integration | `ScorePersister` upsert + history append round-trip | AlloyDB Omni (Docker) |
| Integration | `LegislatorProfileBuilder` query against seeded `member_bill_stance_topics` | AlloyDB Omni |
| Integration | `UserStanceAggregator` with Q&A data | AlloyDB Omni |
| Integration | `StanceMaterializer` full materialization for seeded bill | AlloyDB Omni |
| Integration | `UserBillAligner` alignment computation | AlloyDB Omni |
| Integration | `PairingValidator` creation against seeded terms | AlloyDB Omni |
| E2E | Full scoring run from `scoring.user.requested` to scores written | AlloyDB Omni + WireMock |
| E2E | Stance materialization scanner → materialize → scoring | AlloyDB Omni + WireMock |
| E2E | Ad-hoc scoring request → completion event published | AlloyDB Omni + WireMock + mock Pub/Sub |