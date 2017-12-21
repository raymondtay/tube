package nugit.tube

import cats._, data._, implicits._

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import org.apache.flink.api.common.time.Time
import org.apache.flink.api.common.restartstrategy._


object Main {
  import nugit.tube.api.SlackFunctions._
  import cats.data.Validated._
  import slacks.core.config._
  import providers.slack.models._
  import akka.actor._
  import akka.stream._
  import CommandlineParser._

  def main(args: Array[String]) {
    // Parse the command line options
    val cfg : Option[TubeConfig] = parser.parse(args.toSeq, TubeConfig())

    if (!cfg.isDefined) System.exit(-1)

    val tubeRestartCfg : Option[nugit.tube.configuration.TubeRestartConfig] =
      nugit.tube.configuration.ConfigValidator.loadDefaults(nugit.tube.configuration.Config.config) match {
        case Left(errors) ⇒ none
        case Right(theConfig) ⇒ theConfig.some
      }

    if (!tubeRestartCfg.isDefined) System.exit(-1)

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    cfg.get match {
      case TubeConfig("none")        ⇒ env.setRestartStrategy(RestartStrategies.noRestart())
      case TubeConfig("fixed-delay") ⇒
        val _c = tubeRestartCfg.get.fdCfg
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(_c.attempts.toInt, Time.milliseconds(_c.delay)))
      case TubeConfig("failure-rate") ⇒
        val _c = tubeRestartCfg.get.frCfg
        env.setRestartStrategy(RestartStrategies.failureRateRestart(_c.max_failures_per_interval.toInt, Time.milliseconds(_c.failure_rate_interval), Time.milliseconds(_c.delay)))
    }

    val coreCount = 4
    env.setParallelism(coreCount)

    implicit val actorSystem = ActorSystem("ChannelListingActorSystem")
    implicit val actorMaterializer = ActorMaterializer()

    val channelNameA = "more than or equal to 5 members"
    val channelNameB = "less than 5 members"

    val (channels, logs) = getChannelListing(Config.channelListConfig)(timeout).run(testToken)
    println(s"Total number of channels: ${channels.size}")
    channels.map(c => println(c.id+","+c.name))
    val channelsEnv = env.fromCollection(channels)
    val splitChannels = channelsEnv.name("channel-split").split(channel ⇒ if (channel.num_members >= 5) channelNameA :: Nil else channelNameB :: Nil)

    splitChannels.select(channelNameA).addSink(new PrintSinkFunction[SlackChannel] {
      override def invoke(record: SlackChannel) {
        println(s"[GTEQ-5] ${record.num_members}")
      }
    }).name(channelNameA + " stream")

    splitChannels.select(channelNameB).addSink(new PrintSinkFunction[SlackChannel] {
      override def invoke(record: SlackChannel) {
        println(s"[LT-5] ${record.num_members}")
      }
    }).name(channelNameB + " stream")

    env.execute

    val sleepTime = 5000
    Thread.sleep(sleepTime)
    actorSystem.shutdown
  }

}
