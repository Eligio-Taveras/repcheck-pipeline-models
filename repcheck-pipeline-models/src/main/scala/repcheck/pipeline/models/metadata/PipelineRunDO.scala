package repcheck.pipeline.models.metadata

import java.time.Instant
import java.util.UUID

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class PipelineRunDO(
  runId: UUID,
  pipelineName: String,
  status: PipelineStatus,
  startedAt: Instant,
  completedAt: Option[Instant],
  itemsProcessed: Int,
  itemsSucceeded: Int,
  itemsFailed: Int,
  errorSummary: Option[String],
  snapshotPath: Option[String],
  trigger: PipelineTrigger,
  createdAt: Instant,
)

object PipelineRunDO {

  implicit val encoder: Encoder[PipelineRunDO] = deriveEncoder[PipelineRunDO]
  implicit val decoder: Decoder[PipelineRunDO] = deriveDecoder[PipelineRunDO]

}
