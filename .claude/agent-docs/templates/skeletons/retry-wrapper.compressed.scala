<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/skeletons/retry-wrapper.scala -->

```markdown
# RepCheck Retry Wrapper

**Purpose**: Reusable retry mechanism with exponential backoff for F[A] operations. Each subsystem gets its own RetryConfig and ErrorClassifier.

**Key Decisions**: Max retries 3 (configurable), initial backoff 10ms with 2x multiplier, max backoff 60s, configurable per-subsystem timeout. ErrorClassifier per-subsystem. Transient errors log+continue; systemic errors halt immediately.

## Configuration

```scala
final case class RetryConfig(
    maxRetries: Int = 3,
    initialBackoff: FiniteDuration = 10.millis,
    backoffMultiplier: Double = 2.0,
    maxBackoff: FiniteDuration = 60.seconds,
    timeout: FiniteDuration = 30.seconds
)
```

Example configs:
- congress-gov: `RetryConfig(maxRetries = 3, initialBackoff = 500.millis, maxBackoff = 30.seconds, timeout = 30.seconds)`
- pub-sub: `RetryConfig(maxRetries = 5, initialBackoff = 10.millis, maxBackoff = 10.seconds, timeout = 10.seconds)`
- firestore: `RetryConfig(maxRetries = 3, initialBackoff = 100.millis, maxBackoff = 30.seconds, timeout = 30.seconds)`
- gcs: `RetryConfig(maxRetries = 3, initialBackoff = 100.millis, maxBackoff = 15.seconds, timeout = 15.seconds)`
- llm-claude: `RetryConfig(maxRetries = 2, initialBackoff = 1.second, maxBackoff = 60.seconds, timeout = 120.seconds)`

## Error Classification

```scala
enum ErrorSeverity {
  case Transient  // Item-level failure; log ProcessingResult as failed, continue
  case Systemic   // Infrastructure failure; halt pipeline immediately
}

trait ErrorClassifier {
  def classify(error: Throwable): ErrorSeverity
}
```

Each subsystem adapter implements ErrorClassifier to classify its own errors (e.g., HTTP 429 = Transient, HTTP 403 = Systemic).

## Exceptions

```scala
final case class SystemicFailure(
    subsystem: String,
    originalError: Throwable
) extends Exception(...)

final case class TransientFailure(
    subsystem: String,
    originalError: Throwable,
    retriesAttempted: Int
) extends Exception(...)
```

SystemicFailure halts pipeline (do not catch). TransientFailure caught by pipeline to record failed ProcessingResult.

## Retry Wrapper

```scala
object RetryWrapper {
  def withRetry[F[_]: Temporal, A](
      config: RetryConfig,
      classifier: ErrorClassifier,
      subsystem: String
  )(operation: F[A]): F[A] = {
    val timedOperation: F[A] =
      Temporal[F].timeout(operation, config.timeout)

    def attempt(retriesLeft: Int, currentBackoff: FiniteDuration): F[A] =
      timedOperation.handleErrorWith { error =>
        classifier.classify(error) match {
          case ErrorSeverity.Systemic =>
            // TODO: Log ERROR: s"Systemic failure in $subsystem: ${error.getMessage}"
            Temporal[F].raiseError(SystemicFailure(subsystem, error))

          case ErrorSeverity.Transient =>
            if (retriesLeft <= 0) {
              // TODO: Log WARN: s"Transient failure in $subsystem after ${config.maxRetries} retries"
              Temporal[F].raiseError(
                TransientFailure(subsystem, error, config.maxRetries)
              )
            } else {
              // TODO: Log INFO: s"Retrying $subsystem in $currentBackoff (${retriesLeft} retries left)"
              Temporal[F].sleep(currentBackoff) *>
                attempt(
                  retriesLeft - 1,
                  (currentBackoff * config.backoffMultiplier).min(config.maxBackoff)
                )
            }
        }
      }

    attempt(config.maxRetries, config.initialBackoff)
  }
}
```

**Usage**: Wrap subsystem operation with `RetryWrapper.withRetry(config, classifier, subsystem)(operation)`. Returns F[A] that succeeds, throws TransientFailure (item fails, pipeline continues), or throws SystemicFailure (pipeline halts).
```