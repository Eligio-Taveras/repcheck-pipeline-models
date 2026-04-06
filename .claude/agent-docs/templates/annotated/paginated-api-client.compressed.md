<!-- GENERATED FILE — DO NOT EDIT. Source: docs/templates/annotated/paginated-api-client.md -->

# Pattern: Paginated API Client

## When To Use This Pattern
- Any new Congress.gov API endpoint (votes, members, amendments)
- Any external API that returns paginated results
- When you need memory-efficient streaming of large result sets

## Source Files
- `gov-apis/src/main/scala/apiBase/PagingApiBase.scala` — generic trait
- `gov-apis/src/main/scala/congress/gov/apis/LegislativeBillsApi.scala` — concrete implementation

---

## The Generic Pagination Trait

```scala
// File: gov-apis/src/main/scala/apiBase/PagingApiBase.scala

package apiBase

import java.time.ZonedDateTime
import cats.Semigroup
import cats.effect.{Async, Resource}
import cats.syntax.all._
import org.http4s.Uri.Scheme
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Uri}
import fs2.io.net.Network
import apiBase.PagingApiBase.SortOrder
import common.Constants

// Unique, flat exception for protocol validation failures
case class InvalidProtocol(protocol: String)
    extends Exception(s"Invalid protocol: $protocol")

// Marker trait: DTO reports how many items it contains; tells pagination loop when to stop
trait PagedObject {
  def lengthRetrieved: Int
}

// Core pattern: tagless-final trait parameterized over effect type F[_] and response DTO type T
// Type constraints: F must have Async & Network; T must extend PagedObject & have Semigroup
trait PagingApiBase[F[_]: Async: Network, T <: PagedObject: Semigroup] {

  // Protected abstract members: define API contract every implementation must provide
  protected def protocol: String           // "https"
  protected def host: String               // "api.congress.gov"
  protected def apiKey: String             // API authentication key
  protected def path: String               // "/v3/bill"
  protected def pageSize: Int              // Items per page
  implicit protected def decoder: EntityDecoder[F, T]  // Parse HTTP response → T
  protected def emberClient: Resource[F, Client[F]]    // HTTP client with lifecycle management

  // Public entry point: fetches ALL pages and combines them; uses sensible defaults
  def defaultCall(
      fromDateTime: Option[ZonedDateTime],
      toDateTime: Option[ZonedDateTime]
  ): F[T] = {
    getAll(
      fromDateTime = fromDateTime,
      toDateTime = toDateTime,
      sortOrder = Some(SortOrder.UpdateDateDesc)
    )
  }

  // Public entry point: fetches ONE page as FS2 Stream; used for page-by-page streaming
  def streamBatch(
      fromDateTime: Option[ZonedDateTime],
      toDateTime: Option[ZonedDateTime],
      offset: Int = 0,
      sortOrder: Option[SortOrder] = Some(SortOrder.UpdateDateDesc)
  ): fs2.Stream[F, T] = {
    fs2.Stream.eval(
      getObjects(Some(offset), Some(pageSize), fromDateTime, toDateTime, sortOrder)
    )
  }

  // Stack-safe pagination using Async[F].tailRecM: avoids stack overflow
  // Accumulator: (offset, Option[T]) — current position and accumulated results
  // Semigroup[T] combines pages; stops when page.lengthRetrieved < pageSize
  private def getAll(
      fromDateTime: Option[ZonedDateTime],
      toDateTime: Option[ZonedDateTime],
      sortOrder: Option[SortOrder]
  ): F[T] = {
    Async[F].tailRecM[(Int, Option[T]), T]((0, None)) { case (offset, acc) =>
      getObjects(Some(offset), Some(pageSize), fromDateTime, toDateTime, sortOrder).flatMap {
        page =>
          val combined = acc.fold(page)(_ |+| page)

          if (page.lengthRetrieved < pageSize) {
            Async[F].delay {
              println(s"Retrieved ${page.lengthRetrieved} objects")
              System.out.flush()
            }.as(Right(combined))
          } else {
            Async[F].delay {
              println(s"Retrieved ${page.lengthRetrieved} objects," +
                s" accumulated ${combined.lengthRetrieved} objects total so far")
              System.out.flush()
            }.as(Left((offset + pageSize, Some(combined))))
          }
      }
    }
  }

  // Builds full URL with query parameters; makes HTTP call; Resource[F, Client[F]] ensures proper lifecycle
  private def getObjects(
      offset: Option[Int],
      limit: Option[Int],
      fromDateTime: Option[ZonedDateTime],
      toDateTime: Option[ZonedDateTime],
      sort: Option[SortOrder]
  ): F[T] = {
    emberClient.use[T] { client =>
      // Protocol validation uses Async[F].raiseError for fail-fast behavior
      val schemeF: F[Scheme] = Scheme.fromString(protocol).fold(
        _ => Async[F].raiseError[Scheme](InvalidProtocol(protocol)),
        _.pure[F]
      )
      schemeF.flatMap { scheme =>
        val uriBase: Uri = Uri(
          scheme = Some(scheme),
          authority = Some(Uri.Authority(host = Uri.RegName(host))),
          path = Uri.Path.unsafeFromString(path)
        )
        val uriBaseWithApiKey: Uri = uriBase.withQueryParam("api_key", apiKey)
        val uriWithOffset = offset.fold(uriBaseWithApiKey)(uriBaseWithApiKey.withQueryParam("offset", _))
        val uriWithLimit = limit.fold(uriWithOffset)(uriWithOffset.withQueryParam("limit", _))
        val uriWithFromDateTime = fromDateTime.fold(uriWithLimit) { d =>
          uriWithLimit.withQueryParam("fromDateTime", d.format(Constants.OutgoingDateTimeFormat))
        }
        val uriWithToDateTime = toDateTime.fold(uriWithFromDateTime) { d =>
          uriWithFromDateTime.withQueryParam("toDateTime", d.format(Constants.OutgoingDateTimeFormat))
        }
        val uriWithSort = sort.fold(uriWithToDateTime) { sortO =>
          uriWithToDateTime.withQueryParam("sort", sortO.value)
        }

        // client.expect[T] makes HTTP call and decodes response using implicit EntityDecoder[F, T]
        client.expect[T](uriWithSort)(decoder)
      }
    }
  }
}

// Companion object with supporting enum
object PagingApiBase {
  enum SortOrder(val value: String) {
    case UpdateDateAsc extends SortOrder("updateDate+asc")
    case UpdateDateDesc extends SortOrder("updateDate+desc")
  }

  object SortOrder {
    def fromString(s: String): Option[SortOrder] = s match {
      case UpdateDateAsc.value  => Some(UpdateDateAsc)
      case UpdateDateDesc.value => Some(UpdateDateDesc)
      case _                    => None
    }
  }
}
```

## The Concrete Implementation

```scala
// File: gov-apis/src/main/scala/congress/gov/apis/LegislativeBillsApi.scala

package congress.gov.apis

import cats.effect.{Async, Resource}
import cats.syntax.all._
import org.http4s.EntityDecoder
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import fs2.io.net.Network
import apiBase.PagingApiBase
import congress.gov.DTOs.LegislativeBillsDTO

// Minimal concrete implementation: all pagination logic inherited from PagingApiBase
// Provides only endpoint-specific values
class LegislativeBillsApi[F[_]: Async: Network](
    val protocol: String = "https",
    val host: String = "api.congress.gov",
    val apiKey: String = "DEMO_KEY",
    val path: String = "/v3/bill",
    val pageSize: Int = 250
) extends PagingApiBase[F, LegislativeBillsDTO] {
  override val emberClient: Resource[F, Client[F]] =
    EmberClientBuilder.default[F].build

  override val decoder: EntityDecoder[F, LegislativeBillsDTO] =
    LegislativeBillsDTO.entityDecoder
}

// Factory method pattern: LegislativeBillsApi[IO](apiKey, pageSize)
object LegislativeBillsApi {
  def apply[F[_]: Async: Network](
      apiKey: String,
      pageSize: Int
  ): F[LegislativeBillsApi[F]] = new LegislativeBillsApi[F](
    apiKey = apiKey,
    pageSize = pageSize
  ).pure[F]
}
```

## How to Create a New API Client

1. Define response DTO extending `PagedObject` with `Semigroup` instance
2. Create class extending `PagingApiBase[F, YourDTO]`
3. Provide 7 abstract members (protocol, host, apiKey, path, pageSize, decoder, emberClient)
4. Add companion object factory method
5. Pagination, URL building, HTTP handling all inherited

Example for Votes API:
```scala
class VotesApi[F[_]: Async: Network](
    val protocol: String = "https",
    val host: String = "api.congress.gov",
    val apiKey: String = "DEMO_KEY",
    val path: String = "/v3/vote",
    val pageSize: Int = 250
) extends PagingApiBase[F, VotesDTO] {
  override val emberClient: Resource[F, Client[F]] =
    EmberClientBuilder.default[F].build
  override val decoder: EntityDecoder[F, VotesDTO] =
    VotesDTO.entityDecoder
}
```