package repcheck.pipeline.models.changes

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class ChangeDetectionResult[T](
  entity: T,
  entityId: String,
  entityType: String,
  changed: Boolean,
  strategy: ChangeDetectionStrategy,
  persistenceStrategy: PersistenceStrategy,
  emitEvent: Option[String],
)

object ChangeDetectionResult {

  def apply[T](
    entity: T,
    entityId: String,
    config: EntityChangeConfig,
    changed: Boolean,
  ): ChangeDetectionResult[T] = {
    val event = if (changed) { config.eventType }
    else { None }
    ChangeDetectionResult(
      entity,
      entityId,
      config.entityType,
      changed,
      config.detectionStrategy,
      config.persistenceStrategy,
      event,
    )
  }

  implicit def encoder[T](implicit te: Encoder[T]): Encoder[ChangeDetectionResult[T]] =
    deriveEncoder[ChangeDetectionResult[T]]

  implicit def decoder[T](implicit td: Decoder[T]): Decoder[ChangeDetectionResult[T]] =
    deriveDecoder[ChangeDetectionResult[T]]

}
