package nugit.tube.api


import org.slf4j.{Logger, LoggerFactory}


object SlackFunctions {
  import cats._, data._, implicits._
  import scala.concurrent.duration._
  import akka.actor._
  import akka.stream._
  import slacks.core.program._
  import slacks.core.config._
  import slacks.core.models._
  import providers.slack.algebra.TeamId
  import providers.slack.models._
  import org.atnos.eff._
  import org.atnos.eff.future._
  import org.atnos.eff.all._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._
  import nugit.tube.api.model.ChannelPosts


  val logger = LoggerFactory.getLogger(getClass)

  /**
    * API to retrieve Team info with the given access token; this conflates
    * both team and emojis used.
    * @param teamCfg configuration to locate Slack's API for team info
    * @param emojiCfg configuration to locate Slack's API for emoji info for the team
    * @param token
    */
  def retrieveTeamInfo(teamInfoCfg: NonEmptyList[ConfigValidation] Either SlackTeamInfoConfig[String],
                       emojiListCfg: NonEmptyList[ConfigValidation] Either SlackEmojiListConfig[String])
                       (httpService : HttpService)
                       (implicit actorSystem : ActorSystem, actorMat : ActorMaterializer)
                       : Reader[SlackAccessToken[String], ((TeamId, Team), List[String])] =  Reader { (accessToken: SlackAccessToken[String]) ⇒
    import scala.concurrent._, duration._
    import slacks.core.config.Config
    import scala.concurrent.ExecutionContext.Implicits.global
    import TeamInfoInterpreter._

    implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext

    def onError(teamId: TeamId) =
      Reader{ (e: io.circe.DecodingFailure) ⇒ 
        logger.error(s"[retrieveTeamInfo] Unable to decode json with details: ${e}")
        ((teamId, Team("", "", "", "", Nil)), e.message :: Nil)
      }
    def onSuccess(logs: List[String])(teamId: TeamId) =
      Reader{ (team: Team) ⇒ 
        logger.info(s"[retrieveTeamInfo] Appears to be successful.")
        ((teamId, team), logs)
      }

    (emojiListCfg, teamInfoCfg) match {
      case (Right(emojiListCfg),Right(teamInfoCfg)) ⇒
        val timeout = Monoid[Long].combine(emojiListCfg.timeout, teamInfoCfg.timeout) seconds
        val ((teamId, minedResults), logInfo) =
          Await.result( getTeamInfo(teamInfoCfg, emojiListCfg, httpService).runReader(accessToken).runWriter.runSequential, timeout)

        minedResults.bimap(onError(teamId).run, onSuccess(logInfo)(teamId).run).toOption match {
          case Some(d) ⇒ d
          case None ⇒ (("", Team("", "", "", "", Nil)), Nil)
        }
      case (Right(_), Left(teamInfoErrors)) ⇒ (("",Team("","","","",Nil)), teamInfoErrors.toList.map(_.errorMessage))
      case (Left(emojiErrors), Right(_)) ⇒ (("",Team("","","","",Nil)), emojiErrors.toList.map(_.errorMessage))
      case (Left(emojiErrors), Left(teamInfoErrors)) ⇒ (("",Team("","","","",Nil)), teamInfoErrors.toList.map(_.errorMessage) ++ emojiErrors.toList.map(_.errorMessage))
    }
  }

  /**
    * API to retrieve Team Id with the given access token.
    *
    * @param teamCfg configuration to locate Slack's API for team info
    * @param token
    */
  def retrieveTeam(teamInfoCfg: NonEmptyList[ConfigValidation] Either SlackTeamInfoConfig[String])
                  (httpService : HttpService)
                  (implicit actorSystem : ActorSystem, actorMat : ActorMaterializer)
                  : Reader[SlackAccessToken[String], (TeamId, List[String])] =  Reader { (accessToken: SlackAccessToken[String]) ⇒
    import scala.concurrent._, duration._
    import slacks.core.config.Config
    import scala.concurrent.ExecutionContext.Implicits.global
    import TeamInfoInterpreter._

    implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext

    teamInfoCfg match {
      case Right(teamInfoCfg) ⇒
        val timeout = teamInfoCfg.timeout seconds
        val (teamId, logs) =
          Await.result( getTeam(teamInfoCfg, httpService).runReader(accessToken).runWriter.runSequential, timeout )
        ((teamId, logs))
      case Left(teamInfoErrors) ⇒ (("", teamInfoErrors.toList.map(_.errorMessage)))
    }
  }

  /** 
    * API to retrieve all the users from Slack 
    * with the given access token
    * @param config configuration we are going to use
    * @param timeout how long to wait before timeout
    */
  def retrieveAllUsers(config: NonEmptyList[ConfigValidation] Either SlackUsersListConfig[String])
                      (httpService : HttpService)
                      (implicit actorSystem : ActorSystem, actorMat : ActorMaterializer) : Reader[SlackAccessToken[String], (List[User], List[String])] = Reader { (accessToken: SlackAccessToken[String]) ⇒
    import UsersInterpreter._
    import scala.concurrent._, duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
    import slacks.core.config.Config._
    usersListConfig match { // this tests the configuration loaded in application.conf
      case Right(cfg) ⇒
        val timeout = cfg.timeout seconds
        val (retrievedUsers, logInfo) =
           Await.result(
             getAllUsers(cfg, httpService).
               runReader(accessToken).runWriter.runSequential, timeout)
        (retrievedUsers.users, logInfo)
      case Left(validationErrors)  ⇒ (Nil, validationErrors.toList.map(_.errorMessage))
    }
  }

  /**
    * API to retrieve all the channels from Slack
    * @param timeout how long to wait before a timeout
    * @param token the slack access token
    */
  def getChannelListing(config: NonEmptyList[ConfigValidation] Either SlackChannelListConfig[String])
                       (httpService : HttpService)
                       (implicit actorSystem : ActorSystem, actorMat : ActorMaterializer)
                       : Reader[SlackAccessToken[String], (List[SlackChannel], List[String])] = Reader { (token: SlackAccessToken[String]) ⇒
    import ChannelsInterpreter._
    import scala.concurrent._, duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
    import slacks.core.config.Config
    config match {
      case Right(cfg) ⇒
        val timeout = cfg.timeout seconds
        val (channels, logInfo) =
          Await.result(
            getChannelList(cfg, httpService).
              runReader(token).
              runWriter.runSequential, timeout)
        (channels.xs, logInfo)
      case Left(validationErrors)  ⇒ (Nil, validationErrors.toList.map(_.errorMessage))
    }
  }

  /**
    * API to retrieve all the activities from Slack for a particular channel
    * @param channelId the ID of the channel you are interested 
    * @param timeout how long to wait before a timeout
    * @param token the slack access token
    */
  def getChannelConversationHistory(config: NonEmptyList[ConfigValidation] Either SlackChannelReadConfig[String])
                                   (channelId: String)
                                   (httpService : HttpService)
                                   : Reader[SlackAccessToken[String], (ChannelPosts, List[String])] = Reader { (token: SlackAccessToken[String]) ⇒
    import ChannelConversationInterpreter._
    import scala.concurrent._, duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
    implicit val actorSystem = ActorSystem("ChannelConversationHistoryActorSystem")
    implicit val actorMat    = ActorMaterializer()
    import slacks.core.config.Config
    val datum =
      config match {
        case Right(cfg) ⇒
          val timeout : Duration = cfg.timeout seconds
          val (messages, logInfo) =
            Await.result(
              ChannelConversationInterpreter.getChannelConversationHistory(channelId, cfg, httpService).
                runReader(token).
                runWriter.runSequential, timeout)
          (ChannelPosts(channelId, messages), logInfo)
        case Left(validationErrors)  ⇒ (ChannelPosts(channelId, SievedMessages(Nil,Nil,Nil)), validationErrors.toList.map(_.errorMessage))
      }
    actorMat.shutdown()
    actorSystem.shutdown()
    datum
  }

  /**
    * API to retrieve all the activities from Slack for a particular channel
    * @param channelId the ID of the channel you are interested 
    * @param timeout how long to wait before a timeout
    * @param token the slack access token
    */
  @deprecated("To be dropped in favour of 'conversation' APIs")
  def getChannelHistory(config: NonEmptyList[ConfigValidation] Either SlackChannelReadConfig[String])
                       (channelId: String, timeout : scala.concurrent.duration.Duration)
                       (httpService : HttpService)
                       (implicit actorSystem : ActorSystem, actorMat : ActorMaterializer)
                       : Reader[SlackAccessToken[String], (List[Message], List[String])] = Reader { (token: SlackAccessToken[String]) ⇒
    import ChannelConversationInterpreter._
    import scala.concurrent._, duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
    import slacks.core.config.Config
    config match {
      case Right(cfg) ⇒
        val timeout = cfg.timeout seconds
        val (channelHistories, logInfo) =
          Await.result(
            ChannelConversationInterpreter.getChannelHistory(channelId, cfg, httpService).
              runReader(token).
              runWriter.runSequential, timeout)
        (channelHistories.xs, logInfo)
      case Left(validationErrors)  ⇒ (Nil, validationErrors.toList.map(_.errorMessage))
    }
  }

}

