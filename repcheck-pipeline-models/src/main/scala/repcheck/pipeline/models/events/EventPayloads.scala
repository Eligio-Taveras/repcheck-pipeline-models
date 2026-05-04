package repcheck.pipeline.models.events

import java.time.Instant
import java.util.UUID

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import repcheck.shared.models.congress.amendment.AmendmentType

final case class BillTextAvailableEvent(
  naturalKey: String,
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

/**
 * Published when a new amendment text version is detected by the amendments-pipeline. Downstream consumers (e.g.
 * amendment-text-pipeline) fetch the URL and persist the text.
 *
 * Completion is signaled by `amendment_text_versions.fetched_at IS NOT NULL`; there is no separate
 * AmendmentTextIngested event.
 */
final case class AmendmentTextAvailableEvent(
  amendmentId: Long,
  naturalKey: String,
  congress: Int,
  amendmentType: AmendmentType,
  number: String,
  versionTypeCode: String,
  formatType: String,
  url: String,
  publishedDate: Option[Instant],
  correlationId: UUID,
)

object AmendmentTextAvailableEvent {
  implicit val encoder: Encoder[AmendmentTextAvailableEvent] = deriveEncoder[AmendmentTextAvailableEvent]
  implicit val decoder: Decoder[AmendmentTextAvailableEvent] = deriveDecoder[AmendmentTextAvailableEvent]
}

final case class BillTextIngestedEvent(
  naturalKey: String,
  versionId: UUID,
  congress: Int,
  versionCode: String,
  previousVersionCode: Option[String],
)

object BillTextIngestedEvent {
  implicit val encoder: Encoder[BillTextIngestedEvent] = deriveEncoder[BillTextIngestedEvent]
  implicit val decoder: Decoder[BillTextIngestedEvent] = deriveDecoder[BillTextIngestedEvent]
}

final case class DecompositionCompletedEvent(
  naturalKey: String,
  versionId: UUID,
  conceptGroupCount: Int,
  sectionCount: Int,
)

object DecompositionCompletedEvent {
  implicit val encoder: Encoder[DecompositionCompletedEvent] = deriveEncoder[DecompositionCompletedEvent]
  implicit val decoder: Decoder[DecompositionCompletedEvent] = deriveDecoder[DecompositionCompletedEvent]
}

/**
 * Published when a vote is recorded or updated by the votes-pipeline.
 *
 * Carries canonical natural keys for both the vote itself and, when applicable, the bill the vote is linked to.
 * Downstream consumers (e.g. the scoring engine) use these natural keys to look up the vote and bill rows without
 * reinventing the key formats.
 *
 * @param voteNaturalKey
 *   Canonical natural key for the vote. Format: `s"$congress-$chamber-$session-$rollCallNumber"`, where `chamber`
 *   preserves Congress.gov API casing ("House" / "Senate"). Example: `"119-House-1-17"`. Every vote has this — never
 *   empty.
 * @param billNaturalKey
 *   Canonical natural key for the linked bill when the vote is bill-linked; `None` for procedural votes (motions to
 *   adjourn, quorum calls, etc.). Format matches the natural-key format used by the bills-pipeline (e.g.
 *   `"119-HR-30"`). Pipeline-models treats this as an opaque round-trip string.
 * @param chamber
 *   Chamber that cast the vote, in Congress.gov API casing ("House" / "Senate").
 * @param date
 *   Timestamp of the roll-call vote.
 * @param congress
 *   Congress number (e.g. 119).
 * @param isUpdate
 *   `true` when this event reports an update to a previously published vote; `false` for first-time publication.
 */
final case class VoteRecordedEvent(
  voteNaturalKey: String,
  billNaturalKey: Option[String],
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
  naturalKey: String,
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
