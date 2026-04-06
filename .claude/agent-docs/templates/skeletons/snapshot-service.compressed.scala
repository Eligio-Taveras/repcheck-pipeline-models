<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/skeletons/snapshot-service.scala -->

```markdown
# RepCheck Snapshot Service

**Purpose:** Dedicated first pipeline step. Reads AlloyDB → serializes JSON → writes versioned snapshots to GCS. Downstream apps read snapshots only, never query live DBs mid-run.

**Key Design:**
- Dedicated service runs first in state machine
- All snapshots versioned (semver in filename)
- Snapshot path passed via PipelineEvent
- Apps read at startup; exception: pipeline run status writes to DB
- Workflow definitions published from CI to GCS

## Data Structures

```scala
final case class SnapshotManifest(
    runId: String,
    snapshotVersion: String,
    createdAt: Instant,
    sourceDetails: Map[String, String], // e.g., "firestore" -> "bills collection"
    basePath: String                     // e.g., "snapshots/run-abc-123/"
)

object SnapshotManifest {
  given Encoder[SnapshotManifest] = io.circe.generic.semiauto.deriveEncoder
  given Decoder[SnapshotManifest] = io.circe.generic.semiauto.deriveDecoder
}
```

## SnapshotService Trait

```scala
trait SnapshotService[F[_]] {
  /** Create snapshots for a pipeline run. @return SnapshotManifest with GCS base path */
  def createSnapshots(runId: String, version: String): F[SnapshotManifest]

  /** Read a specific snapshot file, deserializing to T. */
  def readSnapshot[T: Decoder](manifest: SnapshotManifest, fileName: String): F[T]

  /** Read a list of items from a snapshot file. */
  def readSnapshotList[T: Decoder](manifest: SnapshotManifest, fileName: String): F[List[T]]
}
```

## Implementation

```scala
object SnapshotService {

  final case class SnapshotConfig(
      bucket: String = "repcheck-snapshots",
      tables: List[String] = List(
        "bills", "members", "votes", "vote_positions", "amendments", "bill_analyses"
      )
  )

  def make[F[_]: Async](
      config: SnapshotConfig,
      gcsClient: GcsClient[F],
      alloyDbReader: AlloyDbSnapshotReader[F]
  ): SnapshotService[F] =
    new SnapshotService[F] {

      def createSnapshots(runId: String, version: String): F[SnapshotManifest] = {
        val basePath = s"snapshots/$runId"

        for {
          createdAt <- Clock[F].realTimeInstant
          // Snapshot AlloyDB tables → GCS
          _ <- config.tables.traverse { table =>
            snapshotAlloyDbTable(table, s"$basePath/$table-$version.json")
          }
          // Snapshot prompt fragments (pin current versions)
          _ <- snapshotPromptFragments(s"$basePath/prompts/", version)

          manifest = SnapshotManifest(
            runId = runId,
            snapshotVersion = version,
            createdAt = createdAt,
            sourceDetails = Map(
              "alloydb" -> config.tables.mkString(", "),
              "prompts" -> s"pinned at $version"
            ),
            basePath = basePath
          )

          // Write manifest as last file (signals snapshot complete)
          _ <- gcsClient.writeJson(
            config.bucket,
            s"$basePath/manifest-$version.json",
            manifest
          )
        } yield manifest
      }

      def readSnapshot[T: Decoder](
          manifest: SnapshotManifest,
          fileName: String
      ): F[T] =
        gcsClient.readJson[T](config.bucket, s"${manifest.basePath}/$fileName")

      def readSnapshotList[T: Decoder](
          manifest: SnapshotManifest,
          fileName: String
      ): F[List[T]] =
        gcsClient.readJson[List[T]](config.bucket, s"${manifest.basePath}/$fileName")

      private def snapshotAlloyDbTable(table: String, gcsPath: String): F[Unit] =
        for {
          rows <- alloyDbReader.readTable(table)
          _ <- gcsClient.writeJson(config.bucket, gcsPath, rows)
        } yield ()

      private def snapshotPromptFragments(basePath: String, version: String): F[Unit] =
        for {
          billPaths <- gcsClient.listVersioned("repcheck-prompt-configs", "bills/", version)
          userPaths <- gcsClient.listVersioned("repcheck-prompt-configs", "users/", version)
          _ <- (billPaths ++ userPaths).traverse { srcPath =>
            for {
              bytes <- gcsClient.readBytes("repcheck-prompt-configs", srcPath)
              fileName = srcPath.split("/").last
              _ <- gcsClient.writeJson(
                config.bucket,
                s"$basePath$fileName",
                io.circe.parser.parse(new String(bytes)).getOrElse(io.circe.Json.Null)
              )
            } yield ()
          }
        } yield ()
    }
}
```

## Data Source Reader Traits

```scala
/** Reads AlloyDB tables for snapshotting. */
trait AlloyDbSnapshotReader[F[_]] {
  def readTable(table: String): F[io.circe.Json]
}
```
```