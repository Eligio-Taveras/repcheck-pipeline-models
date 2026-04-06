<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/system-design/06-llm-cost-strategy.md -->

# LLM Cost Strategy — Tiered Model Selection

**Design principle**: Use the cheapest model that produces acceptable quality for each task. Reserve expensive models for tasks that genuinely require stronger reasoning.

## Why Tiered?

Analyzing ~10,000 bills per congress with a single model is cost-prohibitive at higher tiers.

| Approach | Est. Cost per Congress | Quality |
|----------|----------------------|---------|
| All Opus | ~$10,000 | Highest |
| All Sonnet | ~$2,000 | High |
| All Haiku | ~$300 | Adequate for extraction, weak on reasoning |
| **Tiered (recommended)** | **~$500–800** | **High where it matters, adequate elsewhere** |

## Task-to-Model Mapping

| Task | Model Tier | Reasoning Required | Volume |
|------|-----------|-------------------|--------|
| Structured extraction (sponsors, dates, references) | Haiku | Pattern matching | All bills |
| Topic/policy classification | Haiku | Classification | All bills |
| Plain language summary | Haiku | Straightforward | All bills |
| Pork/rider detection | Sonnet | Multi-step reasoning about legislative intent | Filtered bills |
| Impact analysis (who benefits/harmed) | Sonnet | Second-order reasoning | Filtered bills |
| Stance classification | Sonnet | Nuance in political language | Filtered bills |
| Fiscal estimates | Sonnet | Economic reasoning | Filtered bills |
| Ambiguity resolution | Opus | Complex multi-factor analysis | Flagged bills only |
| Cross-bill interaction analysis | Opus | Cross-document reasoning | Flagged bills only |
| User alignment scoring | Sonnet | Comparing user values against analysis | Per user-legislator pair |

## Pass Routing Configuration

```
bill-analysis {
  pass-1 {
    model = "haiku"
    applies-to = "all"    # every bill gets Pass 1
  }
  pass-2 {
    model = "sonnet"
    filters = [
      "has-active-vote",          # bill has been voted on recently
      "in-tracked-policy-area",   # matches policy areas users care about
      "high-profile"              # media attention or sponsor prominence
    ]
  }
  pass-3 {
    model = "opus"
    filters = [
      "pass-2-confidence-below(0.7)",  # Pass 2 flagged uncertainty
      "contentious-vote-split"         # close vote margins suggest complexity
    ]
  }
}
```

## Bill Text Decomposition and Token Management

Large bills can be thousands of pages — far exceeding context windows. Decomposition uses hybrid in-process + LLM approach minimizing API costs.

| Step | Method | Cost |
|------|--------|------|
| 1. Text parsing / section identification | Ollama sidecar (Llama 3.2 1B) — format-agnostic | Free (local) |
| 2. Section embedding | DJL + ONNX Runtime (all-MiniLM-L6-v2, 384-dim) — in-process | Free |
| 3. Semantic clustering | Smile ML (k-means/DBSCAN) — group related sections | Free |
| 4. Concept simplification | Haiku API — summarize each group | ~$0.001/group |

**Key insight**: Steps 1–3 have zero external API cost. Ollama handles format-agnostic parsing. DJL embedding and Smile clustering replace LLM-based section classification (~$0.05–$0.10 per bill). Only external API cost is Haiku simplification.

Cost impact:
- Most bills are short and skip decomposition
- Large bills typically produce 5–15 concept groups → 5–15 Haiku calls (~$0.005–$0.015 per bill)
- Simplified summaries reduce token cost in Pass 1/2/3
- Total decomposition cost per congress (~10,000 bills): **~$20** (vs ~$500–1,000 if all steps used LLM)

## Cost Controls

- **Budget caps**: Configurable per-run spending limits; pipeline halts if projected cost exceeds cap.
- **Pass 2/3 volume monitoring**: Alert if more than configurable % of bills escalate (may indicate prompt quality issues).
- **Model substitution**: On provider unavailability, fall back to next tier down rather than up (cost protection over quality).