package repcheck.pipeline.models.workflow.schema

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class MetadataConfig(
  labels: Map[String, String] = Map.empty,
  annotations: Map[String, String] = Map.empty,
)

object MetadataConfig {

  implicit val encoder: Encoder[MetadataConfig] = deriveEncoder[MetadataConfig]
  implicit val decoder: Decoder[MetadataConfig] = deriveDecoder[MetadataConfig]

}
