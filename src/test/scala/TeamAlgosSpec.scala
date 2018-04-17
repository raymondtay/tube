package nugit.tube.api.teams

import org.specs2._
import org.specs2.specification.AfterAll
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._

import cats._, cats.data._, implicits._
import cats.data.Validated._

import akka.actor._
import akka.stream._

import slacks.core.config._
import slacks.core.models._
import slacks.core.program.HttpService
import providers.slack.models.{SlackAccessToken, Team}
import nugit.tube.configuration._
import nugit.tube.api.SlackFunctions

class TeamsAlgosSpecs extends mutable.Specification with ScalaCheck with AfterAll with TeamAlgosInTest {override def is = sequential ^ s2"""
  Tube returns an empty collection of slack team when slack access token is invalid $emptyCollectionWhenTokenInvalid
  Tube returns collected slack team data when slack access token is valid $validTeamDataWhenTokenValid
  """

  implicit val actorSystem = ActorSystem("teams-algos-specs")
  implicit val actorMaterializer = ActorMaterializer()

  def afterAll() : Unit = {
    actorMaterializer.shutdown()
    actorSystem.shutdown()
  }

  def emptyCollectionWhenTokenInvalid = {
    val token = SlackAccessToken(Token("xoxp-","fake-slack-token"), "channel:list" :: Nil)
    val fakeTeamId = "TEAM-12345"
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    implicit val httpService = new nugit.tube.api.FakeTeamAlgosErrorHttpService

    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(slacks.core.config.Config.config).toOption : @unchecked) match {
      case Some(cerebroConfig) ⇒
        (runGetSlackTeamInfo(slacks.core.config.Config.teamInfoConfig,
                             slacks.core.config.Config.emojiListConfig,
                             cerebroConfig.teamInfoCfg,
                             cerebroConfig.apiGatewayCfg,
                             env)(httpService).run(token) : @unchecked) match {
          case Some((team, logs)) ⇒ 
            team.name must be_==("")
            team.domain must be_==("")
            team.email_domain must be_==("")
            team.image_132 must be_==("")
            team.emojis must be empty
        }
    }
  }

  def validTeamDataWhenTokenValid = {
    val token = SlackAccessToken(Token("xoxp-","fake-slack-token"), "channel:list" :: Nil)
    val fakeTeamId = "TEAM-12345"
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    implicit val httpService = new nugit.tube.api.FakeTeamInfoHttpService

    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(slacks.core.config.Config.config).toOption : @unchecked) match {
      case Some(cerebroConfig) ⇒
        (runGetSlackTeamInfo(slacks.core.config.Config.teamInfoConfig,
                             slacks.core.config.Config.emojiListConfig,
                             cerebroConfig.teamInfoCfg,
                             cerebroConfig.apiGatewayCfg,
                             env)(httpService).run(token) : @unchecked) match {
          case Some((team, logs)) ⇒ 
            team.name must be_==("My Team")
            team.domain must be_==("example")
            team.email_domain must be_==("example.com")
            team.image_132 must be_==("""https://...""")
            team.emojis must not be empty
            team.emojis.size must be_==(3)
        }
    }
  }

}

/**
  * Purpose of overriding this trait is because the base trait runs the actual
  * sinking of the data which is already taken care in [[TeamSinkSpecs]].
  * Hence, the base trait is overridden.
  */
trait TeamAlgosInTest extends TeamAlgos {

  override
  def runGetSlackTeamInfo(teamInfoCfg   : NonEmptyList[slacks.core.config.ConfigValidation] Either SlackTeamInfoConfig[String],
                          emojiListCfg  : NonEmptyList[slacks.core.config.ConfigValidation] Either SlackEmojiListConfig[String],
                          cerebroConfig : CerebroTeamInfoConfig,
                          gatewayConfig : ApiGatewayConfig,
                          env: StreamExecutionEnvironment)
                         (httpService : HttpService)
                         (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer) : Reader[SlackAccessToken[String], Option[(Team, List[String])]] = Reader{ (token: SlackAccessToken[String]) ⇒

    val ((teamId, minedResults), logs) = SlackFunctions.retrieveTeamInfo(teamInfoCfg, emojiListCfg)(httpService).run(token)
    ((minedResults, logs)).some
 }

}

