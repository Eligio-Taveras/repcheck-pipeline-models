package repcheck.pipeline.models.workflow.schema

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class ResourceConfig(
  cpu: String = "1",
  memory: String = "512Mi",
  gpu: Option[String] = None,
)

object ResourceConfig {

  val validCpuValues: Set[String] = Set("1", "2", "4", "6", "8")

  private val memoryPattern: scala.util.matching.Regex = """^\d+(?:Mi|Gi)$""".r

  def isValidMemory(memory: String): Boolean =
    memoryPattern.findFirstIn(memory).isDefined

  implicit val encoder: Encoder[ResourceConfig] = deriveEncoder[ResourceConfig]
  implicit val decoder: Decoder[ResourceConfig] = deriveDecoder[ResourceConfig]

}
