package repcheck.pipeline.models.config

import pureconfig.ConfigReader

import repcheck.shared.models.congress.vote.VoteType

final case class VoteWeights(
  passage: Double = 1.0,
  conferenceReport: Double = 1.0,
  cloture: Double = 0.8,
  vetoOverride: Double = 0.9,
  amendment: Double = 0.5,
  committee: Double = 0.4,
  motionToRecommit: Double = 0.6,
  other: Double = 0.5,
) derives ConfigReader {

  def forVoteType(vt: VoteType): Double =
    vt match {
      case VoteType.Passage          => passage
      case VoteType.ConferenceReport => conferenceReport
      case VoteType.Cloture          => cloture
      case VoteType.VetoOverride     => vetoOverride
      case VoteType.Amendment        => amendment
      case VoteType.Committee        => committee
      case VoteType.Recommit         => motionToRecommit
      case VoteType.Other            => other
    }

}
