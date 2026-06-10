package repcheck.pipeline.models.errors

import java.time.Instant

import io.circe.parser.decode
import io.circe.syntax._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * ErrorClass / ErrorClassifier / RetryConfig / RetryWrapper migrated to repcheck-utils (com.repcheck.utils.errors) —
 * their specs live there. DeadLetterMessage stays here (messaging concern).
 */
class ErrorHandlingSpec extends AnyFlatSpec with Matchers {

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
    val _      = result.isRight shouldBe true
    result.foreach { m =>
      val _ = m.eventType shouldBe "vote.recorded"
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
        val _ = m.originalPayload shouldBe "payload"
        val _ = m.eventType shouldBe "event.type"
        val _ = m.failureReason shouldBe "reason"
        val _ = m.failureTimestamp shouldBe ts
        m.retryCount shouldBe 5
      },
    )
  }

}
