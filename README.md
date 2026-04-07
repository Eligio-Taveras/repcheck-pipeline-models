# repcheck-pipeline-models

Shared operational types for the RepCheck pipeline ecosystem. This library defines the foundational contracts that all RepCheck pipeline applications depend on: event schemas, error classification, retry policies, workflow definitions, change detection, and pipeline execution tracking.

Published to GitHub Packages as a Maven artifact, versioned automatically via sbt-dynver.

## What This Library Provides

| Package | Purpose | Key Types |
|---------|---------|-----------|
| `events` | Inter-pipeline Pub/Sub communication | `PipelineEvent`, `EventTypes`, `EventPayloads` |
| `errors` | Error handling and retry | `ErrorClassifier`, `ErrorClass`, `RetryConfig`, `RetryWrapper`, `DeadLetterMessage` |
| `changes` | Entity change detection | `ChangeDetectionResult`, `ChangeDetectionStrategy`, `EntityChangeConfig`, `PersistenceStrategy` |
| `metadata` | Pipeline execution tracking | `ProcessingResult`, `ProcessingResultDO`, `PipelineRunDO`, `PipelineRunSummary`, `PipelineStatus`, `ResultStatus` |
| `workflow/schema` | Workflow definition (Cloud Run Jobs) | `WorkflowDefinition`, `WorkflowStepDefinition`, `ContainerConfig`, `ExecutionConfig`, `ResourceConfig` |
| `workflow/state` | Workflow execution state | `WorkflowRunDO`, `WorkflowRunStepDO`, `WorkflowRunStatus`, `WorkflowStepStatus` |
| `config` | Pipeline configuration | `PipelineConfig`, `AnalysisPassConfig`, `VoteWeights`, `CommitteeAttributionWeights` |
| `constants` | Shared constants | `Tables` (database table names), `EventTypes` (re-exported from shared-models) |
| `launcher` | Step launcher execution model | `LauncherTraits`, `LaunchResult`, `MacroContext`, `MacroResolver`, `ResolvedStepConfig` |

## How It Fits in the RepCheck Ecosystem

```
repcheck-shared-models          (legislative domain types: bills, votes, members)
    |
repcheck-pipeline-models        (this library -- operational pipeline types)
    |
repcheck-ingestion-common       (shared ingestion infrastructure: API clients, repos, publishers)
    |
+-- bills-pipeline
+-- votes-pipeline
+-- members-pipeline
+-- amendments-pipeline
+-- analysis-pipeline
+-- scoring-engine
+-- orchestrator
```

Every pipeline application depends on this library for event schemas, error handling contracts, and workflow definitions. The `Tables` constants object is the single source of truth for database table names across all repositories.

## Tech Stack

| Concern | Library |
|---------|---------|
| Language | Scala 3.4.1 |
| Effect system | Cats Effect (tagless final `F[_]`) |
| Streaming | FS2 |
| JSON | Circe (semi-auto derivation) |
| Database | Doobie (auto-derived `Read`/`Write`) |
| Config | PureConfig (auto-derivation) |
| Testing | ScalaTest + MockitoScala + cats-effect-testing |
| Linting | WartRemover (11 error rules), tpolecat scalac options |
| Versioning | sbt-dynver (git-tag-based semver) |

## Build Commands

```bash
sbt compile              # Compile with WartRemover + tpolecat
sbt test                 # Run all tests
sbt scalafmtCheckAll     # Check formatting (fails if unformatted)
sbt scalafmtAll          # Auto-format all source files
sbt scalafixAll --check  # Check import ordering and lint rules
sbt scalafixAll          # Auto-fix import ordering
sbt coverage test coverageReport  # Run tests with coverage
```

On Windows with Coursier-installed SBT, use `sbt.bat` instead of `sbt`.

## Project Structure

```
repcheck-pipeline-models/
  src/main/scala/repcheck/pipeline/models/
    changes/       # Change detection types
    config/        # Pipeline configuration case classes
    constants/     # Tables, EventTypes constants
    errors/        # ErrorClassifier, RetryConfig, RetryWrapper
    events/        # PipelineEvent, EventTypes, EventPayloads
    launcher/      # Step launcher execution model
    metadata/      # ProcessingResult, PipelineRunDO, status enums
    workflow/
      schema/      # WorkflowDefinition, step/container/resource config
      state/       # WorkflowRunDO, step status tracking
  src/test/scala/  # Unit tests (ScalaTest AnyFlatSpec)
doc-generator/     # Documentation compression utility (Anthropic SDK)
docs/              # Full project documentation
.claude/           # Compressed agent docs
```

## Publishing

This library is published to **GitHub Packages** (Maven) via sbt-dynver. Version numbers are derived automatically from git tags.

### Consuming this library

Add the resolver and dependency to your `build.sbt`:

```scala
resolvers += "GitHub Packages" at "https://maven.pkg.github.com/Eligio-Taveras/repcheck-pipeline-models"

libraryDependencies += "com.repcheck" %% "repcheck-pipeline-models" % "<version>"
```

Authentication requires a GitHub token with `read:packages` scope, configured via `~/.sbt/.github-packages-credentials` or `GITHUB_ACTOR`/`GITHUB_TOKEN` environment variables.

## Documentation

See [CLAUDE.md](CLAUDE.md) for the full agent routing guide, coding conventions, and task routing table.
