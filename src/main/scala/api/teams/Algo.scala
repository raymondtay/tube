package nugit.tube.api.teams

import nugit.routes._
import nugit.tube.configuration.{ApiGatewayConfig, ConfigValidator, CerebroTeamInfoConfig}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import nugit.tube.api.SlackFunctions._
import cats.data.Validated._
import slacks.core.config._
import slacks.core.program.HttpService
import providers.slack.models._
import akka.actor._
import akka.stream._
import cats._, data._, implicits._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._


trait TeamAlgos {

  /** 
    * Demonstration of retrieving all users and how this works is:
    * (a) when slack token is invalid or slack is unreachable, an empty
    *     collection of users and logs is returned to caller
    * (b) if slack token is valid and able to retrieve team information (from Slack), then
    *     tube would attempt to connect to cerebro via REST and stream to it;
    *     any errors encountered would throw a runtime error - this is
    *     necessary so that Flink recognizes it and restarts it based on the
    *     `RestartStrategy` in Flink.
    *     Once data is sunk, it is considered "gone" and we would return None.
    *
    * @param teamId some team id
    * @param env StreamExecutionEnvironment instance
    * @param teaminfoConfig configuration for retrieving slack via REST
    * @param emojiListConfig configuration for retrieving slack via REST
    * @param cerebroConfig configuration that reveals where cerebro is hosted
    * @param actorSystem  (environment derived)
    * @param actorMaterializer (environment derived)
    * @param token slack token
    */
  def runGetSlackTeamInfo(teamInfoCfg: NonEmptyList[ConfigValidation] Either SlackTeamInfoConfig[String],
                          emojiListCfg: NonEmptyList[ConfigValidation] Either SlackEmojiListConfig[String],
                          cerebroConfig : CerebroTeamInfoConfig,
                          gatewayConfig : ApiGatewayConfig,
                          env: StreamExecutionEnvironment)
                         (httpService : HttpService)
                         (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) : Reader[SlackAccessToken[String], Option[(Team, List[String])]] = Reader{ (token: SlackAccessToken[String]) ⇒

    val ((teamId, minedResults), logs) = retrieveTeamInfo(teamInfoCfg, emojiListCfg)(httpService).run(token)

    env.fromCollection(minedResults :: Nil).addSink(new TeamSink(teamId, cerebroConfig, gatewayConfig))
    env.execute("cerebro-seed-slack-users")
    /* NOTE: be aware that RTEs can be thrown here */

    ((minedResults, logs)).some
  }

}

