package repcheck.pipeline.models.workflow.schema

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class ExecutionConfig(
  timeout: String = "10m",
  maxRetries: Int = 3,
  taskCount: Int = 1,
  parallelism: Int = 1,
)

object ExecutionConfig {

  implicit val encoder: Encoder[ExecutionConfig] = deriveEncoder[ExecutionConfig]
  implicit val decoder: Decoder[ExecutionConfig] = deriveDecoder[ExecutionConfig]

}
