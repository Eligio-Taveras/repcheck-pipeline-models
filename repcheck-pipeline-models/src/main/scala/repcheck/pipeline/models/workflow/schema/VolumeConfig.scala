package repcheck.pipeline.models.workflow.schema

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class VolumeConfig(
  gcsVolumes: Map[String, String] = Map.empty,
  inMemoryVolumes: Map[String, String] = Map.empty,
)

object VolumeConfig {

  implicit val encoder: Encoder[VolumeConfig] = deriveEncoder[VolumeConfig]
  implicit val decoder: Decoder[VolumeConfig] = deriveDecoder[VolumeConfig]

}
