package repcheck.pipeline.models.workflow.schema

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class NetworkConfig(
  vpcConnector: Option[String] = None,
  vpcEgress: Option[String] = None,
  cloudSqlConnections: List[String] = List.empty,
)

object NetworkConfig {

  implicit val encoder: Encoder[NetworkConfig] = deriveEncoder[NetworkConfig]
  implicit val decoder: Decoder[NetworkConfig] = deriveDecoder[NetworkConfig]

}
