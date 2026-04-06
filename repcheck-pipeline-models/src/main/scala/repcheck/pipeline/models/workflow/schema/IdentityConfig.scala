package repcheck.pipeline.models.workflow.schema

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class IdentityConfig(
  serviceAccount: String
)

object IdentityConfig {

  implicit val encoder: Encoder[IdentityConfig] = deriveEncoder[IdentityConfig]
  implicit val decoder: Decoder[IdentityConfig] = deriveDecoder[IdentityConfig]

}
