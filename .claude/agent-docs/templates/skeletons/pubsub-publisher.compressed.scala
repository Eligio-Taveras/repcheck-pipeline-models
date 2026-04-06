<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/skeletons/pubsub-publisher.scala -->

# RepCheck Skeleton: Pub/Sub Publisher

**Purpose:** PipelineEvent[T] envelope for inter-service messages + tagless-final publisher wrapping Google Pub/Sub Java SDK.

**Key Design:** ResourceRequirements travel with message for orchestrator capacity checks; payload T requires own Circe codecs; Google SDK wrapped in Sync[F].blocking; centralized retry wrapper.

---

## ResourceRequirements

```scala
final case class ResourceRequirements(
    maxCpu: String = "1",        // e.g., "1", "2", "4"
    maxMemory: String = "512Mi"  // e.g., "512Mi", "1Gi", "4Gi"
)

object ResourceRequirements {
  given Encoder[ResourceRequirements] = deriveEncoder
  given Decoder[ResourceRequirements] = deriveDecoder
}
```

---

## PipelineEvent[T] Envelope

**Structure:** eventId (UUID), eventType (discriminator), timestamp, source (originating service), retryCount, maxRetries (dead-letter ceiling), resources (Cloud Run capacity hints), payload (business data T).

**Codecs:** Auto-derived via Circe; payload T must have Encoder/Decoder.

```scala
final case class PipelineEvent[T](
    eventId: UUID,
    eventType: String,
    timestamp: Instant,
    source: String,
    retryCount: Int = 0,
    maxRetries: Int = 5,
    resources: ResourceRequirements = ResourceRequirements(),
    payload: T
)

object PipelineEvent {
  given [T: Encoder]: Encoder[PipelineEvent[T]] = deriveEncoder
  given [T: Decoder]: Decoder[PipelineEvent[T]] = deriveDecoder

  def create[T](
      eventType: String,
      source: String,
      payload: T,
      resources: ResourceRequirements = ResourceRequirements(),
      maxRetries: Int = 5
  ): PipelineEvent[T] =
    PipelineEvent(
      eventId = UUID.randomUUID(),
      eventType = eventType,
      timestamp = Instant.now(),
      source = source,
      retryCount = 0,
      maxRetries = maxRetries,
      resources = resources,
      payload = payload
    )
}
```

---

## PubSubPublisher Trait

Tagless-final. One instance per topic.

```scala
trait PubSubPublisher[F[_]] {
  def publish[T: Encoder](event: PipelineEvent[T]): F[String]
}
```

---

## Google Pub/Sub SDK Implementation

**Config:** projectId, topicId, optional RetryConfig.

**Lifecycle:** Resource manages SDK Publisher acquire/release.

**publish:** Serializes event to JSON, wraps in PubsubMessage with attributes (eventType, source, retryCount), publishes via SDK, applies centralized retry wrapper.

```scala
object PubSubPublisher {

  final case class PubSubPublisherConfig(
      projectId: String,
      topicId: String,
      retry: RetryConfig = RetryConfig()
  )

  def make[F[_]: Sync](
      config: PubSubPublisherConfig,
      classifier: ErrorClassifier
  ): Resource[F, PubSubPublisher[F]] =
    Resource
      .make(
        Sync[F].blocking {
          // TODO: Replace with real Publisher.newBuilder(...).build()
          ???: com.google.cloud.pubsub.v1.Publisher
        }
      )(publisher =>
        Sync[F].blocking {
          // TODO: publisher.shutdown()
          // TODO: publisher.awaitTermination(30, TimeUnit.SECONDS)
        }
      )
      .map { sdkPublisher =>
        new PubSubPublisher[F] {
          def publish[T: Encoder](event: PipelineEvent[T]): F[String] = {
            val operation: F[String] = Sync[F].blocking {
              val json = event.asJson.noSpaces
              val data =
                com.google.protobuf.ByteString.copyFromUtf8(json)
              val message = com.google.pubsub.v1.PubsubMessage
                .newBuilder()
                .setData(data)
                .putAttributes("eventType", event.eventType)
                .putAttributes("source", event.source)
                .putAttributes("retryCount", event.retryCount.toString)
                .build()

              // TODO: sdkPublisher.publish(message).get()
              // Returns the message ID string
              ???
            }

            RetryWrapper.withRetry[F, String](
              config.retry,
              classifier,
              s"pubsub-publish(${config.topicId})"
            )(operation)
          }
        }
      }
}
```

**Usage:**
```scala
PubSubPublisher.make[IO](config, PubSubErrorClassifier).use { publisher =>
  publisher.publish(event)
}
```