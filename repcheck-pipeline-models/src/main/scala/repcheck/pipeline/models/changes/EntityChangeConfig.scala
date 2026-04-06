package repcheck.pipeline.models.changes

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class EntityChangeConfig(
  entityType: String,
  detectionStrategy: ChangeDetectionStrategy,
  persistenceStrategy: PersistenceStrategy,
  eventType: Option[String],
)

object EntityChangeConfig {

  val BillMetadata: EntityChangeConfig = EntityChangeConfig(
    "bill_metadata",
    ChangeDetectionStrategy.UpdateDateComparison,
    PersistenceStrategy.Upsert,
    None,
  )

  val BillTextVersion: EntityChangeConfig = EntityChangeConfig(
    "bill_text_version",
    ChangeDetectionStrategy.UpdateDateComparison,
    PersistenceStrategy.AppendOnly,
    Some("bill.text.available"),
  )

  val Vote: EntityChangeConfig = EntityChangeConfig(
    "vote",
    ChangeDetectionStrategy.UpdateDateComparison,
    PersistenceStrategy.UpsertWithHistory,
    Some("vote.recorded"),
  )

  val Member: EntityChangeConfig = EntityChangeConfig(
    "member",
    ChangeDetectionStrategy.FieldLevelDiff,
    PersistenceStrategy.Upsert,
    None,
  )

  val Amendment: EntityChangeConfig = EntityChangeConfig(
    "amendment",
    ChangeDetectionStrategy.UpdateDateComparison,
    PersistenceStrategy.Upsert,
    None,
  )

  val Analysis: EntityChangeConfig = EntityChangeConfig(
    "analysis",
    ChangeDetectionStrategy.AlwaysNew,
    PersistenceStrategy.AppendOnly,
    Some("analysis.completed"),
  )

  val Score: EntityChangeConfig = EntityChangeConfig(
    "score",
    ChangeDetectionStrategy.AlwaysNew,
    PersistenceStrategy.Upsert,
    None,
  )

  implicit val encoder: Encoder[EntityChangeConfig] = deriveEncoder[EntityChangeConfig]
  implicit val decoder: Decoder[EntityChangeConfig] = deriveDecoder[EntityChangeConfig]
}
