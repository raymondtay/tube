package nugit.tube.api.channels

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import nugit.tube.api.SlackFunctions._
import cats.data.Validated._
import slacks.core.config._
import providers.slack.models._
import akka.actor._
import akka.stream._
import cats._, data._, implicits._

trait ChannelAlgos {

  /** 
    * Demonstration of channel sieving 
    * @env StreamExecutionEnvironment instance
    * @actorSystem  (environment derived)
    * @actorMaterializer (environment derived)
    * @token slack token
    */
  def displayChannel(env: StreamExecutionEnvironment)
                    (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) = Reader{ (token: SlackAccessToken[String]) ⇒

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
  }

}
