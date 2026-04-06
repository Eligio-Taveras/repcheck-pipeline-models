package repcheck.pipeline.models.workflow.schema

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class WorkflowStepDefinition(
  stepName: String,
  triggeredBy: List[String] = List.empty,
  dependencies: List[String] = List.empty,
  container: ContainerConfig,
  resources: ResourceConfig = ResourceConfig(),
  execution: ExecutionConfig = ExecutionConfig(),
  networking: NetworkConfig = NetworkConfig(),
  volumes: VolumeConfig = VolumeConfig(),
  identity: IdentityConfig,
  healthChecks: HealthCheckConfig = HealthCheckConfig(),
  metadata: MetadataConfig = MetadataConfig(),
)

object WorkflowStepDefinition {

  implicit val encoder: Encoder[WorkflowStepDefinition] = deriveEncoder[WorkflowStepDefinition]
  implicit val decoder: Decoder[WorkflowStepDefinition] = deriveDecoder[WorkflowStepDefinition]

}
