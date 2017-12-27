package nugit

package object routes {

  import akka.actor._
  import akka.http.scaladsl.{Http, HttpExt}
  import akka.http.scaladsl.model._
  import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
  import akka.util.{ByteString, Timeout}
  
  // Base trait for http based services
  //
  trait HttpService {
    import cats.data.Kleisli, cats.implicits._
    import scala.concurrent.Future
    def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) : Kleisli[Future,HttpRequest,HttpResponse]
  }

  class RealHttpService extends HttpService {
    import cats.data.Kleisli, cats.implicits._
    override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
      (httpReq: HttpRequest) ⇒
        http.singleRequest(httpReq)
    }
  }

}
