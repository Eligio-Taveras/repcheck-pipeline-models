package repcheck.pipeline.models.workflow.schema

import io.circe.{Decoder, Encoder, HCursor, Json}

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

  implicit val encoder: Encoder[ProbeConfig] = Encoder.instance { p =>
    Json.obj(
      "initialDelaySeconds" -> Json.fromInt(p.initialDelaySeconds),
      "timeoutSeconds"      -> Json.fromInt(p.timeoutSeconds),
      "periodSeconds"       -> Json.fromInt(p.periodSeconds),
      "failureThreshold"    -> Json.fromInt(p.failureThreshold),
    )
  }

  implicit val decoder: Decoder[ProbeConfig] = Decoder.instance { (c: HCursor) =>
    for {
      ids <- c.get[Int]("initialDelaySeconds")
      ts  <- c.get[Int]("timeoutSeconds")
      ps  <- c.get[Int]("periodSeconds")
      ft  <- c.get[Int]("failureThreshold")
    } yield ProbeConfig(ids, ts, ps, ft)
  }

}

final case class HealthCheckConfig(
  startupProbe: Option[ProbeConfig] = None,
  livenessProbe: Option[ProbeConfig] = None,
)

object HealthCheckConfig {

  implicit val encoder: Encoder[HealthCheckConfig] = Encoder.instance { h =>
    Json.obj(
      "startupProbe"  -> Encoder.encodeOption[ProbeConfig].apply(h.startupProbe),
      "livenessProbe" -> Encoder.encodeOption[ProbeConfig].apply(h.livenessProbe),
    )
  }

  implicit val decoder: Decoder[HealthCheckConfig] = Decoder.instance { (c: HCursor) =>
    for {
      sp <- c.get[Option[ProbeConfig]]("startupProbe")
      lp <- c.get[Option[ProbeConfig]]("livenessProbe")
    } yield HealthCheckConfig(sp, lp)
  }

}
