package repcheck.pipeline.models.workflow.state

import io.circe.{Decoder, Encoder}

final case class UnrecognizedWorkflowRunStatus(value: String)
    extends Exception(
      s"Unrecognized WorkflowRunStatus: '$value'. Valid values: Pending, Running, Completed, CompletedWithErrors, Failed"
    )

enum WorkflowRunStatus(val label: String) {
  case Pending             extends WorkflowRunStatus("Pending")
  case Running             extends WorkflowRunStatus("Running")
  case Completed           extends WorkflowRunStatus("Completed")
  case CompletedWithErrors extends WorkflowRunStatus("CompletedWithErrors")
  case Failed              extends WorkflowRunStatus("Failed")
}

object WorkflowRunStatus {

  private val aliases: Map[String, WorkflowRunStatus] = Map(
    "PENDING"             -> WorkflowRunStatus.Pending,
    "RUNNING"             -> WorkflowRunStatus.Running,
    "COMPLETED"           -> WorkflowRunStatus.Completed,
    "COMPLETEDWITHERRORS" -> WorkflowRunStatus.CompletedWithErrors,
    "FAILED"              -> WorkflowRunStatus.Failed,
  )

  def fromString(value: String): Either[UnrecognizedWorkflowRunStatus, WorkflowRunStatus] =
    aliases.get(value.toUpperCase.replaceAll("[_\\-\\s]", "")) match {
      case Some(v) => Right(v)
      case None    => Left(UnrecognizedWorkflowRunStatus(value))
    }

  def deriveFromSteps(stepStatuses: List[WorkflowStepStatus]): WorkflowRunStatus =
    if (stepStatuses.isEmpty) {
      WorkflowRunStatus.Pending
    } else if (stepStatuses.exists(_ == WorkflowStepStatus.Running)) {
      WorkflowRunStatus.Running
    } else if (stepStatuses.forall(_ == WorkflowStepStatus.Completed)) {
      WorkflowRunStatus.Completed
    } else if (stepStatuses.forall(_ == WorkflowStepStatus.Pending)) {
      WorkflowRunStatus.Pending
    } else if (
      stepStatuses.exists(_ == WorkflowStepStatus.Completed) && stepStatuses.exists(_ == WorkflowStepStatus.Failed)
    ) {
      WorkflowRunStatus.CompletedWithErrors
    } else if (stepStatuses.filterNot(_ == WorkflowStepStatus.Pending).forall(_ == WorkflowStepStatus.Failed)) {
      WorkflowRunStatus.Failed
    } else {
      WorkflowRunStatus.Running
    }

  implicit val encoder: Encoder[WorkflowRunStatus] = Encoder.encodeString.contramap(_.toString)

  implicit val decoder: Decoder[WorkflowRunStatus] = Decoder.decodeString.emap { str =>
    fromString(str).left.map(_.getMessage)
  }

  private val doobieMeta: doobie.Meta[WorkflowRunStatus] =
    doobie.postgres.implicits.pgEnumStringOpt(
      "workflow_status_type",
      s => fromString(s).toOption,
      _.label.toLowerCase,
    )

  implicit val doobieGet: doobie.Get[WorkflowRunStatus] = doobieMeta.get
  implicit val doobiePut: doobie.Put[WorkflowRunStatus] = doobieMeta.put

}
