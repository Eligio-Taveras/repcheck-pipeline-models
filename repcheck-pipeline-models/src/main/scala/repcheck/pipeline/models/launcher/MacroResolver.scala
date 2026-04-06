package repcheck.pipeline.models.launcher

object MacroResolver {

  private val macroPattern = """\{\{([^}]+)\}\}""".r

  def resolve(template: String, context: MacroContext): String =
    macroPattern.replaceAllIn(
      template,
      { m =>
        val key = m.group(1)
        val replacement = key match {
          case "run_id"    => context.runId.toString
          case "date"      => context.date
          case "timestamp" => context.timestamp.toString
          case fieldRef if fieldRef.startsWith("message.") =>
            val fieldName = fieldRef.stripPrefix("message.")
            context.messagePayload.getOrElse(fieldName, m.matched)
          case _ => m.matched
        }
        java.util.regex.Matcher.quoteReplacement(replacement)
      },
    )

}
