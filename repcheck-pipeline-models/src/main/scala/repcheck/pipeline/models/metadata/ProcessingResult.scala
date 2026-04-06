package repcheck.pipeline.models.metadata

import java.time.Instant
import java.util.UUID

import cats.effect.Sync

sealed trait ProcessingResult {
  def entityId: String
  def isSucceeded: Boolean
  def isFailed: Boolean
  def isSkipped: Boolean

  def toResultDO[F[_]: Sync](
    runId: UUID,
    correlationId: UUID,
    entityType: String,
  ): F[ProcessingResultDO]

}

object ProcessingResult {

  final case class Succeeded(entityId: String, eventEmitted: Boolean = false) extends ProcessingResult {

    override def isSucceeded: Boolean = true
    override def isFailed: Boolean    = false
    override def isSkipped: Boolean   = false

    override def toResultDO[F[_]: Sync](
      runId: UUID,
      correlationId: UUID,
      entityType: String,
    ): F[ProcessingResultDO] =
      Sync[F].delay {
        ProcessingResultDO(
          resultId = UUID.randomUUID(),
          runId = runId,
          correlationId = correlationId,
          entityType = entityType,
          entityId = entityId,
          status = ResultStatus.Succeeded,
          errorMessage = None,
          errorClass = None,
          processedAt = Instant.now(),
        )
      }

  }

  final case class Failed(entityId: String, reason: String, errorClass: String = "Systemic") extends ProcessingResult {

    override def isSucceeded: Boolean = false
    override def isFailed: Boolean    = true
    override def isSkipped: Boolean   = false

    override def toResultDO[F[_]: Sync](
      runId: UUID,
      correlationId: UUID,
      entityType: String,
    ): F[ProcessingResultDO] =
      Sync[F].delay {
        ProcessingResultDO(
          resultId = UUID.randomUUID(),
          runId = runId,
          correlationId = correlationId,
          entityType = entityType,
          entityId = entityId,
          status = ResultStatus.Failed,
          errorMessage = Some(reason),
          errorClass = Some(errorClass),
          processedAt = Instant.now(),
        )
      }

  }

  final case class Skipped(entityId: String, reason: String) extends ProcessingResult {

    override def isSucceeded: Boolean = false
    override def isFailed: Boolean    = false
    override def isSkipped: Boolean   = true

    override def toResultDO[F[_]: Sync](
      runId: UUID,
      correlationId: UUID,
      entityType: String,
    ): F[ProcessingResultDO] =
      Sync[F].delay {
        ProcessingResultDO(
          resultId = UUID.randomUUID(),
          runId = runId,
          correlationId = correlationId,
          entityType = entityType,
          entityId = entityId,
          status = ResultStatus.Skipped,
          errorMessage = Some(reason),
          errorClass = None,
          processedAt = Instant.now(),
        )
      }

  }

}
