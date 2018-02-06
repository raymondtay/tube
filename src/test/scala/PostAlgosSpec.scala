package nugit.tube.api.posts

import org.specs2._
import org.specs2.specification.AfterAll
import org.scalacheck._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import org.apache.flink.streaming.api.windowing.time.Time

import com.typesafe.config._
import akka.actor._
import akka.stream._

import nugit.tube.api.SlackFunctions._
import slacks.core.program.SievedMessages
import providers.slack.models.{SlackAccessToken,SlackChannel}

class PostsAlgosSpecs extends mutable.Specification with ScalaCheck with AfterAll with PostsAlgos {override def is = sequential ^ s2"""
  Tube returns an key-value pair where the value is an empty collection of messages when slack access token is invalid $emptyCollectionWhenTokenInvalid
  Tube returns nothing when slack access token is invalid $nothingWhenTokenInvalid
  """

  implicit val actorSystem = ActorSystem("posts-algos-specs")
  implicit val actorMaterializer = ActorMaterializer()

  def afterAll() : Unit = {
    actorMaterializer.shutdown
    actorSystem.shutdown
  }

  def emptyCollectionWhenTokenInvalid = {
    val token = SlackAccessToken("fake", "channel:list" :: Nil)
    val channelId = "fake-channel-id"
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    val (channelPosts, logs) =
      getChannelConversationHistory(slacks.core.config.Config.channelReadConfig)(channelId).run(token)

    channelPosts.channel must_==(channelId)
    channelPosts.posts.botMessages.size must be_==(0)
    channelPosts.posts.userFileShareMessages.size must be_==(0)
    channelPosts.posts.userAttachmentMessages.size must be_==(0)
  }

  def nothingWhenTokenInvalid = {
    val token = SlackAccessToken("fake", "channel:list" :: Nil)
    val channelId = "fake-channel-id"
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(nugit.tube.configuration.Config.config).toOption : @unchecked) match {
      case Some(cerebroConfig) ⇒
        runSeedSlackPostsGraph(slacks.core.config.Config.teamInfoConfig,
                               slacks.core.config.Config.channelListConfig,
                               slacks.core.config.Config.channelReadConfig,
                               cerebroConfig.seedPostsCfg,
                               cerebroConfig.apiGatewayCfg, env).run(token) must beNone
    }
  }

}


