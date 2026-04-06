<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/acceptance-criteria/09-PROMPT-ENGINE-USERS.md -->

# Acceptance Criteria: Component 9 — Prompt Engine Users

Standalone library composing LLM prompts for user-legislator alignment scoring. Loads versioned instruction blocks from GCS, assembles them into prompt chains using profiles, and injects dynamic user/legislator context at runtime. **Depends on**: `repcheck-shared-models` (Component 1 — base traits §1.7, output schemas §1.6).

## System Context

### What This Component Does

Composable instruction framework for scoring prompts — assembles prompt strings sent to LLM (Component 11 calls the LLM).

```
GCS (instruction blocks + profiles)
    ↓
Prompt Engine Users
  ├─ Load instruction blocks from GCS by stage
  ├─ Resolve profile (which blocks, order, weights)
  ├─ Inject dynamic context (user preferences, legislator record, bill analyses)
  ├─ Apply weight translation (emphasis markers)
  └─ Assemble final prompt string
    ↓
Assembled prompt → scoring-pipeline (Component 11) → LLM
```

### Relationship to Component 8

Components 8 & 9 share identical assembly mechanism, differ in config path and context types:

| Aspect | Component 8 (Bills) | Component 9 (Users) |
|--------|---------------------|---------------------|
| GCS path | `bills/` | `users/` |
| Context types | BillAnalysisContext, amendments | UserScoringContext, preferences, voting records |
| Profiles | pass1-extraction, pass2-deep-analysis, pass3-ambiguity | full-alignment, topic-breakdown, quick-score |
| Consumer | Bill-analysis-pipeline (10) | Scoring-pipeline (11) |
| Output schemas | BillSummaryOutput, PorkDetectionOutput | AlignmentScoreOutput (§1.6) |

### No Hardcoded Prompts

All prompt fragments live in GCS. Prompt engines are loaders + assemblers only — zero prompt text in source code.

### Scoring Profiles

| Profile | Model | Purpose | When Used |
|---------|-------|---------|-----------|
| `full-alignment` | Sonnet | Comprehensive topic-by-topic analysis with reasoning, highlights, per-congress breakdown | Default for all scoring runs |
| `topic-breakdown` | Haiku | Lightweight topic-level scoring without reasoning — faster, cheaper for batch re-scoring | Batch re-scoring of many pairs |
| `quick-score` | Haiku | Single aggregate score with minimal explanation — rapid feedback | Score delta below threshold |

Profile selection is Component 11's responsibility, driven by score-delta thresholds (large change → richer profile, small change → quick profile).

### Base Traits (Component 1 §1.7)

| Type | Role |
|------|------|
| `PromptStage` | Enum: System, Persona, Lens, Context, Guardrails, Output, Custom |
| `InstructionBlock` | Atomic prompt fragment: name, stage, weight, version, content |
| `StageConfig` | Stage + block names + weight for profile entry |
| `PromptProfile` | Named chain of StageConfig entries |
| `ChainAssembler` | Orders stages, applies weights, merges blocks, injects context |
| `WeightTranslator` | Converts weight (0.0–1.0) to emphasis markers |

### Output Schema (Component 1 §1.6)

`AlignmentScoreOutput`: topicScores (List[TopicAlignmentScore]), overallScore (Double), highlights (List[AlignmentHighlight]), reasoning (String). Referenced by name in Output stage blocks; Component 11 parses actual schema type.

### GCS Layout

```
gs://repcheck-prompt-configs/users/
├── blocks/
│   ├── system/
│   │   └── base-scoring-analyst-v1.0.0.yaml
│   ├── persona/
│   │   ├── plain-language-explainer-v1.0.0.yaml
│   │   └── data-driven-analyst-v1.0.0.yaml
│   ├── lens/
│   │   ├── topic-alignment-lens-v1.0.0.yaml
│   │   ├── voting-consistency-lens-v1.0.0.yaml
│   │   └── bipartisan-lens-v1.0.0.yaml
│   ├── context/
│   │   ├── user-preferences-v1.0.0.yaml ({{user_preferences}})
│   │   ├── voting-record-v1.0.0.yaml ({{voting_record}})
│   │   ├── bill-analyses-v1.0.0.yaml ({{bill_analyses}})
│   │   └── legislator-profile-v1.0.0.yaml ({{legislator_profile}})
│   ├── guardrails/
│   │   ├── fairness-constraint-v1.0.0.yaml
│   │   └── no-party-bias-v1.0.0.yaml
│   └── output/
│       ├── full-alignment-schema-v1.0.0.yaml
│       ├── topic-breakdown-schema-v1.0.0.yaml
│       └── quick-score-schema-v1.0.0.yaml
└── profiles/
    ├── full-alignment-v1.0.0.yaml
    ├── topic-breakdown-v1.0.0.yaml
    └── quick-score-v1.0.0.yaml
```

### Deployment Pipeline

Blocks/profiles version-controlled in repo under `prompt-configs/users/` → GitHub Actions deploys to GCS on merge → prompt engine reads from GCS at runtime (local file fallback for development).

## Implementation Areas

| Area | Description |
|------|-------------|
| 9.1 GCS Block Loader | Load instruction blocks and profiles from GCS with version filtering and local fallback |
| 9.2 User Scoring Profiles | Scoring profiles and user-specific block catalog |
| 9.3 User Prompt Assembler | User-specific ChainAssembler with context injection for preferences, voting records, bill analyses |

## Cross-Cutting Concerns

### Package Structure

```
repcheck-prompt-engine-users/repcheck.prompt.users
├── loader
│   ├── BlockLoader
│   └── ProfileLoader
├── profiles
│   └── UserScoringProfiles
├── assembler
│   ├── UserPromptAssembler
│   └── UserContextInjector
├── config
│   └── UserPromptEngineConfig
└── errors
    ├── BlockLoadFailed
    ├── ProfileLoadFailed
    └── ContextInjectionFailed
```

### Dependencies

```
repcheck-prompt-engine-users
├── repcheck-shared-models (Component 1)
│   ├── PromptStage, InstructionBlock, StageConfig, PromptProfile (§1.7)
│   ├── ChainAssembler, WeightTranslator (§1.7)
│   └── AlignmentScoreOutput, TopicAlignmentScore (§1.6, referenced by name)
└── GCS Java SDK (Sync[F] wrapped)
```

No dependency on pipeline-models, ingestion-common, Pub/Sub, Doobie, or pipeline execution infrastructure. Pure library.

### Testing Strategy

| Test Type | Scope | Tools |
|-----------|-------|-------|
| Unit tests | ChainAssembler integration, weight translation, context injection, profile validation | MockitoScala (mock GCS) |
| GCS integration | Block loading, version filtering, profile resolution | Testcontainers (fake GCS) or local file fallback |
| Prompt assembly | Full profile assembly with real blocks → verify structure and ordering | Local file fallback |
| Contract tests | Assembled prompts contain expected output schema references | Unit tests |