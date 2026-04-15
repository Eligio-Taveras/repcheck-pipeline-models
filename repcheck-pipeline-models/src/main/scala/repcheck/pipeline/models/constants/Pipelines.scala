package repcheck.pipeline.models.constants

/**
 * Identity constants for the 7 independent pipelines.
 *
 * Used as `workflow_run_steps.step_name` and in workflow definitions. Each pipeline application hardcodes its own
 * `Pipelines.*` constant — the identity is never passed as a CLI arg.
 */
object Pipelines {
  val Bills: String           = "bills-pipeline"
  val Members: String         = "members-pipeline"
  val Votes: String           = "votes-pipeline"
  val Amendments: String      = "amendments-pipeline"
  val LlmBillAnalysis: String = "llm-bill-analysis-pipeline"
  val MemberProfiling: String = "member-profiling-pipeline"
  val UserScoring: String     = "user-scoring-pipeline"
}
