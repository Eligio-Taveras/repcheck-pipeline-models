<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/system-design/09-storage-mapping.md -->

# Storage Mapping

| Entity | Store | Table(s) | Rationale |
|--------|-------|----------|-----------|
| Bills | AlloyDB | `bills`, `bill_cosponsors`, `bill_subjects` | Congressional metadata, normalized cosponsors and subjects |
| Bill Text Versions | AlloyDB | `bill_text_versions` | Immutable text versions with content and embeddings (pgvector) |
| Members | AlloyDB | `members`, `member_terms`, `member_party_history` | Congressional metadata, terms, party affiliations |
| Member History | AlloyDB | `member_history`, `member_term_history` | Archive-before-overwrite snapshots |
| LIS Mapping | AlloyDB | `lis_member_mapping` | Senate LIS ID → bioguideId mapping from senator-lookup.xml |
| Committees | AlloyDB | `committees`, `committee_members`, `bill_committee_referrals` | Committee membership from chamber XML feeds |
| Votes | AlloyDB | `votes`, `vote_positions` | Congressional metadata, normalized positions |
| Vote History | AlloyDB | `vote_history`, `vote_history_positions` | Archive-before-overwrite snapshots |
| Amendments | AlloyDB | `amendments` | Congressional metadata, linked to bills |
| Bill Analyses | AlloyDB | `bill_analyses`, `bill_findings`, `amendment_findings`, `finding_types` | LLM analysis results linked to bills via `bill_id` FK |
| Users | AlloyDB | `users`, `user_preferences` | Relational user data, joins with preferences |
| Member Bill Stances | AlloyDB | `member_bill_stances` | Derived vote positions per bill per member |
| Pre-computed Scores | AlloyDB | `scores`, `score_topics`, `score_congress`, `score_congress_topics` | Denormalized scoring tables, keyed by `(user_id, member_id)` |
| Score History | AlloyDB | `score_history`, `score_history_congress`, `score_history_congress_topics`, `score_history_highlights` | Historical score snapshots with LLM reasoning |
| Workflow State | AlloyDB | `workflow_runs`, `workflow_run_steps` | Launcher workflow execution tracking |
| Pipeline Runs | AlloyDB | `pipeline_runs`, `processing_results` | Per-pipeline execution metadata |

**Reference table name constants via `Tables` object (Component 2 §2.10), never hardcoded strings.**

**Dev cost optimization**: Cloud SQL PostgreSQL (db-f1-micro, ~$10/mo) replaces AlloyDB (~$390/mo) in dev; both PostgreSQL wire-compatible, same Doobie code. pgvector available on both. Staging/prod use AlloyDB for columnar engine and optimized vector search. Control via `db_engine` variable in Terraform `data` module.