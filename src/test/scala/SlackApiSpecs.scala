package nugit.tube.api

import org.specs2.ScalaCheck
import org.specs2.mutable._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.Specs2RouteTest
import akka.http.scaladsl.server._
import Directives._
import org.specs2.concurrent.ExecutionEnv
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}

import org.atnos.eff._
import org.atnos.eff.future._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._
import org.atnos.eff.syntax.future._

import cats._, implicits._
import akka.actor._
import akka.stream._
import providers.slack.models._
import providers.slack.algebra.TeamId
import slacks.core.models.Token
import slacks.core.program._

object ConfigurationData {
  import com.typesafe.config._

  val badListingConfigs = List("""
    slacks.api.channel.list {
      params = ["aaa", "bbb"]
    }""" ,
    """
    slacks.api.channel.list {
      url = "https://slack.com/oauth/authorize"
    }""" ,
    """
    slacks.api.channel.list {
    }
    """).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val badHistoryConfigs = List("""
    slacks.api.channel.read {
      params = ["aaa", "bbb"]
    }""" ,
    """
    slacks.api.channel.read {
      url = "https://slack.com/oauth/authorize"
    }""" ,
    """
    slacks.api.channel.read {
    }
    """).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val genBadListingConfigs = for { cfg <- oneOf(badListingConfigs) } yield cfg
  implicit val arbBadListingConfigs = Arbitrary(genBadListingConfigs)
  val genBadHistoryConfigs = for { cfg <- oneOf(badHistoryConfigs) } yield cfg
  implicit val arbBadHistoryConfigs = Arbitrary(genBadHistoryConfigs)
}

/**
  * Specification for `SlackFunctions`
  */
class SlackApiSpecs(implicit ee: ExecutionEnv) extends Specification with ScalaCheck with Specs2RouteTest { override def is = sequential ^ s2"""
  Catch missing keys in configuration:
  -----------------------------------------

  When given a bad configuration (e.g. missing keys) in channel-listing, validation errors are caught    $catchListingConfigErrors
  When given a bad configuration (e.g. missing keys) in channel-histories, validation errors are caught  $catchHistoriesConfigErrors

  Assuming configuration has passed :
  -----------------------------------------
  When given a valid slack token, the channels would be collected $getChannelListingWhenAllIsGood
  When given a valid slack token but the remote site might be down, then channels would not be collected and error logs are detected $getChannelListingWhenAllIsNotGood
  When given a valid slack token, the channel conversations would be collected $getChannelHistoryWhenAllIsGood
  When given a valid slack token but the remote site might be down, then channel conversations would not be collected and error logs are detected $getChannelHistoryWhenAllIsNotGood
  When given a valid slack token, we should be able to retrieve the team's info and corresponding emojis associated with this token $getTeamInfoWhenAllIsGood
  When given a valid slack token, we should be able to retrieve the team's id associated with this token $getTeamIdWhenAllIsGood
  """


  def catchListingConfigErrors = {
    import scala.concurrent._, duration._
    import SlackFunctions._
    import slacks.core.config._

    val fakeService = new FakeChannelListingHttpService
    val token = SlackAccessToken(Token("xoxp-","fake"), "channel:list" :: Nil)

    import ConfigurationData.arbBadListingConfigs
    prop { (config : Config) ⇒
      val parsedConfig = ConfigValidator.validateChannelConfig(config.getConfig("slacks.api.channel.list")).toEither
      val (channels, validationErrors) = getChannelListing(parsedConfig)(fakeService).run(token)
      channels.size == 0 && validationErrors.size >= 1
    }
  }

  def catchHistoriesConfigErrors = {
    import scala.concurrent._, duration._
    import SlackFunctions._
    import slacks.core.config._
    val fakeService = new FakeChannelHistoryHttpService
    val token = SlackAccessToken(Token("xoxp-","fake"), "channel:list" :: Nil)

    import ConfigurationData.arbBadHistoryConfigs
    prop { (config : Config) ⇒
      val parsedConfig = ConfigValidator.validateChannelReadConfig(config.getConfig("slacks.api.channel.read")).toEither
      val (channels, validationErrors) = getChannelHistory(parsedConfig)("fake-channel-id", 2 second)(fakeService).run(token)
      channels.size == 0 && validationErrors.size >= 1
    }
  }

  def getChannelListingWhenAllIsGood = {
    import scala.concurrent._, duration._
    import SlackFunctions._
    import slacks.core.config._

    val fakeService = new FakeChannelListingHttpService
    val token = SlackAccessToken(Token("xoxp-","fake"), "channel:list" :: Nil)
    val (channels, loginfo) = getChannelListing(Config.channelListConfig)(fakeService).run(token)
    channels.size > 0
  }

  def getChannelListingWhenAllIsNotGood = {
    import scala.concurrent._, duration._
    import SlackFunctions._
    import slacks.core.config._

    val fakeService = new FakeChannelListingErrorHttpService
    val token = SlackAccessToken(Token("xoxp-","fake"), "channel:list" :: Nil)
    val (channels, loginfo) = getChannelListing(Config.channelListConfig)(fakeService).run(token)
    channels.size == 0
  }

  def getChannelHistoryWhenAllIsGood = {
    import scala.concurrent._, duration._
    import SlackFunctions._
    import slacks.core.config._

    val fakeService = new FakeChannelHistoryHttpService
    val token = SlackAccessToken(Token("xoxp-","fake"), "channel:list" :: Nil)
    val (channels, loginfo) = getChannelHistory(Config.channelReadConfig)("fake-channel-id", 2 second)(fakeService).run(token)
    channels.size > 0
  }

  def getChannelHistoryWhenAllIsNotGood = {
    import scala.concurrent._, duration._
    import SlackFunctions._
    import slacks.core.config._

    val fakeService = new FakeChannelHistoryErrorHttpService
    val token = SlackAccessToken(Token("xoxp-","fake"), "channel:list" :: Nil)
    val (channels, loginfo) = getChannelHistory(Config.channelReadConfig)("fake-channel-id", 2 second)(fakeService).run(token)
    channels.size == 0
  }

  def getTeamIdWhenAllIsGood = {
    import SlackFunctions._
    import slacks.core.config._
    val token = SlackAccessToken(Token("xoxp-","fake-slack-token"), "channel:list" :: Nil)
    implicit val httpService = new nugit.tube.api.FakeTeamInfoHttpService

    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
      case Some(cerebroConfig) ⇒
        (retrieveTeam(Config.teamInfoConfig)(httpService).run(token) : @unchecked) match {
          case d : (TeamId, List[String]) ⇒
            d._1 must be_==("T12345")
        }
    }
  }

  def getTeamInfoWhenAllIsGood = {
    import SlackFunctions._
    import slacks.core.config._
    val token = SlackAccessToken(Token("xoxp-","fake-slack-token"), "channel:list" :: Nil)
    implicit val httpService = new nugit.tube.api.FakeTeamInfoHttpService

    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
      case Some(cerebroConfig) ⇒
        (retrieveTeamInfo(Config.teamInfoConfig, Config.emojiListConfig)(httpService).run(token) : @unchecked) match {
          case d : ((TeamId, Team), List[String]) ⇒
            d._1._1 must be_==("T12345")
            d._1._2.name must be_==("My Team")
            d._1._2.domain must be_==("example")
            d._1._2.email_domain must be_==("example.com")
        }
    }
  }

}

