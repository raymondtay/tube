package nugit.tracer

import com.uber.jaeger.Configuration
import io.opentracing.ActiveSpan
import io.opentracing.util.GlobalTracer

/**
  * Functions here are meant to be convenient to use.
  */
object Tracer extends Defaults {
  import cats._, implicits._

  def closeAfterLog[E,A](prefix: String, msg: String)(f: => A)(implicit span: ActiveSpan) : Either[Throwable,A] =
    Either.catchNonFatal{
      span.log(s"[$prefix] $msg")
      val result = f
      span.close()
      result
    }

}
