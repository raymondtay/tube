package nugit.tube.api.channels

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import org.apache.flink.streaming.api.windowing.assigners._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.runtime.operators.windowing._
import nugit.tube.api.SlackFunctions._
import nugit.tube.configuration.{ApiGatewayConfig, CerebroSeedChannelsConfig}
import cats.data.Validated._
import slacks.core.config.{Config, ConfigValidation, SlackTeamInfoConfig, SlackChannelListConfig}
import slacks.core.program.{HttpService, RealHttpService}
import providers.slack.models._
import providers.slack.algebra.TeamId
import akka.actor._
import akka.stream._
import cats._, data._, implicits._

trait ChannelAlgos {

  /** 
    * Demonstration of retrieving all channels and how this works is:
    * (a) when slack token is invalid or slack is unreachable, an empty
    *     collection of users and logs is returned to caller
    * (b) if slack token is valid and able to retrieve users (from Slack), then
    *     tube would attempt to connect to cerebro via REST and stream to it;
    *     any errors encountered would throw a runtime error - this is
    *     necessary so that Flink recognizes it and restarts it based on the
    *     `RestartStrategy` in Flink.
    *     Once data is sunk, it is considered "gone" and we would return None.
    * @param teamId some kind of team Id
    * @param env StreamExecutionEnvironment instance
    * @param config configuration for retrieving slack via REST
    * @param cerebroConfig configuration that reveals where cerebro is hosted
    * @param actorSystem  (environment derived)
    * @param actorMaterializer (environment derived)
    * @param token slack token
    */
  def runSeedSlackChannelsGraph(teamId: TeamId,
                    config: NonEmptyList[ConfigValidation] Either SlackChannelListConfig[String],
                    cerebroConfig : CerebroSeedChannelsConfig,
                    gatewayConfig : ApiGatewayConfig,
                    env: StreamExecutionEnvironment)
                    (httpService : HttpService)
                   (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) : Reader[SlackAccessToken[String], Option[(List[SlackChannel], List[String])]] = Reader{ (token: SlackAccessToken[String]) ⇒

    val (channels, logs) = getChannelListing(Config.channelListConfig)(httpService).run(token)

    channels match {
      case Nil ⇒ ((channels, logs)).some
      case _   ⇒
        env.fromCollection(channels :: Nil).addSink(new ChannelSink(teamId, cerebroConfig, gatewayConfig))
        env.execute("cerebro-seed-slack-channels")
        /* NOTE: be aware that RTEs can be thrown here */
        ((channels, logs)).some
    }
  }

  /**
    * Split slack based on a partition-function
    * @param config channel configuration
    * @param partitionFunction split-function
    * @param env StreamExecutionEnvironment instance
    * @param actorSystem  (environment derived)
    * @param actorMaterializer (environment derived)
    * @param token slack token
    */
  def retrieveChannels(config: NonEmptyList[ConfigValidation] Either SlackChannelListConfig[String],
                       partitionFunction : SlackChannel ⇒ Boolean,
                       windowSize : Time,
                       slideTime : Time,
                       env: StreamExecutionEnvironment)
                      (httpService : HttpService)
                      (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) = Reader{ (token: SlackAccessToken[String]) ⇒

    val channelNameA = "more than or equal to 5 members"
    val channelNameB = "less than 5 members"

    val (channels, logs) = getChannelListing(Config.channelListConfig)(httpService).run(token)

    println(s"Total number of channels: ${channels.size}")

    channels.map(c => println(c.id+","+c.name))

    val channelsEnv = env.fromCollection(channels)
    val splitChannels =
      channelsEnv.name("channel-split").
      split(channel ⇒ if (partitionFunction(channel)) channelNameA :: Nil else channelNameB :: Nil)

    val leftAgg = new ChannelAggregator
    val rightAgg = new ChannelAggregator

    splitChannels.select(channelNameA)
      .windowAll(SlidingEventTimeWindows.of(windowSize, slideTime))
      .aggregate(leftAgg)

    splitChannels.select(channelNameB)
      .windowAll(SlidingEventTimeWindows.of(windowSize, slideTime))
      .aggregate(rightAgg)

    env.execute
    ((leftAgg.getResult(leftAgg.createAccumulator), rightAgg.getResult(leftAgg.createAccumulator)), logs)

  }

  /**
    * Demonstration of channel sieving
    * @param config channel configuration
    * @param partitionFunction split-function
    * @param env StreamExecutionEnvironment instance
    * @param actorSystem  (environment derived)
    * @param actorMaterializer (environment derived)
    * @param token slack token
    */
  def displayChannels(config: NonEmptyList[ConfigValidation] Either SlackChannelListConfig[String],
                      partitionFunction : SlackChannel ⇒ Boolean,
                      env: StreamExecutionEnvironment)
                     (httpService : HttpService)
                     (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) = Reader{ (token: SlackAccessToken[String]) ⇒

    val channelNameA = "more than or equal to 5 members"
    val channelNameB = "less than 5 members"

    val (channels, logs) = getChannelListing(config)(httpService).run(token)
    println(s"Total number of channels: ${channels.size}")
    channels.map(c => println(c.id+","+c.name))
    val channelsEnv = env.fromCollection(channels)
    val splitChannels = channelsEnv.name("channel-split").split(channel ⇒ if (partitionFunction(channel)) channelNameA :: Nil else channelNameB :: Nil)

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
    logs
  }

}
