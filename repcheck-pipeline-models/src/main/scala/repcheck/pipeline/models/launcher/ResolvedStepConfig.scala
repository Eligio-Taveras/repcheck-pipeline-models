package repcheck.pipeline.models.launcher

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import repcheck.pipeline.models.workflow.schema._

final case class ResolvedStepConfig(
  stepName: String,
  container: ContainerConfig,
  resources: ResourceConfig,
  execution: ExecutionConfig,
  networking: NetworkConfig,
  volumes: VolumeConfig,
  identity: IdentityConfig,
  healthChecks: HealthCheckConfig,
  metadata: MetadataConfig,
)

object ResolvedStepConfig {

  implicit val encoder: Encoder[ResolvedStepConfig] = deriveEncoder[ResolvedStepConfig]
  implicit val decoder: Decoder[ResolvedStepConfig] = deriveDecoder[ResolvedStepConfig]

}
