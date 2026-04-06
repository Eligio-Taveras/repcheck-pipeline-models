<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/acceptance-criteria/10-LLM-ANALYSIS.md -->

# Component 10 — LLM Analysis

Repository `repcheck-llm-analysis`: three SBT projects — `llm-adapter` (vendor-neutral LLM client), `bill-decomposition-pipeline` (decomposes bills into concept groups), `bill-analysis-pipeline` (multi-pass analysis).
**Depends on**: Component 1, 2, 8.

## System Context

### What This Component Does

Execution engine for bill analysis: decomposition pipeline receives `bill.text.ingested` events, decomposes bills into sections and concept groups, emits `bill.decomposition.completed`. Analysis pipeline receives those events, runs tiered multi-pass LLM analysis on simplified concept groups, persists results, emits `analysis.completed`.

```
Pub/Sub: bill.text.ingested
    |
    v
bill-decomposition-pipeline
    |
    |-- 1. Fetch bill text + amendments from AlloyDB
    |-- 2. Decomposition (ALL bills):
    |       a. Ollama sidecar: parse text into sections (any format)
    |       b. DJL/ONNX: generate embeddings per section (in-process, free)
    |       c. Persist sections + embeddings to AlloyDB
    |       d. DJL/ONNX: embed sections for clustering (384-dim, ephemeral)
    |       e. Smile: cluster into concept groups
    |       f. Persist concept groups to AlloyDB
    |       g. LLM (Haiku): simplify each concept group
    |       h. DJL/ONNX: generate embeddings per concept group (in-process, free)
    |       i. Persist simplified text + embeddings to AlloyDB
    |
    v
Pub/Sub: bill.decomposition.completed
    |
    v
bill-analysis-pipeline
    |
    |-- 3. Fetch simplified concept groups from AlloyDB
    |-- 4. Pass 1 (Haiku — all bills): full analysis per concept group
    |       (summary, topics, stance, pork, impact, fiscal — classified by finding type)
    |       → Persist Pass 1 results → DJL/ONNX embeddings for findings
    |-- 5. Pass 2 (Sonnet — filtered): same outputs, higher quality
    |       → Persist Pass 2 results → DJL/ONNX embeddings for findings
    |-- 6. Pass 3 (Opus — rare): same outputs, highest quality + cross-concept resolution
    |       → Persist Pass 3 results → DJL/ONNX embeddings for findings
    |
    v
DB: stance_materialization_status (has_analysis = true, all_passes_completed = true)
    |
    v
Pub/Sub: analysis.completed (informational)
```

### Three SBT Projects

| Project | Type | Purpose |
|---------|------|---------|
| `llm-adapter` | Library (publishable) | LLM client: `LlmProvider[F]` trait + `ClaudeProvider` (Anthropic Java SDK) + `OllamaProvider` (HTTP to sidecar). Structured output only (`completeStructured[A]`). Handles enforcement, retry, rate limiting. |
| `bill-decomposition-pipeline` | Cloud Run Job | Subscribes to `bill.text.ingested`, decomposes bills into sections and concept groups using Ollama + DJL + Smile + Haiku, persists artifacts, emits `bill.decomposition.completed`. Has Ollama sidecar. |
| `bill-analysis-pipeline` | Cloud Run Job | Subscribes to `bill.decomposition.completed`, runs multi-pass LLM analysis on simplified concept groups, persists results, emits `analysis.completed`. No sidecar. |

### Decomposition Architecture

All bills undergo decomposition. Analysis always operates on simplified concept groups, never raw text. This reduces LLM call count and cost.

| Step | Technology | Cost | Output |
|------|-----------|------|--------|
| 1. Text parsing / section identification | Ollama sidecar (Llama 3.2 1B) | Free (local) | Section boundaries |
| 2. Section search embeddings | DJL + ONNX (in-process) | Free | Embedding vectors |
| 3. Persist sections + embeddings | AlloyDB | N/A | `bill_text_sections` rows |
| 4. Section embedding for clustering | DJL + ONNX (384-dim) | Free | Ephemeral 384-dim vectors |
| 5. Semantic clustering | Smile (k-means/DBSCAN) | Free | Concept group assignments |
| 6. Persist concept groups | AlloyDB | N/A | `bill_concept_groups`, `bill_concept_group_sections` |
| 7. Concept simplification | Haiku API | ~$0.001/group | `bill_concept_groups.simplified_text` |
| 8. Concept group search embeddings | DJL + ONNX (in-process) | Free | Embedding vectors |
| 9. Persist simplified text + embeddings | AlloyDB | N/A | `bill_concept_groups.embedding` populated |

**Key rules:**
- All bills decomposed — uniform input for analysis pipeline, searchable sections and concept groups for all
- Decomposition artifacts tied to text version (`version_id`), reusable across re-analyses
- 384-dim DJL embeddings ephemeral (clustering only)
- All search embeddings generated locally via DJL/ONNX — zero embedding API costs
- If decomposition already done for text version, reused
- Large bills produce many concept groups; simplification per-concept-group scales with bill complexity

### Multi-Pass Analysis Chain

Analysis operates on simplified concept groups. Finding types guide output classification.

| Pass | Model | Applies To | Input | Output Schemas (§1.6) |
|------|-------|-----------|-------|----------------------|
| Pass 1 | Haiku | All bills | Simplified concept groups + finding type descriptions | `BillSummaryOutput`, `TopicClassificationOutput`, `StanceClassificationOutput`, `PorkDetectionOutput`, `ImpactAnalysisOutput`, `FiscalEstimateOutput`. Produces routing scores: high-profile, media coverage, appropriations estimate. |
| Pass 2 | Sonnet | Filtered | Concept groups + Pass 1 results + finding type descriptions | Same schemas, higher quality. Produces cross-concept contradiction score for Pass 3 routing. |
| Pass 3 | Opus | Rare/flagged | Concept groups + Pass 1 + Pass 2 results + finding type descriptions | Same schemas, highest quality + cross-concept resolution |

Every bill needs complete Pass 1 analysis — summary, topics, stance, pork, impact, fiscal. Pass 2/3 produce *better versions* of same outputs. Finding types in prompts define categories (e.g., pork_barrel, fiscal_impact) — LLM classifies results along these established categories.

**Pass routing rules** (all thresholds configurable):

- **Pass 2** (any match):
  - Pass 1 high-profile score exceeds threshold
  - Pass 1 media coverage level exceeds threshold
  - Pass 1 appropriations estimate exceeds configurable dollar threshold
  - Pass 1 stance confidence below threshold
- **Pass 3** (any match):
  - Pass 2 confidence scores below threshold
  - Pass 2 expected vote contention exceeds threshold (LLM-assessed, no vote data dependency)
  - Pass 2 cross-concept contradiction score exceeds threshold

**Routing scores:**
- **Pass 1 produces:** high-profile score, media coverage level, appropriations estimate, stance confidence → Pass 2 routing
- **Pass 2 produces:** cross-concept contradiction score, expected vote contention, overall confidence → Pass 3 routing

**Idempotency**: Re-analysis creates new run (append-only), preserves history.

### Persistence Mapping

| Layer | Tables | Tied To | When Written |
|-------|--------|---------|--------------|
| Bill text | `bill_text_sections`, `bill_concept_groups`, `bill_concept_group_sections` | Text version | Decomposition pipeline |
| Analysis | `bill_analyses`, `bill_concept_summaries`, `bill_analysis_topics`, `bill_findings`, `bill_fiscal_estimates` | Analysis run | Analysis pipeline (each pass) |

### Ollama Sidecar

Second container in decomposition pipeline Cloud Run Job:
- **Model**: Llama 3.2 1B
- **Communication**: HTTP on `localhost:11434`
- **Lifecycle**: Starts with Cloud Run Job, shares network namespace
- **Cost**: Runs on allocated CPU/memory, no per-token charges
- **Note**: Decomposition only. Analysis pipeline does not use sidecar.

### Event Contracts

| Event | Direction | Pipeline | Payload |
|-------|-----------|----------|---------|
| `bill.text.ingested` | Consumes | Decomposition | `{ billId, versionId, congress, versionCode, previousVersionCode, committeeCode }` |
| `bill.decomposition.completed` | Produces / Consumes | Decomposition → Analysis | `{ billId, versionId, conceptGroupCount, sectionCount }` |
| `analysis.completed` | Produces | Analysis | `{ billId, analysisId, topics[], modelUsed }` |

---

## Implementation Areas

| Area | Project | Description |
|------|---------|-------------|
| 10.1 | `llm-adapter` | `LlmProvider[F]` trait + `ClaudeProvider` (Anthropic Java SDK) + `OllamaProvider` (HTTP sidecar). Structured output only — `completeStructured[A]`. |
| 10.2 | `llm-adapter` | Provider config, API key management, retry with exponential backoff, rate limiting, fallback chains |
| 10.3 | `llm-adapter` | `OllamaProviderConfig` and `SectionParseResult` — Ollama-specific configuration and output types |
| 10.4 | `bill-decomposition-pipeline` | Orchestrates full decomposition: Ollama parsing → DJL embedding → Smile clustering → persistence → Haiku simplification → embedding |
| 10.5 | `bill-decomposition-pipeline` | DJL/ONNX embedding (search + clustering) and Smile clustering — all local, no API dependencies |
| 10.6 | `bill-analysis-pipeline` | Runs Pass 1/2/3 chain using Component 8 prompts and 10.1 providers, with pass routing. Includes finding type descriptions. |
| 10.7 | `bill-analysis-pipeline` | Maps LLM output schemas to DOs, persists to AlloyDB analysis layer |
| 10.8 | Shared (both pipelines) | Local DJL/ONNX utility for search embeddings — sections, concept groups, findings. All local, zero API cost. |
| 10.9 | `bill-decomposition-pipeline` | Cloud Run entry point, Pub/Sub subscriber for `bill.text.ingested`, event emission for `bill.decomposition.completed` |
| 10.10 | `bill-analysis-pipeline` | Cloud Run entry point, Pub/Sub subscriber for `bill.decomposition.completed`, event emission for `analysis.completed` |

---

## Package Structure

```
repcheck-llm-analysis/
├── llm-adapter/
│   └── repcheck.llm.adapter
│       ├── core
│       │   ├── LlmProvider
│       │   ├── LlmRequest
│       │   ├── LlmResponse
│       │   └── StructuredOutputEnforcer
│       ├── providers
│       │   ├── ClaudeProvider
│       │   ├── OllamaProvider
│       │   ├── OllamaProviderConfig
│       │   └── SectionParseResult
│       ├── config
│       │   ├── LlmAdapterConfig
│       │   └── ClaudeProviderConfig
│       ├── retry
│       │   ├── LlmRetryWrapper
│       │   └── LlmErrorClassifier
│       └── errors
│           ├── LlmCallFailed
│           ├── StructuredOutputParseFailed
│           └── ProviderUnavailable
│
├── bill-decomposition-pipeline/
│   └── repcheck.decomposition.pipeline
│       ├── orchestration
│       │   ├── DecompositionOrchestrator
│       │   └── SimplifiedConceptOutput
│       ├── ml
│       │   ├── SectionEmbedder
│       │   └── ConceptClusterer
│       ├── persistence
│       │   └── DecompositionPersister
│       ├── config
│       │   └── DecompositionPipelineConfig
│       ├── app
│       │   └── BillDecompositionPipelineApp
│       └── errors
│           ├── DecompositionFailed
│           └── ClusteringFailed
│
├── bill-analysis-pipeline/
│   └── repcheck.analysis.pipeline
│       ├── analysis
│       │   ├── AnalysisOrchestrator
│       │   ├── PassExecutor
│       │   ├── PassRouter
│       │   ├── ConceptGroupPassOutput
│       │   ├── RoutingAssessment
│       │   ├── AnalysisConceptGroup
│       │   ├── AnalysisFindingType
│       │   ├── Pass1RoutingScores
│       │   ├── Pass2RoutingScores
│       │   └── PassRoutingConfig
│       ├── persistence
│       │   ├── AnalysisResultPersister
│       │   └── AnalysisRunCreator
│       ├── config
│       │   └── AnalysisPipelineConfig
│       ├── app
│       │   └── BillAnalysisPipelineApp
│       └── errors
│           ├── AnalysisPassFailed
│           └── PersistenceFailed
│
└── shared
    └── repcheck.analysis.embedding
        ├── SemanticEmbeddingService
        └── EmbeddingGenerationFailed
```

---

## Dependencies

```
repcheck-llm-analysis/
├── llm-adapter (publishable library)
│   ├── repcheck-shared-models (Component 1 — LLM output schemas §1.6, base traits §1.7)
│   ├── Anthropic Java SDK
│   └── http4s Ember
│
├── bill-decomposition-pipeline (Cloud Run Job)
│   ├── llm-adapter (internal SBT dependency)
│   ├── repcheck-shared-models (Component 1 — DOs §1.9, §1.4)
│   ├── repcheck-pipeline-models (Component 2 — events §2.1, tables, pipeline tracking)
│   ├── Doobie (AlloyDB)
│   ├── Google Cloud Pub/Sub SDK
│   ├── DJL + ONNX Runtime (in-process embedding + clustering)
│   ├── Smile ML (clustering)
│   └── http4s Ember (Ollama sidecar HTTP)
│
└── bill-analysis-pipeline (Cloud Run Job)
    ├── llm-adapter (internal SBT dependency)
    ├── repcheck-shared-models (Component 1 — DOs §1.9, §1.4)
    ├── repcheck-pipeline-models (Component 2 — events §2.1, tables, pipeline tracking)
    ├── repcheck-prompt-engine-bills (Component 8 — prompt assembly)
    ├── Doobie (AlloyDB)
    ├── Google Cloud Pub/Sub SDK
    ├── DJL + ONNX Runtime (in-process embedding for findings)
    └── http4s Ember (LLM API calls)
```

---

## Cross-Component Updates Required

| Component | Update | Reason |
|-----------|--------|--------|
| **Component 1 §1.9** | Add `code` column to `FindingTypeDO` | 10.7 looks up finding types by `code` (e.g., "pork_barrel") |
| **Component 1 §1.9** | Add ~15 fields to `BillAnalysisDO` | 10.7 writes `status`, `versionId`, `createdAt`, `completedAt`, `passesExecuted`, `highestModelUsed`, routing score columns |
| **Component 1 §1.9** | Add `passNumber` to `BillFindingDO`, `BillAnalysisTopicDO`, `BillConceptSummaryDO`, `BillFiscalEstimateDO` | Multi-pass appends findings per pass |
| **Component 1 §1.9** | Add `topics`, `readingLevel`, `keyPoints` to `BillConceptSummaryDO` | 10.7 writes richer per-concept summaries |
| **Component 2 §2.1** | Add `versionId: UUID` to `BillTextIngestedEvent` payload | Decomposition pipeline needs text version PK |
| **Component 2 §2.1** | Update `analysis.completed` event: `passCompleted: Int` → `passesExecuted: List[Int]`, `llmModel` → `modelUsed` | Multi-pass analysis |
| **Component 2 §2.1** | Register `bill.decomposition.completed` as new event type | Connects decomposition → analysis pipelines |
| **Component 4 §4.8** | Include `versionId` in `BillTextIngestedEvent` emission | Component 4 creates `bill_text_versions` row, must include PK in event |

---

## Testing Strategy

| Test Type | Scope | Infrastructure |
|-----------|-------|---------------|
| Unit | LLM adapter trait, Claude provider, retry, structured output parsing | MockitoScala |
| Unit | Decomposition orchestrator, pass router, result persister, embedding service | MockitoScala |
| WireMock | Claude API HTTP interactions | WireMock |
| WireMock | Ollama sidecar API contract | WireMock |
| Integration | Full decomposition with real DJL + Smile + PostgreSQL | AlloyDB Omni (Docker), DJL models |
| Integration | Analysis result persistence round-trip | AlloyDB Omni (Docker) |
| Integration | DJL embedding generation (search + clustering) | DJL models |
| E2E | Full decomposition from `bill.text.ingested` to `bill.decomposition.completed` | AlloyDB Omni (Docker) + WireMock (mock Haiku) |
| E2E | Full analysis from `bill.decomposition.completed` to `analysis.completed` | AlloyDB Omni (Docker) + WireMock (mock LLM APIs) |