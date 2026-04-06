package repcheck.pipeline.models.metadata

import java.time.Instant
import java.util.UUID

import io.circe.parser.decode
import io.circe.syntax._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PipelineTrackingSpec extends AnyFlatSpec with Matchers {

  // ── PipelineStatus Circe round-trip ──

  "PipelineStatus" should "round-trip Running through JSON" in {
    val original: PipelineStatus = PipelineStatus.Running
    val json                     = original.asJson
    json.as[PipelineStatus] shouldBe Right(original)
  }

  it should "round-trip Completed through JSON" in {
    val original: PipelineStatus = PipelineStatus.Completed
    val json                     = original.asJson
    json.as[PipelineStatus] shouldBe Right(original)
  }

  it should "round-trip CompletedWithErrors through JSON" in {
    val original: PipelineStatus = PipelineStatus.CompletedWithErrors
    val json                     = original.asJson
    json.as[PipelineStatus] shouldBe Right(original)
  }

  it should "round-trip Failed through JSON" in {
    val original: PipelineStatus = PipelineStatus.Failed
    val json                     = original.asJson
    json.as[PipelineStatus] shouldBe Right(original)
  }

  it should "parse case-insensitively via fromString" in {
    PipelineStatus.fromString("running") shouldBe Right(PipelineStatus.Running)
    PipelineStatus.fromString("COMPLETED") shouldBe Right(PipelineStatus.Completed)
    PipelineStatus.fromString("completedwitherrors") shouldBe Right(PipelineStatus.CompletedWithErrors)
  }

  it should "fail to decode unknown string" in {
    val result = decode[PipelineStatus]("\"Bogus\"")
    result.isLeft shouldBe true
  }

  // ── ResultStatus Circe round-trip ──

  "ResultStatus" should "round-trip Succeeded through JSON" in {
    val original: ResultStatus = ResultStatus.Succeeded
    val json                   = original.asJson
    json.as[ResultStatus] shouldBe Right(original)
  }

  it should "round-trip Failed through JSON" in {
    val original: ResultStatus = ResultStatus.Failed
    val json                   = original.asJson
    json.as[ResultStatus] shouldBe Right(original)
  }

  it should "round-trip Skipped through JSON" in {
    val original: ResultStatus = ResultStatus.Skipped
    val json                   = original.asJson
    json.as[ResultStatus] shouldBe Right(original)
  }

  it should "parse case-insensitively via fromString" in {
    ResultStatus.fromString("succeeded") shouldBe Right(ResultStatus.Succeeded)
    ResultStatus.fromString("FAILED") shouldBe Right(ResultStatus.Failed)
    ResultStatus.fromString("Skipped") shouldBe Right(ResultStatus.Skipped)
  }

  it should "fail to decode unknown string" in {
    val result = decode[ResultStatus]("\"Nope\"")
    result.isLeft shouldBe true
  }

  // ── PipelineTrigger Circe round-trip ──

  "PipelineTrigger" should "round-trip Scheduled through JSON" in {
    val original: PipelineTrigger = PipelineTrigger.Scheduled
    val json                      = original.asJson
    json.as[PipelineTrigger] shouldBe Right(original)
  }

  it should "round-trip Manual through JSON" in {
    val original: PipelineTrigger = PipelineTrigger.Manual
    val json                      = original.asJson
    json.as[PipelineTrigger] shouldBe Right(original)
  }

  it should "round-trip Event through JSON" in {
    val original: PipelineTrigger = PipelineTrigger.Event
    val json                      = original.asJson
    json.as[PipelineTrigger] shouldBe Right(original)
  }

  it should "parse case-insensitively via fromString" in {
    PipelineTrigger.fromString("scheduled") shouldBe Right(PipelineTrigger.Scheduled)
    PipelineTrigger.fromString("MANUAL") shouldBe Right(PipelineTrigger.Manual)
    PipelineTrigger.fromString("Event") shouldBe Right(PipelineTrigger.Event)
  }

  it should "fail to decode unknown string" in {
    val result = decode[PipelineTrigger]("\"Xyz\"")
    result.isLeft shouldBe true
  }

  // ── PipelineRunDO Circe round-trip ──

  "PipelineRunDO" should "round-trip through JSON" in {
    val runDO = PipelineRunDO(
      runId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      pipelineName = "test-pipeline",
      status = PipelineStatus.Completed,
      startedAt = Instant.parse("2024-06-01T10:00:00Z"),
      completedAt = Some(Instant.parse("2024-06-01T10:30:00Z")),
      itemsProcessed = 100,
      itemsSucceeded = 95,
      itemsFailed = 5,
      errorSummary = Some("5 items failed"),
      snapshotPath = Some("/snapshots/run-001.json"),
      trigger = PipelineTrigger.Scheduled,
      createdAt = Instant.parse("2024-06-01T10:00:00Z"),
    )
    val json    = runDO.asJson.noSpaces
    val decoded = decode[PipelineRunDO](json)
    decoded shouldBe Right(runDO)
  }

  it should "round-trip with completedAt = None" in {
    val runDO = PipelineRunDO(
      runId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
      pipelineName = "in-progress-pipeline",
      status = PipelineStatus.Running,
      startedAt = Instant.parse("2024-06-01T10:00:00Z"),
      completedAt = None,
      itemsProcessed = 0,
      itemsSucceeded = 0,
      itemsFailed = 0,
      errorSummary = None,
      snapshotPath = None,
      trigger = PipelineTrigger.Manual,
      createdAt = Instant.parse("2024-06-01T10:00:00Z"),
    )
    val json    = runDO.asJson.noSpaces
    val decoded = decode[PipelineRunDO](json)
    decoded shouldBe Right(runDO)
  }

  // ── ProcessingResultDO Circe round-trip ──

  "ProcessingResultDO" should "round-trip through JSON" in {
    val resultDO = ProcessingResultDO(
      resultId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
      runId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      correlationId = UUID.fromString("00000000-0000-0000-0000-000000000020"),
      entityType = "bill",
      entityId = "HR-1234",
      status = ResultStatus.Failed,
      errorMessage = Some("Connection timeout"),
      errorClass = Some("Transient"),
      processedAt = Instant.parse("2024-06-01T10:15:00Z"),
    )
    val json    = resultDO.asJson.noSpaces
    val decoded = decode[ProcessingResultDO](json)
    decoded shouldBe Right(resultDO)
  }

  it should "round-trip with optional fields as None" in {
    val resultDO = ProcessingResultDO(
      resultId = UUID.fromString("00000000-0000-0000-0000-000000000011"),
      runId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      correlationId = UUID.fromString("00000000-0000-0000-0000-000000000021"),
      entityType = "legislator",
      entityId = "L-5678",
      status = ResultStatus.Succeeded,
      errorMessage = None,
      errorClass = None,
      processedAt = Instant.parse("2024-06-01T10:16:00Z"),
    )
    val json    = resultDO.asJson.noSpaces
    val decoded = decode[ProcessingResultDO](json)
    decoded shouldBe Right(resultDO)
  }

  // ── Doobie instances compile check ──

  "Doobie instances" should "resolve Get/Put for PipelineStatus" in {
    implicitly[doobie.Get[PipelineStatus]]
    implicitly[doobie.Put[PipelineStatus]]
    succeed
  }

  it should "resolve Get/Put for ResultStatus" in {
    implicitly[doobie.Get[ResultStatus]]
    implicitly[doobie.Put[ResultStatus]]
    succeed
  }

  it should "resolve Get/Put for PipelineTrigger" in {
    implicitly[doobie.Get[PipelineTrigger]]
    implicitly[doobie.Put[PipelineTrigger]]
    succeed
  }

  it should "resolve Read/Write for PipelineRunDO" in {
    import doobie.implicits._
    import doobie.postgres.implicits._

    implicitly[doobie.Read[PipelineRunDO]]
    implicitly[doobie.Write[PipelineRunDO]]
    succeed
  }

  it should "resolve Read/Write for ProcessingResultDO" in {
    import doobie.implicits._
    import doobie.postgres.implicits._

    implicitly[doobie.Read[ProcessingResultDO]]
    implicitly[doobie.Write[ProcessingResultDO]]
    succeed
  }

}
