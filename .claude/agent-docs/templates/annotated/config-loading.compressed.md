<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/annotated/config-loading.md -->

# Pattern: Configuration Loading

Configuration using PureConfig with manual `ConfigReader` via cursor API. Config passed as JSON string via CLI args, parsed into case class, wrapped in `IO` for fail-fast error handling.

## When To Use
- Every pipeline application needs a config case class and loader
- Cloud Run Jobs receive config via CLI args or environment variables

---

## The Config Case Class

```scala
// File: bill-identifier/src/main/scala/config/BillIdentifierConfig.scala

package config

import pureconfig._

case class BillIdentifierConfig(
    apiKey: String,
    pageSize: Int,
    billLookBackInDays: Int
)

object BillIdentifierConfig {
  implicit val configReader: ConfigReader[BillIdentifierConfig] = {
    (cur: ConfigCursor) =>
      {
        for {
          objCur <- cur.asObjectCursor
          apiKey <- objCur.atKey("apiKey").flatMap(_.asString)
          pageSize <- objCur.atKey("pageSize").flatMap(_.asInt)
          billLookBack <- objCur.atKey("billLookBackInDays").flatMap(_.asInt)
        } yield BillIdentifierConfig(apiKey, pageSize, billLookBack)
      }
  }
}
```

**Manual ConfigReader rationale:** Explicit field mapping via cursor API. For new code, prefer auto-derivation: `import pureconfig.generic.derivation.default._` and add `derives ConfigReader` to case class.

## The Config Loader

```scala
// File: bill-identifier/src/main/scala/config/ConfigLoader.scala

package config

import cats.effect.IO
import cats.syntax.all._

import pureconfig.ConfigSource

object ConfigLoader {
  def LoadConfig(args: List[String]): IO[BillIdentifierConfig] = {
    args.headOption match {
      case None =>
        IO.raiseError(
          IllegalArgumentException(
            "You must provide a configuration json string."
          )
        )
      case Some(jsonString) =>
        ConfigSource.string(jsonString).load[BillIdentifierConfig] match {
          case Right(conf) => conf.pure[IO]
          case Left(errors) =>
            IO.raiseError(IllegalArgumentException(errors.toString))
        }
    }
  }
}
```

**Pattern:** Runs first in IOApp for-comprehension. No args → fail immediately. Parse failure → error with PureConfig details. Success → lift into IO.

## Key Patterns

| Pattern | Usage |
|---------|-------|
| `args.headOption` | Access CLI args safely (WartRemover forbids `.head`) |
| `IO.raiseError(...)` | Fail fast with descriptive error |
| `.pure[IO]` | Lift pure value into effect type |
| `ConfigSource.string(...)` | Parse inline JSON/HOCON |
| Manual `ConfigReader` | Custom validation or mismatched field names |
| Auto-derived `ConfigReader` | New code — `derives ConfigReader` with pureconfig-generic-scala3 |

## How to Create a New Config (Auto-Derivation)

```scala
package config

import pureconfig._
import pureconfig.generic.derivation.default._

case class VoteIngestionConfig(
    apiKey: String,
    pageSize: Int,
    voteLookBackInDays: Int,
    firestoreProjectId: String,
    retry: RetryConfig,
    parallelism: Int
) derives ConfigReader

case class RetryConfig(
    maxRetries: Int,
    initialBackoffMs: Int,
    maxBackoffMs: Int,
    backoffMultiplier: Double
) derives ConfigReader
```

**HOCON config:**
```hocon
api-key = "YOUR_KEY"
page-size = 250
vote-look-back-in-days = 120
firestore-project-id = "repcheck-421801"
retry {
  max-retries = 3
  initial-backoff-ms = 10
  max-backoff-ms = 60000
  backoff-multiplier = 2.0
}
parallelism = 4
```

**Note:** PureConfig auto-converts camelCase (Scala) ↔ kebab-case (HOCON).