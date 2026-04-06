package repcheck.pipeline.models.errors

trait ErrorClassifier {
  def classify(error: Throwable): ErrorClass
}

object DefaultErrorClassifier extends ErrorClassifier {

  def classify(error: Throwable): ErrorClass =
    ErrorClass.Systemic

}

class HttpErrorClassifier(statusExtractor: Throwable => Option[Int]) extends ErrorClassifier {

  private val transientStatusCodes: Set[Int] = Set(429, 500, 502, 503, 504)

  def classify(error: Throwable): ErrorClass =
    statusExtractor(error) match {
      case Some(code) =>
        if (transientStatusCodes.contains(code)) {
          ErrorClass.Transient
        } else {
          ErrorClass.Systemic
        }
      case None =>
        DefaultErrorClassifier.classify(error)
    }

}
