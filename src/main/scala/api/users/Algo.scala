package nugit.tube.api.users

import nugit.routes._
import nugit.tube.configuration.{ConfigValidator,CerebroConfig}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import nugit.tube.api.SlackFunctions._
import cats.data.Validated._
import slacks.core.config._
import providers.slack.models._
import akka.actor._
import akka.stream._
import cats._, data._, implicits._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._

protected[users] case class Users(users : List[User])

trait UsersAlgos {

  /** 
    * Demonstration of retrieving all users and how this works is:
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
  def runSeedSlackUsersGraph(config: NonEmptyList[ConfigValidation] Either SlackUsersListConfig[String],
                    cerebroConfig : CerebroConfig,
                    env: StreamExecutionEnvironment)
                   (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) : Reader[SlackAccessToken[String], Option[(List[User], List[String])]] = Reader{ (token: SlackAccessToken[String]) ⇒

    val (users, logs) = retrieveAllUsers(config, timeout).run(token)

    users match {
      case Nil ⇒ ((users, logs)).some
      case _   ⇒
        env.fromCollection(users :: Nil).addSink(new UserSink(cerebroConfig))
        env.execute("cerebro-seed-slack-users")
        /* NOTE: be aware that RTEs can be thrown here */
        none
    }
  }
}

