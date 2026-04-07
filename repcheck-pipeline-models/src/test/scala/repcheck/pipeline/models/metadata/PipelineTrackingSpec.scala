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
    val _ = PipelineStatus.fromString("running") shouldBe Right(PipelineStatus.Running)
    val _ = PipelineStatus.fromString("COMPLETED") shouldBe Right(PipelineStatus.Completed)
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
    val _ = ResultStatus.fromString("succeeded") shouldBe Right(ResultStatus.Succeeded)
    val _ = ResultStatus.fromString("FAILED") shouldBe Right(ResultStatus.Failed)
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
    val _ = PipelineTrigger.fromString("scheduled") shouldBe Right(PipelineTrigger.Scheduled)
    val _ = PipelineTrigger.fromString("MANUAL") shouldBe Right(PipelineTrigger.Manual)
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

  // ── PipelineRunSummary Circe round-trip ──

  "PipelineRunSummary" should "round-trip through JSON" in {
    val summary = PipelineRunSummary(
      runId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      pipelineName = "test-pipeline",
      status = PipelineStatus.CompletedWithErrors,
      startedAt = Instant.parse("2024-06-01T10:00:00Z"),
      completedAt = Instant.parse("2024-06-01T10:30:00Z"),
      itemsProcessed = 100,
      itemsSucceeded = 95,
      itemsFailed = 5,
      errorCounts = Map("timeout" -> 3, "auth" -> 2),
    )
    val json    = summary.asJson.noSpaces
    val decoded = decode[PipelineRunSummary](json)
    decoded shouldBe Right(summary)
  }

  it should "round-trip with empty errorCounts" in {
    val summary = PipelineRunSummary(
      runId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
      pipelineName = "clean-run",
      status = PipelineStatus.Completed,
      startedAt = Instant.parse("2024-06-01T10:00:00Z"),
      completedAt = Instant.parse("2024-06-01T10:15:00Z"),
      itemsProcessed = 50,
      itemsSucceeded = 50,
      itemsFailed = 0,
      errorCounts = Map.empty,
    )
    val json    = summary.asJson.noSpaces
    val decoded = decode[PipelineRunSummary](json)
    decoded shouldBe Right(summary)
  }

  // ── decodeAccumulating coverage ──

  "PipelineRunDO decodeAccumulating" should "decode valid JSON" in {
    val runDO = PipelineRunDO(
      runId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      pipelineName = "test",
      status = PipelineStatus.Completed,
      startedAt = Instant.parse("2024-06-01T10:00:00Z"),
      completedAt = Some(Instant.parse("2024-06-01T10:30:00Z")),
      itemsProcessed = 10,
      itemsSucceeded = 10,
      itemsFailed = 0,
      errorSummary = None,
      snapshotPath = None,
      trigger = PipelineTrigger.Manual,
      createdAt = Instant.parse("2024-06-01T10:00:00Z"),
    )
    val json   = runDO.asJson
    val result = implicitly[io.circe.Decoder[PipelineRunDO]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "ProcessingResultDO decodeAccumulating" should "decode valid JSON" in {
    val resultDO = ProcessingResultDO(
      resultId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
      runId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      correlationId = UUID.fromString("00000000-0000-0000-0000-000000000020"),
      entityType = "bill",
      entityId = "HR-1234",
      status = ResultStatus.Succeeded,
      errorMessage = None,
      errorClass = None,
      processedAt = Instant.parse("2024-06-01T10:15:00Z"),
    )
    val json   = resultDO.asJson
    val result = implicitly[io.circe.Decoder[ProcessingResultDO]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "PipelineRunSummary decodeAccumulating" should "decode valid JSON" in {
    val summary = PipelineRunSummary(
      runId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      pipelineName = "test",
      status = PipelineStatus.Completed,
      startedAt = Instant.parse("2024-06-01T10:00:00Z"),
      completedAt = Instant.parse("2024-06-01T10:15:00Z"),
      itemsProcessed = 50,
      itemsSucceeded = 50,
      itemsFailed = 0,
      errorCounts = Map.empty,
    )
    val json   = summary.asJson
    val result = implicitly[io.circe.Decoder[PipelineRunSummary]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  // ── Doobie instances compile check ──

  "Doobie instances" should "resolve Get/Put for PipelineStatus" in {
    val _ = implicitly[doobie.Get[PipelineStatus]]
    val _ = implicitly[doobie.Put[PipelineStatus]]
    succeed
  }

  it should "resolve Get/Put for ResultStatus" in {
    val _ = implicitly[doobie.Get[ResultStatus]]
    val _ = implicitly[doobie.Put[ResultStatus]]
    succeed
  }

  it should "resolve Get/Put for PipelineTrigger" in {
    val _ = implicitly[doobie.Get[PipelineTrigger]]
    val _ = implicitly[doobie.Put[PipelineTrigger]]
    succeed
  }

  it should "resolve Read/Write for PipelineRunDO" in {
    import doobie.postgres.implicits._

    val _ = implicitly[doobie.Read[PipelineRunDO]]
    val _ = implicitly[doobie.Write[PipelineRunDO]]
    succeed
  }

  it should "resolve Read/Write for ProcessingResultDO" in {
    import doobie.postgres.implicits._

    val _ = implicitly[doobie.Read[ProcessingResultDO]]
    val _ = implicitly[doobie.Write[ProcessingResultDO]]
    succeed
  }

  // ── Doobie Get/Put round-trip via H2 ──

  import cats.effect.IO
  import cats.effect.unsafe.implicits.global
  import doobie._
  import doobie.implicits._

  private val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    driver = "org.h2.Driver",
    url = "jdbc:h2:mem:pipeline_tracking_test;DB_CLOSE_DELAY=-1",
    user = "sa",
    password = "",
    logHandler = None,
  )

  "Doobie Get/Put for PipelineStatus" should "round-trip via H2" in {
    val status = PipelineStatus.Running
    val result = sql"SELECT ${status.toString}".query[PipelineStatus].unique.transact(xa).unsafeRunSync()
    result shouldBe status
  }

  it should "round-trip all variants via H2" in {
    PipelineStatus.values.foreach { status =>
      val result = sql"SELECT ${status.toString}".query[PipelineStatus].unique.transact(xa).unsafeRunSync()
      result shouldBe status
    }
  }

  "Doobie Get/Put for ResultStatus" should "round-trip via H2" in {
    val status = ResultStatus.Succeeded
    val result = sql"SELECT ${status.toString}".query[ResultStatus].unique.transact(xa).unsafeRunSync()
    result shouldBe status
  }

  it should "round-trip all variants via H2" in {
    ResultStatus.values.foreach { status =>
      val result = sql"SELECT ${status.toString}".query[ResultStatus].unique.transact(xa).unsafeRunSync()
      result shouldBe status
    }
  }

  "Doobie Get/Put for PipelineTrigger" should "round-trip via H2" in {
    val trigger = PipelineTrigger.Scheduled
    val result  = sql"SELECT ${trigger.toString}".query[PipelineTrigger].unique.transact(xa).unsafeRunSync()
    result shouldBe trigger
  }

  it should "round-trip all variants via H2" in {
    PipelineTrigger.values.foreach { trigger =>
      val result = sql"SELECT ${trigger.toString}".query[PipelineTrigger].unique.transact(xa).unsafeRunSync()
      result shouldBe trigger
    }
  }

}
