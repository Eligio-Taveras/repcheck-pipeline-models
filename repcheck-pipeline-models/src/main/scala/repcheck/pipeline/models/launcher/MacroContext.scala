package repcheck.pipeline.models.launcher

import java.time.Instant
import java.util.UUID

final case class MacroContext(
  runId: UUID,
  timestamp: Instant,
  date: String,
  messagePayload: Map[String, String],
)
