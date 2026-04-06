package repcheck.pipeline.models.metadata

import java.time.Instant
import java.util.UUID

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class ProcessingResultDO(
  resultId: UUID,
  runId: UUID,
  correlationId: UUID,
  entityType: String,
  entityId: String,
  status: ResultStatus,
  errorMessage: Option[String],
  errorClass: Option[String],
  processedAt: Instant,
)

object ProcessingResultDO {

  implicit val encoder: Encoder[ProcessingResultDO] = deriveEncoder[ProcessingResultDO]
  implicit val decoder: Decoder[ProcessingResultDO] = deriveDecoder[ProcessingResultDO]

}
