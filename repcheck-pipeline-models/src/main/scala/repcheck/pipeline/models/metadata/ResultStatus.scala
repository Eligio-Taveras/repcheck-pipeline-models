package repcheck.pipeline.models.metadata

import io.circe.{Decoder, Encoder}

final case class UnrecognizedResultStatus(value: String)
    extends Exception(
      s"Unrecognized ResultStatus: '$value'. Valid values: Succeeded, Failed, Skipped"
    )

enum ResultStatus(val label: String) {
  case Succeeded extends ResultStatus("Succeeded")
  case Failed    extends ResultStatus("Failed")
  case Skipped   extends ResultStatus("Skipped")
}

object ResultStatus {

  private val aliases: Map[String, ResultStatus] = Map(
    "SUCCEEDED" -> ResultStatus.Succeeded,
    "FAILED"    -> ResultStatus.Failed,
    "SKIPPED"   -> ResultStatus.Skipped,
  )

  def fromString(value: String): Either[UnrecognizedResultStatus, ResultStatus] =
    aliases.get(value.toUpperCase) match {
      case Some(rs) => Right(rs)
      case None     => Left(UnrecognizedResultStatus(value))
    }

  implicit val encoder: Encoder[ResultStatus] = Encoder.encodeString.contramap(_.toString)

  implicit val decoder: Decoder[ResultStatus] = Decoder.decodeString.emap { str =>
    fromString(str).left.map(_.getMessage)
  }

  implicit val doobieGet: doobie.Get[ResultStatus] =
    doobie.Get[String].temap(s => fromString(s).left.map(_.getMessage))

  implicit val doobiePut: doobie.Put[ResultStatus] = doobie.Put[String].contramap(_.toString)

}
