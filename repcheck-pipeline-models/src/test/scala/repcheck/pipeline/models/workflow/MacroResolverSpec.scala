package repcheck.pipeline.models.workflow

import java.time.Instant
import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.pipeline.models.launcher.{MacroContext, MacroResolver}

class MacroResolverSpec extends AnyFlatSpec with Matchers {

  private val fixedId        = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
  private val fixedTimestamp = Instant.parse("2024-06-15T10:30:00Z")
  private val fixedDate      = "2024-06-15"

  private val context = MacroContext(
    runId = fixedId,
    timestamp = fixedTimestamp,
    date = fixedDate,
    messagePayload = Map("naturalKey" -> "hr-1", "congress" -> "118"),
  )

  "MacroResolver" should "replace {{run_id}} with the run ID" in {
    val result = MacroResolver.resolve("job-{{run_id}}", context)
    result shouldBe s"job-$fixedId"
  }

  it should "replace {{date}} with the ISO date" in {
    val result = MacroResolver.resolve("snapshot-{{date}}", context)
    result shouldBe "snapshot-2024-06-15"
  }

  it should "replace {{timestamp}} with the ISO timestamp" in {
    val result = MacroResolver.resolve("ts={{timestamp}}", context)
    result shouldBe "ts=2024-06-15T10:30:00Z"
  }

  it should "replace {{message.naturalKey}} from payload map" in {
    val result = MacroResolver.resolve("processing {{message.naturalKey}}", context)
    result shouldBe "processing hr-1"
  }

  it should "leave {{message.missing}} unchanged when key not in payload" in {
    val result = MacroResolver.resolve("id={{message.missing}}", context)
    result shouldBe "id={{message.missing}}"
  }

  it should "leave {{unknown_macro}} unchanged" in {
    val result = MacroResolver.resolve("val={{unknown_macro}}", context)
    result shouldBe "val={{unknown_macro}}"
  }

  it should "return template unchanged when no macros present" in {
    val template = "no macros here"
    val result   = MacroResolver.resolve(template, context)
    result shouldBe template
  }

  it should "replace multiple macros in one template" in {
    val template = "{{run_id}}/{{date}}/{{message.naturalKey}}/{{message.congress}}"
    val result   = MacroResolver.resolve(template, context)
    result shouldBe s"$fixedId/2024-06-15/hr-1/118"
  }

}
