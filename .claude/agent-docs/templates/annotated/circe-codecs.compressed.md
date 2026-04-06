<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/annotated/circe-codecs.md -->

# Pattern: Circe Codecs

## When To Use This Pattern
- Any DTO needing JSON serialization/deserialization
- Custom types (dates, enums, URIs) not natively supported by Circe
- Bridging external API field names to internal naming conventions

## Approach 1: Semi-Auto Derivation (Internal DTOs)

Use when field names in JSON match case class field names exactly.

```scala
// File: gov-apis/src/main/scala/congress/gov/DTOs/Internal/LegislativeBillDTO.scala

package congress.gov.DTOs.Internal

import cats.effect.Concurrent

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import org.http4s.EntityDecoder

import congress.gov.DTOs.LegislativeBillDTO

object LegislativeBillDTO {
  // Semi-auto derivation generates encoder/decoder from case class fields. Field names in JSON must EXACTLY match case class field names.
  implicit val decoder: Decoder[LegislativeBillDTO] =
    deriveDecoder[LegislativeBillDTO]
  implicit val encoder: Encoder[LegislativeBillDTO] =
    deriveEncoder[LegislativeBillDTO]

  // http4s bridge — creates EntityDecoder from Circe Decoder for automatic JSON response parsing.
  implicit def entityDecoder[F[_]: Concurrent]
  : EntityDecoder[F, LegislativeBillDTO] =
    org.http4s.circe.jsonOf[F, LegislativeBillDTO]
}
```

## Approach 2: Manual Decoder (External API Field Mapping)

Use when external API field names don't match your case class.

```scala
// File: gov-apis/src/main/scala/congress/gov/DTOs/GovSite/LegislativeBillDTOGovSite.scala

package congress.gov.DTOs.GovSite

import java.time.{LocalDate, ZonedDateTime}

import io.circe.Decoder

import congress.gov.DTOs.{LatestAction, LegislativeBillDTO}

object LegislativeBillDTOGovSite {

  import common.Serializers.*

  // Manual HCursor decoder for field name mapping. c.downField("name").as[Type] navigates and decodes. For-comprehension works because Decoder.Result is Either — any field failure aborts with descriptive error.
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
        congress, bill_id, bill_type, latestAction,
        originChamber, originChamberCode, title,
        updateDate, updateDateIncludingText, url
      )
}
```

## Approach 3: Custom Codecs for Domain Types

Use for types not natively supported (dates, enums, URIs).

```scala
// File: gov-apis/src/main/scala/common/Serializers.scala

package common

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import io.circe.{Decoder, Encoder}

object Serializers {
  // Decoder.decodeString.emap: decode raw String, transform with emap, Left = error message. scala.util.Try wraps parsing exceptions.
  implicit val zonedDateTimeDecoder: Decoder[ZonedDateTime] =
    Decoder.decodeString.emap { str =>
      scala.util
        .Try {
          ZonedDateTime.parse(str, DateTimeFormatter.ISO_ZONED_DATE_TIME)
        }
        .toEither
        .left
        .map(error => {
          "Failed to decode datetime: " + error.getMessage
        })
    }

  // Encoder.encodeString.contramap: start with String encoder, transform input (ZonedDateTime → String), then encode.
  implicit val zonedDateTimeEncoder: Encoder[ZonedDateTime] =
    Encoder.encodeString.contramap[ZonedDateTime](dt =>
      val strVal = dt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
      strVal
    )
}
```

## Approach 4: Custom Encoder Reshaping Fields

Use when serialized JSON shape differs from case class shape.

```scala
// File: gov-apis/src/main/scala/congress/gov/DTOs/LatestAction.scala

package congress.gov.DTOs

import java.time.{LocalDate, LocalTime, ZoneId, ZonedDateTime}
import scala.jdk.CollectionConverters._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}

case class LatestAction(actionDate: ZonedDateTime, text: String)

object LatestAction {
  // Encoder splits single ZonedDateTime field into two JSON fields (actionDate, actionTime). Json.obj creates JSON object from tuples; .asJson uses Circe encoder for each value.
  implicit val encoder: Encoder[LatestAction] = (a: LatestAction) => {
    Json.obj(
      ("actionDate", a.actionDate.toLocalDate.asJson),
      ("actionTime", a.actionDate.toLocalTime.asJson),
      ("text", Json.fromString(a.text))
    )
  }

  // Decoder combines two JSON fields (actionDate, optional actionTime) into single ZonedDateTime. .fold provides midnight default if actionTime absent.
  implicit val decoder: Decoder[LatestAction] = (c: HCursor) => {
    for {
      actionLocalDate <- c.downField("actionDate").as[LocalDate]
      actionLocalTime <- c.downField("actionTime").as[Option[LocalTime]]
      text <- c.downField("text").as[String]
    } yield {
      val actionTime = actionLocalTime.fold(LocalTime.of(0, 0, 0))(identity)
      val actionDate =
        ZonedDateTime.of(actionLocalDate, actionTime, ZoneId.of("UTC"))
      LatestAction(actionDate, text)
    }
  }
}
```

## Decision Guide

| Situation | Approach | Import |
|-----------|----------|--------|
| Field names match exactly | Semi-auto derivation | `io.circe.generic.semiauto._` |
| External API field names differ | Manual HCursor decoder | `io.circe.Decoder` |
| Custom types (dates, enums) | `emap` / `contramap` | `io.circe.{Decoder, Encoder}` |
| JSON shape differs from case class | Manual `Json.obj` encoder | `io.circe.{Encoder, Json}` |
| http4s integration | `jsonOf[F, T]` EntityDecoder | `org.http4s.circe.jsonOf` |

## How to Create Codecs for a New DTO

```scala
// For a new VoteDTO with matching field names:
object VoteDTO {
  implicit val decoder: Decoder[VoteDTO] = deriveDecoder[VoteDTO]
  implicit val encoder: Encoder[VoteDTO] = deriveEncoder[VoteDTO]
  implicit def entityDecoder[F[_]: Concurrent]: EntityDecoder[F, VoteDTO] =
    org.http4s.circe.jsonOf[F, VoteDTO]
}

// For a GovSite decoder with different field names:
object VoteDTOGovSite {
  implicit val decoder: Decoder[VoteDTO] = (c: HCursor) =>
    for {
      voteId    <- c.downField("roll_call").as[String]
      chamber   <- c.downField("chamber").as[String]
      result    <- c.downField("result").as[String]
    } yield VoteDTO(voteId, chamber, result)
}
```