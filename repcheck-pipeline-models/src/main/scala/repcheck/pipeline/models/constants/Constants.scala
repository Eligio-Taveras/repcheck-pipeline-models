package repcheck.pipeline.models.constants

object Constants {

  /**
   * Earliest Congress for which Congress.gov amendment data is reliably present. Pipelines should refuse to ingest
   * amendments below this Congress.
   */
  val MinAmendmentCongress: Int = 102
}
