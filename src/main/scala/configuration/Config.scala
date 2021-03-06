package nugit.tube.configuration

import scala.util.Try
import com.typesafe.config._
import cats._, data._, implicits._
import shapeless._

/**
  * @author Raymond Tay
  * @version 1.0
  */

object RestartTypes extends Enumeration {
  type RestartType = Value
  val none , fixed_delay, failure_rate = Value
}

object JobTypes extends Enumeration {
  type JobType = Value
  val seed_users , seed_channels, seed_posts, team_info = Value
}

object Config {
  lazy val config = ConfigFactory.load()
  lazy val jobTypes = Set(JobTypes.seed_users.toString, JobTypes.seed_channels.toString, JobTypes.seed_posts.toString, JobTypes.team_info.toString)
  lazy val restartTypes = Set(RestartTypes.none.toString, RestartTypes.fixed_delay.toString, RestartTypes.failure_rate.toString)
}

sealed trait ConfigValidation {
  def errorMessage : String
}

case object MissingFailureRateObject extends ConfigValidation {
  def errorMessage : String = "There was no valid object marked by key 'failure-rate' found in 'tube.conf'."
}

case object MissingFixedDelayObject extends ConfigValidation {
  def errorMessage : String = "There was no valid object marked by key 'fixed-delay' found in 'tube.conf'."
}

case object MissingNoneObject extends ConfigValidation {
  def errorMessage : String = "There was no valid object marked by key 'none' found in 'tube.conf'."
}

case object MissingFailureRateIntervalKey extends ConfigValidation {
  def errorMessage : String = "There was no key 'failure_rate_interval' found in 'tube.conf'."
}

case object MissingDelayKey extends ConfigValidation {
  def errorMessage : String = "There was no key 'delay' found in 'tube.conf'."
}

case object MissingTimeoutKey extends ConfigValidation {
  def errorMessage : String = "key: 'timeout' is missing in 'application.conf'."
}

case object MissingPartitionSizeKey extends ConfigValidation {
  def errorMessage : String = "key: 'partition.size' is missing in 'application.conf'."
}

case object MissingAttemptsKey extends ConfigValidation {
  def errorMessage : String = "There was no key 'attempts' found in 'tube.conf'."
}

case object MissingMaxFailuresPerIntervalKey extends ConfigValidation {
  def errorMessage : String = "There was no key 'max_failures_per_interval' found in 'tube.conf'."
}

case object MissingParentStrategyOptions extends ConfigValidation {
  def errorMessage : String = "There was no strategy option(s) found in 'tube.conf'."
}

case object MissingStrategyOption extends ConfigValidation {
  def errorMessage : String = "There was no strategy option for restarting this job."
}

case object MissingTimeUnit extends ConfigValidation {
  def errorMessage : String = "Either 's' or 'sec' for seconds; 'm' or 'min' for minutes"
}

case class MissingCerebrokey(keyname: String) extends ConfigValidation {
  def errorMessage : String = s"Missing keyname: $keyname in configuration file."
}

// Cerebro's Configuration
case class CerebroConfig(
  apiGatewayCfg : ApiGatewayConfig,
  seedUsersCfg : CerebroSeedUsersConfig,
  seedChannelsCfg : CerebroSeedChannelsConfig,
  seedPostsCfg : CerebroSeedPostsConfig,
  teamInfoCfg : CerebroTeamInfoConfig
)

case class ApiGatewayConfig(hostname: String)

case class CerebroTeamInfoConfig(
  method: String,
  hostname : String,
  port : Int,
  uri: String,
  url : String,
  timeout : Long
  )
case class CerebroSeedUsersConfig(
  method: String,
  hostname : String,
  port : Int,
  uri: String,
  url : String,
  timeout : Long,
  teamIdPlaceHolder: String
  )
case class CerebroSeedChannelsConfig(
  method: String,
  hostname : String,
  port : Int,
  uri: String,
  url : String,
  timeout : Long,
  teamIdPlaceHolder: String
  )
case class CerebroSeedPostsConfig(
  method: String,
  hostname : String,
  port : Int,
  uri: String,
  url : String,
  timeout : Long,
  parSize : Int,
  teamIdPlaceHolder: String
  )

//
// The `RestartStrategy` represents the configuration derived from the
// configuration files and the developer can override these options by
// providing counter arguments over the command line.
//
sealed trait RestartStrategy
case class NONE(attempts: Long, delay: Long) extends RestartStrategy
case class FixedDelay(attempts: Long, delay: Long) extends RestartStrategy
case class FailureRate(max_failures_per_interval: Long, failure_rate_interval: Long, delay: Long) extends RestartStrategy
case class TubeRestartConfig(noneCfg: NONE, fdCfg: FixedDelay, frCfg: FailureRate)

// All time values will be resolved to milliseconds as represented by the `TubeTime`
trait TimeUnitParser {
  import fastparse.all._
  import cats._, data._, implicits._

  val number: P[Int] = P( CharIn('0'to'9').rep(1).!.map(_.toInt) )
  val spaces = P(" ".rep)
  val timeUnitIdentifiers = P( ("sec" | "min" | CharIn('s' to 's') | CharIn('m' to 'm') ) )

  // Parse the value associated with the time field keys which might look like
  // "3 (s|m)" or "3 (sec|min)" and convert them to milliseconds otherwise it
  // would assume its a numeric value and simply returns that.
  val timeUnitParser = Reader{ (timeString: String) ⇒
    P( number.! ~ spaces.? ~ timeUnitIdentifiers.?.! ).parse(timeString) match {
      case Parsed.Success(datum, _) ⇒
        datum._2 match {
          case "min" | "m" ⇒ datum._1.toInt * 60 * 1000
          case "sec" | "s" ⇒ datum._1.toInt * 1000
          case _           ⇒ datum._1.toInt
        }
      case Parsed.Failure(_, _, _) ⇒ 0L
    }
  }
}

sealed trait ConfigValidator extends TimeUnitParser {
  import collection.JavaConverters._

  val NONEGen = LabelledGeneric[NONE]
  val FixedDelayGen = LabelledGeneric[FixedDelay]
  val FailureRateGen = LabelledGeneric[FailureRate]

  type ValidationResult[A] = ValidatedNel[ConfigValidation, A]

  def validatePartitionSize(c: Config, namespace : String) : ValidationResult[Int] = {
    Try{c.getInt(s"tube.cerebro.${namespace}.partition.size")}.toOption match {
      case Some(parSize) ⇒ parSize.validNel
      case None ⇒ MissingPartitionSizeKey.invalidNel
    }
  }

  def validateTimeout(c: Config, namespace : String) : ValidationResult[Long] = {
    Try{c.getLong(s"tube.cerebro.${namespace}.timeout")}.toOption match {
      case Some(timeout) ⇒ timeout.validNel
      case None ⇒ MissingTimeoutKey.invalidNel
    }
  }

  def validateUrlHttpMethod(c: Config, namespace: String) : ValidationResult[String] =
    Try{c.getString(s"tube.cerebro.${namespace}.url.method")}.toOption match {
      case Some(cerebroSeedUrlMethod) ⇒ cerebroSeedUrlMethod.validNel
      case None ⇒ MissingCerebrokey(s"tube.cerebro.${namespace}.url.method").invalidNel
    }

  def validateHost(c: Config) : ValidationResult[String] =
    Try{c.getString(s"tube.cerebro.gateway.hostname")}.toOption match {
      case Some(gatewayHost) ⇒ gatewayHost.validNel
      case None ⇒ MissingCerebrokey(s"tube.cerebro.gateway.hostname").invalidNel
    }

  def validateHost(c: Config, namespace: String) : ValidationResult[String] =
    Try{c.getString(s"tube.cerebro.${namespace}.host")}.toOption match {
      case Some(cerebroSeedHost) ⇒ cerebroSeedHost.validNel
      case None ⇒ MissingCerebrokey(s"tube.cerebro.${namespace}.host").invalidNel
    }

  def validatePlaceHolder(c: Config, namespace: String) : ValidationResult[List[String]] =
    Try{c.getStringList(s"tube.cerebro.${namespace}.placeholder")}.toOption match {
      case Some(cerebroTeamIdPlaceholder) ⇒ cerebroTeamIdPlaceholder.asScala.toList.validNel
      case None ⇒ MissingCerebrokey(s"tube.cerebro.${namespace}.placeholder").invalidNel
    }

  def validatePort(c: Config, namespace: String) : ValidationResult[Int] =
    Try{c.getInt(s"tube.cerebro.${namespace}.port")}.toOption match {
      case Some(cerebroSeedPort) ⇒ cerebroSeedPort.validNel
      case None ⇒ MissingCerebrokey(s"tube.cerebro.${namespace}.port").invalidNel
    }

  def validateUri(c: Config, namespace: String) : ValidationResult[String] =
    Try{c.getString(s"tube.cerebro.${namespace}.uri")}.toOption match {
      case Some(cerebroSeedUri) ⇒ cerebroSeedUri.validNel
      case None ⇒ MissingCerebrokey(s"tube.cerebro.${namespace}.uri").invalidNel
    }

  def validateUrl(c: Config, namespace: String) : ValidationResult[String] =
    Try{c.getString(s"tube.cerebro.${namespace}.url.s")}.toOption match {
      case Some(cerebroSeedUrl) ⇒ cerebroSeedUrl.validNel
      case None ⇒ MissingCerebrokey(s"tube.cerebro.${namespace}.url.s").invalidNel
    }

  def getSupportedStrategies(c : Config) : ValidationResult[Set[String]] = {
    val keys = c.getObject("restart-strategy").keySet.asScala.toSet
    keys.isEmpty match {
      case true ⇒ MissingParentStrategyOptions.invalidNel
      case false ⇒ keys.validNel
    }
  }

  def validateStrategy(c: Config) : ValidationResult[String] = {
    Try{c.getString("tube.restart-strategy")}.toOption match {
      case Some(strategy) ⇒ strategy.validNel
      case None           ⇒ MissingStrategyOption.invalidNel
    }
  }

  def validateAttempt(c: Config) : ValidationResult[Long] = {
    Try{c.getLong("attempts")}.toOption match {
      case Some(attempts) ⇒ attempts.validNel
      case None ⇒ MissingAttemptsKey.invalidNel
    }
  }

  def validateMaxFailuresPerInterval(c: Config) : ValidationResult[Long] = {
    Try{c.getLong("max_failures_per_interval")}.toOption match {
      case Some(maxFailuresPerInterval) ⇒ maxFailuresPerInterval.validNel
      case None ⇒ MissingMaxFailuresPerIntervalKey.invalidNel
    }
  }

  def validateDelay(c: Config) : ValidationResult[Long] = {
    Try{c.getString("delay")}.toOption match {
      case Some(delay) ⇒ timeUnitParser.run(delay).validNel
      case None ⇒ MissingDelayKey.invalidNel
    }
  }

  def validateFailureRate(c: Config) : ValidationResult[Long] = {
    Try{c.getString("failure_rate_interval")}.toOption match {
      case Some(failureRateInterval) ⇒ timeUnitParser.run(failureRateInterval).validNel
      case None ⇒ MissingFailureRateIntervalKey.invalidNel
    }
  }

  def loadNoneStrategy(cfg: Config) : ValidationResult[NONE] = {
    import record._, shapeless.syntax.singleton._
    var n : NONEGen.Repr = NONEGen.to(NONE(0L, 0L))
    (validateAttempt(cfg),
     validateDelay(cfg)).mapN{
        (attempt, delay) ⇒
          NONEGen.from(n.updateWith('attempts)(_ + attempt).updateWith('delay)(_ + delay))
      }
  }

  def loadFixedDelayStrategy(cfg: Config) : ValidationResult[FixedDelay] = {
    import record._, shapeless.syntax.singleton._
    var n : FixedDelayGen.Repr = FixedDelayGen.to(FixedDelay(0L, 0L))
    (validateAttempt(cfg),
     validateDelay(cfg)).mapN{
        (attempt, delay) ⇒
          FixedDelayGen.from(n.updateWith('attempts)(_ + attempt).updateWith('delay)(_ + delay))
      }
  }

  def loadFailureRateStrategy(cfg: Config) : ValidationResult[FailureRate] = {
    import record._, shapeless.syntax.singleton._
    var n : FailureRateGen.Repr = FailureRateGen.to(FailureRate(0L, 0L, 0L))
    (validateFailureRate(cfg),
     validateDelay(cfg),
     validateMaxFailuresPerInterval(cfg)).mapN{
        (failureRate, maxFailuresPerInterval, delay) ⇒
          FailureRateGen.from(n.updateWith('failure_rate_interval)(_ + failureRate).updateWith('delay)(_ + delay).updateWith('max_failures_per_interval)(_ + maxFailuresPerInterval))
      }
  }

  def isNonePresent(c: Config) : ValidationResult[ConfigObject] = {
    Try{c.getObject("restart-strategy.none")}.toOption match {
      case Some(noneObj) ⇒ noneObj.validNel
      case None ⇒ MissingNoneObject.invalidNel
    }
  }
  def isFixedDelayPresent(c: Config) : ValidationResult[ConfigObject] = {
    Try{c.getObject("restart-strategy.fixed-delay")}.toOption match {
      case Some(fdObj) ⇒ fdObj.validNel
      case None ⇒ MissingFixedDelayObject.invalidNel
    }
  }
  def isFailureRatePresent(c: Config) : ValidationResult[ConfigObject] = {
    Try{c.getObject("restart-strategy.failure-rate")}.toOption match {
      case Some(frObj) ⇒ frObj.validNel
      case None ⇒ MissingFailureRateObject.invalidNel
    }
  }

}

object ConfigValidator extends ConfigValidator {
  import cats._, data._, implicits._
  import cats.data.Validated._

  // Extracts all supported restart strategies based on the configuration from
  // `application.conf` associated with tube.
  def validateRestartStrategy : Reader[String, Boolean] = Reader{ (strategy: String) ⇒
    getSupportedStrategies(Config.config) match {
      case Valid(supportedList) ⇒ supportedList.exists(_ == strategy)
      case Invalid(_) ⇒ false
    }
  }

  // Loads the default configuration from `tube.conf`.
  def loadDefaults(config: Config) : Either[NonEmptyList[ConfigValidation], TubeRestartConfig] =
    (isNonePresent(config) |@| isFixedDelayPresent(config) |@| isFailureRatePresent(config)).map{
      (a,b,c) ⇒
         (loadNoneStrategy(a.toConfig) |@| loadFixedDelayStrategy(b.toConfig) |@| loadFailureRateStrategy(c.toConfig)).map{
           (_a,_b,_c) ⇒ TubeRestartConfig(_a, _b, _c)
    }.toEither
   }.toEither.joinRight

  // Loads Cerebro's configuration
  def loadCerebroConfig(config: Config) =
    ( validateHost(config).map(gwHostname ⇒ ApiGatewayConfig(gwHostname)),
     (validateUrlHttpMethod(config, "team.info"),
     validateHost(config, "team.info"),
     validatePort(config, "team.info"),
     validateUri(config,  "team.info"),
     validateUrl(config,  "team.info"),
     validateTimeout(config, "team.info")).mapN((m,h,p,uri,url,timeout) ⇒ CerebroTeamInfoConfig(m,h,p,uri,url,timeout)),
     (validateUrlHttpMethod(config, "seed.users"),
     validateHost(config, "seed.users"),
     validatePort(config, "seed.users"),
     validateUri(config,  "seed.users"),
     validateUrl(config,  "seed.users"),
     validateTimeout(config, "seed.users"),
     validatePlaceHolder(config, "seed.users")).mapN((m,h,p,uri,url,timeout,teamIdVar) ⇒ CerebroSeedUsersConfig(m,h,p,uri,url,timeout,teamIdVar.head)),
    (validateUrlHttpMethod(config, "seed.channels"),
     validateHost(config, "seed.channels"),
     validatePort(config, "seed.channels"),
     validateUri(config,  "seed.channels"),
     validateUrl(config,  "seed.channels"),
     validateTimeout(config, "seed.channels"),
     validatePlaceHolder(config, "seed.channels")).mapN((m,h,p,uri,url,timeout,teamIdVar) ⇒ CerebroSeedChannelsConfig(m,h,p,uri,url,timeout,teamIdVar.head)),
    (validateUrlHttpMethod(config, "seed.posts"),
     validateHost(config, "seed.posts"),
     validatePort(config, "seed.posts"),
     validateUri(config,  "seed.posts"),
     validateUrl(config,  "seed.posts"),
     validateTimeout(config, "seed.posts"),
     validatePartitionSize(config, "seed.posts"),
     validatePlaceHolder(config, "seed.posts")).mapN((m,h,p,uri,url,timeout,par_size,teamIdVar) ⇒ CerebroSeedPostsConfig(m,h,p,uri,url,timeout,par_size,teamIdVar.head))).mapN((gatewayCfg, teamInfoCfg, usersCfg, channelsCfg, postsCfg) ⇒ CerebroConfig(gatewayCfg, usersCfg, channelsCfg, postsCfg, teamInfoCfg))

}

