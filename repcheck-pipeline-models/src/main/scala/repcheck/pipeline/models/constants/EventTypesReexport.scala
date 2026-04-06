package repcheck.pipeline.models.constants

import repcheck.pipeline.models.events.EventTypes

object PipelineEventTypes {
  val BillTextAvailable: String      = EventTypes.BillTextAvailable
  val BillTextIngested: String       = EventTypes.BillTextIngested
  val DecompositionCompleted: String = EventTypes.DecompositionCompleted
  val VoteRecorded: String           = EventTypes.VoteRecorded
  val AnalysisCompleted: String      = EventTypes.AnalysisCompleted
  val MemberUpdated: String          = EventTypes.MemberUpdated
  val UserProfileUpdated: String     = EventTypes.UserProfileUpdated
  val ScoringUserRequested: String   = EventTypes.ScoringUserRequested
  val ScoringUserCompleted: String   = EventTypes.ScoringUserCompleted
  val DailyIngestionStart: String    = EventTypes.DailyIngestionStart
}
