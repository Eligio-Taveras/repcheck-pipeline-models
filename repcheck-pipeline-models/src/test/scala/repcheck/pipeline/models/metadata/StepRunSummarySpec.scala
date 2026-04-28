package repcheck.pipeline.models.metadata

import java.time.Instant

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.pipeline.models.workflow.state.WorkflowStepStatus

/**
 * Spec for [[StepRunSummary.fromResults]] — the rollup that downstream consumers (`workflow_run_steps` table, the
 * `Pipeline completed: ...` log line, dashboards) read for "did this run do its job?".
 *
 * Key semantic: a `ProcessingResult.Skipped` is a successful no-op (e.g., the bill-text-pipeline's idempotent skip when
 * an already-completed `bill_text_versions` row matches a re-delivered Pub/Sub event). Skipped rolls into
 * `itemsSucceeded` so a healthy idempotent skip isn't reported as something other than success. `itemsFailed` stays
 * strict — only `Failed` increments it.
 */
class StepRunSummarySpec extends AnyFlatSpec with Matchers {

  private val stepRunId   = 1L
  private val stepName    = "bill-text-pipeline"
  private val startedAt   = Instant.parse("2026-01-01T00:00:00Z")
  private val completedAt = Instant.parse("2026-01-01T00:01:00Z")

  private def summary(results: List[ProcessingResult]): StepRunSummary =
    StepRunSummary.fromResults(stepRunId, stepName, startedAt, completedAt, results)

  "fromResults" should "count Succeeded results in itemsSucceeded" in {
    val s = summary(List(ProcessingResult.Succeeded("a"), ProcessingResult.Succeeded("b")))
    val _ = s.itemsProcessed shouldBe 2
    val _ = s.itemsSucceeded shouldBe 2
    s.itemsFailed shouldBe 0
  }

  it should "count Skipped results as Succeeded (idempotent skip is a successful no-op)" in {
    val s =
      summary(List(ProcessingResult.Skipped("a", "already-processed"), ProcessingResult.Skipped("b", "duplicate")))
    val _ = s.itemsProcessed shouldBe 2
    val _ = s.itemsSucceeded shouldBe 2
    s.itemsFailed shouldBe 0
  }

  it should "sum Succeeded + Skipped into itemsSucceeded for mixed runs" in {
    val s = summary(
      List(
        ProcessingResult.Succeeded("a"),
        ProcessingResult.Skipped("b", "already-processed"),
        ProcessingResult.Succeeded("c"),
        ProcessingResult.Skipped("d", "already-processed"),
      )
    )
    val _ = s.itemsProcessed shouldBe 4
    val _ = s.itemsSucceeded shouldBe 4
    s.itemsFailed shouldBe 0
  }

  it should "leave itemsFailed strict — only Failed increments it" in {
    val s = summary(
      List(
        ProcessingResult.Succeeded("a"),
        ProcessingResult.Skipped("b", "already-processed"),
        ProcessingResult.Failed("c", "timeout"),
      )
    )
    val _ = s.itemsProcessed shouldBe 3
    val _ = s.itemsSucceeded shouldBe 2 // Succeeded(a) + Skipped(b)
    s.itemsFailed shouldBe 1 // only Failed(c)
  }

  it should "report Completed status when a run is all-Skipped (no failures)" in {
    val s = summary(List.fill(3)(ProcessingResult.Skipped("entity", "duplicate")))
    s.status shouldBe WorkflowStepStatus.Completed
  }

  it should "report CompletedWithErrors when any Failed but not all" in {
    val s = summary(
      List(
        ProcessingResult.Succeeded("a"),
        ProcessingResult.Skipped("b", "already-processed"),
        ProcessingResult.Failed("c", "timeout"),
      )
    )
    s.status shouldBe WorkflowStepStatus.CompletedWithErrors
  }

  it should "report Failed status when every result is Failed" in {
    val s = summary(List(ProcessingResult.Failed("a", "boom"), ProcessingResult.Failed("b", "boom")))
    s.status shouldBe WorkflowStepStatus.Failed
  }

  it should "report Completed status when results are empty" in {
    val s = summary(Nil)
    val _ = s.status shouldBe WorkflowStepStatus.Completed
    val _ = s.itemsProcessed shouldBe 0
    val _ = s.itemsSucceeded shouldBe 0
    s.itemsFailed shouldBe 0
  }

  it should "aggregate errorCounts only from Failed results — Skipped reasons are not errors" in {
    val s = summary(
      List(
        ProcessingResult.Skipped("a", "already-processed"), // skip reason should NOT appear in errorCounts
        ProcessingResult.Failed("b", "HTTP timeout"),
        ProcessingResult.Failed("c", "HTTP timeout"),
        ProcessingResult.Failed("d", "DB conflict"),
      )
    )
    val _ = s.errorCounts.get("already-processed") shouldBe None
    val _ = s.errorCounts.get("HTTP timeout") shouldBe Some(2)
    s.errorCounts.get("DB conflict") shouldBe Some(1)
  }

  it should "preserve startedAt, completedAt, stepRunId, stepName" in {
    val s = summary(List(ProcessingResult.Succeeded("a")))
    val _ = s.stepRunId shouldBe stepRunId
    val _ = s.stepName shouldBe stepName
    val _ = s.startedAt shouldBe startedAt
    s.completedAt shouldBe completedAt
  }

}
