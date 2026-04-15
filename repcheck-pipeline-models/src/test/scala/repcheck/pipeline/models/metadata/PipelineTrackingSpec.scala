package repcheck.pipeline.models.metadata

import java.time.Instant
import java.util.UUID

import io.circe.parser.decode
import io.circe.syntax._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.pipeline.models.workflow.state.WorkflowStepStatus

class PipelineTrackingSpec extends AnyFlatSpec with Matchers {

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

  // ── ProcessingResultDO Circe round-trip ──

  "ProcessingResultDO" should "round-trip through JSON" in {
    val resultDO = ProcessingResultDO(
      id = 10L,
      stepRunId = 1L,
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
      id = 11L,
      stepRunId = 1L,
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

  // ── StepRunSummary Circe round-trip ──

  "StepRunSummary" should "round-trip through JSON" in {
    val summary = StepRunSummary(
      stepRunId = 1L,
      stepName = "bills-pipeline",
      status = WorkflowStepStatus.CompletedWithErrors,
      startedAt = Instant.parse("2024-06-01T10:00:00Z"),
      completedAt = Instant.parse("2024-06-01T10:30:00Z"),
      itemsProcessed = 100,
      itemsSucceeded = 95,
      itemsFailed = 5,
      errorCounts = Map("timeout" -> 3, "auth" -> 2),
    )
    val json    = summary.asJson.noSpaces
    val decoded = decode[StepRunSummary](json)
    decoded shouldBe Right(summary)
  }

  it should "round-trip with empty errorCounts" in {
    val summary = StepRunSummary(
      stepRunId = 2L,
      stepName = "votes-pipeline",
      status = WorkflowStepStatus.Completed,
      startedAt = Instant.parse("2024-06-01T10:00:00Z"),
      completedAt = Instant.parse("2024-06-01T10:15:00Z"),
      itemsProcessed = 50,
      itemsSucceeded = 50,
      itemsFailed = 0,
      errorCounts = Map.empty,
    )
    val json    = summary.asJson.noSpaces
    val decoded = decode[StepRunSummary](json)
    decoded shouldBe Right(summary)
  }

  // ── decodeAccumulating coverage ──

  "ProcessingResultDO decodeAccumulating" should "decode valid JSON" in {
    val resultDO = ProcessingResultDO(
      id = 10L,
      stepRunId = 1L,
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

  "StepRunSummary decodeAccumulating" should "decode valid JSON" in {
    val summary = StepRunSummary(
      stepRunId = 1L,
      stepName = "bills-pipeline",
      status = WorkflowStepStatus.Completed,
      startedAt = Instant.parse("2024-06-01T10:00:00Z"),
      completedAt = Instant.parse("2024-06-01T10:15:00Z"),
      itemsProcessed = 50,
      itemsSucceeded = 50,
      itemsFailed = 0,
      errorCounts = Map.empty,
    )
    val json   = summary.asJson
    val result = implicitly[io.circe.Decoder[StepRunSummary]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  // ── Doobie instances compile check ──

  "Doobie instances" should "resolve Get/Put for ResultStatus" in {
    val _ = implicitly[doobie.Get[ResultStatus]]
    val _ = implicitly[doobie.Put[ResultStatus]]
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

}
