package repcheck.pipeline.models.metadata

import io.circe.{Decoder, Encoder}

final case class UnrecognizedPipelineStatus(value: String)
    extends Exception(
      s"Unrecognized PipelineStatus: '$value'. Valid values: Running, Completed, CompletedWithErrors, Failed"
    )

enum PipelineStatus(val label: String) {
  case Running             extends PipelineStatus("Running")
  case Completed           extends PipelineStatus("Completed")
  case CompletedWithErrors extends PipelineStatus("CompletedWithErrors")
  case Failed              extends PipelineStatus("Failed")
}

object PipelineStatus {

  private val aliases: Map[String, PipelineStatus] = Map(
    "RUNNING"             -> PipelineStatus.Running,
    "COMPLETED"           -> PipelineStatus.Completed,
    "COMPLETEDWITHERRORS" -> PipelineStatus.CompletedWithErrors,
    "FAILED"              -> PipelineStatus.Failed,
  )

  def fromString(value: String): Either[UnrecognizedPipelineStatus, PipelineStatus] =
    aliases.get(value.toUpperCase.replaceAll("[_\\-\\s]", "")) match {
      case Some(ps) => Right(ps)
      case None     => Left(UnrecognizedPipelineStatus(value))
    }

  implicit val encoder: Encoder[PipelineStatus] = Encoder.encodeString.contramap(_.toString)

  implicit val decoder: Decoder[PipelineStatus] = Decoder.decodeString.emap { str =>
    fromString(str).left.map(_.getMessage)
  }

  implicit val doobieGet: doobie.Get[PipelineStatus] =
    doobie.Get[String].temap(s => fromString(s).left.map(_.getMessage))

  implicit val doobiePut: doobie.Put[PipelineStatus] = doobie.Put[String].contramap(_.toString)

}
