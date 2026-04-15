package repcheck.pipeline.models.workflow.state

import io.circe.{Decoder, Encoder}

final case class UnrecognizedWorkflowStepStatus(value: String)
    extends Exception(
      s"Unrecognized WorkflowStepStatus: '$value'. Valid values: Pending, Running, Completed, CompletedWithErrors, Failed"
    )

enum WorkflowStepStatus(val label: String) {
  case Pending             extends WorkflowStepStatus("Pending")
  case Running             extends WorkflowStepStatus("Running")
  case Completed           extends WorkflowStepStatus("Completed")
  case CompletedWithErrors extends WorkflowStepStatus("CompletedWithErrors")
  case Failed              extends WorkflowStepStatus("Failed")
}

object WorkflowStepStatus {

  private val aliases: Map[String, WorkflowStepStatus] = Map(
    "PENDING"             -> WorkflowStepStatus.Pending,
    "RUNNING"             -> WorkflowStepStatus.Running,
    "COMPLETED"           -> WorkflowStepStatus.Completed,
    "COMPLETEDWITHERRORS" -> WorkflowStepStatus.CompletedWithErrors,
    "FAILED"              -> WorkflowStepStatus.Failed,
  )

  def fromString(value: String): Either[UnrecognizedWorkflowStepStatus, WorkflowStepStatus] =
    aliases.get(value.toUpperCase.replaceAll("[_\\-\\s]", "")) match {
      case Some(v) => Right(v)
      case None    => Left(UnrecognizedWorkflowStepStatus(value))
    }

  implicit val encoder: Encoder[WorkflowStepStatus] = Encoder.encodeString.contramap(_.toString)

  implicit val decoder: Decoder[WorkflowStepStatus] = Decoder.decodeString.emap { str =>
    fromString(str).left.map(_.getMessage)
  }

  private val doobieMeta: doobie.Meta[WorkflowStepStatus] =
    doobie.postgres.implicits.pgEnumStringOpt(
      "workflow_status_type",
      s => fromString(s).toOption,
      _.label.toLowerCase,
    )

  implicit val doobieGet: doobie.Get[WorkflowStepStatus] = doobieMeta.get
  implicit val doobiePut: doobie.Put[WorkflowStepStatus] = doobieMeta.put

}
