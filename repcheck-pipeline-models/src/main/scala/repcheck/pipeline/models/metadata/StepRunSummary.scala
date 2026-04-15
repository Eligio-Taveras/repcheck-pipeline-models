package repcheck.pipeline.models.metadata

import java.time.Instant

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import repcheck.pipeline.models.workflow.state.WorkflowStepStatus

final case class StepRunSummary(
  stepRunId: Long,
  stepName: String,
  status: WorkflowStepStatus,
  startedAt: Instant,
  completedAt: Instant,
  itemsProcessed: Int,
  itemsSucceeded: Int,
  itemsFailed: Int,
  errorCounts: Map[String, Int],
)

object StepRunSummary {

  implicit val encoder: Encoder[StepRunSummary] = deriveEncoder[StepRunSummary]
  implicit val decoder: Decoder[StepRunSummary] = deriveDecoder[StepRunSummary]

  def fromResults(
    stepRunId: Long,
    stepName: String,
    startedAt: Instant,
    completedAt: Instant,
    results: List[ProcessingResult],
  ): StepRunSummary = {
    val succeeded = results.count(_.isSucceeded)
    val failed    = results.count(_.isFailed)

    val status: WorkflowStepStatus =
      if (results.isEmpty) {
        WorkflowStepStatus.Completed
      } else if (failed == results.length) {
        WorkflowStepStatus.Failed
      } else if (failed > 0) {
        WorkflowStepStatus.CompletedWithErrors
      } else {
        WorkflowStepStatus.Completed
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

    StepRunSummary(
      stepRunId = stepRunId,
      stepName = stepName,
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
