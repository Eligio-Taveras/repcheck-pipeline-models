package repcheck.pipeline.models.config

import pureconfig.{ConfigReader, ConfigSource}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.shared.models.congress.vote.VoteType

import com.repcheck.utils.errors.RetryConfig

final case class TestAppConfig(x: Int) derives ConfigReader

class ConfigSpec extends AnyFlatSpec with Matchers {

  // ── Helper HOCON fragments ──

  private val voteWeightsHocon: String =
    """
      |passage = 1.0
      |conference-report = 1.0
      |cloture = 0.8
      |veto-override = 0.9
      |amendment = 0.5
      |committee = 0.4
      |motion-to-recommit = 0.6
      |other = 0.5
      |""".stripMargin

  private val retryHocon: String =
    """
      |max-retries = 3
      |initial-backoff-ms = 10
      |max-backoff-ms = 60000
      |backoff-multiplier = 2.0
      |""".stripMargin

  private def pipelineConfigHocon(appConfigHocon: String): String =
    s"""
       |parallelism = 4
       |batch-size = 100
       |page-size = 250
       |retry {
       |  $retryHocon
       |}
       |app-config {
       |  $appConfigHocon
       |}
       |""".stripMargin

  // ── VoteWeights defaults ──

  "VoteWeights defaults" should "have correct default values" in {
    val w = VoteWeights()
    val _ = w.passage shouldBe 1.0
    val _ = w.conferenceReport shouldBe 1.0
    val _ = w.cloture shouldBe 0.8
    val _ = w.vetoOverride shouldBe 0.9
    val _ = w.amendment shouldBe 0.5
    val _ = w.committee shouldBe 0.4
    val _ = w.motionToRecommit shouldBe 0.6
    w.other shouldBe 0.5
  }

  // ── VoteWeights.forVoteType for each variant ──

  "VoteWeights.forVoteType" should "return passage weight for Passage" in {
    VoteWeights().forVoteType(VoteType.Passage) shouldBe 1.0
  }

  it should "return conferenceReport weight for ConferenceReport" in {
    VoteWeights().forVoteType(VoteType.ConferenceReport) shouldBe 1.0
  }

  it should "return cloture weight for Cloture" in {
    VoteWeights().forVoteType(VoteType.Cloture) shouldBe 0.8
  }

  it should "return vetoOverride weight for VetoOverride" in {
    VoteWeights().forVoteType(VoteType.VetoOverride) shouldBe 0.9
  }

  it should "return amendment weight for Amendment" in {
    VoteWeights().forVoteType(VoteType.Amendment) shouldBe 0.5
  }

  it should "return committee weight for Committee" in {
    VoteWeights().forVoteType(VoteType.Committee) shouldBe 0.4
  }

  it should "return motionToRecommit weight for Recommit" in {
    VoteWeights().forVoteType(VoteType.Recommit) shouldBe 0.6
  }

  it should "return other weight for Other" in {
    VoteWeights().forVoteType(VoteType.Other) shouldBe 0.5
  }

  // ── VoteWeights PureConfig ──

  "VoteWeights PureConfig" should "load from HOCON with default-matching values" in {
    val result = ConfigSource.string(voteWeightsHocon).load[VoteWeights]
    result shouldBe Right(VoteWeights())
  }

  it should "load with overridden values" in {
    val hocon =
      """
        |passage = 0.95
        |conference-report = 1.0
        |cloture = 0.7
        |veto-override = 0.9
        |amendment = 0.5
        |committee = 0.4
        |motion-to-recommit = 0.6
        |other = 0.3
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[VoteWeights]
    val _      = result.isRight shouldBe true
    result.foreach { w =>
      val _ = w.passage shouldBe 0.95
      val _ = w.cloture shouldBe 0.7
      val _ = w.other shouldBe 0.3
      w.conferenceReport shouldBe 1.0
    }
  }

  // ── CommitteeAttributionWeights ──

  "CommitteeAttributionWeights defaults" should "match CommitteePosition weights" in {
    val w = CommitteeAttributionWeights()
    val _ = w.chairman shouldBe 1.0
    val _ = w.rankingMember shouldBe 0.7
    val _ = w.viceChairman shouldBe 0.6
    w.member shouldBe 0.4
  }

  "CommitteeAttributionWeights PureConfig" should "round-trip through HOCON" in {
    val hocon =
      """
        |chairman = 0.9
        |ranking-member = 0.8
        |vice-chairman = 0.5
        |member = 0.3
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[CommitteeAttributionWeights]
    result shouldBe Right(CommitteeAttributionWeights(0.9, 0.8, 0.5, 0.3))
  }

  // ── AnalysisPassConfig ──

  "AnalysisPassConfig defaults" should "use haiku/sonnet/opus with all passes enabled" in {
    val c = AnalysisPassConfig()
    val _ = c.pass1Model shouldBe "claude-3-haiku"
    val _ = c.pass2Model shouldBe "claude-3-sonnet"
    val _ = c.pass3Model shouldBe "claude-3-opus"
    val _ = c.pass1Enabled shouldBe true
    val _ = c.pass2Enabled shouldBe true
    c.pass3Enabled shouldBe true
  }

  "AnalysisPassConfig PureConfig" should "load with one pass disabled" in {
    val hocon =
      """
        |pass-1-model = "claude-3-haiku"
        |pass-2-model = "claude-3-sonnet"
        |pass-3-model = "claude-3-opus"
        |pass-1-enabled = true
        |pass-2-enabled = false
        |pass-3-enabled = true
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[AnalysisPassConfig]
    val _      = result.isRight shouldBe true
    result.foreach { c =>
      val _ = c.pass1Enabled shouldBe true
      val _ = c.pass2Enabled shouldBe false
      c.pass3Enabled shouldBe true
    }
  }

  // ── PipelineConfig[T] ──

  "PipelineConfig[TestAppConfig]" should "load common fields with defaults" in {
    val hocon  = pipelineConfigHocon("x = 42")
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    val _      = result.isRight shouldBe true
    result.foreach { pc =>
      val _ = pc.parallelism shouldBe 4
      val _ = pc.batchSize shouldBe 100
      val _ = pc.pageSize shouldBe 250
      val _ = pc.retry shouldBe RetryConfig()
      pc.appConfig shouldBe TestAppConfig(42)
    }
  }

  it should "load with custom parallelism and batchSize" in {
    val hocon =
      s"""
         |parallelism = 8
         |batch-size = 50
         |page-size = 500
         |retry {
         |  $retryHocon
         |}
         |app-config {
         |  x = 7
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    val _      = result.isRight shouldBe true
    result.foreach { pc =>
      val _ = pc.parallelism shouldBe 8
      val _ = pc.batchSize shouldBe 50
      val _ = pc.pageSize shouldBe 500
      pc.appConfig shouldBe TestAppConfig(7)
    }
  }

  it should "work with different T types using same common fields" in {
    val hocon =
      s"""
         |parallelism = 4
         |batch-size = 100
         |page-size = 250
         |retry {
         |  $retryHocon
         |}
         |app-config {
         |  $voteWeightsHocon
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[VoteWeights]]
    val _      = result.isRight shouldBe true
    result.foreach { pc =>
      val _ = pc.parallelism shouldBe 4
      pc.appConfig shouldBe VoteWeights()
    }
  }

  it should "load retry config correctly" in {
    val hocon =
      """
        |parallelism = 4
        |batch-size = 100
        |page-size = 250
        |retry {
        |  max-retries = 5
        |  initial-backoff-ms = 20
        |  max-backoff-ms = 30000
        |  backoff-multiplier = 3.0
        |}
        |app-config {
        |  x = 1
        |}
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    val _      = result.isRight shouldBe true
    result.foreach { pc =>
      val _ = pc.retry.maxRetries shouldBe 5
      val _ = pc.retry.initialBackoffMs shouldBe 20L
      val _ = pc.retry.maxBackoffMs shouldBe 30000L
      pc.retry.backoffMultiplier shouldBe 3.0
    }
  }

  it should "load with AnalysisPassConfig as appConfig" in {
    val hocon =
      """
        |parallelism = 2
        |batch-size = 50
        |page-size = 100
        |retry {
        |  max-retries = 3
        |  initial-backoff-ms = 10
        |  max-backoff-ms = 60000
        |  backoff-multiplier = 2.0
        |}
        |app-config {
        |  pass-1-model = "claude-3-haiku"
        |  pass-2-model = "claude-3-sonnet"
        |  pass-3-model = "claude-3-opus"
        |  pass-1-enabled = true
        |  pass-2-enabled = true
        |  pass-3-enabled = false
        |}
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[AnalysisPassConfig]]
    val _      = result.isRight shouldBe true
    result.foreach { pc =>
      val _ = pc.parallelism shouldBe 2
      val _ = pc.batchSize shouldBe 50
      pc.appConfig.pass3Enabled shouldBe false
    }
  }

  it should "load with CommitteeAttributionWeights as appConfig" in {
    val hocon =
      """
        |parallelism = 4
        |batch-size = 100
        |page-size = 250
        |retry {
        |  max-retries = 3
        |  initial-backoff-ms = 10
        |  max-backoff-ms = 60000
        |  backoff-multiplier = 2.0
        |}
        |app-config {
        |  chairman = 1.0
        |  ranking-member = 0.7
        |  vice-chairman = 0.6
        |  member = 0.4
        |}
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[CommitteeAttributionWeights]]
    val _      = result.isRight shouldBe true
    result.foreach(pc => pc.appConfig.chairman shouldBe 1.0)
  }

  it should "support copy with modified fields" in {
    val pc = PipelineConfig[TestAppConfig](
      parallelism = 4,
      batchSize = 100,
      pageSize = 250,
      retry = RetryConfig(),
      appConfig = TestAppConfig(1),
    )
    val copied = pc.copy(parallelism = 16, batchSize = 200)
    val _      = copied.parallelism shouldBe 16
    val _      = copied.batchSize shouldBe 200
    val _      = copied.pageSize shouldBe 250
    copied.appConfig shouldBe TestAppConfig(1)
  }

  it should "implement equals and hashCode" in {
    val a = PipelineConfig[TestAppConfig](appConfig = TestAppConfig(1))
    val b = PipelineConfig[TestAppConfig](appConfig = TestAppConfig(1))
    val c = PipelineConfig[TestAppConfig](appConfig = TestAppConfig(2))
    val _ = a shouldBe b
    val _ = a should not be c
    a.hashCode shouldBe b.hashCode
  }

  it should "implement toString" in {
    val pc = PipelineConfig[TestAppConfig](appConfig = TestAppConfig(42))
    val _  = pc.toString should include("42")
    pc.toString should include("PipelineConfig")
  }

  "PipelineConfig with invalid HOCON" should "return a PureConfig error for non-numeric parallelism" in {
    val hocon =
      s"""
         |parallelism = "not-a-number"
         |batch-size = 100
         |page-size = 250
         |retry {
         |  $retryHocon
         |}
         |app-config {
         |  x = 1
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for non-numeric batchSize" in {
    val hocon =
      s"""
         |parallelism = 4
         |batch-size = "bad"
         |page-size = 250
         |retry {
         |  $retryHocon
         |}
         |app-config {
         |  x = 1
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for non-numeric pageSize" in {
    val hocon =
      s"""
         |parallelism = 4
         |batch-size = 100
         |page-size = "bad"
         |retry {
         |  $retryHocon
         |}
         |app-config {
         |  x = 1
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for invalid retry config" in {
    val hocon =
      """
        |parallelism = 4
        |batch-size = 100
        |page-size = 250
        |retry {
        |  max-retries = "bad"
        |  initial-backoff-ms = 10
        |  max-backoff-ms = 60000
        |  backoff-multiplier = 2.0
        |}
        |app-config {
        |  x = 1
        |}
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for non-numeric backoff-multiplier" in {
    val hocon =
      """
        |parallelism = 4
        |batch-size = 100
        |page-size = 250
        |retry {
        |  max-retries = 3
        |  initial-backoff-ms = 10
        |  max-backoff-ms = 60000
        |  backoff-multiplier = "bad"
        |}
        |app-config {
        |  x = 1
        |}
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for missing parallelism" in {
    val hocon =
      s"""
         |batch-size = 100
         |page-size = 250
         |retry {
         |  $retryHocon
         |}
         |app-config {
         |  x = 1
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for missing batch-size" in {
    val hocon =
      s"""
         |parallelism = 4
         |page-size = 250
         |retry {
         |  $retryHocon
         |}
         |app-config {
         |  x = 1
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for missing page-size" in {
    val hocon =
      s"""
         |parallelism = 4
         |batch-size = 100
         |retry {
         |  $retryHocon
         |}
         |app-config {
         |  x = 1
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for missing retry" in {
    val hocon =
      """
        |parallelism = 4
        |batch-size = 100
        |page-size = 250
        |app-config {
        |  x = 1
        |}
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for missing app-config" in {
    val hocon =
      s"""
         |parallelism = 4
         |batch-size = 100
         |page-size = 250
         |retry {
         |  $retryHocon
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for completely empty config" in {
    val hocon  = ""
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for wrong retry structure" in {
    val hocon =
      """
        |parallelism = 4
        |batch-size = 100
        |page-size = 250
        |retry = "not-an-object"
        |app-config {
        |  x = 1
        |}
        |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for wrong app-config structure" in {
    val hocon =
      s"""
         |parallelism = 4
         |batch-size = 100
         |page-size = 250
         |retry {
         |  $retryHocon
         |}
         |app-config = "not-an-object"
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

  it should "return a PureConfig error for invalid app-config type" in {
    val hocon =
      s"""
         |parallelism = 4
         |batch-size = 100
         |page-size = 250
         |retry {
         |  $retryHocon
         |}
         |app-config {
         |  x = "not-an-int"
         |}
         |""".stripMargin
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isLeft shouldBe true
  }

}
