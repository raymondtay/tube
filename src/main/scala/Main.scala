package nugit.tube

import cats._, data._, implicits._

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._

object Main {
  import nugit.tube.api.SlackFunctions._
  import providers.slack.models._
  import akka.actor._
  import akka.stream._

  def main(args: Array[String]) {

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    val coreCount = 4
    env.setParallelism(coreCount)

    implicit val actorSystem = ActorSystem("ChannelListingActorSystem")
    implicit val actorMaterializer = ActorMaterializer()

    val channelNameA = "more than or equal to 5 members"
    val channelNameB = "less than 5 members"

    val (channels, logs) = getChannelListing(timeout).run(testToken)
    println(s"Total number of channels: ${channels.size}")
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
