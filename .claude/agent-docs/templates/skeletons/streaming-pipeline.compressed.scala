<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/skeletons/streaming-pipeline.scala -->

# RepCheck Skeleton: FS2 Streaming Pipeline with Fail-and-Continue

**Purpose:** Core streaming pattern for all pipeline apps. Each item gets a correlationId, processes via parEvalMap, writes ProcessingResult to AlloyDB immediately, then releases from memory.

**Key Decisions:**
- Always `parEvalMap(config.parallelism)` — sequential = parallelism 1
- No memory accumulation — each ProcessingResult persisted immediately
- ProcessingResult is lightweight: entityId, correlationId, status, errorMessage
- PipelineRunSummary aggregator runs after stream completes
- Correlation ID (UUID) on every item, visible in all logs
- Transient errors → log + continue; Systemic errors → halt pipeline

## Data Models

```scala
package repcheck.pipeline.streaming

import cats.effect.{Async, Clock}
import cats.syntax.all.*
import fs2.Stream
import java.time.Instant
import java.util.UUID
import repcheck.pipeline.models.retry.{
  ErrorClassifier,
  ErrorSeverity,
  SystemicFailure,
  TransientFailure
}

final case class PipelineItem[T](
    correlationId: UUID,
    runId: String,
    payload: T
)

object PipelineItem {
  def wrap[T](runId: String, payload: T): PipelineItem[T] =
    PipelineItem(
      correlationId = UUID.randomUUID(),
      runId = runId,
      payload = payload
    )
}

enum ResultStatus {
  case Succeeded, Failed
}

final case class ProcessingResult(
    correlationId: UUID,
    runId: String,
    entityId: String,
    status: ResultStatus,
    errorMessage: Option[String],
    timestamp: Instant
)

final case class PipelineRunSummary(
    runId: String,
    pipelineName: String,
    totalProcessed: Int,
    succeeded: Int,
    failed: Int,
    startedAt: Instant,
    completedAt: Instant
)
```

## PipelineRunner

```scala
trait PipelineRunner[F[_]] {
  def run[T, R](
      runId: String,
      pipelineName: String,
      parallelism: Int,
      items: Stream[F, T],
      entityIdOf: T => String,
      process: PipelineItem[T] => F[R],
      persistResult: ProcessingResult => F[Unit]
  ): F[PipelineRunSummary]
}

object PipelineRunner {
  def make[F[_]: Async]: PipelineRunner[F] =
    new PipelineRunner[F] {
      def run[T, R](
          runId: String,
          pipelineName: String,
          parallelism: Int,
          items: Stream[F, T],
          entityIdOf: T => String,
          process: PipelineItem[T] => F[R],
          persistResult: ProcessingResult => F[Unit]
      ): F[PipelineRunSummary] =
        for {
          startedAt <- Clock[F].realTimeInstant
          _ <- items
            .map(item => PipelineItem.wrap(runId, item))
            .parEvalMap(parallelism) { pipelineItem =>
              val entityId = entityIdOf(pipelineItem.payload)
              // TODO: Log INFO: s"Processing $entityId [${pipelineItem.correlationId}]"
              process(pipelineItem)
                .as(
                  ProcessingResult(
                    correlationId = pipelineItem.correlationId,
                    runId = runId,
                    entityId = entityId,
                    status = ResultStatus.Succeeded,
                    errorMessage = None,
                    timestamp = Instant.now()
                  )
                )
                .handleErrorWith {
                  case e: SystemicFailure =>
                    Async[F].raiseError(e)

                  case e: TransientFailure =>
                    // TODO: Log WARN: s"Item $entityId failed [${pipelineItem.correlationId}]: ${e.getMessage}"
                    Async[F].pure(
                      ProcessingResult(
                        correlationId = pipelineItem.correlationId,
                        runId = runId,
                        entityId = entityId,
                        status = ResultStatus.Failed,
                        errorMessage = Some(e.getMessage),
                        timestamp = Instant.now()
                      )
                    )

                  case e =>
                    // TODO: Log ERROR: s"Unexpected error on $entityId [${pipelineItem.correlationId}]: ${e.getMessage}"
                    Async[F].pure(
                      ProcessingResult(
                        correlationId = pipelineItem.correlationId,
                        runId = runId,
                        entityId = entityId,
                        status = ResultStatus.Failed,
                        errorMessage = Some(s"Unexpected: ${e.getMessage}"),
                        timestamp = Instant.now()
                      )
                    )
                }
            }
            .evalMap(persistResult)
            .compile
            .drain

          completedAt <- Clock[F].realTimeInstant
          summary <- buildSummary(runId, pipelineName, startedAt, completedAt)
        } yield summary

      private def buildSummary(
          runId: String,
          pipelineName: String,
          startedAt: Instant,
          completedAt: Instant
      ): F[PipelineRunSummary] =
        // TODO: Query AlloyDB `processing_results` table WHERE run_id = $runId
        Async[F].pure(
          PipelineRunSummary(
            runId = runId,
            pipelineName = pipelineName,
            totalProcessed = 0,
            succeeded = 0,
            failed = 0,
            startedAt = startedAt,
            completedAt = completedAt
          )
        )
    }
}
```