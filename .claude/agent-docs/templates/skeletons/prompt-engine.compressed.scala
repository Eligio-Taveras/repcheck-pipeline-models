<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/skeletons/prompt-engine.scala -->

```markdown
# RepCheck Skeleton: Prompt Engine — Fragment Composition

**Repos:** repcheck-prompt-engine-bills, repcheck-prompt-engine-users

**Purpose:** Load prompt fragments from GCS, sort by priority, compose into final prompt string. All fragments live in GCS.

## Prompt Fragment

```scala
final case class PromptFragment(
    key: String,
    priority: Int,
    content: String,
    metadata: Option[PromptFragmentMetadata] = None
) {
  def render(): String = content
}

final case class PromptFragmentMetadata(
    description: String,
    author: String,
    version: String // semver, matches filename
)

object PromptFragment {
  given Encoder[PromptFragment] = deriveEncoder
  given Decoder[PromptFragment] = deriveDecoder
}

object PromptFragmentMetadata {
  given Encoder[PromptFragmentMetadata] = deriveEncoder
  given Decoder[PromptFragmentMetadata] = deriveDecoder
}
```

**Key:** Unique identifier | **Priority:** Lower values first | **Content:** Prompt text | **Metadata:** Optional tracing/debugging info. Override `render()` for variable interpolation.

## GCS Structure

```
repcheck-prompt-configs/
  bills/
    base-system-instruction-v1.0.0.json     (priority: 0)
    bill-structure-analysis-v1.1.0.json      (priority: 10)
    policy-area-classification-v1.0.0.json   (priority: 20)
    impact-assessment-v1.2.0.json            (priority: 30)
    pork-rider-detection-v1.0.0.json         (priority: 40)
    fiscal-estimate-v1.0.0.json              (priority: 50)
  users/
    base-system-instruction-v1.0.0.json      (priority: 0)
    preference-extraction-v1.0.0.json        (priority: 10)
    alignment-criteria-v1.1.0.json           (priority: 20)
```

**Fragment JSON example:**
```json
{
  "key": "base-system-instruction",
  "priority": 0,
  "content": "You are an expert legislative analyst...",
  "metadata": {
    "description": "Base system prompt for bill analysis",
    "author": "eligio",
    "version": "v1.0.0"
  }
}
```

## Prompt Builder

```scala
trait PromptBuilder[F[_]] {
  def assemble(category: String, version: String): F[String]
  def loadFragments(category: String, version: String): F[List[PromptFragment]]
}

object PromptBuilder {

  final case class PromptBuilderConfig(
      bucket: String = "repcheck-prompt-configs",
      defaultBillsVersion: String,
      defaultUsersVersion: String
  )

  def make[F[_]: Sync](
      gcsClient: GcsClient[F],
      config: PromptBuilderConfig
  ): PromptBuilder[F] =
    new PromptBuilder[F] {

      def assemble(category: String, version: String): F[String] =
        loadFragments(category, version).map { fragments =>
          fragments
            .sortBy(_.priority)
            .map(_.render())
            .mkString("\n\n")
        }

      def loadFragments(
          category: String,
          version: String
      ): F[List[PromptFragment]] =
        for {
          paths <- gcsClient.listVersioned(
            config.bucket,
            s"$category/",
            version
          )
          fragments <- paths.traverse { path =>
            gcsClient.readJson[PromptFragment](config.bucket, path)
          }
        } yield fragments
    }
}
```

**`assemble`:** Load fragments for category, sort by priority, render and join with newlines. **`loadFragments`:** Load raw fragments without assembly. **Config:** Bucket name + default versions (set by CI, overridable).

## GCS Client Interface

```scala
trait GcsClient[F[_]] {
  def readJson[T: Decoder](bucket: String, path: String): F[T]
  def listVersioned(bucket: String, prefix: String, version: String): F[List[String]]
}
```

Full implementation in gcs-reader.scala skeleton.

## User-Aware Prompt Assembly

```scala
trait UserAwarePromptBuilder[F[_]] {
  def assembleForUser(userId: String, version: String): F[String]
}
```

**Used by:** repcheck-prompt-engine-users for scoring pipeline. **Implementation pattern:** Load base fragments from GCS → load user prefs from AlloyDB → create dynamic fragment with user prefs → insert at priority position → render final prompt.
```
</markdown>