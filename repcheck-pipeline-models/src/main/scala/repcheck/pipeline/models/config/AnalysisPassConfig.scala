package repcheck.pipeline.models.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default._

final case class AnalysisPassConfig(
  pass1Model: String = "claude-3-haiku",
  pass2Model: String = "claude-3-sonnet",
  pass3Model: String = "claude-3-opus",
  pass1Enabled: Boolean = true,
  pass2Enabled: Boolean = true,
  pass3Enabled: Boolean = true,
) derives ConfigReader
