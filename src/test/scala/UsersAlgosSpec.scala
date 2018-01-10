package nugit.tube.api.users

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
import providers.slack.models.SlackAccessToken
import slacks.core.config.Config

class UsersAlgosSpecs extends mutable.Specification with ScalaCheck with AfterAll with UsersAlgos {override def is = s2"""
  Tube returns an empty collection of slack users when slack access token is invalid $emptyCollectionWhenTokenInvalid
  """

  implicit val actorSystem = ActorSystem("users-algos-specs")
  implicit val actorMaterializer = ActorMaterializer()

  def afterAll() : Unit = {
    actorMaterializer.shutdown()
    actorSystem.shutdown()
  }

  def emptyCollectionWhenTokenInvalid = {
    val token = SlackAccessToken("fake", "channel:list" :: Nil)

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
      case Some(cerebroConfig) ⇒
        (runSeedSlackUsersGraph(Config.usersListConfig, cerebroConfig, env).run(token) : @unchecked) match {
          case Some((users, logs)) ⇒ users.size must_== 0
        }
    }
  }

}

