package nugit

import com.uber.jaeger.Configuration
import io.opentracing.ActiveSpan
import io.opentracing.util.GlobalTracer

/**
  * Leveraging OpenTracing to allow us to trace the runtimes of the code.
  * TODO: Turn this to A/B
  * TODO: Lift the hardcoded values into configuration
  * @author Raymond Tay
  * @version 1.0
  */
package object tracer {

  trait Defaults {
    val samplingPeriod = 1
    val constantSamplerCfg = new Configuration.SamplerConfiguration("const", samplingPeriod)

    val logSpans = true
    val agentHost = "localhost"
    val agentPort = 0
    val flushIntervalMillis = 1000
    val maxQueueSize = 10000
    val reporterCfg = new Configuration.ReporterConfiguration(logSpans, agentHost, agentPort, flushIntervalMillis, maxQueueSize)
    lazy val globalTracer =
      GlobalTracer.register(
      new Configuration(
          "SlackPOCTracer",
          constantSamplerCfg,
          reporterCfg).getTracer())
  }

  // TODO: Need an effect that does the following:
  // Get the active-span from the environment
  // Get the function to be executed from the environment
  // Get the WriterT from the environment (if exist, we leverage it else we create a new one and pass it out)
  // Write the data out to active-span
  // return the result, if any back to the client
}
