package repcheck.pipeline.models.errors

import io.circe.{Decoder, Encoder}

final case class UnrecognizedErrorClass(value: String)
    extends Exception(s"Unrecognized ErrorClass: '$value'. Valid values: Transient, Systemic")

enum ErrorClass(val label: String) {
  case Transient extends ErrorClass("Transient")
  case Systemic  extends ErrorClass("Systemic")
}

object ErrorClass {

  private val aliases: Map[String, ErrorClass] = Map(
    "TRANSIENT" -> ErrorClass.Transient,
    "SYSTEMIC"  -> ErrorClass.Systemic,
  )

  def fromString(value: String): Either[UnrecognizedErrorClass, ErrorClass] =
    aliases.get(value.toUpperCase) match {
      case Some(ec) => Right(ec)
      case None     => Left(UnrecognizedErrorClass(value))
    }

  implicit val encoder: Encoder[ErrorClass] = Encoder.encodeString.contramap(_.toString)

  implicit val decoder: Decoder[ErrorClass] = Decoder.decodeString.emap { str =>
    fromString(str).left.map(_.getMessage)
  }

}
