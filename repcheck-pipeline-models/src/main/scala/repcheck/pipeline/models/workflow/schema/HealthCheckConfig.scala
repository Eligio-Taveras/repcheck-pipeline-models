package repcheck.pipeline.models.workflow.schema

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class ProbeConfig(
  initialDelaySeconds: Int = 0,
  timeoutSeconds: Int = 1,
  periodSeconds: Int = 10,
  failureThreshold: Int = 3,
)

object ProbeConfig {

  def validate(probe: ProbeConfig, stepName: String): Either[String, Unit] =
    if (probe.initialDelaySeconds < 0 || probe.initialDelaySeconds > 240) {
      Left(s"initialDelaySeconds must be 0-240 in step: $stepName")
    } else if (probe.timeoutSeconds < 1 || probe.timeoutSeconds > 3600) {
      Left(s"timeoutSeconds must be 1-3600 in step: $stepName")
    } else if (probe.periodSeconds < 1 || probe.periodSeconds > 240) {
      Left(s"periodSeconds must be 1-240 in step: $stepName")
    } else if (probe.failureThreshold < 1) {
      Left(s"failureThreshold must be >= 1 in step: $stepName")
    } else {
      Right(())
    }

  implicit val encoder: Encoder[ProbeConfig] = deriveEncoder[ProbeConfig]
  implicit val decoder: Decoder[ProbeConfig] = deriveDecoder[ProbeConfig]

}

final case class HealthCheckConfig(
  startupProbe: Option[ProbeConfig] = None,
  livenessProbe: Option[ProbeConfig] = None,
)

object HealthCheckConfig {

  implicit val encoder: Encoder[HealthCheckConfig] = deriveEncoder[HealthCheckConfig]
  implicit val decoder: Decoder[HealthCheckConfig] = deriveDecoder[HealthCheckConfig]

}
