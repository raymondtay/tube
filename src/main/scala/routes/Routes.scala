package nugit.routes

import nugit.tracer._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server._
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.{HttpRequest, HttpEntity}
import com.typesafe.scalalogging._
import io.opentracing.util.GlobalTracer

/**
  * All the routes and associated "handlers"
  *
  * @author Raymond Tay
  * @version 1.0
  */
object Routes extends LazyLogging {

  override lazy val logger = Logger("slack")

  /**
    * The redirect uri on Slack should be pointing to this path s.t. we can
    * capture the authorization code and its state, if any, and we can leverage
    * "slacks" to begin the authorization
    */
  val slackAuthRoute = (get & pathPrefix("slack")) {
    parameters('code, 'state) { (code, state) ⇒
      implicit val span = GlobalTracer.get().buildSpan("slack_trace").startActive()
      logger.debug(s"Incoming [$code], [$state]!")
      Tracer.closeAfterLog("Slack", s"Slack code: $code with state: $state")({})
      complete(OK)
    }
  }
}
