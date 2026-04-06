package repcheck.pipeline.models.changes

import io.circe.parser.decode
import io.circe.syntax._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChangeDetectionSpec extends AnyFlatSpec with Matchers {

  // ── ChangeDetectionStrategy Circe round-trips (4 tests) ──

  "ChangeDetectionStrategy.UpdateDateComparison" should "round-trip through JSON" in {
    val strategy = ChangeDetectionStrategy.UpdateDateComparison
    val json     = strategy.asJson.noSpaces
    val result   = decode[ChangeDetectionStrategy](json)
    result shouldBe Right(strategy)
  }

  "ChangeDetectionStrategy.ExistenceCheck" should "round-trip through JSON" in {
    val strategy = ChangeDetectionStrategy.ExistenceCheck
    val json     = strategy.asJson.noSpaces
    val result   = decode[ChangeDetectionStrategy](json)
    result shouldBe Right(strategy)
  }

  "ChangeDetectionStrategy.FieldLevelDiff" should "round-trip through JSON" in {
    val strategy = ChangeDetectionStrategy.FieldLevelDiff
    val json     = strategy.asJson.noSpaces
    val result   = decode[ChangeDetectionStrategy](json)
    result shouldBe Right(strategy)
  }

  "ChangeDetectionStrategy.AlwaysNew" should "round-trip through JSON" in {
    val strategy = ChangeDetectionStrategy.AlwaysNew
    val json     = strategy.asJson.noSpaces
    val result   = decode[ChangeDetectionStrategy](json)
    result shouldBe Right(strategy)
  }

  // ── PersistenceStrategy Circe round-trips (3 tests) ──

  "PersistenceStrategy.Upsert" should "round-trip through JSON" in {
    val strategy = PersistenceStrategy.Upsert
    val json     = strategy.asJson.noSpaces
    val result   = decode[PersistenceStrategy](json)
    result shouldBe Right(strategy)
  }

  "PersistenceStrategy.UpsertWithHistory" should "round-trip through JSON" in {
    val strategy = PersistenceStrategy.UpsertWithHistory
    val json     = strategy.asJson.noSpaces
    val result   = decode[PersistenceStrategy](json)
    result shouldBe Right(strategy)
  }

  "PersistenceStrategy.AppendOnly" should "round-trip through JSON" in {
    val strategy = PersistenceStrategy.AppendOnly
    val json     = strategy.asJson.noSpaces
    val result   = decode[PersistenceStrategy](json)
    result shouldBe Right(strategy)
  }

  // ── ChangeDetectionResult emitEvent logic (3 tests) ──

  "ChangeDetectionResult" should "emit event when changed is true and config has eventType" in {
    val result = ChangeDetectionResult(
      entity = "test-entity",
      entityId = "id-1",
      config = EntityChangeConfig.Vote,
      changed = true,
    )
    result.emitEvent shouldBe Some("vote.recorded")
  }

  it should "not emit event when changed is true but config has no eventType" in {
    val result = ChangeDetectionResult(
      entity = "test-entity",
      entityId = "id-2",
      config = EntityChangeConfig.BillMetadata,
      changed = true,
    )
    result.emitEvent shouldBe None
  }

  it should "not emit event when changed is false even if config has eventType" in {
    val result = ChangeDetectionResult(
      entity = "test-entity",
      entityId = "id-3",
      config = EntityChangeConfig.Vote,
      changed = false,
    )
    result.emitEvent shouldBe None
  }

  // ── EntityChangeConfig for each entity in rules table (7 tests) ──

  "EntityChangeConfig.BillMetadata" should "have correct strategy, persistence, and event" in {
    EntityChangeConfig.BillMetadata.entityType shouldBe "bill_metadata"
    EntityChangeConfig.BillMetadata.detectionStrategy shouldBe ChangeDetectionStrategy.UpdateDateComparison
    EntityChangeConfig.BillMetadata.persistenceStrategy shouldBe PersistenceStrategy.Upsert
    EntityChangeConfig.BillMetadata.eventType shouldBe None
  }

  "EntityChangeConfig.BillTextVersion" should "have correct strategy, persistence, and event" in {
    EntityChangeConfig.BillTextVersion.entityType shouldBe "bill_text_version"
    EntityChangeConfig.BillTextVersion.detectionStrategy shouldBe ChangeDetectionStrategy.UpdateDateComparison
    EntityChangeConfig.BillTextVersion.persistenceStrategy shouldBe PersistenceStrategy.AppendOnly
    EntityChangeConfig.BillTextVersion.eventType shouldBe Some("bill.text.available")
  }

  "EntityChangeConfig.Vote" should "have correct strategy, persistence, and event" in {
    EntityChangeConfig.Vote.entityType shouldBe "vote"
    EntityChangeConfig.Vote.detectionStrategy shouldBe ChangeDetectionStrategy.UpdateDateComparison
    EntityChangeConfig.Vote.persistenceStrategy shouldBe PersistenceStrategy.UpsertWithHistory
    EntityChangeConfig.Vote.eventType shouldBe Some("vote.recorded")
  }

  "EntityChangeConfig.Member" should "have correct strategy, persistence, and event" in {
    EntityChangeConfig.Member.entityType shouldBe "member"
    EntityChangeConfig.Member.detectionStrategy shouldBe ChangeDetectionStrategy.FieldLevelDiff
    EntityChangeConfig.Member.persistenceStrategy shouldBe PersistenceStrategy.Upsert
    EntityChangeConfig.Member.eventType shouldBe None
  }

  "EntityChangeConfig.Amendment" should "have correct strategy, persistence, and event" in {
    EntityChangeConfig.Amendment.entityType shouldBe "amendment"
    EntityChangeConfig.Amendment.detectionStrategy shouldBe ChangeDetectionStrategy.UpdateDateComparison
    EntityChangeConfig.Amendment.persistenceStrategy shouldBe PersistenceStrategy.Upsert
    EntityChangeConfig.Amendment.eventType shouldBe None
  }

  "EntityChangeConfig.Analysis" should "have correct strategy, persistence, and event" in {
    EntityChangeConfig.Analysis.entityType shouldBe "analysis"
    EntityChangeConfig.Analysis.detectionStrategy shouldBe ChangeDetectionStrategy.AlwaysNew
    EntityChangeConfig.Analysis.persistenceStrategy shouldBe PersistenceStrategy.AppendOnly
    EntityChangeConfig.Analysis.eventType shouldBe Some("analysis.completed")
  }

  "EntityChangeConfig.Score" should "have correct strategy, persistence, and event" in {
    EntityChangeConfig.Score.entityType shouldBe "score"
    EntityChangeConfig.Score.detectionStrategy shouldBe ChangeDetectionStrategy.AlwaysNew
    EntityChangeConfig.Score.persistenceStrategy shouldBe PersistenceStrategy.Upsert
    EntityChangeConfig.Score.eventType shouldBe None
  }

  // ── Full round-trip serialization of ChangeDetectionResult[String] ──

  "ChangeDetectionResult[String]" should "round-trip through JSON" in {
    val result = ChangeDetectionResult(
      entity = "some-bill-text",
      entityId = "bill-123",
      config = EntityChangeConfig.BillTextVersion,
      changed = true,
    )
    val json    = result.asJson.noSpaces
    val decoded = decode[ChangeDetectionResult[String]](json)
    decoded shouldBe Right(result)
  }

  // ── EntityChangeConfig Circe round-trip ──

  "EntityChangeConfig" should "round-trip BillMetadata through JSON" in {
    val json    = EntityChangeConfig.BillMetadata.asJson.noSpaces
    val decoded = decode[EntityChangeConfig](json)
    decoded shouldBe Right(EntityChangeConfig.BillMetadata)
  }

  it should "round-trip Vote through JSON" in {
    val json    = EntityChangeConfig.Vote.asJson.noSpaces
    val decoded = decode[EntityChangeConfig](json)
    decoded shouldBe Right(EntityChangeConfig.Vote)
  }

  it should "round-trip Analysis through JSON" in {
    val json    = EntityChangeConfig.Analysis.asJson.noSpaces
    val decoded = decode[EntityChangeConfig](json)
    decoded shouldBe Right(EntityChangeConfig.Analysis)
  }

  it should "round-trip BillTextVersion through JSON" in {
    val json    = EntityChangeConfig.BillTextVersion.asJson.noSpaces
    val decoded = decode[EntityChangeConfig](json)
    decoded shouldBe Right(EntityChangeConfig.BillTextVersion)
  }

  it should "round-trip Member through JSON" in {
    val json    = EntityChangeConfig.Member.asJson.noSpaces
    val decoded = decode[EntityChangeConfig](json)
    decoded shouldBe Right(EntityChangeConfig.Member)
  }

  it should "round-trip Amendment through JSON" in {
    val json    = EntityChangeConfig.Amendment.asJson.noSpaces
    val decoded = decode[EntityChangeConfig](json)
    decoded shouldBe Right(EntityChangeConfig.Amendment)
  }

  it should "round-trip Score through JSON" in {
    val json    = EntityChangeConfig.Score.asJson.noSpaces
    val decoded = decode[EntityChangeConfig](json)
    decoded shouldBe Right(EntityChangeConfig.Score)
  }

  // ── EntityChangeConfig decodeAccumulating ──

  "EntityChangeConfig decodeAccumulating" should "decode valid JSON via accumulating" in {
    val json   = EntityChangeConfig.Vote.asJson
    val result = implicitly[io.circe.Decoder[EntityChangeConfig]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  it should "accumulate errors for invalid JSON" in {
    val json   = io.circe.parser.parse("""{"entityType":123}""").getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[EntityChangeConfig]].decodeAccumulating(json.hcursor)
    result.isInvalid shouldBe true
  }

  // ── Negative: unknown strategy string → parse error ──

  "ChangeDetectionStrategy decoder" should "fail for unknown strategy string" in {
    val json   = """"UnknownStrategy""""
    val result = decode[ChangeDetectionStrategy](json)
    result.isLeft shouldBe true
  }

  // ── Negative: unknown persistence string → parse error ──

  "PersistenceStrategy decoder" should "fail for unknown persistence string" in {
    val json   = """"UnknownPersistence""""
    val result = decode[PersistenceStrategy](json)
    result.isLeft shouldBe true
  }

}
