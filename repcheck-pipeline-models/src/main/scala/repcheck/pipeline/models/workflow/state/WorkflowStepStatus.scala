package repcheck.pipeline.models.workflow.state

import io.circe.{Decoder, Encoder}

final case class UnrecognizedWorkflowStepStatus(value: String)
    extends Exception(
      s"Unrecognized WorkflowStepStatus: '$value'. Valid values: Pending, Running, Completed, Failed"
    )

enum WorkflowStepStatus(val label: String) {
  case Pending   extends WorkflowStepStatus("Pending")
  case Running   extends WorkflowStepStatus("Running")
  case Completed extends WorkflowStepStatus("Completed")
  case Failed    extends WorkflowStepStatus("Failed")
}

object WorkflowStepStatus {

  private val aliases: Map[String, WorkflowStepStatus] = Map(
    "PENDING"   -> WorkflowStepStatus.Pending,
    "RUNNING"   -> WorkflowStepStatus.Running,
    "COMPLETED" -> WorkflowStepStatus.Completed,
    "FAILED"    -> WorkflowStepStatus.Failed,
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

  implicit val doobieGet: doobie.Get[WorkflowStepStatus] =
    doobie.Get[String].temap(s => fromString(s).left.map(_.getMessage))

  implicit val doobiePut: doobie.Put[WorkflowStepStatus] = doobie.Put[String].contramap(_.toString)

}
