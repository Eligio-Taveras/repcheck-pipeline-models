package repcheck.pipeline.models.constants

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.pipeline.models.events.EventTypes

class TablesSpec extends AnyFlatSpec with Matchers {

  private val allTableNames: List[String] = List(
    Tables.Members,
    Tables.MemberTerms,
    Tables.MemberPartyHistory,
    Tables.Bills,
    Tables.BillCosponsors,
    Tables.BillSubjects,
    Tables.BillTextVersions,
    Tables.Votes,
    Tables.VotePositions,
    Tables.VoteHistory,
    Tables.VoteHistoryPositions,
    Tables.Amendments,
    Tables.Committees,
    Tables.CommitteeMembers,
    Tables.BillCommitteeReferrals,
    Tables.MemberHistory,
    Tables.MemberTermHistory,
    Tables.LisMembers,
    Tables.MemberLisMapping,
    Tables.BillTextSections,
    Tables.BillConceptGroups,
    Tables.BillConceptGroupSections,
    Tables.BillAnalyses,
    Tables.BillConceptSummaries,
    Tables.BillAnalysisTopics,
    Tables.BillFindings,
    Tables.BillFiscalEstimates,
    Tables.AmendmentFindings,
    Tables.FindingTypes,
    Tables.Scores,
    Tables.ScoreTopics,
    Tables.ScoreCongress,
    Tables.ScoreCongressTopics,
    Tables.ScoreHistory,
    Tables.ScoreHistoryCongress,
    Tables.ScoreHistoryCongressTopics,
    Tables.ScoreHistoryHighlights,
    Tables.MemberBillStances,
    Tables.Users,
    Tables.UserPreferences,
    Tables.QaQuestions,
    Tables.QaQuestionTopics,
    Tables.QaAnswerOptions,
    Tables.QaUserResponses,
    Tables.UserTopicPriorities,
    Tables.UserLegislatorPairings,
    Tables.MemberBillStanceTopics,
    Tables.UserBillAlignments,
    Tables.UserAmendmentAlignments,
    Tables.StanceMaterializationStatus,
    Tables.PipelineRuns,
    Tables.ProcessingResults,
    Tables.WorkflowRuns,
    Tables.WorkflowRunSteps,
    Tables.UsStates,
    Tables.EventLog,
  )

  "Tables" should "have exactly 55 constants" in {
    allTableNames.size shouldBe 56
  }

  it should "have all non-empty table name values" in {
    allTableNames.forall(_.nonEmpty) shouldBe true
  }

  it should "have no duplicate table name values" in {
    allTableNames.distinct.size shouldBe allTableNames.size
  }

  it should "match expected values for key constants" in {
    val _ = Tables.Members shouldBe "members"
    val _ = Tables.Bills shouldBe "bills"
    val _ = Tables.Votes shouldBe "votes"
    val _ = Tables.Amendments shouldBe "amendments"
    val _ = Tables.Committees shouldBe "committees"
    val _ = Tables.Scores shouldBe "scores"
    val _ = Tables.Users shouldBe "users"
    val _ = Tables.PipelineRuns shouldBe "pipeline_runs"
    val _ = Tables.EventLog shouldBe "event_log"
    val _ = Tables.LisMembers shouldBe "lis_members"
    Tables.MemberLisMapping shouldBe "member_lis_mapping"
  }

  // ── PipelineEventTypes re-export verification ──

  "PipelineEventTypes" should "re-export all EventTypes values correctly" in {
    val _ = PipelineEventTypes.BillTextAvailable shouldBe EventTypes.BillTextAvailable
    val _ = PipelineEventTypes.BillTextIngested shouldBe EventTypes.BillTextIngested
    val _ = PipelineEventTypes.DecompositionCompleted shouldBe EventTypes.DecompositionCompleted
    val _ = PipelineEventTypes.VoteRecorded shouldBe EventTypes.VoteRecorded
    val _ = PipelineEventTypes.AnalysisCompleted shouldBe EventTypes.AnalysisCompleted
    val _ = PipelineEventTypes.MemberUpdated shouldBe EventTypes.MemberUpdated
    val _ = PipelineEventTypes.UserProfileUpdated shouldBe EventTypes.UserProfileUpdated
    val _ = PipelineEventTypes.ScoringUserRequested shouldBe EventTypes.ScoringUserRequested
    val _ = PipelineEventTypes.ScoringUserCompleted shouldBe EventTypes.ScoringUserCompleted
    PipelineEventTypes.DailyIngestionStart shouldBe EventTypes.DailyIngestionStart
  }

}
