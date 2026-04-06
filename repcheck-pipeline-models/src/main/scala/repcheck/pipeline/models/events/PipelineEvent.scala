package repcheck.pipeline.models.events

import java.time.Instant
import java.util.UUID

import cats.effect.Sync
import cats.syntax.all._

import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}

final case class PipelineEvent[T](
  eventType: String,
  payload: T,
  timestamp: Instant,
  eventId: UUID,
  correlationId: UUID,
  source: String,
)

object PipelineEvent {

  def create[F[_]: Sync, T](
    eventType: String,
    payload: T,
    correlationId: UUID,
    source: String,
  ): F[PipelineEvent[T]] =
    for {
      id <- Sync[F].delay(UUID.randomUUID())
      ts <- Sync[F].delay(Instant.now())
    } yield PipelineEvent(eventType, payload, ts, id, correlationId, source)

  implicit def encoder[T](implicit enc: Encoder[T]): Encoder[PipelineEvent[T]] =
    Encoder.instance { event =>
      Json.obj(
        "eventType"     -> event.eventType.asJson,
        "payload"       -> event.payload.asJson,
        "timestamp"     -> event.timestamp.toString.asJson,
        "eventId"       -> event.eventId.toString.asJson,
        "correlationId" -> event.correlationId.toString.asJson,
        "source"        -> event.source.asJson,
      )
    }

  private def parseInstant(s: String, c: HCursor): Decoder.Result[Instant] =
    Either.catchNonFatal(Instant.parse(s)).left.map { e =>
      DecodingFailure(s"Invalid timestamp: ${e.getMessage}", c.downField("timestamp").history)
    }

  private def parseUUID(s: String, field: String, c: HCursor): Decoder.Result[UUID] =
    Either.catchNonFatal(UUID.fromString(s)).left.map { e =>
      DecodingFailure(s"Invalid UUID for $field: ${e.getMessage}", c.downField(field).history)
    }

  implicit def decoder[T](implicit dec: Decoder[T]): Decoder[PipelineEvent[T]] =
    Decoder.instance { (c: HCursor) =>
      for {
        eventType     <- c.downField("eventType").as[String]
        payload       <- c.downField("payload").as[T].left.map(err => annotatePayloadError(eventType, err))
        tsStr         <- c.downField("timestamp").as[String]
        timestamp     <- parseInstant(tsStr, c)
        eidStr        <- c.downField("eventId").as[String]
        eventId       <- parseUUID(eidStr, "eventId", c)
        cidStr        <- c.downField("correlationId").as[String]
        correlationId <- parseUUID(cidStr, "correlationId", c)
        source        <- c.downField("source").as[String]
      } yield PipelineEvent(eventType, payload, timestamp, eventId, correlationId, source)
    }

  private def annotatePayloadError(eventType: String, err: DecodingFailure): DecodingFailure =
    DecodingFailure(s"Failed to decode payload for eventType '$eventType': ${err.message}", err.history)

  /** Decoder that validates eventType against known EventTypes before decoding the payload. */
  def validatedDecoder[T](implicit dec: Decoder[T]): Decoder[PipelineEvent[T]] =
    Decoder.instance { (c: HCursor) =>
      for {
        eventType <- c.downField("eventType").as[String]
        _ <-
          if (EventTypes.allEventTypes.contains(eventType)) {
            Right(())
          } else {
            Left(
              DecodingFailure(
                s"Unknown eventType '$eventType'. Valid types: ${EventTypes.allEventTypes.toList.sorted.mkString(", ")}",
                c.downField("eventType").history,
              )
            )
          }
        payload       <- c.downField("payload").as[T].left.map(err => annotatePayloadError(eventType, err))
        tsStr         <- c.downField("timestamp").as[String]
        timestamp     <- parseInstant(tsStr, c)
        eidStr        <- c.downField("eventId").as[String]
        eventId       <- parseUUID(eidStr, "eventId", c)
        cidStr        <- c.downField("correlationId").as[String]
        correlationId <- parseUUID(cidStr, "correlationId", c)
        source        <- c.downField("source").as[String]
      } yield PipelineEvent(eventType, payload, timestamp, eventId, correlationId, source)
    }

}
