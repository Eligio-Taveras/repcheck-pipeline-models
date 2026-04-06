<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/scala-code-patterns/05-streaming.md -->

# Scala Code Patterns: Fail-and-Continue Streaming & FS2

## Core Types (in `repcheck-pipeline-models`)

```scala
import java.time.Instant

enum ResultStatus {
  case Succeeded, Failed
}

case class ProcessingResult(
  entityId: String,
  status: ResultStatus,
  errorMessage: Option[String],
  timestamp: Instant
)

case class PipelineRunSummary(
  runId: String,
  pipelineName: String,
  totalProcessed: Int,
  succeeded: Int,
  failed: Int,
  startedAt: Instant,
  completedAt: Instant
)
```

## Stream Pattern

```scala
import fs2.Stream

def processStream[F[_]: Async](
  items: Stream[F, LegislativeBillApiDTO],
  persist: LegislativeBillDO => F[Unit],
  writeResult: ProcessingResult => F[Unit]
): Stream[F, Unit] =
  items.evalMap { dto =>
    val entityId = s"${dto.`type`}-${dto.number}-${dto.congress}"
    processSingleItem(dto, persist)
      .as(ProcessingResult(entityId, ResultStatus.Succeeded, None, Instant.now()))
      .recover { case e: Throwable =>
        ProcessingResult(entityId, ResultStatus.Failed, Some(e.getMessage), Instant.now())
      }
      .flatMap(writeResult)
  }

private def processSingleItem[F[_]: Async](
  dto: LegislativeBillApiDTO,
  persist: LegislativeBillDO => F[Unit]
): F[Unit] = {
  val bill = dto.toDO()
  persist(bill)
}
```

## Aggregator

```scala
def summarizeRun[F[_]: Sync](
  runId: String,
  pipelineName: String,
  startedAt: Instant,
  readResults: String => F[List[ProcessingResult]],
  writeSummary: PipelineRunSummary => F[Unit]
): F[PipelineRunSummary] =
  for {
    results <- readResults(runId)
    summary = PipelineRunSummary(
      runId = runId,
      pipelineName = pipelineName,
      totalProcessed = results.size,
      succeeded = results.count(_.status == ResultStatus.Succeeded),
      failed = results.count(_.status == ResultStatus.Failed),
      startedAt = startedAt,
      completedAt = Instant.now()
    )
    _ <- writeSummary(summary)
  } yield summary
```

## Fail-and-Continue Rules
- Never accumulate items or results in memory across stream elements
- Write each `ProcessingResult` to AlloyDB `processing_results` table immediately, keyed by `run_id`/`entity_id`
- Release item and result from memory after writing
- Aggregator reads persisted results post-stream to build summary
- Write `PipelineRunSummary` to AlloyDB `pipeline_runs` table
- Store only lightweight metadata in `ProcessingResult` — never full payload

## FS2 Stream Construction

```scala
import fs2.Stream

def streamBills[F[_]: Async: Network](
  api: LegislativeBillsApi[F],
  fromDate: Option[ZonedDateTime]
): Stream[F, LegislativeBillApiDTO] =
  api.streamBatch(fromDateTime = fromDate)
    .flatMap(batch => Stream.emits(batch.bills))

def streamAllPages[F[_]: Async: Network](
  api: LegislativeBillsApi[F],
  fromDate: Option[ZonedDateTime],
  offset: Int = 0,
  pageSize: Int = 250
): Stream[F, LegislativeBillApiDTO] =
  Stream.eval(api.getObjects(offset, fromDate)).flatMap { batch =>
    val items = Stream.emits(batch.bills)
    if (batch.lengthRetrieved >= pageSize)
      items ++ streamAllPages(api, fromDate, offset + pageSize, pageSize)
    else
      items
  }
```

## Processing Pattern

```scala
streamAllPages(api, fromDate)
  .evalMap { dto =>
    processAndPersist(dto)
  }
  .evalMap { result =>
    writeProcessingResult(result)
  }
  .compile
  .drain
```

## FS2 Rules
- Use `evalMap` for per-item effectful processing
- Use `Stream.emits` to flatten batches into individual items
- Use `.compile.drain` when results are persisted per-item
- Recursive streams for pagination — stop when `lengthRetrieved < pageSize`
- Never use `.compile.toList` on unbounded streams — drain or fold with bounded accumulation