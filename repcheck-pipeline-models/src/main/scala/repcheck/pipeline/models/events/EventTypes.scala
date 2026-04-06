package repcheck.pipeline.models.events

object EventTypes {
  val BillTextAvailable: String      = "bill.text.available"
  val BillTextIngested: String       = "bill.text.ingested"
  val DecompositionCompleted: String = "bill.decomposition.completed"
  val VoteRecorded: String           = "vote.recorded"
  val AnalysisCompleted: String      = "analysis.completed"
  val MemberUpdated: String          = "member.updated"
  val UserProfileUpdated: String     = "user.profile.updated"
  val ScoringUserRequested: String   = "scoring.user.requested"
  val ScoringUserCompleted: String   = "scoring.user.completed"
  val DailyIngestionStart: String    = "daily.ingestion.start"

  val allEventTypes: Set[String] = Set(
    BillTextAvailable,
    BillTextIngested,
    DecompositionCompleted,
    VoteRecorded,
    AnalysisCompleted,
    MemberUpdated,
    UserProfileUpdated,
    ScoringUserRequested,
    ScoringUserCompleted,
    DailyIngestionStart,
  )

}
