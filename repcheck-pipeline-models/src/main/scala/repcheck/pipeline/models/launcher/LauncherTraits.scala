package repcheck.pipeline.models.launcher

import java.util.UUID

import repcheck.pipeline.models.events.PipelineEvent
import repcheck.pipeline.models.workflow.schema.WorkflowDefinition

trait MessagePuller[F[_]] {
  def pull(subscriptionId: String, maxMessages: Int): F[List[PipelineEvent[String]]]
}

trait WorkflowResolver[F[_]] {
  def resolve(gcsPath: String): F[WorkflowDefinition]
}

trait DependencyChecker[F[_]] {
  def allDependenciesMet(workflowRunId: UUID, dependencies: List[String]): F[Boolean]
}

trait JobLauncher[F[_]] {
  def launch(config: ResolvedStepConfig): F[LaunchResult]
}
