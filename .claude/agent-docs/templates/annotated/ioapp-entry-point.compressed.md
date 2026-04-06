<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/annotated/ioapp-entry-point.md -->

# Pattern: IOApp Entry Point

## When To Use This Pattern
- Every Cloud Run Job needs an IOApp entry point
- Each pipeline application (bill ingestion, vote ingestion, analysis, scoring)

## The Entry Point

```scala
// File: bill-identifier/src/main/scala/BillIdentifierApp.scala

import java.time.{ZoneId, ZonedDateTime}

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._

import doobie.Transactor
import doobie.hikari.HikariTransactor

import config.{BillIdentifierConfig, ConfigLoader}
import congress.gov.apis.LegislativeBillsApi
import org.slf4j.LoggerFactory

object BillIdentifierApp extends IOApp {

  private val logger = LoggerFactory.getLogger(getClass)

  override def run(args: List[String]): IO[ExitCode] = {
    val loadedConfig: IO[BillIdentifierConfig] = ConfigLoader.LoadConfig(args)
    val x: IO[Unit] = AlloyDbTransactor.make[IO](
      sys.env.getOrElse("DATABASE_URL", "jdbc:postgresql://localhost:5432/repcheck"),
      sys.env.getOrElse("DATABASE_USER", "repcheck"),
      sys.env.getOrElse("DATABASE_PASSWORD", "")
    ).use { xa =>
      for {
        config <- loadedConfig
        api    <- LegislativeBillsApi[IO](config.apiKey, config.pageSize)
        lookbackWindowStart <- IO {
          Option(
            ZonedDateTime
              .now(ZoneId.of("UTC"))
              .minusDays(config.billLookBackInDays)
          )
        }
        now <- IO {
          Option(ZonedDateTime.now(ZoneId.of("UTC")))
        }
        _ <- streamAllToAlloyDb(
          xa,
          api,
          lookbackWindowStart,
          now,
          config.pageSize
        ).compile.drain
      } yield ()
    }
    x
  }.as(ExitCode.Success)

  private def streamAllToAlloyDb(
      xa: Transactor[IO],
      api: LegislativeBillsApi[IO],
      lookbackWindowStart: Option[ZonedDateTime],
      now: Option[ZonedDateTime],
      offset: Int = 0
  ): fs2.Stream[IO, Unit] = {
    for {
      _ <- fs2.Stream.eval(IO(logger.info(s"Offset: $offset")))
      billsStream <- api.streamBatch(
        fromDateTime = lookbackWindowStart,
        toDateTime = now,
        offset = offset
      )
      recurse = billsStream.lengthRetrieved == api.pageSize
      _ <- fs2.Stream.eval(
        IO(
          logger.info(
            s"Recurse: $recurse, Bills Retrieved: ${billsStream.lengthRetrieved}, " +
            s"Page Size: ${api.pageSize}, Offset: $offset"
          )
        )
      )
      _ <- fs2.Stream.eval(
        fs2.Stream
          .emits(billsStream.bills)
          .covary[IO]
          .evalMap { bill =>
            IO.fromEither(bill.toDO.left.map(new IllegalArgumentException(_)))
              .flatMap(_.saveBill[IO](xa, logger))
          }
          .compile
          .drain
      )
      result <- fs2.Stream.emit[IO, Unit](()) ++
        (if (recurse) {
           streamAllToAlloyDb(
             xa,
             api,
             lookbackWindowStart,
             now,
             offset + api.pageSize
           )
         } else {
           fs2.Stream.empty: fs2.Stream[IO, Unit]
         })
    } yield result
  }
}
```

## Key Patterns

1. IOApp provides runtime — never create own `IORuntime`
2. For-comprehension for sequential steps — each `<-` can fail and short-circuits chain
3. Transactor as Resource — `.use { xa => ... }` keeps HikariCP pool alive for duration
4. Config first, then resources, then work — fail fast on invalid config
5. FS2 Stream for batch processing — compile at end, not middle
6. ExitCode via `.as(ExitCode.Success)` — transforms any result type
7. Environment variables with defaults — `sys.env.getOrElse` for GCP/DB config

## How to Create a New Pipeline Entry Point

```scala
object VoteIngestionApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    AlloyDbTransactor.make[IO](dbUrl, dbUser, dbPassword).use { xa =>
      for {
        config   <- ConfigLoader.LoadConfig(args)
        snapshot <- SnapshotService.loadSnapshot(config.snapshotPath)
        api      <- VotesApi[IO](config.apiKey, config.pageSize)
        _        <- VoteIngestionPipeline
                      .run(api, xa, snapshot)
                      .compile
                      .drain
      } yield ExitCode.Success
    }
  }
}
```