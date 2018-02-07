package nugit.tube.cli

import nugit.tube.configuration.{Config, JobTypes, RestartTypes}
import JobTypes._
import RestartTypes._

/**
  * Represents the commandline options we want to support in Tube
  *
  * Note: The slack token must be provided.
  *
  * By default, if the user does not enter ANY option over the command-line,
  * then the following are applied:
  * (a) run "seed-users" job with a parallelism of `1` (i.e. sequential) and no
  * restarts upon meeting failures during execution.
  *
  * @author Raymond Tay
  * @version 1.0
  */
case class TubeConfig(
  restart_strategy : RestartType = RestartTypes.none,
  job_type : JobType = JobTypes.seed_users,
  token : Option[slacks.core.models.Token] = None,
  parallelism : Int = 1
)

object CommandlineParser {
  import scopt._
  import cats._, data._, implicits._
  import slacks.core.models.decipher

  private implicit val zeroTubeConfig = Zero.zero(TubeConfig())
  private val parser = new scopt.OptionParser[TubeConfig]("tube") {
    head("tube", "version : 0.1-SNAPSHOT")

    // slack token
    opt[String]('T', "slack-token").required().
      valueName(s"The slack token you were issued, we do not support legacy slack tokens").
      validate(token ⇒ decipher(token) match {
        case Right(_) ⇒ success
        case Left(_) ⇒ failure(s"The token you have provided is not in the expected format.")
      }).
      action( (x, c) ⇒ c.copy(token = decipher(x).toOption) ).
      text("slack token with a prefix like 'xoxp-', 'xoxb-' or 'xoxp-'")

    // which jobs to trigger
    opt[String]('X', "job-type").
      valueName(s"The type of job to trigger, enter one of the following: ${Config.jobTypes.mkString(", ")}").
      validate(x ⇒ if (Config.jobTypes.contains(x)) success else failure(s"The following applies: ${Config.jobTypes.mkString(", ")}")).
      action( (x, c) ⇒ c.copy(job_type = JobTypes.withName(x))).
      text("job-type is required.")

    // number of parallel jobs to launch
    opt[Int]('P', "parallelism").
      valueName("Number of parallel jobs, hint: maximum number should be the core count of the machine").
      action( (x, c ) ⇒ c.copy(parallelism = x)).
      text("default parallelism is 1 unless you override")

    // indicate what is the restart strategy
    opt[String]('S', "restart-strategy").
      valueName("Enter one of the following: none, fixed-delay, failure-rate").
      validate(x ⇒  if (Config.restartTypes.contains(x)) success else failure(s"The following applies: ${Config.restartTypes.mkString(", ")}")).
      action( (x, c) ⇒ c.copy(restart_strategy = RestartTypes.withName(x))).
      text("restart-strategy is required.")
  }

  def parseCommandlineArgs : Kleisli[Option, Seq[String], TubeConfig] = Kleisli{ (args: Seq[String]) ⇒
    parser.parse(args, TubeConfig())
  }
}
