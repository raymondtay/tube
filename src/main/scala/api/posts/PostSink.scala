package nugit.tube.api.posts

import org.apache.flink.metrics.{Counter, SimpleCounter}
import nugit.tube.configuration.{ApiGatewayConfig, CerebroSeedPostsConfig}
import nugit.tube.api.model._
import nugit.tube.api.codec._
import providers.slack.algebra.TeamId
import providers.slack.models.User
import slacks.core.program.SievedMessages

import org.slf4j.{Logger, LoggerFactory}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink._


/**
  * Any failures while transfering to Cerebro would result in a RTE being
  * thrown and that should restart the sending process by Flink
  * Refer to [https://ci.apache.org/projects/flink/flink-docs-release-1.4/dev/restart_strategies.html#restart-strategies-1]
  *
  * The following metrics are implemented:
  * (a) sink-posts-counter
  *
  * @param cerebroConfig config object used to locate Cerebro
  * @param gatewayCfg config object for Kongk
  */
class PostSink(teamId : TeamId, cerebroConfig : CerebroSeedPostsConfig, gatewayCfg : ApiGatewayConfig) extends RichSinkFunction[(ChannelPosts, List[String])] {
  import cats._, data._, implicits._
  import cats.effect._
  import io.circe._
  import io.circe.literal._
  import io.circe.generic.auto._
  import io.circe.syntax._
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  import org.http4s._
  import org.http4s.client._
  import org.http4s.dsl.io._
  import org.http4s.headers._
  import org.http4s.client.blaze._

  @transient var logger : Logger = _
  @transient var httpClient : Client[cats.effect.IO] = _
  @transient var pCounter : Counter = _

  override def open(params: Configuration) : Unit = {
    logger = LoggerFactory.getLogger(classOf[PostSink])
    httpClient = Http1Client[IO](config = BlazeClientConfig.defaultConfig.copy(responseHeaderTimeout = cerebroConfig.timeout seconds)).unsafeRunSync
    pCounter = getRuntimeContext().getMetricGroup().counter("sink-posts-counter")
  }

  /* Closes the http client, and pool as well */
  override def close() : Unit = {
    httpClient.shutdownNow()
  }

  /* Flink calls this when it needs to send */
  override def invoke(record : (ChannelPosts, List[String])) : Unit = {
    if ( !needDataXfer(record._1)) {
      logger.info(s"No message data xfer needed for channel: [${record._1.channel}]")
    } else
    transferToCerebro.run(record._1) match {
      case Left(error) ⇒ throw new RuntimeException(error)
      case Right(result) ⇒
        parseResponse(result).bimap((error:String) ⇒ onError(error), (result: Boolean) ⇒ onSuccess(result))
    }
  }

  private def needDataXfer = Reader{ (record: ChannelPosts) ⇒
    (record.posts.botMessages.size |+| 
    record.posts.userAttachmentMessages.size |+| 
    record.posts.userFileShareMessages.size |+| 
    record.posts.fileCommentMessages.size |+| 
    record.posts.whitelistedMessages.size) > 0
  }

  protected def onError(error : String) {
    logger.error(s"Error detected while sending data to Cerebro: $error")
    throw new RuntimeException("Error detected while xfer to Cerebro")
  }

  protected def onSuccess(result: Boolean) {
    logger.info("Data transferred to cerebro.")
  }

  protected def transferToCerebro : Reader[ChannelPosts, Either[String,IO[String]]] = Reader{ (record: ChannelPosts) ⇒
    Uri.fromString(cerebroConfig.teamIdPlaceHolder.r.replaceAllIn("<channel_id>".r.replaceAllIn(cerebroConfig.url, record.channel), teamId)) match {
      case Left(error) ⇒ "Unable to parse cerebro's configuration".asLeft
      case Right(config) ⇒
        import JsonCodec._
        val jsonData =
            record.posts.botMessages.map(_.asJson) ++
            record.posts.userAttachmentMessages.map(_.asJson) ++
            record.posts.userFileShareMessages.map(_.asJson) ++
            record.posts.fileCommentMessages.map(_.asJson) ++
            record.posts.whitelistedMessages

        val req = Request[IO](method = POST, uri=config).withBody(jsonData.asJson.noSpaces).putHeaders(`Content-Type`(MediaType.`application/json`), `Host`(gatewayCfg.hostname))
        Either.catchOnly[java.net.ConnectException](httpClient.expect[String](req)) match {
          case Left(cannotConnect) ⇒ "cannot connect to cerebro".asLeft
          case Right(ok) ⇒ 
            pCounter.inc(sumOfMessages(record.posts))
            ok.asRight
        }
    }
  }

  private def parseResponseWhenError = Reader{ (jsonString: String) ⇒
    import io.circe.parser._
    decode[CerebroNOK](jsonString) match {
      case Right(ok) ⇒
        logger.error(s"[NOK] Cerebro returned the following errors on the data: ${ok}")
        "Cerebro was not happy with the input".asLeft[Boolean]
      case Left(somethingelse) ⇒
        logger.error(s"[NOK] Unexpected error: $somethingelse")
        "Cerebro returned an unknown error".asLeft[Boolean]
    }
  }

  /* if we can decode the json as `CerebroOK` that means its OK; else its going
   * to be either `CerebroNOK` which means that there's invalid json data
   * otherwise its "unknown"
   */
  protected def parseResponse : Reader[IO[String], Either[String,Boolean]] = Reader{ (jsonEffect: IO[String]) ⇒
    import io.circe.parser._
    val jsonString = jsonEffect.unsafeRunSync
    decode[CerebroOK](jsonString) match {
      case Left(error) ⇒ parseResponseWhenError(jsonString)

      case Right(CerebroOK(Some(ok))) ⇒
        logger.info(s"[OK] Cerebro returned: ${ok}")
        true.asRight[String]

      case Right(CerebroOK(None)) ⇒ parseResponseWhenError(jsonString)
    }
  }

  /* Int operations are closed under addition so therefore, its ∈ Monoid and i
   * know its repeated in the Mapper as well. TODO is to refactor
   **/
  private def sumOfMessages(datum : slacks.core.program.SievedMessages) = 
    (datum.botMessages.size            |+|
     datum.userAttachmentMessages.size |+|
     datum.userFileShareMessages.size  |+|
     datum.whitelistedMessages.size    |+| datum.fileCommentMessages.size)
 
}
