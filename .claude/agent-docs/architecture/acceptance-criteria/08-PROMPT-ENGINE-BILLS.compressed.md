<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/acceptance-criteria/08-PROMPT-ENGINE-BILLS.md -->

# Acceptance Criteria: Component 8 — Prompt Engine Bills

Standalone library (`repcheck-prompt-engine-bills`) that composes LLM prompts for bill analysis. Loads versioned instruction blocks from GCS, assembles them into prompt chains using profiles, and injects dynamic bill context at runtime. **Depends on**: `repcheck-shared-models` (Component 1 — base traits from §1.7, output schemas from §1.6).

## System Context

### What This Component Does

The prompt engine assembles the prompt string sent to the LLM. It does NOT call the LLM — that is the LLM adapter's job (Component 10). Sole responsibility: assemble prompt string from GCS-stored instruction blocks.

```
GCS (instruction blocks + profiles)
    |
    v
Prompt Engine Bills (this component)
    |-- Load instruction blocks from GCS by stage (system, persona, lens, etc.)
    |-- Resolve a profile (which blocks to use, in what order, with what weights)
    |-- Inject dynamic context (bill text, amendments, metadata)
    |-- Apply weight translation (emphasis markers)
    |-- Assemble final prompt string
    |
    v
Assembled prompt string → bill-analysis-pipeline (Component 10) → LLM adapter → LLM
```

### No Hardcoded Prompts

**All prompt fragments live in GCS. Prompt engines are loaders + assemblers only.** Zero prompt text in source code — all instructional content stored in GCS and loaded at runtime.

- Prompt wording tuned without redeploying code
- A/B testing requires only GCS file changes
- Prompt versioning uses semver in filenames (e.g., `fiscal-lens-v1.2.0.yaml`)

### Tiered Analysis Profiles

Bill-analysis-pipeline (Component 10) uses three LLM passes with different model tiers:

| Pass | Model | Applies To | Analysis Type |
|------|-------|-----------|---------------|
| Pass 1 | Haiku | All bills | Extraction, classification, summary |
| Pass 2 | Sonnet | Filtered bills | Pork detection, impact analysis, stance, fiscal |
| Pass 3 | Opus | Rare/flagged bills | Ambiguity resolution, cross-bill analysis |

Each pass has its own **prompt profile** — different combination of instruction blocks assembled in different order. Prompt engine provides one profile per pass. Bill-analysis-pipeline selects profile based on pass routing rules.

### Bill Text Decomposition Support

Bill text can be extremely large. Raw text cannot be injected into single LLM context window. Bill-analysis-pipeline (Component 10) owns **decomposition orchestration** — text parsing (Ollama sidecar), embedding (DJL/ONNX), and clustering (Smile) are free, in-process. Only **concept simplification** calls external LLM using prompts from this component.

```
Raw bill text (XML, plain text, PDF-extracted)
    |
    v
Component 10: Text parsing / section identification (Ollama — free, local)
    |
    v
Component 10: Section embedding (DJL + ONNX, 384-dim — free, in-process)
    |
    v
Component 10: Semantic clustering (Smile k-means/DBSCAN — free, in-process)
    |
    v
Component 10: Persist sections + groups to AlloyDB
    |
    v
Component 10 calls LLM with Component 8 prompt: "Simplify this concept group"
    |   (concept-simplification profile — Haiku, ~$0.001/group)
    v
Simplified concept summaries → persisted to bill_concept_groups
    |
    v
Component 10 calls LLM with Component 8 prompt: Pass 1/2/3 analysis
    |   (analysis profiles, with simplified concepts as context)
    v
Structured analysis output → persisted to analysis layer tables
```

Prompt engine provides **decomposition and analysis profiles**:

| Profile | Purpose | Used By |
|---------|---------|---------|
| `concept-simplification` | Simplify group of related bill sections into coherent summary | Component 10 decomposition (Haiku) |
| `section-classification` | Classify bill section by topic/policy area — fallback for insufficient embedding clustering | Component 10 grouping (Haiku) |
| `pass1-extraction` | Full extraction/classification/summary from simplified concepts | Component 10 Pass 1 |
| `pass2-deep-analysis` | Pork, impact, stance, fiscal analysis from simplified concepts | Component 10 Pass 2 |
| `pass3-ambiguity-resolution` | Cross-concept ambiguity resolution | Component 10 Pass 3 |

**Decomposition logic vs prompts:** Component 10 decides *when* to decompose, *how* to parse, *how* to embed, and *how* to cluster. Component 8 provides the *prompts* for LLM-assisted simplification and fallback section-classification. All instructional content in GCS; orchestration in pipeline.

### Base Traits (from Component 1 §1.7)

| Type | Role |
|------|------|
| `PromptStage` | Enum: System, Persona, Lens, Context, Guardrails, Output, Custom |
| `InstructionBlock` | Atomic prompt fragment: name, stage, weight, version, content |
| `StageConfig` | Stage + block names + weight for profile entry |
| `PromptProfile` | Named chain of `StageConfig` entries |
| `ChainAssembler` | Trait that orders stages, applies weights, merges blocks, injects context |
| `WeightTranslator` | Converts weight (0.0–1.0) to emphasis markers |

### Output Schemas (from Component 1 §1.6)

Referenced by name in Output stage blocks; LLM adapter uses actual schema types for response parsing:

| Schema | Used By |
|--------|---------|
| `BillSummaryOutput` | Pass 1 |
| `TopicClassificationOutput` | Pass 1 |
| `StanceClassificationOutput` | Pass 2 |
| `PorkDetectionOutput` | Pass 2 |
| `ImpactAnalysisOutput` | Pass 2 |
| `FiscalEstimateOutput` | Pass 2 |

### GCS Layout

```
gs://repcheck-prompt-configs/
  └── bills/
      ├── blocks/
      │   ├── system/
      │   │   └── base-legislative-analyst-v1.0.0.yaml
      │   ├── persona/
      │   │   ├── general-audience-v1.0.0.yaml
      │   │   └── expert-audience-v1.0.0.yaml
      │   ├── lens/
      │   │   ├── fiscal-lens-v1.0.0.yaml
      │   │   ├── civil-liberties-lens-v1.0.0.yaml
      │   │   ├── healthcare-lens-v1.0.0.yaml
      │   │   └── pork-detector-v1.0.0.yaml
      │   ├── context/
      │   │   ├── bill-text-v1.0.0.yaml
      │   │   ├── amendments-v1.0.0.yaml
      │   │   └── related-bills-v1.0.0.yaml
      │   ├── guardrails/
      │   │   ├── nonpartisan-constraint-v1.0.0.yaml
      │   │   └── accuracy-constraint-v1.0.0.yaml
      │   └── output/
      │       ├── pass1-extraction-schema-v1.0.0.yaml
      │       ├── pass2-deep-analysis-schema-v1.0.0.yaml
      │       └── pass3-resolution-schema-v1.0.0.yaml
      └── profiles/
          ├── concept-simplification-v1.0.0.yaml
          ├── section-classification-v1.0.0.yaml
          ├── pass1-extraction-v1.0.0.yaml
          ├── pass2-deep-analysis-v1.0.0.yaml
          ├── pass3-ambiguity-resolution-v1.0.0.yaml
          ├── summary-only-v1.0.0.yaml
          └── pork-detection-v1.0.0.yaml
```

### Deployment Pipeline

```
repo: prompt-configs/bills/  ── git push ──→  GitHub Actions  ──→  gs://repcheck-prompt-configs/bills/
```

Version-controlled in repo under `prompt-configs/bills/`. GitHub Actions deploys to GCS on merge to main. Prompt engine reads from GCS at runtime. Local file fallback for development.

---

## Implementation Areas

| Area | Status | Description |
|------|--------|-------------|
| 8.1 GCS Block Loader | New | Loads instruction blocks and profiles from GCS with version filtering and local fallback |
| 8.2 Bill Analysis Profiles | New | Defines decomposition and analysis profiles, and the bill-specific block catalog |
| 8.3 Bill Prompt Assembler | New | Bill-specific `ChainAssembler` implementation with context injection for bill concepts, amendments, and metadata |

## Component Routing Table

| Task | Area File |
|------|-----------|
| Loading instruction blocks and profiles from GCS | [8.1 GCS Block Loader](08-prompt-engine-bills/08.1-gcs-block-loader.md) |
| Decomposition and analysis profile definitions, block catalog | [8.2 Bill Analysis Profiles](08-prompt-engine-bills/08.2-bill-analysis-profiles.md) |
| Bill-specific prompt assembly with context injection | [8.3 Bill Prompt Assembler](08-prompt-engine-bills/08.3-bill-prompt-assembler.md) |

---

## Cross-Cutting Concerns

### Package Structure

```
repcheck-prompt-engine-bills/
└── repcheck.prompt.bills
    ├── loader
    │   ├── BlockLoader                    (8.1)
    │   └── ProfileLoader                  (8.1)
    ├── profiles
    │   └── BillAnalysisProfiles           (8.2)
    ├── assembler
    │   ├── BillPromptAssembler            (8.3)
    │   └── BillContextInjector            (8.3)
    ├── config
    │   └── BillPromptEngineConfig         (8.1)
    └── errors
        ├── BlockLoadFailed                (8.1)
        ├── ProfileLoadFailed              (8.1)
        └── ContextInjectionFailed         (8.3)
```

### Dependencies

```
repcheck-prompt-engine-bills
├── repcheck-shared-models               (published artifact — Component 1)
│   ├── PromptStage, InstructionBlock, StageConfig, PromptProfile   (§1.7)
│   ├── ChainAssembler, WeightTranslator                            (§1.7)
│   └── BillSummaryOutput, TopicClassificationOutput, etc.          (§1.6)
└── GCS Java SDK                         (runtime dependency)
    └── Wrapped in Sync[F] per project conventions
```

**No dependency on pipeline-models or ingestion-common.** Pure library — no Pub/Sub, Doobie, or pipeline execution infrastructure. Only RepCheck dependency: `shared-models`.

### Testing Strategy

| Test Type | Scope | Infrastructure |
|-----------|-------|---------------|
| Unit tests | `ChainAssembler` integration, weight translation, context injection, profile validation | MockitoScala (mock GCS client) |
| GCS integration tests | Block loading, version filtering, profile resolution | Testcontainers (fake GCS) or local file fallback |
| Prompt assembly tests | Full profile assembly with real blocks → verify prompt structure and content ordering | Local file fallback |
| Contract tests | Assembled prompts contain expected output schema references | Unit tests |