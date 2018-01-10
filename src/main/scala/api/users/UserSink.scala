package nugit.tube.api.users

import nugit.tube.configuration.CerebroConfig
import providers.slack.models.User

import org.apache.flink.streaming.api.functions.sink._

/* Representation objects received from cerebro
 * and when cerebro is ok:
 * (a) {"received" : <some number>}
 * (b) {errors : "users" : { json-objects }} this latter format is driven by
 *     Python Flask (which drives Cerebro)
 *
 */
case class CerebroOK(received : Int)
case class CerebroNOK(errors : List[io.circe.JsonObject]) /* As long as we see this structure */

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
  import org.http4s.client._
  import org.http4s.client.blaze._

  private[this] val httpClient = PooledHttp1Client()

  /* Flink calls this when it needs to send */
  override def invoke(record : List[User]) : Unit = {
    transferToCerebro.run(record) match {
      case Left(error) ⇒ throw new RuntimeException(error)
      case Right(result) ⇒ parseResponse(result)
    }
    httpClient.shutdownNow()
  }

  private def transferToCerebro : Reader[List[User], Either[String,String]] = Reader{ (record: List[User]) ⇒
    Uri.fromString(cerebroConfig.url) match {
      case Left(error) ⇒ "Unable to parse cerebro's configuration".asLeft
      case Right(config) ⇒
        val req = POST(uri=config, Users(record).asJson.noSpaces)
        httpClient.expect[String](req).unsafeRun.asRight
    }
  }

  private def parseResponse : Reader[String, Either[String,Boolean]] = Reader{ (jsonString: String) ⇒
    import io.circe.parser._
    decode[CerebroOK](jsonString) match {
      case Left(error) ⇒
        decode[CerebroNOK](jsonString) match {
          case Right(ok) ⇒ "Cerebro was not happy with the input".asLeft[Boolean]
          case Left(somethingelse) ⇒ "Cerebro returned an unknown error".asLeft[Boolean]
        }
      case Right(ok) ⇒ true.asRight[String]
    }
  }
}
