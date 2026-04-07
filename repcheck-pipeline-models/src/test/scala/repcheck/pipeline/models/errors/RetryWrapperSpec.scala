package repcheck.pipeline.models.errors

import java.util.UUID

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RetryWrapperSpec extends AnyFlatSpec with Matchers {

  private val noOpLog: (Int, Int, Long, ErrorClass, String, UUID) => IO[Unit] =
    (_, _, _, _, _, _) => IO.unit

  private val correlationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

  private val transientClassifier: ErrorClassifier = new ErrorClassifier {
    def classify(error: Throwable): ErrorClass = ErrorClass.Transient
  }

  private val systemicClassifier: ErrorClassifier = new ErrorClassifier {
    def classify(error: Throwable): ErrorClass = ErrorClass.Systemic
  }

  private def testErrorFactory(msg: String, cause: Throwable): Throwable =
    new RuntimeException(s"FactoryError: $msg", cause)

  private val defaultConfig: RetryConfig =
    RetryConfig(maxRetries = 3, initialBackoffMs = 1L, maxBackoffMs = 60000L, backoffMultiplier = 2.0)

  // ── Transient error retries then fails ──

  "RetryWrapper" should "retry transient errors up to maxRetries times then fail" in {
    val wrapper      = new RetryWrapper[IO](noOpLog)
    val attemptCount = Ref.unsafe[IO, Int](0)

    val operation =
      attemptCount.updateAndGet(_ + 1).flatMap(_ => IO.raiseError[String](new RuntimeException("transient failure")))

    val result = wrapper
      .withRetry(
        operation,
        defaultConfig,
        transientClassifier,
        testErrorFactory,
        correlationId,
      )
      .attempt
      .unsafeRunSync()

    val _ = result.isLeft shouldBe true
    attemptCount.get.unsafeRunSync() shouldBe 4 // 1 initial + 3 retries
  }

  it should "succeed on second attempt for transient error" in {
    val wrapper      = new RetryWrapper[IO](noOpLog)
    val attemptCount = Ref.unsafe[IO, Int](0)

    val operation = attemptCount.updateAndGet(_ + 1).flatMap { count =>
      if (count < 2) {
        IO.raiseError[String](new RuntimeException("transient failure"))
      } else {
        IO.pure("success")
      }
    }

    val result = wrapper
      .withRetry(
        operation,
        defaultConfig,
        transientClassifier,
        testErrorFactory,
        correlationId,
      )
      .unsafeRunSync()

    val _ = result shouldBe "success"
    attemptCount.get.unsafeRunSync() shouldBe 2
  }

  it should "fail immediately on systemic error without retry" in {
    val wrapper      = new RetryWrapper[IO](noOpLog)
    val attemptCount = Ref.unsafe[IO, Int](0)

    val operation =
      attemptCount.updateAndGet(_ + 1).flatMap(_ => IO.raiseError[String](new RuntimeException("systemic failure")))

    val result = wrapper
      .withRetry(
        operation,
        defaultConfig,
        systemicClassifier,
        testErrorFactory,
        correlationId,
      )
      .attempt
      .unsafeRunSync()

    val _ = result.isLeft shouldBe true
    attemptCount.get.unsafeRunSync() shouldBe 1
  }

  it should "not retry when maxRetries is 0" in {
    val wrapper      = new RetryWrapper[IO](noOpLog)
    val attemptCount = Ref.unsafe[IO, Int](0)
    val zeroRetryConfig =
      RetryConfig(maxRetries = 0, initialBackoffMs = 1L, maxBackoffMs = 60000L, backoffMultiplier = 2.0)

    val operation =
      attemptCount.updateAndGet(_ + 1).flatMap(_ => IO.raiseError[String](new RuntimeException("fail")))

    val result = wrapper
      .withRetry(
        operation,
        zeroRetryConfig,
        transientClassifier,
        testErrorFactory,
        correlationId,
      )
      .attempt
      .unsafeRunSync()

    val _ = result.isLeft shouldBe true
    attemptCount.get.unsafeRunSync() shouldBe 1
  }

  it should "raise exception from errorFactory after exhausting retries" in {
    val wrapper = new RetryWrapper[IO](noOpLog)

    val operation = IO.raiseError[String](new RuntimeException("original"))

    val result = wrapper
      .withRetry(
        operation,
        defaultConfig,
        transientClassifier,
        testErrorFactory,
        correlationId,
      )
      .attempt
      .unsafeRunSync()

    result match {
      case Left(err) =>
        err.getMessage should startWith("FactoryError:")
      case Right(_) =>
        fail("Expected failure")
    }
  }

  it should "pass the last Throwable as cause to errorFactory" in {
    val wrapper       = new RetryWrapper[IO](noOpLog)
    val originalError = new RuntimeException("the original cause")

    val operation = IO.raiseError[String](originalError)

    val result = wrapper
      .withRetry(
        operation,
        defaultConfig,
        transientClassifier,
        testErrorFactory,
        correlationId,
      )
      .attempt
      .unsafeRunSync()

    result match {
      case Left(err) =>
        err.getCause shouldBe originalError
      case Right(_) =>
        fail("Expected failure")
    }
  }

  it should "log retry attempts with correct parameters" in {
    val logEntries = Ref.unsafe[IO, List[(Int, Int, Long, ErrorClass, String, UUID)]](List.empty)

    val logFn: (Int, Int, Long, ErrorClass, String, UUID) => IO[Unit] =
      (attempt, maxRetries, delay, errorClass, msg, corrId) =>
        logEntries.update(_ :+ (attempt, maxRetries, delay, errorClass, msg, corrId))

    val wrapper = new RetryWrapper[IO](logFn)
    val config  = RetryConfig(maxRetries = 2, initialBackoffMs = 1L, maxBackoffMs = 60000L, backoffMultiplier = 2.0)

    val operation = IO.raiseError[String](new RuntimeException("fail"))

    val _ = wrapper
      .withRetry(
        operation,
        config,
        transientClassifier,
        testErrorFactory,
        correlationId,
      )
      .attempt
      .unsafeRunSync()

    val entries = logEntries.get.unsafeRunSync()
    val _       = entries.length shouldBe 2
    entries.foreach {
      case (_, maxR, _, ec, _, corrId) =>
        val _ = maxR shouldBe 2
        val _ = ec shouldBe ErrorClass.Transient
        corrId shouldBe correlationId
    }
  }

  // ── Backoff delay calculation ──

  it should "calculate correct backoff delays" in {
    val logEntries = Ref.unsafe[IO, List[(Int, Int, Long, ErrorClass, String, UUID)]](List.empty)

    val logFn: (Int, Int, Long, ErrorClass, String, UUID) => IO[Unit] =
      (attempt, maxRetries, delay, errorClass, msg, corrId) =>
        logEntries.update(_ :+ (attempt, maxRetries, delay, errorClass, msg, corrId))

    val wrapper = new RetryWrapper[IO](logFn)
    val config  = RetryConfig(maxRetries = 4, initialBackoffMs = 10L, maxBackoffMs = 60000L, backoffMultiplier = 2.0)

    val operation = IO.raiseError[String](new RuntimeException("fail"))

    val _ = wrapper
      .withRetry(
        operation,
        config,
        transientClassifier,
        testErrorFactory,
        correlationId,
      )
      .attempt
      .unsafeRunSync()

    val entries = logEntries.get.unsafeRunSync()
    val _       = entries.length shouldBe 4

    // delay = min(10 * 2^attempt, 60000)
    // attempt 0: min(10 * 1, 60000) = 10
    // attempt 1: min(10 * 2, 60000) = 20
    // attempt 2: min(10 * 4, 60000) = 40
    // attempt 3: min(10 * 8, 60000) = 80
    val _ = entries(0)._3 shouldBe 10L
    val _ = entries(1)._3 shouldBe 20L
    val _ = entries(2)._3 shouldBe 40L
    entries(3)._3 shouldBe 80L
  }

  it should "cap backoff at maxBackoffMs" in {
    val logEntries = Ref.unsafe[IO, List[(Int, Int, Long, ErrorClass, String, UUID)]](List.empty)

    val logFn: (Int, Int, Long, ErrorClass, String, UUID) => IO[Unit] =
      (attempt, maxRetries, delay, errorClass, msg, corrId) =>
        logEntries.update(_ :+ (attempt, maxRetries, delay, errorClass, msg, corrId))

    val wrapper = new RetryWrapper[IO](logFn)
    val config  = RetryConfig(maxRetries = 3, initialBackoffMs = 10000L, maxBackoffMs = 50L, backoffMultiplier = 2.0)

    val operation = IO.raiseError[String](new RuntimeException("fail"))

    val _ = wrapper
      .withRetry(
        operation,
        config,
        transientClassifier,
        testErrorFactory,
        correlationId,
      )
      .attempt
      .unsafeRunSync()

    val entries = logEntries.get.unsafeRunSync()
    entries.foreach {
      case (_, _, delay, _, _, _) =>
        delay shouldBe 50L
    }
  }

  it should "succeed immediately without retries on successful operation" in {
    val wrapper      = new RetryWrapper[IO](noOpLog)
    val attemptCount = Ref.unsafe[IO, Int](0)

    val operation = attemptCount.updateAndGet(_ + 1).map(_ => "immediate success")

    val result = wrapper
      .withRetry(
        operation,
        defaultConfig,
        transientClassifier,
        testErrorFactory,
        correlationId,
      )
      .unsafeRunSync()

    val _ = result shouldBe "immediate success"
    attemptCount.get.unsafeRunSync() shouldBe 1
  }

}
