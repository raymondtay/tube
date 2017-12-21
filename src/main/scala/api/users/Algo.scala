package nugit.tube.api.users

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import nugit.tube.api.SlackFunctions._
import cats.data.Validated._
import slacks.core.config._
import providers.slack.models._
import akka.actor._
import akka.stream._
import cats._, data._, implicits._

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
   
    println(s"Logs we have uncovered : $logs")
    //users.foreach(println(_))

    import io.circe._, io.circe.parser._
    import io.circe.syntax._
    import io.circe._, io.circe.generic.semiauto._

    implicit val uenc = deriveEncoder[User]
    implicit val xsenc = deriveEncoder[List[User]]
    //println(users.asJson.spaces4)
    (users, logs)
  }

}
