package repcheck.pipeline.models.errors

import java.time.Instant

import io.circe.parser.decode
import io.circe.syntax._

import pureconfig._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorHandlingSpec extends AnyFlatSpec with Matchers {

  // ── ErrorClass Circe round-trip ──

  "ErrorClass" should "encode Transient as string" in {
    val json = (ErrorClass.Transient: ErrorClass).asJson.noSpaces
    json shouldBe "\"Transient\""
  }

  it should "encode Systemic as string" in {
    val json = (ErrorClass.Systemic: ErrorClass).asJson.noSpaces
    json shouldBe "\"Systemic\""
  }

  it should "round-trip Transient through JSON" in {
    val original: ErrorClass = ErrorClass.Transient
    val json                 = original.asJson
    val decoded              = json.as[ErrorClass]
    decoded shouldBe Right(original)
  }

  it should "round-trip Systemic through JSON" in {
    val original: ErrorClass = ErrorClass.Systemic
    val json                 = original.asJson
    val decoded              = json.as[ErrorClass]
    decoded shouldBe Right(original)
  }

  it should "fail to decode unknown string" in {
    val result = decode[ErrorClass]("\"Unknown\"")
    result.isLeft shouldBe true
  }

  it should "parse from string case-insensitively" in {
    ErrorClass.fromString("transient") shouldBe Right(ErrorClass.Transient)
    ErrorClass.fromString("SYSTEMIC") shouldBe Right(ErrorClass.Systemic)
    ErrorClass.fromString("Transient") shouldBe Right(ErrorClass.Transient)
  }

  it should "return Left for unrecognized string" in {
    val result = ErrorClass.fromString("bogus")
    result.isLeft shouldBe true
  }

  // ── DefaultErrorClassifier ──

  "DefaultErrorClassifier" should "classify all errors as Systemic" in {
    DefaultErrorClassifier.classify(new RuntimeException("test")) shouldBe ErrorClass.Systemic
  }

  it should "classify NullPointerException as Systemic" in {
    DefaultErrorClassifier.classify(new NullPointerException("npe")) shouldBe ErrorClass.Systemic
  }

  it should "classify IllegalArgumentException as Systemic" in {
    DefaultErrorClassifier.classify(new IllegalArgumentException("bad arg")) shouldBe ErrorClass.Systemic
  }

  // ── HttpErrorClassifier ──

  "HttpErrorClassifier" should "classify 429 as Transient" in {
    val classifier = new HttpErrorClassifier(_ => Some(429))
    classifier.classify(new RuntimeException("rate limited")) shouldBe ErrorClass.Transient
  }

  it should "classify 500 as Transient" in {
    val classifier = new HttpErrorClassifier(_ => Some(500))
    classifier.classify(new RuntimeException("internal server error")) shouldBe ErrorClass.Transient
  }

  it should "classify 502 as Transient" in {
    val classifier = new HttpErrorClassifier(_ => Some(502))
    classifier.classify(new RuntimeException("bad gateway")) shouldBe ErrorClass.Transient
  }

  it should "classify 503 as Transient" in {
    val classifier = new HttpErrorClassifier(_ => Some(503))
    classifier.classify(new RuntimeException("service unavailable")) shouldBe ErrorClass.Transient
  }

  it should "classify 504 as Transient" in {
    val classifier = new HttpErrorClassifier(_ => Some(504))
    classifier.classify(new RuntimeException("gateway timeout")) shouldBe ErrorClass.Transient
  }

  it should "classify 400 as Systemic" in {
    val classifier = new HttpErrorClassifier(_ => Some(400))
    classifier.classify(new RuntimeException("bad request")) shouldBe ErrorClass.Systemic
  }

  it should "classify 401 as Systemic" in {
    val classifier = new HttpErrorClassifier(_ => Some(401))
    classifier.classify(new RuntimeException("unauthorized")) shouldBe ErrorClass.Systemic
  }

  it should "classify 403 as Systemic" in {
    val classifier = new HttpErrorClassifier(_ => Some(403))
    classifier.classify(new RuntimeException("forbidden")) shouldBe ErrorClass.Systemic
  }

  it should "classify 404 as Systemic" in {
    val classifier = new HttpErrorClassifier(_ => Some(404))
    classifier.classify(new RuntimeException("not found")) shouldBe ErrorClass.Systemic
  }

  it should "classify unknown status code as Systemic" in {
    val classifier = new HttpErrorClassifier(_ => Some(418))
    classifier.classify(new RuntimeException("i'm a teapot")) shouldBe ErrorClass.Systemic
  }

  it should "fall back to DefaultErrorClassifier when no status extracted" in {
    val classifier = new HttpErrorClassifier(_ => None)
    classifier.classify(new RuntimeException("no status")) shouldBe ErrorClass.Systemic
  }

  // ── RetryConfig PureConfig ──

  "RetryConfig" should "have correct defaults" in {
    val config = RetryConfig()
    config.maxRetries shouldBe 3
    config.initialBackoffMs shouldBe 10L
    config.maxBackoffMs shouldBe 60000L
    config.backoffMultiplier shouldBe 2.0
  }

  it should "round-trip through PureConfig" in {
    val source = ConfigSource.string(
      """|max-retries = 5
         |initial-backoff-ms = 100
         |max-backoff-ms = 30000
         |backoff-multiplier = 3.0
         |""".stripMargin
    )
    val result = source.load[RetryConfig]
    result shouldBe Right(RetryConfig(5, 100L, 30000L, 3.0))
  }

  it should "load defaults from PureConfig when fields are provided" in {
    val source = ConfigSource.string(
      """|max-retries = 3
         |initial-backoff-ms = 10
         |max-backoff-ms = 60000
         |backoff-multiplier = 2.0
         |""".stripMargin
    )
    val result = source.load[RetryConfig]
    result shouldBe Right(RetryConfig())
  }

  // ── DeadLetterMessage Circe round-trip ──

  "DeadLetterMessage" should "round-trip through JSON" in {
    val msg = DeadLetterMessage(
      originalPayload = """{"key":"value"}""",
      eventType = "bill.text.available",
      failureReason = "Connection timeout after 3 retries",
      failureTimestamp = Instant.parse("2024-06-01T12:00:00Z"),
      retryCount = 3,
    )
    val json    = msg.asJson.noSpaces
    val decoded = decode[DeadLetterMessage](json)
    decoded shouldBe Right(msg)
  }

  it should "decode from raw JSON string" in {
    val json =
      """{"originalPayload":"{\"k\":\"v\"}","eventType":"vote.recorded","failureReason":"timeout","failureTimestamp":"2024-06-01T12:00:00Z","retryCount":2}"""
    val result = decode[DeadLetterMessage](json)
    result.isRight shouldBe true
    result.foreach { m =>
      m.eventType shouldBe "vote.recorded"
      m.retryCount shouldBe 2
    }
  }

  "DeadLetterMessage decodeAccumulating" should "decode valid JSON via accumulating" in {
    val msg    = DeadLetterMessage("payload", "event.type", "reason", Instant.parse("2024-07-15T08:30:00Z"), 5)
    val json   = msg.asJson
    val result = implicitly[io.circe.Decoder[DeadLetterMessage]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  it should "accumulate errors for invalid JSON" in {
    val json   = io.circe.parser.parse("""{"originalPayload":123}""").getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[DeadLetterMessage]].decodeAccumulating(json.hcursor)
    result.isInvalid shouldBe true
  }

  it should "preserve all fields after deserialization" in {
    val ts      = Instant.parse("2024-07-15T08:30:00Z")
    val msg     = DeadLetterMessage("payload", "event.type", "reason", ts, 5)
    val decoded = decode[DeadLetterMessage](msg.asJson.noSpaces)
    decoded.fold(
      _ => fail("decode failed"),
      { m =>
        m.originalPayload shouldBe "payload"
        m.eventType shouldBe "event.type"
        m.failureReason shouldBe "reason"
        m.failureTimestamp shouldBe ts
        m.retryCount shouldBe 5
      },
    )
  }

}
