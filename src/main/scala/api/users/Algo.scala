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

trait UsersAlgos extends nugit.tube.api.Implicits {

  /** 
    * Demonstration of retrieving all users
    * @env StreamExecutionEnvironment instance
    * @actorSystem  (environment derived)
    * @actorMaterializer (environment derived)
    * @token slack token
    */
  def retrieveUsers(config: NonEmptyList[ConfigValidation] Either SlackUsersListConfig[String],
                    env: StreamExecutionEnvironment)
                   (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) : Reader[SlackAccessToken[String], (List[User], List[String])] = Reader{ (token: SlackAccessToken[String]) ⇒

    val (users, logs) = retrieveAllUsers(config, timeout).run(token)

    val usersCol = env.fromCollection(users)
    /**
      * TODOs: Decide the strategy we will be hitting Cerebro (seq or parallel)
      * and we need to handle the scatter-gather situation.
      */
    val usersCount =
      usersCol.map{(_, 1)}.
      keyBy(0).
      sum(1)
    env.execute

    println(s"Total number of users: ${usersCount.print}")
    println(s"Total number of users: ${users.size}")

    (users, logs)
  }

  /**
    * Performs a RESTful call to Cerebro
    * @cerebroConfig 
    * @httpService
    * @users the collection of users we are going to transmit over the wire
    */
  def transferToCerebro(cerebroConfig : CerebroConfig)
                       (httpService : HttpService)
                       (implicit http : HttpExt, actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) = Reader{ (users: List[User]) ⇒
    import akka.http.scaladsl.model.ContentTypes._
    import io.circe._, io.circe.parser._, io.circe.syntax._ , io.circe.generic.semiauto._

    implicit val uenc = deriveEncoder[providers.slack.models.User]
    implicit val usersEnc = deriveEncoder[Users]

    println(Users(users).asJson.noSpaces)

    val httpRequest =
      HttpRequest(HttpMethods.POST,
                  uri = cerebroConfig.url,
                  entity = HttpEntity(`application/json`, Users(users).asJson.noSpaces))
    // Invoke
    httpService.makeSingleRequest.run(httpRequest)
  }

}

protected[users] case class Users(users : List[User])

