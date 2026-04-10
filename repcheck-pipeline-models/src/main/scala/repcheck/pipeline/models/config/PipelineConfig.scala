package repcheck.pipeline.models.config

import pureconfig.ConfigReader

import repcheck.pipeline.models.errors.RetryConfig

final case class PipelineConfig[T](
  parallelism: Int = 4,
  batchSize: Int = 100,
  pageSize: Int = 250,
  retry: RetryConfig = RetryConfig(),
  appConfig: T,
)

object PipelineConfig {

  implicit def configReader[T: ConfigReader]: ConfigReader[PipelineConfig[T]] =
    ConfigReader.derived[PipelineConfig[T]]

}
