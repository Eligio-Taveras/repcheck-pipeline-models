package repcheck.pipeline.models.workflow

import java.time.Instant
import java.util.UUID

import io.circe.parser.decode
import io.circe.syntax._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.pipeline.models.workflow.state._

class WorkflowStateSpec extends AnyFlatSpec with Matchers {

  // ── WorkflowRunStatus Circe ──

  "WorkflowRunStatus" should "encode Pending" in {
    WorkflowRunStatus.Pending.asJson.noSpaces shouldBe "\"Pending\""
  }

  it should "encode Running" in {
    WorkflowRunStatus.Running.asJson.noSpaces shouldBe "\"Running\""
  }

  it should "encode Completed" in {
    WorkflowRunStatus.Completed.asJson.noSpaces shouldBe "\"Completed\""
  }

  it should "encode CompletedWithErrors" in {
    WorkflowRunStatus.CompletedWithErrors.asJson.noSpaces shouldBe "\"CompletedWithErrors\""
  }

  it should "encode Failed" in {
    WorkflowRunStatus.Failed.asJson.noSpaces shouldBe "\"Failed\""
  }

  it should "decode all variants" in {
    decode[WorkflowRunStatus]("\"Pending\"") shouldBe Right(WorkflowRunStatus.Pending)
    decode[WorkflowRunStatus]("\"Running\"") shouldBe Right(WorkflowRunStatus.Running)
    decode[WorkflowRunStatus]("\"Completed\"") shouldBe Right(WorkflowRunStatus.Completed)
    decode[WorkflowRunStatus]("\"CompletedWithErrors\"") shouldBe Right(WorkflowRunStatus.CompletedWithErrors)
    decode[WorkflowRunStatus]("\"Failed\"") shouldBe Right(WorkflowRunStatus.Failed)
  }

  it should "fail to decode unknown status" in {
    val result = decode[WorkflowRunStatus]("\"Cancelled\"")
    result.isLeft shouldBe true
  }

  // ── WorkflowStepStatus Circe ──

  "WorkflowStepStatus" should "encode all variants" in {
    WorkflowStepStatus.Pending.asJson.noSpaces shouldBe "\"Pending\""
    WorkflowStepStatus.Running.asJson.noSpaces shouldBe "\"Running\""
    WorkflowStepStatus.Completed.asJson.noSpaces shouldBe "\"Completed\""
    WorkflowStepStatus.Failed.asJson.noSpaces shouldBe "\"Failed\""
  }

  it should "decode all variants" in {
    decode[WorkflowStepStatus]("\"Pending\"") shouldBe Right(WorkflowStepStatus.Pending)
    decode[WorkflowStepStatus]("\"Running\"") shouldBe Right(WorkflowStepStatus.Running)
    decode[WorkflowStepStatus]("\"Completed\"") shouldBe Right(WorkflowStepStatus.Completed)
    decode[WorkflowStepStatus]("\"Failed\"") shouldBe Right(WorkflowStepStatus.Failed)
  }

  it should "fail to decode unknown status" in {
    val result = decode[WorkflowStepStatus]("\"Cancelled\"")
    result.isLeft shouldBe true
  }

  // ── WorkflowRunStatus.deriveFromSteps ──

  "WorkflowRunStatus.deriveFromSteps" should "return Completed when all steps are Completed" in {
    val statuses = List(WorkflowStepStatus.Completed, WorkflowStepStatus.Completed, WorkflowStepStatus.Completed)
    WorkflowRunStatus.deriveFromSteps(statuses) shouldBe WorkflowRunStatus.Completed
  }

  it should "return CompletedWithErrors when some Completed and some Failed" in {
    val statuses = List(WorkflowStepStatus.Completed, WorkflowStepStatus.Failed, WorkflowStepStatus.Completed)
    WorkflowRunStatus.deriveFromSteps(statuses) shouldBe WorkflowRunStatus.CompletedWithErrors
  }

  it should "return Pending when all steps are Pending" in {
    val statuses = List(WorkflowStepStatus.Pending, WorkflowStepStatus.Pending)
    WorkflowRunStatus.deriveFromSteps(statuses) shouldBe WorkflowRunStatus.Pending
  }

  it should "return Running when any step is Running" in {
    val statuses = List(WorkflowStepStatus.Running, WorkflowStepStatus.Pending, WorkflowStepStatus.Completed)
    WorkflowRunStatus.deriveFromSteps(statuses) shouldBe WorkflowRunStatus.Running
  }

  it should "return Failed when all non-Pending steps are Failed" in {
    val statuses = List(WorkflowStepStatus.Failed, WorkflowStepStatus.Failed)
    WorkflowRunStatus.deriveFromSteps(statuses) shouldBe WorkflowRunStatus.Failed
  }

  it should "return Pending for empty list" in {
    WorkflowRunStatus.deriveFromSteps(List.empty) shouldBe WorkflowRunStatus.Pending
  }

  // ── WorkflowRunDO Circe round-trip ──

  "WorkflowRunDO" should "round-trip through JSON" in {
    val runId = UUID.randomUUID()
    val now   = Instant.parse("2024-06-01T12:00:00Z")
    val dobj = WorkflowRunDO(
      workflowRunId = runId,
      workflowName = "daily-ingestion",
      workflowDefinitionPath = "gs://bucket/workflows/daily-ingestion.json",
      status = WorkflowRunStatus.Running,
      triggeredBy = "scheduler",
      startedAt = Some(now),
      completedAt = None,
      createdAt = now,
    )
    val json   = dobj.asJson.noSpaces
    val result = decode[WorkflowRunDO](json)
    result shouldBe Right(dobj)
  }

  // ── WorkflowRunStepDO Circe round-trip ──

  "WorkflowRunStepDO" should "round-trip through JSON with originalMessage" in {
    val runId      = UUID.randomUUID()
    val pipelineId = UUID.randomUUID()
    val now        = Instant.parse("2024-06-01T12:00:00Z")
    val dobj = WorkflowRunStepDO(
      workflowRunId = runId,
      stepName = "ingest-bills",
      status = WorkflowStepStatus.Completed,
      pipelineRunId = Some(pipelineId),
      retryCount = 1,
      maxRetries = 3,
      originalMessage = Some("""{"billId":"hr-1"}"""),
      startedAt = Some(now),
      completedAt = Some(now),
      errorMessage = None,
      createdAt = now,
      updatedAt = now,
    )
    val json   = dobj.asJson.noSpaces
    val result = decode[WorkflowRunStepDO](json)
    result shouldBe Right(dobj)
  }

  it should "round-trip through JSON with pipelineRunId = None" in {
    val runId = UUID.randomUUID()
    val now   = Instant.parse("2024-06-01T12:00:00Z")
    val dobj = WorkflowRunStepDO(
      workflowRunId = runId,
      stepName = "pending-step",
      status = WorkflowStepStatus.Pending,
      pipelineRunId = None,
      retryCount = 0,
      maxRetries = 3,
      originalMessage = None,
      startedAt = None,
      completedAt = None,
      errorMessage = None,
      createdAt = now,
      updatedAt = now,
    )
    val json   = dobj.asJson.noSpaces
    val result = decode[WorkflowRunStepDO](json)
    result shouldBe Right(dobj)
  }

  // ── Retry logic (criteria 19-21) ──

  private def failedStep(retryCount: Int, maxRetries: Int): WorkflowRunStepDO = {
    val now = Instant.parse("2024-06-01T12:00:00Z")
    WorkflowRunStepDO(
      workflowRunId = UUID.randomUUID(),
      stepName = "test-step",
      status = WorkflowStepStatus.Failed,
      pipelineRunId = None,
      retryCount = retryCount,
      maxRetries = maxRetries,
      originalMessage = Some("""{"billId":"hr-1"}"""),
      startedAt = Some(now),
      completedAt = Some(now),
      errorMessage = Some("transient error"),
      createdAt = now,
      updatedAt = now,
    )
  }

  "WorkflowRunStepDO.canRetry" should "return true when retryCount < maxRetries" in {
    val step = failedStep(retryCount = 1, maxRetries = 3)
    WorkflowRunStepDO.canRetry(step) shouldBe true
  }

  it should "return false when retryCount >= maxRetries" in {
    val step = failedStep(retryCount = 3, maxRetries = 3)
    WorkflowRunStepDO.canRetry(step) shouldBe false
  }

  it should "return false when retryCount exceeds maxRetries" in {
    val step = failedStep(retryCount = 5, maxRetries = 3)
    WorkflowRunStepDO.canRetry(step) shouldBe false
  }

  "WorkflowRunStepDO.applyRetry" should "increment retryCount and set Running when retries remain" in {
    val step   = failedStep(retryCount = 1, maxRetries = 3)
    val now    = Instant.parse("2024-06-02T08:00:00Z")
    val result = WorkflowRunStepDO.applyRetry(step, now)
    result.isRight shouldBe true
    result.foreach { retried =>
      retried.retryCount shouldBe 2
      retried.status shouldBe WorkflowStepStatus.Running
      retried.updatedAt shouldBe now
    }
  }

  it should "return Left when retries are exhausted" in {
    val step   = failedStep(retryCount = 3, maxRetries = 3)
    val now    = Instant.parse("2024-06-02T08:00:00Z")
    val result = WorkflowRunStepDO.applyRetry(step, now)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("exhausted retries"))
  }

  "Failed → Running transition" should "be valid when a failed step is retried" in {
    val step = failedStep(retryCount = 1, maxRetries = 3)
    step.status shouldBe WorkflowStepStatus.Failed
    val now     = Instant.parse("2024-06-02T08:00:00Z")
    val retried = WorkflowRunStepDO.applyRetry(step, now)
    retried.isRight shouldBe true
    retried.foreach { r =>
      r.status shouldBe WorkflowStepStatus.Running
      r.retryCount shouldBe step.retryCount + 1
    }
  }

  // ── Doobie instances compile check ──

  "Doobie instances" should "resolve Get/Put for WorkflowRunStatus" in {
    implicitly[doobie.Get[WorkflowRunStatus]]
    implicitly[doobie.Put[WorkflowRunStatus]]
    succeed
  }

  it should "resolve Get/Put for WorkflowStepStatus" in {
    implicitly[doobie.Get[WorkflowStepStatus]]
    implicitly[doobie.Put[WorkflowStepStatus]]
    succeed
  }

  it should "resolve Read/Write for WorkflowRunDO" in {
    import doobie.implicits._
    import doobie.postgres.implicits._

    implicitly[doobie.Read[WorkflowRunDO]]
    implicitly[doobie.Write[WorkflowRunDO]]
    succeed
  }

  it should "resolve Read/Write for WorkflowRunStepDO" in {
    import doobie.implicits._
    import doobie.postgres.implicits._

    implicitly[doobie.Read[WorkflowRunStepDO]]
    implicitly[doobie.Write[WorkflowRunStepDO]]
    succeed
  }

}
