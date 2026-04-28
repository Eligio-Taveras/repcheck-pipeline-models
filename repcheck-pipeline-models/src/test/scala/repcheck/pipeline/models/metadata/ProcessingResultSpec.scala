package repcheck.pipeline.models.metadata

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.pipeline.models.workflow.state.WorkflowStepStatus

class ProcessingResultSpec extends AnyFlatSpec with Matchers {

  private val testStepRunId     = 42L
  private val testCorrelationId = UUID.fromString("00000000-0000-0000-0000-000000000002")
  private val testEntityType    = "bill"

  // ── isSucceeded / isFailed / isSkipped helpers ──

  "ProcessingResult.Succeeded" should "report isSucceeded = true" in {
    val result = ProcessingResult.Succeeded("entity-1")
    val _      = result.isSucceeded shouldBe true
    val _      = result.isFailed shouldBe false
    result.isSkipped shouldBe false
  }

  "ProcessingResult.Failed" should "report isFailed = true" in {
    val result = ProcessingResult.Failed("entity-2", "timeout")
    val _      = result.isSucceeded shouldBe false
    val _      = result.isFailed shouldBe true
    result.isSkipped shouldBe false
  }

  "ProcessingResult.Skipped" should "report isSkipped = true" in {
    val result = ProcessingResult.Skipped("entity-3", "duplicate")
    val _      = result.isSucceeded shouldBe false
    val _      = result.isFailed shouldBe false
    result.isSkipped shouldBe true
  }

  // ── toResultDO ──

  "ProcessingResult.Succeeded.toResultDO" should "produce correct DO fields" in {
    val result = ProcessingResult.Succeeded("entity-1", eventEmitted = true)
    val resultDO = result
      .toResultDO[IO](testStepRunId, testCorrelationId, testEntityType)
      .unsafeRunSync()

    val _ = resultDO.stepRunId shouldBe testStepRunId
    val _ = resultDO.correlationId shouldBe testCorrelationId
    val _ = resultDO.entityType shouldBe testEntityType
    val _ = resultDO.entityId shouldBe "entity-1"
    val _ = resultDO.status shouldBe ResultStatus.Succeeded
    val _ = resultDO.errorMessage shouldBe None
    resultDO.errorClass shouldBe None
  }

  "ProcessingResult.Failed.toResultDO" should "produce correct DO fields" in {
    val result = ProcessingResult.Failed("entity-2", "Connection refused")
    val resultDO = result
      .toResultDO[IO](testStepRunId, testCorrelationId, testEntityType)
      .unsafeRunSync()

    val _ = resultDO.stepRunId shouldBe testStepRunId
    val _ = resultDO.correlationId shouldBe testCorrelationId
    val _ = resultDO.entityType shouldBe testEntityType
    val _ = resultDO.entityId shouldBe "entity-2"
    val _ = resultDO.status shouldBe ResultStatus.Failed
    val _ = resultDO.errorMessage shouldBe Some("Connection refused")
    resultDO.errorClass shouldBe Some("Systemic")
  }

  "ProcessingResult.Failed.toResultDO" should "use custom errorClass when provided" in {
    val result = ProcessingResult.Failed("entity-4", "Rate limited", errorClass = "Transient")
    val resultDO = result
      .toResultDO[IO](testStepRunId, testCorrelationId, testEntityType)
      .unsafeRunSync()

    val _ = resultDO.status shouldBe ResultStatus.Failed
    val _ = resultDO.errorMessage shouldBe Some("Rate limited")
    resultDO.errorClass shouldBe Some("Transient")
  }

  "ProcessingResult.Skipped.toResultDO" should "produce correct DO fields" in {
    val result = ProcessingResult.Skipped("entity-3", "Already processed")
    val resultDO = result
      .toResultDO[IO](testStepRunId, testCorrelationId, testEntityType)
      .unsafeRunSync()

    val _ = resultDO.stepRunId shouldBe testStepRunId
    val _ = resultDO.correlationId shouldBe testCorrelationId
    val _ = resultDO.entityType shouldBe testEntityType
    val _ = resultDO.entityId shouldBe "entity-3"
    val _ = resultDO.status shouldBe ResultStatus.Skipped
    val _ = resultDO.errorMessage shouldBe Some("Already processed")
    resultDO.errorClass shouldBe None
  }

  // ── StepRunSummary.fromResults status derivation ──

  private val now       = Instant.parse("2024-06-01T10:00:00Z")
  private val completed = Instant.parse("2024-06-01T10:30:00Z")

  "StepRunSummary.fromResults" should "derive Completed when all results succeeded" in {
    val results = List(
      ProcessingResult.Succeeded("e1"),
      ProcessingResult.Succeeded("e2"),
      ProcessingResult.Succeeded("e3"),
    )
    val summary = StepRunSummary.fromResults(testStepRunId, "bills-pipeline", now, completed, results)
    val _       = summary.status shouldBe WorkflowStepStatus.Completed
    val _       = summary.itemsProcessed shouldBe 3
    val _       = summary.itemsSucceeded shouldBe 3
    summary.itemsFailed shouldBe 0
  }

  it should "derive CompletedWithErrors for mixed results" in {
    val results = List(
      ProcessingResult.Succeeded("e1"),
      ProcessingResult.Failed("e2", "timeout"),
      ProcessingResult.Succeeded("e3"),
    )
    val summary = StepRunSummary.fromResults(testStepRunId, "bills-pipeline", now, completed, results)
    val _       = summary.status shouldBe WorkflowStepStatus.CompletedWithErrors
    val _       = summary.itemsProcessed shouldBe 3
    val _       = summary.itemsSucceeded shouldBe 2
    summary.itemsFailed shouldBe 1
  }

  it should "derive Failed when all results failed" in {
    val results = List(
      ProcessingResult.Failed("e1", "error A"),
      ProcessingResult.Failed("e2", "error B"),
    )
    val summary = StepRunSummary.fromResults(testStepRunId, "bills-pipeline", now, completed, results)
    val _       = summary.status shouldBe WorkflowStepStatus.Failed
    val _       = summary.itemsProcessed shouldBe 2
    val _       = summary.itemsSucceeded shouldBe 0
    summary.itemsFailed shouldBe 2
  }

  it should "derive Completed for empty results" in {
    val summary =
      StepRunSummary.fromResults(testStepRunId, "bills-pipeline", now, completed, List.empty)
    val _ = summary.status shouldBe WorkflowStepStatus.Completed
    val _ = summary.itemsProcessed shouldBe 0
    val _ = summary.itemsSucceeded shouldBe 0
    summary.itemsFailed shouldBe 0
  }

  it should "compute errorCounts from failed results" in {
    val results = List(
      ProcessingResult.Failed("e1", "timeout"),
      ProcessingResult.Failed("e2", "timeout"),
      ProcessingResult.Failed("e3", "auth failure"),
    )
    val summary = StepRunSummary.fromResults(testStepRunId, "bills-pipeline", now, completed, results)
    summary.errorCounts shouldBe Map("timeout" -> 2, "auth failure" -> 1)
  }

  it should "roll skipped results into itemsSucceeded (idempotent skip is a successful no-op)" in {
    val results = List(
      ProcessingResult.Succeeded("e1"),
      ProcessingResult.Skipped("e2", "duplicate"),
    )
    val summary = StepRunSummary.fromResults(testStepRunId, "bills-pipeline", now, completed, results)
    val _       = summary.status shouldBe WorkflowStepStatus.Completed
    val _       = summary.itemsProcessed shouldBe 2
    val _       = summary.itemsSucceeded shouldBe 2
    summary.itemsFailed shouldBe 0
  }

}
