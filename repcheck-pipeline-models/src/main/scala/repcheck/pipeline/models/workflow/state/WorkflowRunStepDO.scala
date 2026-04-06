package repcheck.pipeline.models.workflow.state

import java.time.Instant
import java.util.UUID

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class WorkflowRunStepDO(
  workflowRunId: UUID,
  stepName: String,
  status: WorkflowStepStatus,
  pipelineRunId: Option[UUID],
  retryCount: Int,
  maxRetries: Int,
  originalMessage: Option[String],
  startedAt: Option[Instant],
  completedAt: Option[Instant],
  errorMessage: Option[String],
  createdAt: Instant,
  updatedAt: Instant,
)

object WorkflowRunStepDO {

  implicit val encoder: Encoder[WorkflowRunStepDO] = deriveEncoder[WorkflowRunStepDO]
  implicit val decoder: Decoder[WorkflowRunStepDO] = deriveDecoder[WorkflowRunStepDO]

  /** Returns true if the step can be retried (retryCount < maxRetries). */
  def canRetry(step: WorkflowRunStepDO): Boolean =
    step.retryCount < step.maxRetries

  /** Increments retryCount on a failed step. Returns Left if retries exhausted. */
  def applyRetry(step: WorkflowRunStepDO, now: Instant): Either[String, WorkflowRunStepDO] =
    if (canRetry(step)) {
      Right(
        step.copy(
          retryCount = step.retryCount + 1,
          status = WorkflowStepStatus.Running,
          updatedAt = now,
        )
      )
    } else {
      Left(s"Step '${step.stepName}' has exhausted retries (${step.retryCount}/${step.maxRetries})")
    }

}
