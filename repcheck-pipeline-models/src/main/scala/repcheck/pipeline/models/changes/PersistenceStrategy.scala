package repcheck.pipeline.models.changes

import io.circe.{Decoder, Encoder}

final case class UnrecognizedPersistenceStrategy(value: String)
    extends Exception(
      s"Unrecognized PersistenceStrategy: '$value'. Valid values: Upsert, UpsertWithHistory, AppendOnly"
    )

enum PersistenceStrategy(val apiValue: String) {
  case Upsert            extends PersistenceStrategy("upsert")
  case UpsertWithHistory extends PersistenceStrategy("upsert_with_history")
  case AppendOnly        extends PersistenceStrategy("append_only")
}

object PersistenceStrategy {

  private val aliases: Map[String, PersistenceStrategy] = Map(
    "UPSERT"            -> PersistenceStrategy.Upsert,
    "UPSERTWITHHISTORY" -> PersistenceStrategy.UpsertWithHistory,
    "APPENDONLY"        -> PersistenceStrategy.AppendOnly,
  )

  def fromString(value: String): Either[UnrecognizedPersistenceStrategy, PersistenceStrategy] =
    aliases.get(value.toUpperCase.replaceAll("[_\\-]", "")) match {
      case Some(s) => Right(s)
      case None    => Left(UnrecognizedPersistenceStrategy(value))
    }

  implicit val encoder: Encoder[PersistenceStrategy] = Encoder.encodeString.contramap(_.toString)

  implicit val decoder: Decoder[PersistenceStrategy] = Decoder.decodeString.emap { str =>
    fromString(str).left.map(_.getMessage)
  }

}
