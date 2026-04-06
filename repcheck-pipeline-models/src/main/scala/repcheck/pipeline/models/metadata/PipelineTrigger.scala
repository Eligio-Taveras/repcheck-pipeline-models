package repcheck.pipeline.models.metadata

import io.circe.{Decoder, Encoder}

final case class UnrecognizedPipelineTrigger(value: String)
    extends Exception(
      s"Unrecognized PipelineTrigger: '$value'. Valid values: Scheduled, Manual, Event"
    )

enum PipelineTrigger(val label: String) {
  case Scheduled extends PipelineTrigger("Scheduled")
  case Manual    extends PipelineTrigger("Manual")
  case Event     extends PipelineTrigger("Event")
}

object PipelineTrigger {

  private val aliases: Map[String, PipelineTrigger] = Map(
    "SCHEDULED" -> PipelineTrigger.Scheduled,
    "MANUAL"    -> PipelineTrigger.Manual,
    "EVENT"     -> PipelineTrigger.Event,
  )

  def fromString(value: String): Either[UnrecognizedPipelineTrigger, PipelineTrigger] =
    aliases.get(value.toUpperCase) match {
      case Some(pt) => Right(pt)
      case None     => Left(UnrecognizedPipelineTrigger(value))
    }

  implicit val encoder: Encoder[PipelineTrigger] = Encoder.encodeString.contramap(_.toString)

  implicit val decoder: Decoder[PipelineTrigger] = Decoder.decodeString.emap { str =>
    fromString(str).left.map(_.getMessage)
  }

  implicit val doobieGet: doobie.Get[PipelineTrigger] =
    doobie.Get[String].temap(s => fromString(s).left.map(_.getMessage))

  implicit val doobiePut: doobie.Put[PipelineTrigger] = doobie.Put[String].contramap(_.toString)

}
