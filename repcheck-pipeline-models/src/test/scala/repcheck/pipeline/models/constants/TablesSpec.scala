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
    Tables.BillSummaries,
    Tables.BillTextVersions,
    Tables.Votes,
    Tables.VotePositions,
    Tables.VoteHistory,
    Tables.VoteHistoryPositions,
    Tables.Amendments,
    Tables.AmendmentTextVersions,
    Tables.AmendmentTextChunks,
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
    Tables.ProcessingResults,
    Tables.WorkflowRuns,
    Tables.WorkflowRunSteps,
    Tables.UsStates,
    Tables.EventLog,
  )

  "Tables" should "have exactly 58 constants" in {
    allTableNames.size shouldBe 58
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
    val _ = Tables.BillSummaries shouldBe "bill_summaries"
    val _ = Tables.Votes shouldBe "votes"
    val _ = Tables.Amendments shouldBe "amendments"
    val _ = Tables.AmendmentTextVersions shouldBe "amendment_text_versions"
    val _ = Tables.AmendmentTextChunks shouldBe "amendment_text_chunks"
    val _ = Tables.Committees shouldBe "committees"
    val _ = Tables.Scores shouldBe "scores"
    val _ = Tables.Users shouldBe "users"
    val _ = Tables.ProcessingResults shouldBe "processing_results"
    val _ = Tables.WorkflowRuns shouldBe "workflow_runs"
    val _ = Tables.WorkflowRunSteps shouldBe "workflow_run_steps"
    val _ = Tables.EventLog shouldBe "event_log"
    val _ = Tables.LisMembers shouldBe "lis_members"
    Tables.MemberLisMapping shouldBe "member_lis_mapping"
  }

  // ── Pipelines constants ──

  private val allPipelineNames: List[String] = List(
    Pipelines.Bills,
    Pipelines.Members,
    Pipelines.Votes,
    Pipelines.Amendments,
    Pipelines.LlmBillAnalysis,
    Pipelines.MemberProfiling,
    Pipelines.UserScoring,
  )

  "Pipelines" should "have exactly 7 constants" in {
    allPipelineNames.size shouldBe 7
  }

  it should "have all non-empty values ending in '-pipeline'" in {
    allPipelineNames.foreach { name =>
      val _ = name.nonEmpty shouldBe true
      name should endWith("-pipeline")
    }
  }

  it should "have no duplicate pipeline name values" in {
    allPipelineNames.distinct.size shouldBe allPipelineNames.size
  }

  it should "match expected pipeline identity values" in {
    val _ = Pipelines.Bills shouldBe "bills-pipeline"
    val _ = Pipelines.Members shouldBe "members-pipeline"
    val _ = Pipelines.Votes shouldBe "votes-pipeline"
    val _ = Pipelines.Amendments shouldBe "amendments-pipeline"
    val _ = Pipelines.LlmBillAnalysis shouldBe "llm-bill-analysis-pipeline"
    val _ = Pipelines.MemberProfiling shouldBe "member-profiling-pipeline"
    Pipelines.UserScoring shouldBe "user-scoring-pipeline"
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
    val _ = PipelineEventTypes.DailyIngestionStart shouldBe EventTypes.DailyIngestionStart
    val _ = PipelineEventTypes.AmendmentTextAvailable shouldBe EventTypes.AmendmentTextAvailable
    PipelineEventTypes.CommitteeMembershipRefreshed shouldBe EventTypes.CommitteeMembershipRefreshed
  }

  // ── Constants object ──

  "Constants.MinAmendmentCongress" should "be 102" in {
    Constants.MinAmendmentCongress shouldBe 102
  }

}
