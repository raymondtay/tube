package nugit.tube.api.teams

import org.apache.flink.metrics.{Counter, SimpleCounter}
import nugit.tube.configuration.{ApiGatewayConfig, CerebroTeamInfoConfig}
import providers.slack.models.{Team, Emoji}
import providers.slack.algebra.TeamId
import nugit.tube.api.codec._
import nugit.tube.api.model._

import org.slf4j.{Logger, LoggerFactory}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink._


/**
  * Any failures while transfering to Cerebro would result in a RTE being
  * thrown and that should restart the sending process by Flink
  * Refer to [https://ci.apache.org/projects/flink/flink-docs-release-1.4/dev/restart_strategies.html#restart-strategies-1]
  *
  * The following metrics are implemented:
  * (a) sink-teams-counter
  *
  * @param cerebroConfig config object to locate Cerbro
  * @param gatewayCfg config object related to Kong only
  */
class TeamSink(teamId: TeamId,
               cerebroConfig : CerebroTeamInfoConfig,
               gatewayCfg : ApiGatewayConfig) extends RichSinkFunction[Team] {
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
  @transient var tCounter : Counter = _

  override def open(params: Configuration) : Unit = {
    logger = LoggerFactory.getLogger(classOf[TeamSink])
    httpClient = Http1Client[IO](config = BlazeClientConfig.defaultConfig.copy(responseHeaderTimeout = cerebroConfig.timeout seconds)).unsafeRunSync
    tCounter = getRuntimeContext().getMetricGroup().counter("sink-teams-counter")
  }

  override def close() : Unit = {
    httpClient.shutdownNow()
  }

  /* Flink calls this when it needs to send */
  override def invoke(record : Team) : Unit = {
    if (record.id.isEmpty) {
      logger.info(s"No team data xfer needed for team: [${teamId}]")
    } else
    transferToCerebro.run(record) match {
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
    logger.info("Transfer to cerebro is OK.")
  }

  protected def transferToCerebro : Reader[Team, Either[String,IO[String]]] = Reader{ (record: Team) ⇒
    Uri.fromString(cerebroConfig.url) match {
      case Left(error) ⇒ "Unable to parse cerebro's configuration".asLeft
      case Right(config) ⇒
        import io.circe.generic.auto._, io.circe.syntax._
        val req = Request[IO](method = POST, uri=config).withBody(record.asJson.noSpaces).putHeaders(`Content-Type`(MediaType.`application/json`), `Host`(gatewayCfg.hostname))
        Either.catchOnly[java.net.ConnectException](httpClient.expect[String](req)) match {
          case Left(cannotConnect) ⇒ "cannot connect to cerebro".asLeft
          case Right(ok) ⇒ 
            tCounter.inc()
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
}
