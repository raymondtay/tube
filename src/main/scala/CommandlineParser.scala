package nugit.tube

/**
  * Represents the commandline options we want to support in Tube
  * @author Raymond Tay
  * @version 1.0
  */
case class TubeConfig(
  restart_strategy : String = "none"
)

object CommandlineParser {
  import scopt._
  implicit val zeroTubeConfig = Zero.zero(TubeConfig())
  val parser = new scopt.OptionParser[TubeConfig]("tube") {
    head("tube", "version : 0.1-SNAPSHOT")

    opt[String]('S', "restart-strategy").
      required().
      valueName("Enter one of the following: none, fixed-delay, failure-rate").
      validate(x ⇒  if (Set("none", "fixed-delay", "failure-rate").contains(x)) success else failure("The following applies: none, fixed-delay, failure-rate")).
      action( (x, c) ⇒ c.copy(restart_strategy = x)).
      text("restart-strategy is required.")
  }

}
