package repcheck.pipeline.models.config

import pureconfig.generic.derivation.default._
import pureconfig.{ConfigReader, ConfigSource}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.pipeline.models.errors.RetryConfig
import repcheck.shared.models.congress.vote.VoteType

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
    w.passage shouldBe 1.0
    w.conferenceReport shouldBe 1.0
    w.cloture shouldBe 0.8
    w.vetoOverride shouldBe 0.9
    w.amendment shouldBe 0.5
    w.committee shouldBe 0.4
    w.motionToRecommit shouldBe 0.6
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
    result.isRight shouldBe true
    result.foreach { w =>
      w.passage shouldBe 0.95
      w.cloture shouldBe 0.7
      w.other shouldBe 0.3
      w.conferenceReport shouldBe 1.0
    }
  }

  // ── CommitteeAttributionWeights ──

  "CommitteeAttributionWeights defaults" should "match CommitteePosition weights" in {
    val w = CommitteeAttributionWeights()
    w.chairman shouldBe 1.0
    w.rankingMember shouldBe 0.7
    w.viceChairman shouldBe 0.6
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
    c.pass1Model shouldBe "claude-3-haiku"
    c.pass2Model shouldBe "claude-3-sonnet"
    c.pass3Model shouldBe "claude-3-opus"
    c.pass1Enabled shouldBe true
    c.pass2Enabled shouldBe true
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
    result.isRight shouldBe true
    result.foreach { c =>
      c.pass1Enabled shouldBe true
      c.pass2Enabled shouldBe false
      c.pass3Enabled shouldBe true
    }
  }

  // ── PipelineConfig[T] ──

  "PipelineConfig[TestAppConfig]" should "load common fields with defaults" in {
    val hocon  = pipelineConfigHocon("x = 42")
    val result = ConfigSource.string(hocon).load[PipelineConfig[TestAppConfig]]
    result.isRight shouldBe true
    result.foreach { pc =>
      pc.parallelism shouldBe 4
      pc.batchSize shouldBe 100
      pc.pageSize shouldBe 250
      pc.retry shouldBe RetryConfig()
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
    result.isRight shouldBe true
    result.foreach { pc =>
      pc.parallelism shouldBe 8
      pc.batchSize shouldBe 50
      pc.pageSize shouldBe 500
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
    result.isRight shouldBe true
    result.foreach { pc =>
      pc.parallelism shouldBe 4
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
    result.isRight shouldBe true
    result.foreach { pc =>
      pc.retry.maxRetries shouldBe 5
      pc.retry.initialBackoffMs shouldBe 20L
      pc.retry.maxBackoffMs shouldBe 30000L
      pc.retry.backoffMultiplier shouldBe 3.0
    }
  }

  "PipelineConfig with invalid HOCON" should "return a PureConfig error" in {
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

}
