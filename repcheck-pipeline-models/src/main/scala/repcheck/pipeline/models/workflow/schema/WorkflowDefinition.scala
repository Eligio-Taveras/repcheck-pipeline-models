package repcheck.pipeline.models.workflow.schema

import scala.annotation.tailrec

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class WorkflowDefinition(
  workflowId: String,
  name: String,
  steps: List[WorkflowStepDefinition],
  schedule: Option[String],
)

object WorkflowDefinition {

  def validated(
    workflowId: String,
    name: String,
    steps: List[WorkflowStepDefinition],
    schedule: Option[String],
  ): Either[String, WorkflowDefinition] =
    for {
      _ <- validateUniqueStepNames(steps)
      _ <- validateDependencyReferences(steps)
      _ <- validateNoCycles(steps)
      _ <- validateResourceConfigs(steps)
      _ <- validateExecutionConfigs(steps)
      _ <- validateProbeConfigs(steps)
    } yield WorkflowDefinition(workflowId, name, steps, schedule)

  private def validateUniqueStepNames(steps: List[WorkflowStepDefinition]): Either[String, Unit] = {
    val names      = steps.map(_.stepName)
    val duplicates = names.diff(names.distinct)
    if (duplicates.nonEmpty) {
      Left(s"Duplicate step names: ${duplicates.distinct.mkString(", ")}")
    } else {
      Right(())
    }
  }

  private def validateDependencyReferences(steps: List[WorkflowStepDefinition]): Either[String, Unit] = {
    val stepNames = steps.map(_.stepName).toSet
    val missing   = steps.flatMap(step => step.dependencies.filterNot(stepNames.contains))
    if (missing.nonEmpty) {
      Left(s"Missing dependency references: ${missing.distinct.mkString(", ")}")
    } else {
      Right(())
    }
  }

  private def validateNoCycles(steps: List[WorkflowStepDefinition]): Either[String, Unit] = {
    val adjacency: Map[String, List[String]] = steps.map(step => step.stepName -> step.dependencies).toMap

    val allNames = steps.map(_.stepName)

    @tailrec
    def detectCycleFrom(
      stack: List[(String, List[String])],
      visited: Set[String],
      inPath: Set[String],
    ): Option[String] =
      stack match {
        case Nil => None
        case (node, neighbors) :: rest =>
          neighbors match {
            case Nil =>
              detectCycleFrom(rest, visited, inPath - node)
            case next :: remaining =>
              if (inPath.contains(next)) {
                Some(s"Circular dependency detected involving step: $next")
              } else if (visited.contains(next)) {
                detectCycleFrom((node, remaining) :: rest, visited, inPath)
              } else {
                val nextNeighbors = adjacency.getOrElse(next, List.empty)
                detectCycleFrom(
                  (next, nextNeighbors) :: (node, remaining) :: rest,
                  visited + next,
                  inPath + next,
                )
              }
          }
      }

    val result = allNames.foldLeft(Option.empty[String]) { (acc, name) =>
      acc match {
        case Some(_) => acc
        case None =>
          val neighbors = adjacency.getOrElse(name, List.empty)
          detectCycleFrom(
            List((name, neighbors)),
            Set(name),
            Set(name),
          )
      }
    }

    result match {
      case Some(msg) => Left(msg)
      case None      => Right(())
    }
  }

  private def validateResourceConfigs(steps: List[WorkflowStepDefinition]): Either[String, Unit] = {
    val invalidCpus = steps.filterNot(step => ResourceConfig.validCpuValues.contains(step.resources.cpu))
    if (invalidCpus.nonEmpty) {
      val stepName = invalidCpus.map(_.stepName).mkString(", ")
      Left(s"Invalid CPU value in steps: $stepName. Valid values: ${ResourceConfig.validCpuValues.mkString(", ")}")
    } else {
      val invalidMemory = steps.filterNot(step => ResourceConfig.isValidMemory(step.resources.memory))
      if (invalidMemory.nonEmpty) {
        Left(
          s"Invalid memory format in steps: ${invalidMemory.map(_.stepName).mkString(", ")}. Expected format: <number>Mi or <number>Gi"
        )
      } else {
        Right(())
      }
    }
  }

  private def validateProbeConfigs(steps: List[WorkflowStepDefinition]): Either[String, Unit] = {
    val probeErrors = steps.flatMap { step =>
      val startupErrors = step.healthChecks.startupProbe.toList.flatMap { probe =>
        ProbeConfig.validate(probe, step.stepName).left.toOption
      }
      val livenessErrors = step.healthChecks.livenessProbe.toList.flatMap { probe =>
        ProbeConfig.validate(probe, step.stepName).left.toOption
      }
      startupErrors ++ livenessErrors
    }
    probeErrors match {
      case first :: _ => Left(first)
      case Nil        => Right(())
    }
  }

  private def validateExecutionConfigs(steps: List[WorkflowStepDefinition]): Either[String, Unit] = {
    val retryErrors     = steps.filter(step => step.execution.maxRetries < 0 || step.execution.maxRetries > 10)
    val taskCountErrors = steps.filter(step => step.execution.taskCount < 1 || step.execution.taskCount > 10000)
    if (retryErrors.nonEmpty) {
      Left(s"maxRetries must be 0-10 in steps: ${retryErrors.map(_.stepName).mkString(", ")}")
    } else if (taskCountErrors.nonEmpty) {
      Left(s"taskCount must be 1-10000 in steps: ${taskCountErrors.map(_.stepName).mkString(", ")}")
    } else {
      Right(())
    }
  }

  implicit val encoder: Encoder[WorkflowDefinition] = deriveEncoder[WorkflowDefinition]
  implicit val decoder: Decoder[WorkflowDefinition] = deriveDecoder[WorkflowDefinition]

}
