package nugit.tube.api.posts

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import akka.actor._
import akka.stream._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._

import scala.concurrent.duration._

import cats._, data._, implicits._
import cats.data.Validated._

import slacks.core.program.HttpService
import nugit.tube.api.Implicits
import nugit.tube.api.SlackFunctions._
import nugit.routes._
import nugit.tube.configuration.{ApiGatewayConfig, ConfigValidator,CerebroSeedPostsConfig}
import slacks.core.config._
import slacks.core.program.SievedMessages
import providers.slack.models._


trait PostsAlgos extends Implicits {

  /**
    * Demonstration of retrieving all posts and how this works is:
    * (a) when slack token is invalid or slack is unreachable, an empty
    *     collection of users and logs is returned to caller
    * (b) if slack token is valid and able to retrieve users (from Slack), then
    *     tube would attempt to connect to cerebro via REST and stream to it;
    *     any errors encountered would throw a runtime error - this is
    *     necessary so that Flink recognizes it and restarts it based on the
    *     `RestartStrategy` in Flink.
    *     Once data is sunk, it is considered "gone" and we would return None.
    *
    * @env StreamExecutionEnvironment instance
    * @config configuration for retrieving slack via REST
    * @cerebroConfig configuration that reveals where cerebro is hosted
    * @actorSystem  (environment derived)
    * @actorMaterializer (environment derived)
    * @token slack token
    */
  def runSeedSlackPostsGraph(config: NonEmptyList[ConfigValidation] Either SlackChannelListConfig[String],
                             slackReadCfg: NonEmptyList[ConfigValidation] Either SlackChannelReadConfig[String],
                             cerebroConfig : CerebroSeedPostsConfig,
                             gatewayConfig : ApiGatewayConfig,
                             env: StreamExecutionEnvironment)
                            (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer, httpService : HttpService) : Reader[SlackAccessToken[String], Option[(List[(String, Option[SievedMessages])], List[String])]] = Reader{ (token: SlackAccessToken[String]) ⇒
    val (channels, logs) = getChannelListing(Config.channelListConfig).run(token)

    channels match {
      case Nil ⇒ none
      case _   ⇒
        val channelIds : List[String] = channels.map(_.id)
        /*
        val datum = Applicative[List].map(channelIds)(channelId ⇒ getChannelConversationHistory(slackReadCfg)(channelId).run(token))
        env.fromCollection(datum)
          .addSink(new PostSink(cerebroConfig))
        */
        env.fromParallelCollection(new ChannelIdsSplittableIterator(channelIds)(cerebroConfig))
          .map(new StatefulPostsRetriever(token)(slackReadCfg)).name("channel-posts-retriever")
          .addSink(new PostSink(cerebroConfig, gatewayConfig)).name("channel-posts-sink")
        env.execute("cerebro-seed-slack-posts")
        /* NOTE: be aware that RTEs can be thrown here */
        none
    }
  }
}

