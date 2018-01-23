package nugit.tube.api.users

import nugit.tube.configuration.CerebroSeedUsersConfig
import nugit.tube.api.model.Users
import providers.slack.models.User
import nugit.tube.api.codec._
import nugit.tube.api.model._

import org.slf4j.{Logger, LoggerFactory}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink._


/**
  * Any failures while transfering to Cerebro would result in a RTE being
  * thrown and that should restart the sending process by Flink
  * Refer to [https://ci.apache.org/projects/flink/flink-docs-release-1.4/dev/restart_strategies.html#restart-strategies-1]
  */
class UserSink(cerebroConfig : CerebroSeedUsersConfig) extends RichSinkFunction[List[User]] {
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
  @transient private[this] var httpClient : Client[cats.effect.IO] = _

  override def open(params: Configuration) : Unit = {
    logger = LoggerFactory.getLogger(classOf[UserSink])
    httpClient = Http1Client[IO](config = BlazeClientConfig.defaultConfig.copy(responseHeaderTimeout = cerebroConfig.timeout seconds)).unsafeRunSync
  }

  override def close() : Unit = {
    httpClient.shutdownNow()
  }

  /* Flink calls this when it needs to send */
  override def invoke(record : List[User]) : Unit = {
    transferToCerebro.run(record) match {
      case Left(error) ⇒ throw new RuntimeException(error)
      case Right(result) ⇒
        parseResponse(result).bimap((error:String) ⇒ onError(error), (result: Boolean) ⇒ onSuccess(result))
    }
  }

  private def onError(error : String) {
    logger.error(s"Error detected while sending data to Cerebro: $error")
    throw new RuntimeException("Error detected while xfer to Cerebro")
  }

  private def onSuccess(result: Boolean) {
    logger.info("Transfer to cerebro is OK.")
  }

  private def transferToCerebro : Reader[List[User], Either[String,IO[String]]] = Reader{ (record: List[User]) ⇒
    Uri.fromString(cerebroConfig.url) match {
      case Left(error) ⇒ "Unable to parse cerebro's configuration".asLeft
      case Right(config) ⇒
        val req = Request[IO](method = POST, uri=config).withBody(Users(record).asJson.noSpaces).putHeaders(`Content-Type`(MediaType.`application/json`))
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
  private def parseResponse : Reader[IO[String], Either[String,Boolean]] = Reader{ (jsonEffect: IO[String]) ⇒
    import io.circe.parser._
    val jsonString = jsonEffect.unsafeRunSync
    decode[CerebroOK](jsonString) match {
      case Left(error) ⇒
        decode[CerebroNOK](jsonString) match {
          case Right(ok) ⇒
            logger.error(s"[NOK] Cerebro returned the following errors on the data: ${ok}")
            "Cerebro was not happy with the input".asLeft[Boolean]
          case Left(somethingelse) ⇒
            logger.error(s"[NOK] Unexpected error: $somethingelse")
            "Cerebro returned an unknown error".asLeft[Boolean]
        }
      case Right(ok) ⇒
        logger.info(s"[OK] Cerebro returned: ${ok.received}")
        true.asRight[String]
    }
  }
}
