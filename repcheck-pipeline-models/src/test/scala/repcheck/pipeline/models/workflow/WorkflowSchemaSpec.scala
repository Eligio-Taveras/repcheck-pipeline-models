package repcheck.pipeline.models.workflow

import io.circe.parser.decode
import io.circe.syntax._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.pipeline.models.workflow.schema._

class WorkflowSchemaSpec extends AnyFlatSpec with Matchers {

  private def minimalStep(
    name: String,
    deps: List[String] = List.empty,
  ): WorkflowStepDefinition =
    WorkflowStepDefinition(
      stepName = name,
      dependencies = deps,
      container = ContainerConfig(image = "gcr.io/project/image:latest"),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
    )

  // ── WorkflowDefinition.validated ──

  "WorkflowDefinition.validated" should "return Right for valid steps" in {
    val steps = List(
      minimalStep("step-a"),
      minimalStep("step-b", deps = List("step-a")),
    )
    val result = WorkflowDefinition.validated("wf-1", "test-workflow", steps, None)
    result.isRight shouldBe true
    result.foreach { wf =>
      wf.workflowId shouldBe "wf-1"
      wf.steps.length shouldBe 2
    }
  }

  it should "return Left for circular dependencies" in {
    val steps = List(
      minimalStep("step-a", deps = List("step-b")),
      minimalStep("step-b", deps = List("step-a")),
    )
    val result = WorkflowDefinition.validated("wf-1", "cycle-test", steps, None)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("Circular dependency"))
  }

  it should "return Left for missing dependency references" in {
    val steps = List(
      minimalStep("step-a", deps = List("nonexistent-step"))
    )
    val result = WorkflowDefinition.validated("wf-1", "missing-dep", steps, None)
    result.isLeft shouldBe true
    result.left.foreach { msg =>
      msg should include("Missing dependency")
      msg should include("nonexistent-step")
    }
  }

  it should "return Left for duplicate step names" in {
    val steps = List(
      minimalStep("step-a"),
      minimalStep("step-a"),
    )
    val result = WorkflowDefinition.validated("wf-1", "dup-test", steps, None)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("Duplicate step names"))
  }

  it should "return Left for invalid CPU value" in {
    val step = WorkflowStepDefinition(
      stepName = "bad-cpu",
      container = ContainerConfig(image = "gcr.io/project/image:latest"),
      resources = ResourceConfig(cpu = "3"),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
    )
    val result = WorkflowDefinition.validated("wf-1", "bad-cpu", List(step), None)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("Invalid CPU"))
  }

  it should "return Left for maxRetries = -1" in {
    val step = WorkflowStepDefinition(
      stepName = "bad-retries",
      container = ContainerConfig(image = "gcr.io/project/image:latest"),
      execution = ExecutionConfig(maxRetries = -1),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
    )
    val result = WorkflowDefinition.validated("wf-1", "bad-retries", List(step), None)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("maxRetries"))
  }

  it should "return Left for maxRetries = 11" in {
    val step = WorkflowStepDefinition(
      stepName = "too-many-retries",
      container = ContainerConfig(image = "gcr.io/project/image:latest"),
      execution = ExecutionConfig(maxRetries = 11),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
    )
    val result = WorkflowDefinition.validated("wf-1", "too-many-retries", List(step), None)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("maxRetries"))
  }

  it should "return Left for taskCount = 0" in {
    val step = WorkflowStepDefinition(
      stepName = "zero-tasks",
      container = ContainerConfig(image = "gcr.io/project/image:latest"),
      execution = ExecutionConfig(taskCount = 0),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
    )
    val result = WorkflowDefinition.validated("wf-1", "zero-tasks", List(step), None)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("taskCount"))
  }

  it should "return Left for invalid memory format" in {
    val step = WorkflowStepDefinition(
      stepName = "bad-memory",
      container = ContainerConfig(image = "gcr.io/project/image:latest"),
      resources = ResourceConfig(memory = "banana"),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
    )
    val result = WorkflowDefinition.validated("wf-1", "bad-memory", List(step), None)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("Invalid memory format"))
  }

  it should "accept valid memory formats" in {
    val step512Mi = minimalStep("step-512mi").copy(resources = ResourceConfig(memory = "512Mi"))
    val step2Gi   = minimalStep("step-2gi").copy(resources = ResourceConfig(memory = "2Gi"))
    val result1   = WorkflowDefinition.validated("wf-1", "test", List(step512Mi), None)
    val result2   = WorkflowDefinition.validated("wf-2", "test", List(step2Gi), None)
    result1.isRight shouldBe true
    result2.isRight shouldBe true
  }

  it should "return Left for invalid probe initialDelaySeconds" in {
    val step = WorkflowStepDefinition(
      stepName = "bad-probe",
      container = ContainerConfig(image = "gcr.io/project/image:latest"),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
      healthChecks = HealthCheckConfig(
        startupProbe = Some(ProbeConfig(initialDelaySeconds = 300))
      ),
    )
    val result = WorkflowDefinition.validated("wf-1", "bad-probe", List(step), None)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("initialDelaySeconds"))
  }

  it should "return Left for invalid probe timeoutSeconds" in {
    val step = WorkflowStepDefinition(
      stepName = "bad-timeout",
      container = ContainerConfig(image = "gcr.io/project/image:latest"),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
      healthChecks = HealthCheckConfig(
        livenessProbe = Some(ProbeConfig(timeoutSeconds = 5000))
      ),
    )
    val result = WorkflowDefinition.validated("wf-1", "bad-timeout", List(step), None)
    result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("timeoutSeconds"))
  }

  it should "return Right for a minimal step with just image and identity" in {
    val steps  = List(minimalStep("minimal"))
    val result = WorkflowDefinition.validated("wf-1", "minimal-wf", steps, Some("0 9 * * *"))
    result.isRight shouldBe true
    result.foreach(wf => wf.schedule shouldBe Some("0 9 * * *"))
  }

  // ── Config sub-type Circe round-trips ──

  "ContainerConfig" should "round-trip through JSON" in {
    val config = ContainerConfig(
      image = "gcr.io/project/image:v1",
      command = Some(List("/bin/sh", "-c")),
      args = List("echo", "hello"),
      env = Map("FOO" -> "bar"),
      secretMounts = Map("/secret/db-pass" -> "projects/p/secrets/s/versions/1"),
    )
    val json   = config.asJson.noSpaces
    val result = decode[ContainerConfig](json)
    result shouldBe Right(config)
  }

  "ResourceConfig" should "round-trip through JSON" in {
    val config = ResourceConfig(cpu = "4", memory = "2Gi", gpu = Some("nvidia-tesla-t4"))
    val json   = config.asJson.noSpaces
    val result = decode[ResourceConfig](json)
    result shouldBe Right(config)
  }

  "ExecutionConfig" should "round-trip through JSON" in {
    val config = ExecutionConfig(timeout = "30m", maxRetries = 5, taskCount = 100, parallelism = 10)
    val json   = config.asJson.noSpaces
    val result = decode[ExecutionConfig](json)
    result shouldBe Right(config)
  }

  "NetworkConfig" should "round-trip through JSON" in {
    val config = NetworkConfig(
      vpcConnector = Some("projects/p/locations/us-central1/connectors/vpc"),
      vpcEgress = Some("all-traffic"),
      cloudSqlConnections = List("project:region:instance"),
    )
    val json   = config.asJson.noSpaces
    val result = decode[NetworkConfig](json)
    result shouldBe Right(config)
  }

  "VolumeConfig" should "round-trip through JSON" in {
    val config = VolumeConfig(
      gcsVolumes = Map("/mnt/data" -> "gs://bucket/path"),
      inMemoryVolumes = Map("/tmp/cache" -> "256Mi"),
    )
    val json   = config.asJson.noSpaces
    val result = decode[VolumeConfig](json)
    result shouldBe Right(config)
  }

  "IdentityConfig" should "round-trip through JSON" in {
    val config = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com")
    val json   = config.asJson.noSpaces
    val result = decode[IdentityConfig](json)
    result shouldBe Right(config)
  }

  "HealthCheckConfig" should "round-trip through JSON" in {
    val config = HealthCheckConfig(
      startupProbe =
        Some(ProbeConfig(initialDelaySeconds = 5, timeoutSeconds = 2, periodSeconds = 15, failureThreshold = 5)),
      livenessProbe = Some(ProbeConfig()),
    )
    val json   = config.asJson.noSpaces
    val result = decode[HealthCheckConfig](json)
    result shouldBe Right(config)
  }

  "MetadataConfig" should "round-trip through JSON" in {
    val config = MetadataConfig(
      labels = Map("app" -> "repcheck", "env" -> "prod"),
      annotations = Map("run.googleapis.com/launch-stage" -> "BETA"),
    )
    val json   = config.asJson.noSpaces
    val result = decode[MetadataConfig](json)
    result shouldBe Right(config)
  }

  // ── WorkflowDefinition Circe round-trip ──

  "WorkflowDefinition" should "round-trip through JSON" in {
    val wf = WorkflowDefinition(
      workflowId = "wf-1",
      name = "test-workflow",
      steps = List(minimalStep("step-a")),
      schedule = Some("0 */6 * * *"),
    )
    val json   = wf.asJson.noSpaces
    val result = decode[WorkflowDefinition](json)
    result shouldBe Right(wf)
  }

  // ── WorkflowStepDefinition Circe round-trip ──

  "WorkflowStepDefinition" should "round-trip through JSON with all fields" in {
    val step = WorkflowStepDefinition(
      stepName = "full-step",
      triggeredBy = List("topic-a"),
      dependencies = List.empty,
      container = ContainerConfig(
        image = "gcr.io/project/image:v1",
        command = Some(List("/bin/sh")),
        args = List("-c", "echo hello"),
        env = Map("KEY" -> "value"),
        secretMounts = Map("/secret" -> "projects/p/secrets/s/versions/1"),
      ),
      resources = ResourceConfig(cpu = "2", memory = "1Gi", gpu = None),
      execution = ExecutionConfig(timeout = "5m", maxRetries = 2, taskCount = 10, parallelism = 5),
      networking = NetworkConfig(
        vpcConnector = Some("vpc-conn"),
        vpcEgress = Some("private-ranges-only"),
        cloudSqlConnections = List("conn-1"),
      ),
      volumes = VolumeConfig(
        gcsVolumes = Map("/data" -> "gs://bucket"),
        inMemoryVolumes = Map("/tmp" -> "128Mi"),
      ),
      identity = IdentityConfig(serviceAccount = "sa@project.iam.gserviceaccount.com"),
      healthChecks = HealthCheckConfig(
        startupProbe = Some(ProbeConfig(initialDelaySeconds = 10)),
        livenessProbe = None,
      ),
      metadata = MetadataConfig(
        labels = Map("team" -> "data"),
        annotations = Map("note" -> "test"),
      ),
    )
    val json   = step.asJson.noSpaces
    val result = decode[WorkflowStepDefinition](json)
    result shouldBe Right(step)
  }

}
