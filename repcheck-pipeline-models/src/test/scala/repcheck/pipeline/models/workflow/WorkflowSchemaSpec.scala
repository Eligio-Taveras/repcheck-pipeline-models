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
    val _      = result.isRight shouldBe true
    result.foreach { wf =>
      val _ = wf.workflowId shouldBe "wf-1"
      wf.steps.length shouldBe 2
    }
  }

  it should "return Left for circular dependencies" in {
    val steps = List(
      minimalStep("step-a", deps = List("step-b")),
      minimalStep("step-b", deps = List("step-a")),
    )
    val result = WorkflowDefinition.validated("wf-1", "cycle-test", steps, None)
    val _      = result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("Circular dependency"))
  }

  it should "return Left for missing dependency references" in {
    val steps = List(
      minimalStep("step-a", deps = List("nonexistent-step"))
    )
    val result = WorkflowDefinition.validated("wf-1", "missing-dep", steps, None)
    val _      = result.isLeft shouldBe true
    result.left.foreach { msg =>
      val _ = msg should include("Missing dependency")
      msg should include("nonexistent-step")
    }
  }

  it should "return Left for duplicate step names" in {
    val steps = List(
      minimalStep("step-a"),
      minimalStep("step-a"),
    )
    val result = WorkflowDefinition.validated("wf-1", "dup-test", steps, None)
    val _      = result.isLeft shouldBe true
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
    val _      = result.isLeft shouldBe true
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
    val _      = result.isLeft shouldBe true
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
    val _      = result.isLeft shouldBe true
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
    val _      = result.isLeft shouldBe true
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
    val _      = result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("Invalid memory format"))
  }

  it should "accept valid memory formats" in {
    val step512Mi = minimalStep("step-512mi").copy(resources = ResourceConfig(memory = "512Mi"))
    val step2Gi   = minimalStep("step-2gi").copy(resources = ResourceConfig(memory = "2Gi"))
    val result1   = WorkflowDefinition.validated("wf-1", "test", List(step512Mi), None)
    val result2   = WorkflowDefinition.validated("wf-2", "test", List(step2Gi), None)
    val _         = result1.isRight shouldBe true
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
    val _      = result.isLeft shouldBe true
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
    val _      = result.isLeft shouldBe true
    result.left.foreach(msg => msg should include("timeoutSeconds"))
  }

  it should "return Right for a minimal step with just image and identity" in {
    val steps  = List(minimalStep("minimal"))
    val result = WorkflowDefinition.validated("wf-1", "minimal-wf", steps, Some("0 9 * * *"))
    val _      = result.isRight shouldBe true
    result.foreach(wf => wf.schedule shouldBe Some("0 9 * * *"))
  }

  // ── Decode from raw JSON string (decoder $anon coverage) ──

  "ContainerConfig decoder" should "decode from raw JSON with all fields" in {
    val json =
      """{"image":"gcr.io/proj/img:v1","command":["sh","-c"],"args":["run"],"env":{"K":"V"},"secretMounts":{"p":"s"}}"""
    val result = decode[ContainerConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach { c =>
      val _ = c.image shouldBe "gcr.io/proj/img:v1"
      val _ = c.command shouldBe Some(List("sh", "-c"))
      val _ = c.args shouldBe List("run")
      val _ = c.env shouldBe Map("K" -> "V")
      c.secretMounts shouldBe Map("p" -> "s")
    }
  }

  it should "decode with null optional fields" in {
    val json   = """{"image":"img:v1","command":null,"args":[],"env":{},"secretMounts":{}}"""
    val result = decode[ContainerConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach(_.command shouldBe None)
  }

  "ResourceConfig decoder" should "decode from raw JSON with all fields" in {
    val json   = """{"cpu":"4","memory":"2Gi","gpu":"nvidia-t4"}"""
    val result = decode[ResourceConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach { r =>
      val _ = r.cpu shouldBe "4"
      val _ = r.memory shouldBe "2Gi"
      r.gpu shouldBe Some("nvidia-t4")
    }
  }

  it should "decode with null gpu" in {
    val json   = """{"cpu":"1","memory":"512Mi","gpu":null}"""
    val result = decode[ResourceConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach(_.gpu shouldBe None)
  }

  "ExecutionConfig decoder" should "decode from raw JSON with all fields" in {
    val json   = """{"timeout":"30m","maxRetries":5,"taskCount":10,"parallelism":4}"""
    val result = decode[ExecutionConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach { e =>
      val _ = e.timeout shouldBe "30m"
      val _ = e.maxRetries shouldBe 5
      val _ = e.taskCount shouldBe 10
      e.parallelism shouldBe 4
    }
  }

  "NetworkConfig decoder" should "decode from raw JSON with all fields" in {
    val json   = """{"vpcConnector":"vpc-1","vpcEgress":"all","cloudSqlConnections":["conn-1","conn-2"]}"""
    val result = decode[NetworkConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach { n =>
      val _ = n.vpcConnector shouldBe Some("vpc-1")
      n.cloudSqlConnections shouldBe List("conn-1", "conn-2")
    }
  }

  it should "decode with null optional fields" in {
    val json   = """{"vpcConnector":null,"vpcEgress":null,"cloudSqlConnections":[]}"""
    val result = decode[NetworkConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach(_.vpcConnector shouldBe None)
  }

  "VolumeConfig decoder" should "decode from raw JSON with all fields" in {
    val json   = """{"gcsVolumes":{"/data":"gs://b"},"inMemoryVolumes":{"/tmp":"128Mi"}}"""
    val result = decode[VolumeConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach { v =>
      val _ = v.gcsVolumes shouldBe Map("/data" -> "gs://b")
      v.inMemoryVolumes shouldBe Map("/tmp" -> "128Mi")
    }
  }

  "MetadataConfig decoder" should "decode from raw JSON with all fields" in {
    val json   = """{"labels":{"app":"rc"},"annotations":{"note":"test"}}"""
    val result = decode[MetadataConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach { m =>
      val _ = m.labels shouldBe Map("app" -> "rc")
      m.annotations shouldBe Map("note" -> "test")
    }
  }

  "HealthCheckConfig decoder" should "decode with both probes from raw JSON" in {
    val json =
      """{"startupProbe":{"initialDelaySeconds":5,"timeoutSeconds":2,"periodSeconds":10,"failureThreshold":3},"livenessProbe":{"initialDelaySeconds":0,"timeoutSeconds":1,"periodSeconds":10,"failureThreshold":3}}"""
    val result = decode[HealthCheckConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach { h =>
      val _ = h.startupProbe.isDefined shouldBe true
      val _ = h.livenessProbe.isDefined shouldBe true
      h.startupProbe.foreach(_.initialDelaySeconds shouldBe 5)
    }
  }

  it should "decode with null probes" in {
    val json   = """{"startupProbe":null,"livenessProbe":null}"""
    val result = decode[HealthCheckConfig](json)
    val _      = result.isRight shouldBe true
    result.foreach { h =>
      val _ = h.startupProbe shouldBe None
      h.livenessProbe shouldBe None
    }
  }

  "IdentityConfig decoder" should "decode from raw JSON" in {
    val json   = """{"serviceAccount":"test@proj.iam.gserviceaccount.com"}"""
    val result = decode[IdentityConfig](json)
    result shouldBe Right(IdentityConfig(serviceAccount = "test@proj.iam.gserviceaccount.com"))
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

  // ── ProbeConfig validation branch coverage ──

  "ProbeConfig.validate" should "return Right for valid probe with defaults" in {
    ProbeConfig.validate(ProbeConfig(), "valid-step") shouldBe Right(())
  }

  it should "return Left for negative initialDelaySeconds" in {
    val result = ProbeConfig.validate(ProbeConfig(initialDelaySeconds = -1), "s")
    val _      = result.isLeft shouldBe true
    result.left.foreach(_ should include("initialDelaySeconds"))
  }

  it should "return Left for periodSeconds = 0" in {
    val result = ProbeConfig.validate(ProbeConfig(periodSeconds = 0), "s")
    val _      = result.isLeft shouldBe true
    result.left.foreach(_ should include("periodSeconds"))
  }

  it should "return Left for periodSeconds > 240" in {
    val result = ProbeConfig.validate(ProbeConfig(periodSeconds = 241), "s")
    val _      = result.isLeft shouldBe true
    result.left.foreach(_ should include("periodSeconds"))
  }

  it should "return Left for failureThreshold = 0" in {
    val result = ProbeConfig.validate(ProbeConfig(failureThreshold = 0), "s")
    val _      = result.isLeft shouldBe true
    result.left.foreach(_ should include("failureThreshold"))
  }

  it should "return Right for boundary values" in {
    val _ = ProbeConfig.validate(
      ProbeConfig(initialDelaySeconds = 0, timeoutSeconds = 1, periodSeconds = 1, failureThreshold = 1),
      "s",
    ) shouldBe Right(())
    ProbeConfig.validate(
      ProbeConfig(initialDelaySeconds = 240, timeoutSeconds = 3600, periodSeconds = 240, failureThreshold = 100),
      "s",
    ) shouldBe Right(())
  }

  // ── ProbeConfig and HealthCheckConfig Circe round-trip ──

  // ── HealthCheckConfig encoder/decoder error accumulation ──

  "ProbeConfig decodeAccumulating" should "accumulate multiple errors" in {
    val json = io.circe.parser
      .parse("""{"initialDelaySeconds":"bad","timeoutSeconds":"bad","periodSeconds":"bad","failureThreshold":"bad"}""")
      .getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[ProbeConfig]].decodeAccumulating(json.hcursor)
    result.isInvalid shouldBe true
  }

  "HealthCheckConfig decodeAccumulating" should "accumulate errors for invalid startupProbe and livenessProbe" in {
    val json   = io.circe.parser.parse("""{"startupProbe":"bad","livenessProbe":"bad"}""").getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[HealthCheckConfig]].decodeAccumulating(json.hcursor)
    result.isInvalid shouldBe true
  }

  it should "decode with only startupProbe present" in {
    val json = io.circe.parser
      .parse(
        """{"startupProbe":{"initialDelaySeconds":5,"timeoutSeconds":2,"periodSeconds":10,"failureThreshold":3}}"""
      )
      .getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[HealthCheckConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  it should "decode with only livenessProbe present" in {
    val json = io.circe.parser
      .parse(
        """{"livenessProbe":{"initialDelaySeconds":0,"timeoutSeconds":1,"periodSeconds":10,"failureThreshold":3}}"""
      )
      .getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[HealthCheckConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "ProbeConfig encoder" should "encode all fields" in {
    val probe = ProbeConfig(5, 10, 30, 5)
    val json  = probe.asJson
    val _     = json.hcursor.get[Int]("initialDelaySeconds") shouldBe Right(5)
    val _     = json.hcursor.get[Int]("timeoutSeconds") shouldBe Right(10)
    val _     = json.hcursor.get[Int]("periodSeconds") shouldBe Right(30)
    json.hcursor.get[Int]("failureThreshold") shouldBe Right(5)
  }

  "HealthCheckConfig encoder" should "encode with both probes present" in {
    val config = HealthCheckConfig(
      startupProbe = Some(ProbeConfig(5, 2, 10, 3)),
      livenessProbe = Some(ProbeConfig(0, 1, 10, 3)),
    )
    val json = config.asJson
    val _    = json.hcursor.downField("startupProbe").focus.isDefined shouldBe true
    json.hcursor.downField("livenessProbe").focus.isDefined shouldBe true
  }

  it should "encode with both probes absent" in {
    val config = HealthCheckConfig(startupProbe = None, livenessProbe = None)
    val json   = config.asJson
    val result = json.hcursor.downField("startupProbe").as[Option[ProbeConfig]]
    result shouldBe Right(None)
  }

  "ProbeConfig" should "round-trip through JSON" in {
    val probe   = ProbeConfig(initialDelaySeconds = 5, timeoutSeconds = 10, periodSeconds = 30, failureThreshold = 5)
    val json    = probe.asJson.noSpaces
    val decoded = decode[ProbeConfig](json)
    decoded shouldBe Right(probe)
  }

  it should "round-trip defaults through JSON" in {
    val probe   = ProbeConfig()
    val json    = probe.asJson.noSpaces
    val decoded = decode[ProbeConfig](json)
    decoded shouldBe Right(probe)
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

  // ── decodeAccumulating coverage for schema config types ──

  "ProbeConfig decodeAccumulating" should "decode valid JSON" in {
    val json   = ProbeConfig(5, 2, 10, 3).asJson
    val result = implicitly[io.circe.Decoder[ProbeConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  it should "accumulate errors for invalid JSON" in {
    val json   = io.circe.parser.parse("""{"initialDelaySeconds":"bad"}""").getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[ProbeConfig]].decodeAccumulating(json.hcursor)
    result.isInvalid shouldBe true
  }

  "HealthCheckConfig decodeAccumulating" should "decode valid JSON" in {
    val config = HealthCheckConfig(startupProbe = Some(ProbeConfig()), livenessProbe = None)
    val json   = config.asJson
    val result = implicitly[io.circe.Decoder[HealthCheckConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  it should "accumulate errors for invalid JSON" in {
    val json   = io.circe.parser.parse("""{"startupProbe":"bad"}""").getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[HealthCheckConfig]].decodeAccumulating(json.hcursor)
    result.isInvalid shouldBe true
  }

  "ContainerConfig decodeAccumulating" should "decode valid JSON" in {
    val config = ContainerConfig(image = "img:v1")
    val json   = config.asJson
    val result = implicitly[io.circe.Decoder[ContainerConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "ResourceConfig decodeAccumulating" should "decode valid JSON" in {
    val config = ResourceConfig(cpu = "2", memory = "1Gi", gpu = Some("nvidia-t4"))
    val json   = config.asJson
    val result = implicitly[io.circe.Decoder[ResourceConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "ExecutionConfig decodeAccumulating" should "decode valid JSON" in {
    val config = ExecutionConfig(timeout = "30m", maxRetries = 5, taskCount = 10, parallelism = 4)
    val json   = config.asJson
    val result = implicitly[io.circe.Decoder[ExecutionConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "NetworkConfig decodeAccumulating" should "decode valid JSON" in {
    val config = NetworkConfig(vpcConnector = Some("vpc"), cloudSqlConnections = List("conn"))
    val json   = config.asJson
    val result = implicitly[io.circe.Decoder[NetworkConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "VolumeConfig decodeAccumulating" should "decode valid JSON" in {
    val config = VolumeConfig(gcsVolumes = Map("/data" -> "gs://b"))
    val json   = config.asJson
    val result = implicitly[io.circe.Decoder[VolumeConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "MetadataConfig decodeAccumulating" should "decode valid JSON" in {
    val config = MetadataConfig(labels = Map("k" -> "v"))
    val json   = config.asJson
    val result = implicitly[io.circe.Decoder[MetadataConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "IdentityConfig decodeAccumulating" should "decode valid JSON" in {
    val config = IdentityConfig(serviceAccount = "sa@p.iam")
    val json   = config.asJson
    val result = implicitly[io.circe.Decoder[IdentityConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "WorkflowDefinition decodeAccumulating" should "decode valid JSON" in {
    val wf     = WorkflowDefinition("wf-1", "test", List(minimalStep("s")), None)
    val json   = wf.asJson
    val result = implicitly[io.circe.Decoder[WorkflowDefinition]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "WorkflowStepDefinition decodeAccumulating" should "decode valid JSON" in {
    val step   = minimalStep("step-a")
    val json   = step.asJson
    val result = implicitly[io.circe.Decoder[WorkflowStepDefinition]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

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
