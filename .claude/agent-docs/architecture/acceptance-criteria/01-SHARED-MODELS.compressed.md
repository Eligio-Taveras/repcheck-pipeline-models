<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/acceptance-criteria/01-SHARED-MODELS.md -->

# Acceptance Criteria: Component 1 — `repcheck-shared-models`

Published library containing domain data types for legislative, user, and analysis data. Used by all repositories that need to read or write domain entities. **Depends on**: Nothing (root domain library, no external repcheck dependencies).

---

## Implementation Areas

| Area | Status | Description |
|------|--------|-------------|
| 1.1 Legislative DTOs | Migrate + Extend | Congress.gov API response shapes + chamber XML source DTOs |
| 1.2 Legislative DOs | Migrate + Extend | AlloyDB storage shapes + `toDO` conversions |
| 1.3 Committee DTOs & DOs | New | Committee membership from official chamber XML feeds |
| 1.4 Bill Text Versioning | New | Multi-version text storage + text diff analysis |
| 1.5 User Domain Objects | New | User, preference, Q&A, and stance domain objects |
| 1.6 LLM Output Schemas | New | Structured JSON schemas for LLM responses |
| 1.7 Prompt Chain Base Traits | New | InstructionBlock, PromptProfile, ChainAssembler |
| 1.8 Shared Serializers & Constants | Migrate + Extend | Common Circe codecs, enums, constants |
| 1.9 Analysis Domain Objects | New | BillAnalysisDO, BillFindingDO, BillConceptSummaryDO, AmendmentFindingDO, FindingTypeDO |

---

## Component Routing Table

| Task | Area File |
|------|-----------|
| Congress.gov API response DTOs, bill/member/vote/amendment DTOs | [1.1 Legislative DTOs](01-shared-models/01.1-legislative-dtos.md) |
| AlloyDB storage DOs, toDO conversions | [1.2 Legislative DOs](01-shared-models/01.2-legislative-dos.md) |
| Committee membership from chamber XML feeds | [1.3 Committee DTOs & DOs](01-shared-models/01.3-committee-dtos-dos.md) |
| Multi-version text storage, text diff analysis | [1.4 Bill Text Versioning](01-shared-models/01.4-bill-text-versioning.md) |
| User, preference, Q&A, stance, and score domain objects | [1.5 User Domain Objects](01-shared-models/01.5-user-domain-objects.md) |
| Structured JSON schemas for LLM responses | [1.6 LLM Output Schemas](01-shared-models/01.6-llm-output-schemas.md) |
| InstructionBlock, PromptProfile, ChainAssembler | [1.7 Prompt Chain Base Traits](01-shared-models/01.7-prompt-chain-base-traits.md) |
| Circe codecs, enums, constants, multi-source alias parsing | [1.8 Shared Serializers & Constants](01-shared-models/01.8-shared-serializers-constants.md) |
| Analysis DOs: bill analyses, findings, concepts, fiscal, amendments | [1.9 Analysis Domain Objects](01-shared-models/01.9-analysis-domain-objects.md) |

---

## Cross-Cutting Acceptance Criteria

**Package Structure:**
- Root: `repcheck.shared.models`
- Sub-packages: `congress.bill`, `congress.member`, `congress.vote`, `congress.amendment`, `congress.committee`, `congress.common` (LatestAction, PagedResponse), `congress.xml` (Senate/House XML source DTOs), `user`, `llm`, `analysis`, `prompt`, `placeholder` (HasPlaceholder trait + givens), `codecs`, `constants`

**Build:**
- Published as versioned artifact to GitHub Packages.
- No repcheck dependencies.
- Dependencies: Circe (semi-auto), Cats (core only), Doobie (Read/Write instances for DOs).

**Code Quality:**
- Passes `sbt compile` with WartRemover + tpolecat.
- Passes `sbt scalafmtCheckAll` and `sbt scalafixAll --check`.
- Test coverage > 90% (enforced by Codecov patch coverage).
- No `@nowarn` or `@SuppressWarnings`.
- Curly brace syntax only.

**Documentation:**
- Each public class has ScalaDoc describing purpose and source (Congress.gov schema, database table, or spec reference).