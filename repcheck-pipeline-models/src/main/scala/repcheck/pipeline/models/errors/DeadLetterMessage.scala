package repcheck.pipeline.models.errors

import java.time.Instant

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class DeadLetterMessage(
  originalPayload: String,
  eventType: String,
  failureReason: String,
  failureTimestamp: Instant,
  retryCount: Int,
)

object DeadLetterMessage {
  implicit val encoder: Encoder[DeadLetterMessage] = deriveEncoder[DeadLetterMessage]
  implicit val decoder: Decoder[DeadLetterMessage] = deriveDecoder[DeadLetterMessage]
}
