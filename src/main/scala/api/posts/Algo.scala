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
import nugit.tube.api.SlackFunctions._
import nugit.routes._
import nugit.tube.configuration.{ApiGatewayConfig, ConfigValidator,CerebroSeedPostsConfig}
import slacks.core.config._
import slacks.core.program.SievedMessages
import providers.slack.models._
import providers.slack.algebra.TeamId


trait PostsAlgos {

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
    * @param env StreamExecutionEnvironment instance
    * @param config configuration for retrieving slack via REST
    * @param blacklistedCfg the configuration object that contains the blacklisted message types
    * @param cerebroConfig configuration that reveals where cerebro is hosted
    * @param actorSystem  (environment derived)
    * @param actorMaterializer (environment derived)
    * @param token slack token
    */
  def runSeedSlackPostsGraph(teamId : TeamId,
                             config: NonEmptyList[ConfigValidation] Either SlackChannelListConfig[String],
                             slackReadCfg: NonEmptyList[ConfigValidation] Either SlackChannelReadConfig[String],
                             blacklistedCfg: NonEmptyList[ConfigValidation] Either SlackBlacklistMessageForUserMentions,
                             cerebroConfig : CerebroSeedPostsConfig,
                             gatewayConfig : ApiGatewayConfig,
                             env: StreamExecutionEnvironment)
                            (httpService : HttpService)
                            (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) : Reader[SlackAccessToken[String], Option[(List[(String, Option[SievedMessages])], List[String])]] = Reader{ (token: SlackAccessToken[String]) ⇒

    val (channels, logs) = getChannelListing(Config.channelListConfig)(httpService).run(token)

    channels match {
      case Nil ⇒ none
      case _   ⇒
        val channelIds : List[String] = channels.map(_.id)
        env.fromParallelCollection(new ChannelIdsSplittableIterator(channelIds)(cerebroConfig))
          .map(new StatefulPostsRetriever(token)(slackReadCfg)(blacklistedCfg)).name("channel-posts-retriever")
          .addSink(new PostSink(teamId, cerebroConfig, gatewayConfig)).name("channel-posts-sink")
        env.execute("cerebro-seed-slack-posts")
        /* NOTE: be aware that RTEs can be thrown here */
        none
    }
  }
}

