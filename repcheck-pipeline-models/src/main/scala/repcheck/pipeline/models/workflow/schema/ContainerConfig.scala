package repcheck.pipeline.models.workflow.schema

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class ContainerConfig(
  image: String,
  command: Option[List[String]] = None,
  args: List[String] = List.empty,
  env: Map[String, String] = Map.empty,
  secretMounts: Map[String, String] = Map.empty,
)

object ContainerConfig {

  implicit val encoder: Encoder[ContainerConfig] = deriveEncoder[ContainerConfig]
  implicit val decoder: Decoder[ContainerConfig] = deriveDecoder[ContainerConfig]

}
