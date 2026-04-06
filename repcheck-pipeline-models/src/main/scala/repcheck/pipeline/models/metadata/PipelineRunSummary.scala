package repcheck.pipeline.models.metadata

import java.time.Instant
import java.util.UUID

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class PipelineRunSummary(
  runId: UUID,
  pipelineName: String,
  status: PipelineStatus,
  startedAt: Instant,
  completedAt: Instant,
  itemsProcessed: Int,
  itemsSucceeded: Int,
  itemsFailed: Int,
  errorCounts: Map[String, Int],
)

object PipelineRunSummary {

  implicit val encoder: Encoder[PipelineRunSummary] = deriveEncoder[PipelineRunSummary]
  implicit val decoder: Decoder[PipelineRunSummary] = deriveDecoder[PipelineRunSummary]

  def fromResults(
    runId: UUID,
    pipelineName: String,
    startedAt: Instant,
    completedAt: Instant,
    results: List[ProcessingResult],
  ): PipelineRunSummary = {
    val succeeded = results.count(_.isSucceeded)
    val failed    = results.count(_.isFailed)

    val status: PipelineStatus =
      if (results.isEmpty) {
        PipelineStatus.Completed
      } else if (failed == results.length) {
        PipelineStatus.Failed
      } else if (failed > 0) {
        PipelineStatus.CompletedWithErrors
      } else {
        PipelineStatus.Completed
      }

    val errorCounts: Map[String, Int] = results
      .collect {
        case f: ProcessingResult.Failed =>
          f.reason
      }
      .groupBy(identity)
      .map {
        case (reason, occurrences) =>
          (reason, occurrences.length)
      }

    PipelineRunSummary(
      runId = runId,
      pipelineName = pipelineName,
      status = status,
      startedAt = startedAt,
      completedAt = completedAt,
      itemsProcessed = results.length,
      itemsSucceeded = succeeded,
      itemsFailed = failed,
      errorCounts = errorCounts,
    )
  }

}
