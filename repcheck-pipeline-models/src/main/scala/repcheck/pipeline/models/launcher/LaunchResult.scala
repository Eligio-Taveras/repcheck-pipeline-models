package repcheck.pipeline.models.launcher

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class LaunchResult(
  success: Boolean,
  jobName: Option[String],
  errorMessage: Option[String],
)

object LaunchResult {

  implicit val encoder: Encoder[LaunchResult] = deriveEncoder[LaunchResult]
  implicit val decoder: Decoder[LaunchResult] = deriveDecoder[LaunchResult]

}
