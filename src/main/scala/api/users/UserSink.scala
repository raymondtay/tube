package nugit.tube.api.users

import nugit.tube.configuration.CerebroSeedUsersConfig
import providers.slack.models.User
import org.slf4j.{Logger, LoggerFactory}
import org.apache.flink.streaming.api.functions.sink._

/* Representation objects received from cerebro
 * and when cerebro is ok:
 * (a) {"received" : <some number>}
 * (b) {"message" : [ ... <json objects> ]} this latter format is driven by
 *     Python Flask (which drives Cerebro) and we haven't decided what to do
 *     with these errors just yet.
 *
 */
case class CerebroOK(received : Int) extends Serializable
case class CerebroNOK(message : List[io.circe.JsonObject]) /* As long as we see this structure */ extends Serializable

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

  @transient implicit val logger = LoggerFactory.getLogger(classOf[UserSink])
  @transient private[this] var httpClient = Http1Client[IO](config = BlazeClientConfig.defaultConfig.copy(responseHeaderTimeout = cerebroConfig.timeout seconds)).unsafeRunSync

  /* Flink calls this when it needs to send */
  override def invoke(record : List[User]) : Unit = {
    transferToCerebro.run(record) match {
      case Left(error) ⇒ throw new RuntimeException(error)
      case Right(result) ⇒
        parseResponse(result).bimap((error:String) ⇒ onError(error), (result: Boolean) ⇒ onSuccess(result))
    }
    httpClient.shutdownNow()
  }

  private def onError(error : String) {
    logger.error(s"Error detected while sending data to Cerebro: $error")
    httpClient.shutdownNow()
    throw new RuntimeException("Error detected while xfer to Cerebro")
  }

  private def onSuccess(result: Boolean) {
    httpClient.shutdownNow()
  }

  private def transferToCerebro : Reader[List[User], Either[String,IO[String]]] = Reader{ (record: List[User]) ⇒
    Uri.fromString(cerebroConfig.url) match {
      case Left(error) ⇒ "Unable to parse cerebro's configuration".asLeft
      case Right(config) ⇒
        val req = Request[IO](method = POST, uri=config).withBody(Users(record).asJson.noSpaces).putHeaders(`Content-Type`(MediaType.`application/json`))
        if (httpClient == null) { /* necessary because 3rd party libs are not Serializable */
          httpClient = Http1Client[IO](config = BlazeClientConfig.defaultConfig.copy(responseHeaderTimeout = cerebroConfig.timeout seconds)).unsafeRunSync
        }
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
          case Right(ok) ⇒ "Cerebro was not happy with the input".asLeft[Boolean]
          case Left(somethingelse) ⇒ 
            logger.error(s"Unexpected error: $somethingelse")
            "Cerebro returned an unknown error".asLeft[Boolean]
        }
      case Right(ok) ⇒ true.asRight[String]
    }
  }
}
