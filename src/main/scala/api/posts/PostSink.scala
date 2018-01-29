package nugit.tube.api.posts

import nugit.tube.configuration.{ApiGatewayConfig, CerebroSeedPostsConfig}
import nugit.tube.api.model._
import nugit.tube.api.codec._
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
  */
class PostSink(cerebroConfig : CerebroSeedPostsConfig, gatewayCfg : ApiGatewayConfig) extends RichSinkFunction[(ChannelPosts, List[String])] {
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

  override def open(params: Configuration) : Unit = {
    logger = LoggerFactory.getLogger(classOf[PostSink])
    httpClient = Http1Client[IO](config = BlazeClientConfig.defaultConfig.copy(responseHeaderTimeout = cerebroConfig.timeout seconds)).unsafeRunSync
  }

  /* Closes the http client, and pool as well */
  override def close() : Unit = {
    httpClient.shutdownNow()
  }

  /* Flink calls this when it needs to send */
  override def invoke(record : (ChannelPosts, List[String])) : Unit = {
    transferToCerebro.run(record._1) match {
      case Left(error) ⇒ throw new RuntimeException(error)
      case Right(result) ⇒
        parseResponse(result).bimap((error:String) ⇒ onError(error), (result: Boolean) ⇒ onSuccess(result))
    }
  }

  protected def onError(error : String) {
    logger.error(s"Error detected while sending data to Cerebro: $error")
    throw new RuntimeException("Error detected while xfer to Cerebro")
  }

  protected def onSuccess(result: Boolean) {
    logger.info("Data transferred to cerebro.")
  }

  protected def transferToCerebro : Reader[ChannelPosts, Either[String,IO[String]]] = Reader{ (record: ChannelPosts) ⇒
    Uri.fromString(cerebroConfig.url) match {
      case Left(error) ⇒ "Unable to parse cerebro's configuration".asLeft
      case Right(config) ⇒
        import JsonCodec._
        val req = Request[IO](method = POST, uri=config).withBody(record.asJson.noSpaces).putHeaders(`Content-Type`(MediaType.`application/json`), `Host`(gatewayCfg.hostname))
        Either.catchOnly[java.net.ConnectException](httpClient.expect[String](req)) match {
          case Left(cannotConnect) ⇒ "cannot connect to cerebro".asLeft
          case Right(ok) ⇒ ok.asRight
        }
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
      case Left(error) ⇒
        decode[CerebroNOK](jsonString) match {
          case Right(ok) ⇒
            println(s"[NOK] Cerebro returned the following errors on the data: ${ok}")
            logger.error(s"[NOK] Cerebro returned the following errors on the data: ${ok}")
            "Cerebro was not happy with the input".asLeft[Boolean]
          case Left(somethingelse) ⇒
            println(s"[NOK] Unexpected error: $somethingelse")
            logger.error(s"[NOK] Unexpected error: $somethingelse")
            "Cerebro returned an unknown error".asLeft[Boolean]
        }
      case Right(ok) ⇒
        println(s"[OK] Cerebro returned: ${ok.received}")
        logger.info(s"[OK] Cerebro returned: ${ok.received}")
        true.asRight[String]
    }
  }
}
