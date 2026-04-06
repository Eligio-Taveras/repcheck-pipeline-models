<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/annotated/dto-do-layering.md -->

# Pattern: DTO/DO Layering

## When To Use
- Any new data source (Congress.gov votes, members, amendments)
- External API response shape differs from storage shape
- Multiple external sources produce same logical entity

## Layer 1: GovSite DTO (External API Shape)

Maps Congress.gov API field names to internal canonical names. Only place external field names appear in codebase.

```scala
// File: gov-apis/src/main/scala/congress/gov/DTOs/GovSite/LegislativeBillDTOGovSite.scala

package congress.gov.DTOs.GovSite

import java.time.{LocalDate, ZonedDateTime}

import io.circe.Decoder

import congress.gov.DTOs.{LatestAction, LegislativeBillDTO}

object LegislativeBillDTOGovSite {

  import common.Serializers.*

  // Maps external field names to internal DTO shape. API: "number" → "bill_id", "type" → "bill_type"
  implicit val govSiteDecoder: Decoder[LegislativeBillDTO] =
    (c: io.circe.HCursor) =>
      for {
        congress <- c.downField("congress").as[Int]
        bill_id <- c.downField("number").as[String]
        bill_type <- c.downField("type").as[String]
        latestAction <- c.downField("latestAction").as[LatestAction]
        originChamber <- c.downField("originChamber").as[String]
        originChamberCode <- c.downField("originChamberCode").as[String]
        title <- c.downField("title").as[String]
        updateDate <- c.downField("updateDate").as[LocalDate]
        updateDateIncludingText <- c
          .downField("updateDateIncludingText")
          .as[ZonedDateTime]
        url <- c.downField("url").as[String]
      } yield LegislativeBillDTO(
        congress,
        bill_id,
        bill_type,
        latestAction,
        originChamber,
        originChamberCode,
        title,
        updateDate,
        updateDateIncludingText,
        url
      )
}
```

## Layer 2: Internal DTO (Canonical Shape)

Canonical naming convention. Raw types (String, LocalDate). Includes `toDO` method for validated conversion to Domain Object.

```scala
// File: gov-apis/src/main/scala/congress/gov/DTOs/LegislativeBillDTO.scala

package congress.gov.DTOs

import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId, ZonedDateTime}

import org.http4s.Uri

import congress.gov.DOs.LegislativeBillDO

case class LegislativeBillDTO(
    congress: Int,
    bill_id: String,
    bill_type: String,
    latestAction: LatestAction,
    originChamber: String,
    originChamberCode: String,
    title: String,
    updateDate: LocalDate,
    updateDateIncludingText: ZonedDateTime,
    url: String
) {
  def uri: Uri = Uri.unsafeFromString(url)

  // KEY CONVERSION: DTO → DO. Validates bill_type (BillTypes enum), converts LocalDate → ZonedDateTime UTC midnight, url String → Uri.
  // Returns Either[String, LegislativeBillDO] (Left = validation error, Right = validated DO).
  def toDO: Either[String, LegislativeBillDO] =
    BillTypes.fromString(bill_type).map { billType =>
      LegislativeBillDO(
        congress,
        bill_id,
        billType,
        latestAction,
        originChamber,
        originChamberCode,
        title,
        ZonedDateTime.of(LocalDateTime.of(updateDate, LocalTime.of(0, 0)), ZoneId.of("UTC")),
        updateDateIncludingText,
        Uri.unsafeFromString(url)
      )
    }
}
```

## Layer 2b: Internal Codecs (Semi-Auto Derivation)

Circe codecs for internal serialization (tests, caching). Separate file keeps DTO case class clean.

```scala
// File: gov-apis/src/main/scala/congress/gov/DTOs/Internal/LegislativeBillDTO.scala

package congress.gov.DTOs.Internal

import cats.effect.Concurrent

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import org.http4s.EntityDecoder

import congress.gov.DTOs.LegislativeBillDTO

// Semi-auto derivation for internal use. GovSite decoder (Layer 1) handles external deserialization with field name mapping.
object LegislativeBillDTO {
  implicit val decoder: Decoder[LegislativeBillDTO] =
    deriveDecoder[LegislativeBillDTO]
  implicit val encoder: Encoder[LegislativeBillDTO] =
    deriveEncoder[LegislativeBillDTO]

  // http4s EntityDecoder: client.expect[LegislativeBillDTO](uri) auto-parses JSON response.
  implicit def entityDecoder[F[_]: Concurrent]
  : EntityDecoder[F, LegislativeBillDTO] =
    org.http4s.circe.jsonOf[F, LegislativeBillDTO]
}
```

## Layer 3: Domain Object (Storage Shape)

Validated types (enums, Uri). Doobie persistence via `saveBill`. No manual Java Map.

```scala
// File: gov-apis/src/main/scala/congress/gov/DOs/LegislativeBillDO.scala

package congress.gov.DOs

import java.time.ZonedDateTime
import cats.effect.Async
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import org.http4s.Uri
import congress.gov.DTOs.{BillTypes, LatestAction}
import pipeline.models.Tables
import org.slf4j.Logger

case class LegislativeBillDO(
    congress: Int,
    billId: String,
    billType: BillTypes,
    latestAction: LatestAction,
    originChamber: String,
    originChamberCode: String,
    title: String,
    updateDate: ZonedDateTime,
    updateDateIncludingText: ZonedDateTime,
    url: Uri
) {
  // AlloyDB persistence. Doobie auto-derives Read/Write from case class. ON CONFLICT DO UPDATE = idempotent upsert.
  def saveBill[F[_]: Async](xa: Transactor[F], logger: Logger): F[Unit] = {
    val table = Tables.bills
    Async[F].blocking(logger.info(s"Saving legislative bill with URL: ${this.url}")) >>
      sql"""
        INSERT INTO #${table}
          (bill_id, congress, bill_type, latest_action_date, latest_action_text,
           origin_chamber, origin_chamber_code, title,
           update_date, update_date_including_text, url)
        VALUES
          ($billId, $congress, ${billType.toString},
           ${latestAction.actionDate}, ${latestAction.text},
           $originChamber, $originChamberCode, $title,
           $updateDate, $updateDateIncludingText, ${url.toString})
        ON CONFLICT (bill_id) DO UPDATE SET
          latest_action_date        = EXCLUDED.latest_action_date,
          latest_action_text        = EXCLUDED.latest_action_text,
          update_date               = EXCLUDED.update_date,
          update_date_including_text = EXCLUDED.update_date_including_text,
          url                       = EXCLUDED.url
      """.update.run.transact(xa).void
  }
}
```

## Flow

```
Congress.gov API JSON → GovSite Decoder (external field names) → Internal DTO (raw String/LocalDate)
→ toDO validation → Domain Object (validated enums/Uri, ZonedDateTime) → saveBill (Doobie SQL, AlloyDB)
```

## How to Create a New DTO/DO Layer

1. **GovSite Decoder**: `DTOs/GovSite/YourEntityDTOGovSite.scala` — manual Decoder mapping external field names
2. **Internal DTO**: `DTOs/YourEntityDTO.scala` — canonical names, `toDO: Either[String, YourEntityDO]`
3. **Internal Codecs**: `DTOs/Internal/YourEntityDTO.scala` — `deriveDecoder`/`deriveEncoder`
4. **Domain Object**: `DOs/YourEntityDO.scala` — validated types, Doobie persistence method

Example VoteDO:
```scala
case class VoteDTO(vote_id: String, chamber: String, result: String) {
  def toDO: Either[String, VoteDO] =
    for {
      chamber <- Chamber.fromString(chamber)
      result <- VoteResult.fromString(result)
    } yield VoteDO(vote_id, chamber, result)
}

case class VoteDO(voteId: String, chamber: Chamber, result: VoteResult) {
  def saveVote[F[_]: Async](xa: Transactor[F], logger: Logger): F[Unit] =
    sql"""INSERT INTO ${Tables.votes} (vote_id, chamber, result)
          VALUES ($voteId, ${chamber.toString}, ${result.toString})
          ON CONFLICT (vote_id) DO UPDATE SET result = EXCLUDED.result
       """.update.run.transact(xa).void
}
```