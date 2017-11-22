package nugit.tube.api

object SlackFunctions {
  import cats._, data._, implicits._
  import scala.concurrent.duration._
  import akka.actor._
  import akka.stream._
  import slacks.core.program._
  import slacks.core.config._
  import slacks.core.models._
  import providers.slack.models._
  import org.atnos.eff._
  import org.atnos.eff.future._
  import org.atnos.eff.all._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  // Test tokens, do not use in PRODUCTION
  val testToken = SlackAccessToken("xoxp-2169191837-242649061349-267955267123-5e965193f448a1ccbb3bbf6f97083f78", "channel:list" :: Nil)
  val timeout : Duration = 9 seconds

  /**
    * API to retrieve all the channels from Slack
    * @param timeout how long to wait before a timeout
    * @param token the slack access token
    */
  def getChannelListing(timeout : scala.concurrent.duration.Duration)
                       (implicit actorSystem : ActorSystem, actorMat : ActorMaterializer)
                       : Reader[SlackAccessToken[String], (List[SlackChannel], List[String])] = Reader { (token: SlackAccessToken[String]) ⇒
    import ChannelsInterpreter._
    import scala.concurrent._, duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
    import slacks.core.config.Config
    Config.channelListConfig match {
      case Right(cfg) ⇒
        val (channels, logInfo) =
          Await.result(
            getChannelList(cfg, new RealHttpService).
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
  def getChannelHistory(channelId: String, timeout : scala.concurrent.duration.Duration)
                       (implicit actorSystem : ActorSystem, actorMat : ActorMaterializer)
                       : Reader[SlackAccessToken[String], (List[Message], List[String])] = Reader { (token: SlackAccessToken[String]) ⇒
    import ChannelConversationInterpreter._
    import scala.concurrent._, duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
    import slacks.core.config.Config
    Config.channelReadConfig match {
      case Right(cfg) ⇒
        val (channelHistories, logInfo) =
          Await.result(
            ChannelConversationInterpreter.getChannelHistory(channelId, cfg, new RealHttpService).
              runReader(token).
              runWriter.runSequential, timeout)
        (channelHistories.xs, logInfo)
      case Left(validationErrors)  ⇒ (Nil, validationErrors.toList.map(_.errorMessage))
    }
  }

}

