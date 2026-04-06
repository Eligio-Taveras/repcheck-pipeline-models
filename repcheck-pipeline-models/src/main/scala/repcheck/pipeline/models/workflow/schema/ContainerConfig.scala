package repcheck.pipeline.models.workflow.schema

import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}

final case class ContainerConfig(
  image: String,
  command: Option[List[String]] = None,
  args: List[String] = List.empty,
  env: Map[String, String] = Map.empty,
  secretMounts: Map[String, String] = Map.empty,
)

object ContainerConfig {

  implicit val encoder: Encoder[ContainerConfig] = Encoder.instance { c =>
    Json.obj(
      "image"        -> c.image.asJson,
      "command"      -> c.command.asJson,
      "args"         -> c.args.asJson,
      "env"          -> c.env.asJson,
      "secretMounts" -> c.secretMounts.asJson,
    )
  }

  implicit val decoder: Decoder[ContainerConfig] = Decoder.instance { (c: HCursor) =>
    for {
      image        <- c.get[String]("image")
      command      <- c.get[Option[List[String]]]("command")
      args         <- c.getOrElse[List[String]]("args")(List.empty)
      env          <- c.getOrElse[Map[String, String]]("env")(Map.empty)
      secretMounts <- c.getOrElse[Map[String, String]]("secretMounts")(Map.empty)
    } yield ContainerConfig(image, command, args, env, secretMounts)
  }

}
