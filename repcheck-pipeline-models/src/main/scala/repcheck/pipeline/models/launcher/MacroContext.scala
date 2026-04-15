package repcheck.pipeline.models.launcher

import java.time.Instant

final case class MacroContext(
  workflowRunId: Long,
  timestamp: Instant,
  date: String,
  messagePayload: Map[String, String],
)
