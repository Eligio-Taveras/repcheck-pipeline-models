package repcheck.pipeline.models.config

import pureconfig.ConfigReader

final case class CommitteeAttributionWeights(
  chairman: Double = 1.0,
  rankingMember: Double = 0.7,
  viceChairman: Double = 0.6,
  member: Double = 0.4,
) derives ConfigReader
