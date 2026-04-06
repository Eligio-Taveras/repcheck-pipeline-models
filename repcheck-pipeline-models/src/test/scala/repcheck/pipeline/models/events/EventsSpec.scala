package repcheck.pipeline.models.events

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.parser.decode
import io.circe.syntax._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EventsSpec extends AnyFlatSpec with Matchers {

  // ── Round-trip serialization for each of the 10 payloads ──

  "BillTextAvailableEvent" should "round-trip through JSON" in {
    val event  = BillTextAvailableEvent("hr-1", 118, "https://example.com/text", "xml", "ih", Some("rh"))
    val json   = event.asJson.noSpaces
    val result = decode[BillTextAvailableEvent](json)
    result shouldBe Right(event)
  }

  "BillTextIngestedEvent" should "round-trip through JSON" in {
    val versionId = UUID.randomUUID()
    val event     = BillTextIngestedEvent("hr-1", versionId, 118, "ih", None, Some("HSAG"))
    val json      = event.asJson.noSpaces
    val result    = decode[BillTextIngestedEvent](json)
    result shouldBe Right(event)
  }

  "DecompositionCompletedEvent" should "round-trip through JSON" in {
    val versionId = UUID.randomUUID()
    val event     = DecompositionCompletedEvent("hr-1", versionId, 5, 12)
    val json      = event.asJson.noSpaces
    val result    = decode[DecompositionCompletedEvent](json)
    result shouldBe Right(event)
  }

  "VoteRecordedEvent" should "round-trip through JSON" in {
    val event  = VoteRecordedEvent("vote-123", Some("hr-1"), "House", Instant.parse("2024-01-15T10:30:00Z"), 118, false)
    val json   = event.asJson.noSpaces
    val result = decode[VoteRecordedEvent](json)
    result shouldBe Right(event)
  }

  "AnalysisCompletedEvent" should "round-trip through JSON" in {
    val analysisId = UUID.randomUUID()
    val event      = AnalysisCompletedEvent("hr-1", analysisId, List("healthcare", "taxes"), List(1, 2, 3), "gpt-4")
    val json       = event.asJson.noSpaces
    val result     = decode[AnalysisCompletedEvent](json)
    result shouldBe Right(event)
  }

  "UserProfileUpdatedEvent" should "round-trip through JSON" in {
    val userId = UUID.randomUUID()
    val event  = UserProfileUpdatedEvent(userId, List("education", "defense"))
    val json   = event.asJson.noSpaces
    val result = decode[UserProfileUpdatedEvent](json)
    result shouldBe Right(event)
  }

  "MemberUpdatedEvent" should "round-trip through JSON" in {
    val event  = MemberUpdatedEvent("M000355")
    val json   = event.asJson.noSpaces
    val result = decode[MemberUpdatedEvent](json)
    result shouldBe Right(event)
  }

  "ScoringUserRequestedEvent" should "round-trip through JSON" in {
    val userId    = UUID.randomUUID()
    val requestId = UUID.randomUUID()
    val event     = ScoringUserRequestedEvent(userId, requestId, "web-app")
    val json      = event.asJson.noSpaces
    val result    = decode[ScoringUserRequestedEvent](json)
    result shouldBe Right(event)
  }

  "ScoringUserCompletedEvent" should "round-trip through JSON" in {
    val userId    = UUID.randomUUID()
    val requestId = UUID.randomUUID()
    val event     = ScoringUserCompletedEvent(userId, requestId, 42, "completed")
    val json      = event.asJson.noSpaces
    val result    = decode[ScoringUserCompletedEvent](json)
    result shouldBe Right(event)
  }

  "DailyIngestionStartEvent" should "round-trip through JSON" in {
    val event  = DailyIngestionStartEvent("2024-01-15", 118)
    val json   = event.asJson.noSpaces
    val result = decode[DailyIngestionStartEvent](json)
    result shouldBe Right(event)
  }

  // ── PipelineEvent envelope round-trip ──

  "PipelineEvent[BillTextAvailableEvent]" should "round-trip through JSON" in {
    val payload       = BillTextAvailableEvent("hr-1", 118, "https://example.com/text", "xml", "ih", Some("rh"))
    val eventId       = UUID.randomUUID()
    val correlationId = UUID.randomUUID()
    val ts            = Instant.parse("2024-06-01T12:00:00Z")
    val envelope = PipelineEvent("bill.text.available", payload, ts, eventId, correlationId, "bill-ingestion-pipeline")
    val json     = envelope.asJson.noSpaces
    val result   = decode[PipelineEvent[BillTextAvailableEvent]](json)
    result shouldBe Right(envelope)
  }

  // ── Optional field tests ──

  "VoteRecordedEvent" should "serialize with None billId for procedural votes" in {
    val event   = VoteRecordedEvent("vote-proc-1", None, "Senate", Instant.parse("2024-03-10T14:00:00Z"), 118, false)
    val json    = event.asJson.noSpaces
    val decoded = decode[VoteRecordedEvent](json)
    decoded shouldBe Right(event)
    decoded.fold(_ => fail("decode failed"), _.billId shouldBe None)
  }

  "BillTextAvailableEvent" should "serialize with None previousVersionCode for first version" in {
    val event   = BillTextAvailableEvent("s-42", 118, "https://example.com/text", "pdf", "ih", None)
    val json    = event.asJson.noSpaces
    val decoded = decode[BillTextAvailableEvent](json)
    decoded shouldBe Right(event)
    decoded.fold(_ => fail("decode failed"), _.previousVersionCode shouldBe None)
  }

  // ── EventTypes constant verification ──

  "EventTypes" should "have correct BillTextAvailable value" in {
    EventTypes.BillTextAvailable shouldBe "bill.text.available"
  }

  it should "have correct BillTextIngested value" in {
    EventTypes.BillTextIngested shouldBe "bill.text.ingested"
  }

  it should "have correct DecompositionCompleted value" in {
    EventTypes.DecompositionCompleted shouldBe "bill.decomposition.completed"
  }

  it should "have correct VoteRecorded value" in {
    EventTypes.VoteRecorded shouldBe "vote.recorded"
  }

  it should "have correct AnalysisCompleted value" in {
    EventTypes.AnalysisCompleted shouldBe "analysis.completed"
  }

  it should "have correct MemberUpdated value" in {
    EventTypes.MemberUpdated shouldBe "member.updated"
  }

  it should "have correct UserProfileUpdated value" in {
    EventTypes.UserProfileUpdated shouldBe "user.profile.updated"
  }

  it should "have correct ScoringUserRequested value" in {
    EventTypes.ScoringUserRequested shouldBe "scoring.user.requested"
  }

  it should "have correct ScoringUserCompleted value" in {
    EventTypes.ScoringUserCompleted shouldBe "scoring.user.completed"
  }

  it should "have correct DailyIngestionStart value" in {
    EventTypes.DailyIngestionStart shouldBe "daily.ingestion.start"
  }

  // ── PipelineEvent field verification ──

  "PipelineEvent fields" should "contain a UUID eventId" in {
    val payload       = MemberUpdatedEvent("M000355")
    val eventId       = UUID.randomUUID()
    val correlationId = UUID.randomUUID()
    val ts            = Instant.now()
    val envelope      = PipelineEvent("member.updated", payload, ts, eventId, correlationId, "member-pipeline")
    envelope.eventId shouldBe eventId
    envelope.eventId shouldBe a[UUID]
  }

  it should "contain an Instant timestamp" in {
    val payload  = MemberUpdatedEvent("M000355")
    val ts       = Instant.parse("2024-07-01T08:00:00Z")
    val envelope = PipelineEvent("member.updated", payload, ts, UUID.randomUUID(), UUID.randomUUID(), "member-pipeline")
    envelope.timestamp shouldBe ts
    envelope.timestamp shouldBe a[Instant]
  }

  it should "contain a UUID correlationId" in {
    val correlationId = UUID.randomUUID()
    val payload       = MemberUpdatedEvent("M000355")
    val envelope =
      PipelineEvent("member.updated", payload, Instant.now(), UUID.randomUUID(), correlationId, "member-pipeline")
    envelope.correlationId shouldBe correlationId
    envelope.correlationId shouldBe a[UUID]
  }

  // ── Negative: missing required field ──

  "Decoder" should "fail with field name when a required field is missing" in {
    val json   = """{"congress":118,"textUrl":"http://x","textFormat":"xml","versionCode":"ih"}"""
    val result = decode[BillTextAvailableEvent](json)
    result.isLeft shouldBe true
    result match {
      case Left(err) => err.getMessage should include("billId")
      case Right(_)  => fail("expected Left")
    }
  }

  // ── Negative: missing correlationId on PipelineEvent envelope ──

  "PipelineEvent decoder" should "fail when correlationId is missing" in {
    val json =
      """{"eventType":"member.updated","payload":{"memberId":"M000355"},"timestamp":"2024-06-01T12:00:00Z","eventId":"550e8400-e29b-41d4-a716-446655440000","source":"test"}"""
    val result = decode[PipelineEvent[MemberUpdatedEvent]](json)
    result.isLeft shouldBe true
    result match {
      case Left(err) => err.getMessage should include("correlationId")
      case Right(_)  => fail("expected Left")
    }
  }

  it should "fail when timestamp is malformed" in {
    val json =
      """{"eventType":"member.updated","payload":{"memberId":"M000355"},"timestamp":"not-a-timestamp","eventId":"550e8400-e29b-41d4-a716-446655440000","correlationId":"550e8400-e29b-41d4-a716-446655440001","source":"test"}"""
    val result = decode[PipelineEvent[MemberUpdatedEvent]](json)
    result.isLeft shouldBe true
    result match {
      case Left(err) => err.getMessage should include("Invalid timestamp")
      case Right(_)  => fail("expected Left")
    }
  }

  it should "fail when eventId is not a valid UUID" in {
    val json =
      """{"eventType":"member.updated","payload":{"memberId":"M000355"},"timestamp":"2024-06-01T12:00:00Z","eventId":"not-a-uuid","correlationId":"550e8400-e29b-41d4-a716-446655440001","source":"test"}"""
    val result = decode[PipelineEvent[MemberUpdatedEvent]](json)
    result.isLeft shouldBe true
    result match {
      case Left(err) => err.getMessage should include("Invalid UUID")
      case Right(_)  => fail("expected Left")
    }
  }

  // ── Decoder-from-string tests (coverage for decoder $anon classes) ──

  "BillTextIngestedEvent decoder" should "decode from raw JSON string" in {
    val versionId = "550e8400-e29b-41d4-a716-446655440000"
    val json =
      s"""{"billId":"hr-1","versionId":"$versionId","congress":118,"versionCode":"ih","previousVersionCode":null,"committeeCode":"HSAG"}"""
    val result = decode[BillTextIngestedEvent](json)
    result.isRight shouldBe true
    result.foreach { e =>
      e.billId shouldBe "hr-1"
      e.versionId shouldBe UUID.fromString(versionId)
      e.previousVersionCode shouldBe None
      e.committeeCode shouldBe Some("HSAG")
    }
  }

  "AnalysisCompletedEvent decoder" should "decode from raw JSON string" in {
    val analysisId = "550e8400-e29b-41d4-a716-446655440001"
    val json =
      s"""{"billId":"s-42","analysisId":"$analysisId","topics":["tax","health"],"passesExecuted":[1,2],"modelUsed":"claude-3"}"""
    val result = decode[AnalysisCompletedEvent](json)
    result.isRight shouldBe true
    result.foreach { e =>
      e.topics shouldBe List("tax", "health")
      e.passesExecuted shouldBe List(1, 2)
    }
  }

  "UserProfileUpdatedEvent decoder" should "decode from raw JSON string" in {
    val userId = "550e8400-e29b-41d4-a716-446655440002"
    val json   = s"""{"userId":"$userId","topicsChanged":["defense"]}"""
    val result = decode[UserProfileUpdatedEvent](json)
    result.isRight shouldBe true
    result.foreach(_.topicsChanged shouldBe List("defense"))
  }

  "ScoringUserRequestedEvent decoder" should "decode from raw JSON string" in {
    val userId    = "550e8400-e29b-41d4-a716-446655440003"
    val requestId = "550e8400-e29b-41d4-a716-446655440004"
    val json      = s"""{"userId":"$userId","requestId":"$requestId","source":"scheduled"}"""
    val result    = decode[ScoringUserRequestedEvent](json)
    result.isRight shouldBe true
    result.foreach(_.source shouldBe "scheduled")
  }

  "ScoringUserCompletedEvent decoder" should "decode from raw JSON string" in {
    val userId    = "550e8400-e29b-41d4-a716-446655440005"
    val requestId = "550e8400-e29b-41d4-a716-446655440006"
    val json      = s"""{"userId":"$userId","requestId":"$requestId","memberScoreCount":42,"status":"completed"}"""
    val result    = decode[ScoringUserCompletedEvent](json)
    result.isRight shouldBe true
    result.foreach { e =>
      e.memberScoreCount shouldBe 42
      e.status shouldBe "completed"
    }
  }

  "DecompositionCompletedEvent decoder" should "decode from raw JSON string" in {
    val versionId = "550e8400-e29b-41d4-a716-446655440007"
    val json      = s"""{"billId":"hr-99","versionId":"$versionId","conceptGroupCount":5,"sectionCount":12}"""
    val result    = decode[DecompositionCompletedEvent](json)
    result.isRight shouldBe true
    result.foreach { e =>
      e.conceptGroupCount shouldBe 5
      e.sectionCount shouldBe 12
    }
  }

  "VoteRecordedEvent decoder" should "decode from raw JSON with billId = null" in {
    val json =
      """{"voteId":"v-1","billId":null,"chamber":"House","date":"2024-01-15T10:30:00Z","congress":118,"isUpdate":false}"""
    val result = decode[VoteRecordedEvent](json)
    result.isRight shouldBe true
    result.foreach(_.billId shouldBe None)
  }

  "DailyIngestionStartEvent decoder" should "decode from raw JSON string" in {
    val json   = """{"date":"2024-01-15","congress":118}"""
    val result = decode[DailyIngestionStartEvent](json)
    result.isRight shouldBe true
    result.foreach { e =>
      e.date shouldBe "2024-01-15"
      e.congress shouldBe 118
    }
  }

  "MemberUpdatedEvent decoder" should "decode from raw JSON string" in {
    val json   = """{"memberId":"M000355"}"""
    val result = decode[MemberUpdatedEvent](json)
    result shouldBe Right(MemberUpdatedEvent("M000355"))
  }

  // ── EventType validation (criteria 10, 11, 16) ──

  "PipelineEvent.validatedDecoder" should "decode a valid eventType successfully" in {
    val payload       = MemberUpdatedEvent("M000355")
    val eventId       = UUID.randomUUID()
    val correlationId = UUID.randomUUID()
    val ts            = Instant.parse("2024-06-01T12:00:00Z")
    val envelope      = PipelineEvent("member.updated", payload, ts, eventId, correlationId, "test")
    val json          = envelope.asJson.noSpaces
    implicit val dec: io.circe.Decoder[PipelineEvent[MemberUpdatedEvent]] =
      PipelineEvent.validatedDecoder[MemberUpdatedEvent]
    val result = decode[PipelineEvent[MemberUpdatedEvent]](json)
    result shouldBe Right(envelope)
  }

  it should "reject an unknown eventType with a descriptive error listing valid types" in {
    val json =
      """{"eventType":"unknown.event","payload":{"memberId":"M000355"},"timestamp":"2024-06-01T12:00:00Z","eventId":"550e8400-e29b-41d4-a716-446655440000","correlationId":"550e8400-e29b-41d4-a716-446655440001","source":"test"}"""
    implicit val dec: io.circe.Decoder[PipelineEvent[MemberUpdatedEvent]] =
      PipelineEvent.validatedDecoder[MemberUpdatedEvent]
    val result = decode[PipelineEvent[MemberUpdatedEvent]](json)
    result.isLeft shouldBe true
    result match {
      case Left(err) =>
        err.getMessage should include("Unknown eventType")
        err.getMessage should include("unknown.event")
        err.getMessage should include("member.updated")
        err.getMessage should include("vote.recorded")
      case Right(_) => fail("expected Left")
    }
  }

  "PipelineEvent decoder" should "annotate payload errors with the eventType" in {
    val json =
      """{"eventType":"member.updated","payload":{"wrongField":"bad"},"timestamp":"2024-06-01T12:00:00Z","eventId":"550e8400-e29b-41d4-a716-446655440000","correlationId":"550e8400-e29b-41d4-a716-446655440001","source":"test"}"""
    val result = decode[PipelineEvent[MemberUpdatedEvent]](json)
    result.isLeft shouldBe true
    result match {
      case Left(err) =>
        err.getMessage should include("member.updated")
        err.getMessage should include("payload")
      case Right(_) => fail("expected Left")
    }
  }

  // ── decodeAccumulating coverage for each payload decoder ──

  "BillTextAvailableEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = BillTextAvailableEvent("hr-1", 118, "https://example.com/text", "xml", "ih", Some("rh"))
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[BillTextAvailableEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  it should "accumulate errors for invalid JSON" in {
    val json   = io.circe.parser.parse("""{"billId":123}""").getOrElse(io.circe.Json.Null)
    val result = implicitly[io.circe.Decoder[BillTextAvailableEvent]].decodeAccumulating(json.hcursor)
    result.isInvalid shouldBe true
  }

  "BillTextIngestedEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = BillTextIngestedEvent("hr-1", UUID.randomUUID(), 118, "ih", None, Some("HSAG"))
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[BillTextIngestedEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "DecompositionCompletedEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = DecompositionCompletedEvent("hr-1", UUID.randomUUID(), 5, 12)
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[DecompositionCompletedEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "VoteRecordedEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = VoteRecordedEvent("v-1", Some("hr-1"), "House", Instant.parse("2024-01-15T10:30:00Z"), 118, false)
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[VoteRecordedEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "AnalysisCompletedEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = AnalysisCompletedEvent("hr-1", UUID.randomUUID(), List("tax"), List(1, 2), "gpt-4")
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[AnalysisCompletedEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "UserProfileUpdatedEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = UserProfileUpdatedEvent(UUID.randomUUID(), List("defense"))
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[UserProfileUpdatedEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "MemberUpdatedEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = MemberUpdatedEvent("M000355")
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[MemberUpdatedEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "ScoringUserRequestedEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = ScoringUserRequestedEvent(UUID.randomUUID(), UUID.randomUUID(), "web")
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[ScoringUserRequestedEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "ScoringUserCompletedEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = ScoringUserCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), 42, "done")
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[ScoringUserCompletedEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  "DailyIngestionStartEvent decodeAccumulating" should "decode valid JSON" in {
    val event  = DailyIngestionStartEvent("2024-01-15", 118)
    val json   = event.asJson
    val result = implicitly[io.circe.Decoder[DailyIngestionStartEvent]].decodeAccumulating(json.hcursor)
    result.isValid shouldBe true
  }

  // ── EventTypes.allEventTypes ──

  "EventTypes.allEventTypes" should "contain all 10 known event types" in {
    EventTypes.allEventTypes.size shouldBe 10
    EventTypes.allEventTypes should contain("bill.text.available")
    EventTypes.allEventTypes should contain("vote.recorded")
    EventTypes.allEventTypes should contain("scoring.user.completed")
  }

  // ── Effectful factory ──

  "PipelineEvent.create" should "produce a PipelineEvent with unique eventId and recent timestamp via IO" in {
    val correlationId = UUID.randomUUID()
    val payload       = DailyIngestionStartEvent("2024-01-15", 118)
    val before        = Instant.now()
    val event = PipelineEvent
      .create[IO, DailyIngestionStartEvent](
        EventTypes.DailyIngestionStart,
        payload,
        correlationId,
        "daily-ingestion",
      )
      .unsafeRunSync()
    val after = Instant.now()

    event.eventType shouldBe "daily.ingestion.start"
    event.payload shouldBe payload
    event.correlationId shouldBe correlationId
    event.source shouldBe "daily-ingestion"
    event.eventId shouldBe a[java.util.UUID]
    (event.timestamp.equals(before) || event.timestamp.isAfter(before)) shouldBe true
    (event.timestamp.equals(after) || event.timestamp.isBefore(after)) shouldBe true
  }

  it should "generate distinct eventIds on successive calls" in {
    val correlationId = UUID.randomUUID()
    val payload       = MemberUpdatedEvent("M000355")
    val event1 = PipelineEvent
      .create[IO, MemberUpdatedEvent](
        EventTypes.MemberUpdated,
        payload,
        correlationId,
        "test",
      )
      .unsafeRunSync()
    val event2 = PipelineEvent
      .create[IO, MemberUpdatedEvent](
        EventTypes.MemberUpdated,
        payload,
        correlationId,
        "test",
      )
      .unsafeRunSync()
    event1.eventId should not be event2.eventId
  }

}
