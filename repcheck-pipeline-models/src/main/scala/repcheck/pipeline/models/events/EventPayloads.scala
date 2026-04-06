package repcheck.pipeline.models.events

import java.time.Instant
import java.util.UUID

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class BillTextAvailableEvent(
  billId: String,
  congress: Int,
  textUrl: String,
  textFormat: String,
  versionCode: String,
  previousVersionCode: Option[String],
)

object BillTextAvailableEvent {
  implicit val encoder: Encoder[BillTextAvailableEvent] = deriveEncoder[BillTextAvailableEvent]
  implicit val decoder: Decoder[BillTextAvailableEvent] = deriveDecoder[BillTextAvailableEvent]
}

final case class BillTextIngestedEvent(
  billId: String,
  versionId: UUID,
  congress: Int,
  versionCode: String,
  previousVersionCode: Option[String],
  committeeCode: Option[String],
)

object BillTextIngestedEvent {
  implicit val encoder: Encoder[BillTextIngestedEvent] = deriveEncoder[BillTextIngestedEvent]
  implicit val decoder: Decoder[BillTextIngestedEvent] = deriveDecoder[BillTextIngestedEvent]
}

final case class DecompositionCompletedEvent(
  billId: String,
  versionId: UUID,
  conceptGroupCount: Int,
  sectionCount: Int,
)

object DecompositionCompletedEvent {
  implicit val encoder: Encoder[DecompositionCompletedEvent] = deriveEncoder[DecompositionCompletedEvent]
  implicit val decoder: Decoder[DecompositionCompletedEvent] = deriveDecoder[DecompositionCompletedEvent]
}

final case class VoteRecordedEvent(
  voteId: String,
  billId: Option[String],
  chamber: String,
  date: Instant,
  congress: Int,
  isUpdate: Boolean,
)

object VoteRecordedEvent {
  implicit val encoder: Encoder[VoteRecordedEvent] = deriveEncoder[VoteRecordedEvent]
  implicit val decoder: Decoder[VoteRecordedEvent] = deriveDecoder[VoteRecordedEvent]
}

final case class AnalysisCompletedEvent(
  billId: String,
  analysisId: UUID,
  topics: List[String],
  passesExecuted: List[Int],
  modelUsed: String,
)

object AnalysisCompletedEvent {
  implicit val encoder: Encoder[AnalysisCompletedEvent] = deriveEncoder[AnalysisCompletedEvent]
  implicit val decoder: Decoder[AnalysisCompletedEvent] = deriveDecoder[AnalysisCompletedEvent]
}

final case class UserProfileUpdatedEvent(
  userId: UUID,
  topicsChanged: List[String],
)

object UserProfileUpdatedEvent {
  implicit val encoder: Encoder[UserProfileUpdatedEvent] = deriveEncoder[UserProfileUpdatedEvent]
  implicit val decoder: Decoder[UserProfileUpdatedEvent] = deriveDecoder[UserProfileUpdatedEvent]
}

final case class MemberUpdatedEvent(
  memberId: String
)

object MemberUpdatedEvent {
  implicit val encoder: Encoder[MemberUpdatedEvent] = deriveEncoder[MemberUpdatedEvent]
  implicit val decoder: Decoder[MemberUpdatedEvent] = deriveDecoder[MemberUpdatedEvent]
}

final case class ScoringUserRequestedEvent(
  userId: UUID,
  requestId: UUID,
  source: String,
)

object ScoringUserRequestedEvent {
  implicit val encoder: Encoder[ScoringUserRequestedEvent] = deriveEncoder[ScoringUserRequestedEvent]
  implicit val decoder: Decoder[ScoringUserRequestedEvent] = deriveDecoder[ScoringUserRequestedEvent]
}

final case class ScoringUserCompletedEvent(
  userId: UUID,
  requestId: UUID,
  memberScoreCount: Int,
  status: String,
)

object ScoringUserCompletedEvent {
  implicit val encoder: Encoder[ScoringUserCompletedEvent] = deriveEncoder[ScoringUserCompletedEvent]
  implicit val decoder: Decoder[ScoringUserCompletedEvent] = deriveDecoder[ScoringUserCompletedEvent]
}

final case class DailyIngestionStartEvent(
  date: String,
  congress: Int,
)

object DailyIngestionStartEvent {
  implicit val encoder: Encoder[DailyIngestionStartEvent] = deriveEncoder[DailyIngestionStartEvent]
  implicit val decoder: Decoder[DailyIngestionStartEvent] = deriveDecoder[DailyIngestionStartEvent]
}
