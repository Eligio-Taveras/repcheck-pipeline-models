package repcheck.pipeline.models.launcher

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.parser.decode
import io.circe.syntax._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.pipeline.models.workflow.state.WorkflowStepStatus

class LauncherTraitsSpec extends AnyFlatSpec with Matchers {

  "LaunchResult" should "round-trip a successful result with jobName" in {
    val result  = LaunchResult(success = true, jobName = Some("job-abc-123"), errorMessage = None)
    val json    = result.asJson.noSpaces
    val decoded = decode[LaunchResult](json)
    decoded shouldBe Right(result)
  }

  it should "round-trip a failed result with errorMessage" in {
    val result  = LaunchResult(success = false, jobName = None, errorMessage = Some("Quota exceeded"))
    val json    = result.asJson.noSpaces
    val decoded = decode[LaunchResult](json)
    decoded shouldBe Right(result)
  }

  // ── DependencyChecker contract tests (criteria 15-17) ──

  /** In-memory DependencyChecker for contract testing. */
  private def inMemoryDependencyChecker(
    stepStatuses: Map[String, WorkflowStepStatus]
  ): DependencyChecker[IO] =
    new DependencyChecker[IO] {
      def allDependenciesMet(workflowRunId: UUID, dependencies: List[String]): IO[Boolean] =
        IO.pure(dependencies.forall(dep => stepStatuses.get(dep).contains(WorkflowStepStatus.Completed)))
    }

  "DependencyChecker" should "return true when all dependencies are Completed" in {
    val statuses = Map(
      "step-a" -> WorkflowStepStatus.Completed,
      "step-b" -> WorkflowStepStatus.Completed,
    )
    val checker = inMemoryDependencyChecker(statuses)
    val result  = checker.allDependenciesMet(UUID.randomUUID(), List("step-a", "step-b")).unsafeRunSync()
    result shouldBe true
  }

  it should "return false when one dependency is not Completed" in {
    val statuses = Map(
      "step-a" -> WorkflowStepStatus.Completed,
      "step-b" -> WorkflowStepStatus.Running,
    )
    val checker = inMemoryDependencyChecker(statuses)
    val result  = checker.allDependenciesMet(UUID.randomUUID(), List("step-a", "step-b")).unsafeRunSync()
    result shouldBe false
  }

  it should "return false when a dependency is Failed" in {
    val statuses = Map(
      "step-a" -> WorkflowStepStatus.Completed,
      "step-b" -> WorkflowStepStatus.Failed,
    )
    val checker = inMemoryDependencyChecker(statuses)
    val result  = checker.allDependenciesMet(UUID.randomUUID(), List("step-a", "step-b")).unsafeRunSync()
    result shouldBe false
  }

  it should "return true when dependencies list is empty (root step)" in {
    val checker = inMemoryDependencyChecker(Map.empty)
    val result  = checker.allDependenciesMet(UUID.randomUUID(), List.empty).unsafeRunSync()
    result shouldBe true
  }

  it should "return false when a dependency step does not exist" in {
    val statuses = Map("step-a" -> WorkflowStepStatus.Completed)
    val checker  = inMemoryDependencyChecker(statuses)
    val result   = checker.allDependenciesMet(UUID.randomUUID(), List("step-a", "step-missing")).unsafeRunSync()
    result shouldBe false
  }

  // ── ResolvedStepConfig ──

  // ── ResolvedStepConfig ──

  "ResolvedStepConfig decoder" should "decode from raw JSON" in {
    val json =
      """{"stepName":"s","container":{"image":"img:v1","command":null,"args":[],"env":{},"secretMounts":{}},"resources":{"cpu":"2","memory":"1Gi","gpu":null},"execution":{"timeout":"15m","maxRetries":3,"taskCount":1,"parallelism":1},"networking":{"vpcConnector":null,"vpcEgress":null,"cloudSqlConnections":[]},"volumes":{"gcsVolumes":{},"inMemoryVolumes":{}},"identity":{"serviceAccount":"sa@p.iam"},"healthChecks":{"startupProbe":null,"livenessProbe":null},"metadata":{"labels":{},"annotations":{}}}"""
    val result = decode[ResolvedStepConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach { c =>
      val _ = c.stepName shouldBe "s"
      val _ = c.container.image shouldBe "img:v1"
      val _ = c.resources.cpu shouldBe "2"
      c.execution.maxRetries shouldBe 3
    }
  }

  "LaunchResult decodeAccumulating" should "decode valid JSON" in {
    val result  = LaunchResult(success = true, jobName = Some("job-1"), errorMessage = None)
    val json    = result.asJson
    val decoded = implicitly[io.circe.Decoder[LaunchResult]].decodeAccumulating(json.hcursor)
    decoded.isValid shouldBe true
  }

  "ResolvedStepConfig decodeAccumulating" should "decode valid JSON" in {
    import repcheck.pipeline.models.workflow.schema._
    val config = ResolvedStepConfig(
      stepName = "s",
      container = ContainerConfig(image = "img:v1"),
      resources = ResourceConfig(cpu = "2", memory = "1Gi"),
      execution = ExecutionConfig(timeout = "15m", maxRetries = 3, taskCount = 1, parallelism = 1),
      networking = NetworkConfig(),
      volumes = VolumeConfig(),
      identity = IdentityConfig(serviceAccount = "sa@p.iam"),
      healthChecks = HealthCheckConfig(),
      metadata = MetadataConfig(),
    )
    val json    = config.asJson
    val decoded = implicitly[io.circe.Decoder[ResolvedStepConfig]].decodeAccumulating(json.hcursor)
    decoded.isValid shouldBe true
  }

  it should "accumulate errors for invalid JSON" in {
    val json   = io.circe.parser.parse("""{"stepName":123}""").getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[ResolvedStepConfig]].decodeAccumulating(json.hcursor)
    result.isInvalid shouldBe true
  }

  "ResolvedStepConfig" should "round-trip through JSON" in {
    import repcheck.pipeline.models.workflow.schema._

    val config = ResolvedStepConfig(
      stepName = "ingest-step",
      container = ContainerConfig(image = "gcr.io/project/ingest:v1"),
      resources = ResourceConfig(cpu = "2", memory = "1Gi"),
      execution = ExecutionConfig(timeout = "15m", maxRetries = 3, taskCount = 1, parallelism = 1),
      networking = NetworkConfig(),
      volumes = VolumeConfig(),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
      healthChecks = HealthCheckConfig(),
      metadata = MetadataConfig(labels = Map("step" -> "ingest")),
    )
    val json    = config.asJson.noSpaces
    val decoded = decode[ResolvedStepConfig](json)
    decoded shouldBe Right(config)
  }

}
