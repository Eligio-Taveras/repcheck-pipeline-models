package repcheck.pipeline.models.workflow.state

import java.time.Instant
import java.util.UUID

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class WorkflowRunDO(
  workflowRunId: UUID,
  workflowName: String,
  workflowDefinitionPath: String,
  status: WorkflowRunStatus,
  triggeredBy: String,
  startedAt: Option[Instant],
  completedAt: Option[Instant],
  createdAt: Instant,
)

object WorkflowRunDO {

  implicit val encoder: Encoder[WorkflowRunDO] = deriveEncoder[WorkflowRunDO]
  implicit val decoder: Decoder[WorkflowRunDO] = deriveDecoder[WorkflowRunDO]

}
