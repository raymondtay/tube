package nugit.tube.api.users

import nugit.tube.configuration.CerebroConfig
import providers.slack.models.User
import org.slf4j.{Logger, LoggerFactory}
import org.apache.flink.streaming.api.functions.sink._

/* Representation objects received from cerebro
 * and when cerebro is ok:
 * (a) {"received" : <some number>}
 * (b) {errors : "users" : { ... <json objects> }} this latter format is driven by
 *     Python Flask (which drives Cerebro) and we haven't decided what to do
 *     with these errors just yet.
 *
 */
case class CerebroOK(received : Int) extends Serializable
case class CerebroNOK(errors : List[io.circe.JsonObject]) /* As long as we see this structure */ extends Serializable

/**
  * Any failures while transfering to Cerebro would result in a RTE being
  * thrown and that should restart the sending process by Flink
  * Refer to [https://ci.apache.org/projects/flink/flink-docs-release-1.4/dev/restart_strategies.html#restart-strategies-1]
  */
class UserSink(cerebroConfig : CerebroConfig) extends RichSinkFunction[List[User]] {
  import cats._, data._, implicits._
  import io.circe._
  import io.circe.generic.auto._
  import io.circe.syntax._
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  import org.http4s._
  import org.http4s.dsl._
  import org.http4s.circe._
  import org.http4s.headers._
  import org.http4s.client._
  import org.http4s.client.blaze._

  @transient implicit val logger = LoggerFactory.getLogger(classOf[UserSink])
  @transient private[this] var httpClient = PooledHttp1Client()

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
    logger.info(s"Error detected while sending data to Cerebro: $error")
    httpClient.shutdownNow()
    throw new RuntimeException("Error detected while xfer to Cerebro")
  }

  private def onSuccess(result: Boolean) {
    httpClient.shutdownNow()
  }

  private def transferToCerebro : Reader[List[User], Either[String,String]] = Reader{ (record: List[User]) ⇒
    Uri.fromString(cerebroConfig.url) match {
      case Left(error) ⇒ "Unable to parse cerebro's configuration".asLeft
      case Right(config) ⇒
        val req = POST(uri=config, Users(record).asJson.noSpaces)
        req.putHeaders(`Content-Type`(MediaType.`application/json`))
        if (httpClient == null) { /* necessary because 3rd party libs are not Serializable */
          httpClient = PooledHttp1Client()
        }
        Either.catchOnly[java.net.ConnectException](httpClient.expect[String](req).unsafeRun) match {
          case Left(cannotConnect) ⇒ "cannot connect to cerebro".asLeft
          case Right(ok) ⇒ ok.asRight
        }
    }
  }

  /* if we can decode the json as `CerebroOK` that means its OK; else its going
   * to be either `CerebroNOK` which means that there's invalid json data
   * otherwise its "unknown"
   */
  private def parseResponse : Reader[String, Either[String,Boolean]] = Reader{ (jsonString: String) ⇒
    import io.circe.parser._
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
