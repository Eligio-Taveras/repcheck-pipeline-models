package repcheck.pipeline.models.errors

import java.util.UUID

import scala.concurrent.duration._

import cats.effect.Temporal
import cats.syntax.all._

class RetryWrapper[F[_]: Temporal](
  logRetry: (Int, Int, Long, ErrorClass, String, UUID) => F[Unit]
) {

  def withRetry[A](
    operation: F[A],
    config: RetryConfig,
    classifier: ErrorClassifier,
    errorFactory: (String, Throwable) => Throwable,
    correlationId: UUID,
  ): F[A] =
    loop(operation, config, classifier, errorFactory, correlationId, 0)

  private def loop[A](
    operation: F[A],
    config: RetryConfig,
    classifier: ErrorClassifier,
    errorFactory: (String, Throwable) => Throwable,
    correlationId: UUID,
    attempt: Int,
  ): F[A] =
    operation.handleErrorWith { error =>
      classifier.classify(error) match {
        case ErrorClass.Systemic =>
          Temporal[F].raiseError(
            errorFactory(error.getMessage, error)
          )
        case ErrorClass.Transient =>
          if (attempt >= config.maxRetries) {
            Temporal[F].raiseError(
              errorFactory(error.getMessage, error)
            )
          } else {
            val delay = calculateDelay(config, attempt)
            logRetry(attempt + 1, config.maxRetries, delay, ErrorClass.Transient, error.getMessage, correlationId) *>
              Temporal[F].sleep(delay.millis) *>
              loop(operation, config, classifier, errorFactory, correlationId, attempt + 1)
          }
      }
    }

  private def calculateDelay(config: RetryConfig, attempt: Int): Long = {
    val raw = config.initialBackoffMs * math.pow(config.backoffMultiplier, attempt.toDouble).toLong
    math.min(raw, config.maxBackoffMs)
  }

}
