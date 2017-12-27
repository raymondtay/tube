package nugit.tube.api.channels

import org.specs2._
import org.specs2.specification.AfterAll
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import akka.actor._
import akka.stream._
import providers.slack.models.{SlackAccessToken,SlackChannel}
import org.apache.flink.streaming.api.windowing.time.Time

class ChannelAlgosSpecs extends mutable.Specification with ScalaCheck with AfterAll with ChannelAlgos {override def is = s2"""
  Tube returns an empty collection of channels when slack access token is invalid $emptyCollectionWhenTokenInvalid
  Tube returns an nothing when data is sunk via a printout and when slack access token is invalid $nothingWhenTokenInvalid
  """

  implicit val actorSystem = ActorSystem("channel-algos-specs")
  implicit val actorMaterializer = ActorMaterializer()

  def afterAll() : Unit = {
    actorMaterializer.shutdown
    actorSystem.shutdown
  }

  def emptyCollectionWhenTokenInvalid = {
    val token = SlackAccessToken("fake", "channel:list" :: Nil)

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    val partF = (c: SlackChannel) ⇒ c.num_members >= 5
    val slideSizeUnit = 100
    val windowSize = Time.seconds(1)
    val slideSize = Time.milliseconds(slideSizeUnit)
    val ((leftAgg, rightAgg), logs) =
      retrieveChannels(slacks.core.config.Config.channelListConfig, partF, windowSize, slideSize, env).run(token)
    (leftAgg ++ rightAgg).size must_== 0
  }

  def nothingWhenTokenInvalid = {
    val token = SlackAccessToken("fake", "channel:list" :: Nil)

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    val partF = (c: SlackChannel) ⇒ c.num_members >= 5
    val logs =
      displayChannels(slacks.core.config.Config.channelListConfig, partF, env).run(token)
    logs.size must be_>=(1)
  }

}

