package repcheck.pipeline.models.changes

import io.circe.{Decoder, Encoder}

final case class UnrecognizedChangeDetectionStrategy(value: String)
    extends Exception(
      s"Unrecognized ChangeDetectionStrategy: '$value'. Valid values: UpdateDateComparison, ExistenceCheck, FieldLevelDiff, AlwaysNew"
    )

enum ChangeDetectionStrategy(val apiValue: String) {
  case UpdateDateComparison extends ChangeDetectionStrategy("update_date_comparison")
  case ExistenceCheck       extends ChangeDetectionStrategy("existence_check")
  case FieldLevelDiff       extends ChangeDetectionStrategy("field_level_diff")
  case AlwaysNew            extends ChangeDetectionStrategy("always_new")
}

object ChangeDetectionStrategy {

  private val aliases: Map[String, ChangeDetectionStrategy] = Map(
    "UPDATEDATECOMPARISON" -> ChangeDetectionStrategy.UpdateDateComparison,
    "EXISTENCECHECK"       -> ChangeDetectionStrategy.ExistenceCheck,
    "FIELDLEVELDIFF"       -> ChangeDetectionStrategy.FieldLevelDiff,
    "ALWAYSNEW"            -> ChangeDetectionStrategy.AlwaysNew,
  )

  def fromString(value: String): Either[UnrecognizedChangeDetectionStrategy, ChangeDetectionStrategy] =
    aliases.get(value.toUpperCase.replaceAll("[_\\-]", "")) match {
      case Some(s) => Right(s)
      case None    => Left(UnrecognizedChangeDetectionStrategy(value))
    }

  implicit val encoder: Encoder[ChangeDetectionStrategy] = Encoder.encodeString.contramap(_.toString)

  implicit val decoder: Decoder[ChangeDetectionStrategy] = Decoder.decodeString.emap { str =>
    fromString(str).left.map(_.getMessage)
  }

}
