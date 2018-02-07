package nugit.tube

import cats._, data._, implicits._
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.sink._
import org.apache.flink.api.common.time.Time
import org.apache.flink.api.common.restartstrategy._
import org.apache.flink.streaming.api.environment.CheckpointConfig
import nugit.routes._
import nugit.tube.api.channels._
import nugit.tube.api.posts._
import nugit.tube.api.users._
import nugit.tube.api.teams._
import akka.http.scaladsl._

object Main extends ChannelAlgos with UsersAlgos with PostsAlgos with TeamAlgos {
  import nugit.tube.configuration.{JobTypes, RestartTypes}
  import nugit.tube.api.SlackFunctions._
  import cats.data.Validated._
  import slacks.core.config._
  import providers.slack.models._
  import akka.actor._
  import akka.stream._
  import nugit.tube.cli._
  import nugit.tube.cli.CommandlineParser._

  /*
   * Utility function that, hopefully, reduces the clutter in the Main function
   * @param args command line arguments
   */
  def canThisJobBeLaunched : Reader[Array[String], Option[(TubeConfig, nugit.tube.configuration.TubeRestartConfig)]] = Reader{ (args: Array[String]) ⇒
    // Parse the command line options
    val cliConfig : Option[TubeConfig] = parseCommandlineArgs(args.toSeq)
    // Load the default configuration
    val tubeRestartCfg : Option[nugit.tube.configuration.TubeRestartConfig] =
      nugit.tube.configuration.ConfigValidator.loadDefaults(nugit.tube.configuration.Config.config) match {
        case Left(errors) ⇒ none
        case Right(theConfig) ⇒ theConfig.some
      }

    val loadedConfiguration : Option[(TubeConfig, nugit.tube.configuration.TubeRestartConfig)] =
      (cliConfig |@| tubeRestartCfg).map((lhs, rhs) ⇒ (lhs, rhs))

    loadedConfiguration
  }

  /**
    * Utility function to configure the restart strategies for this particular
    * job
    * @param env
    * @param defaultCfg configuration loaded from files
    * @param cfg Configuration loaded from the command line
    */
  def setupRestartOption(env : StreamExecutionEnvironment)
                        (defaultCfg: nugit.tube.configuration.TubeRestartConfig) : Reader[TubeConfig, Unit] = Reader{ (cfg: TubeConfig) ⇒
    cfg match {
      case TubeConfig(RestartTypes.none, _, _, _)        ⇒ env.setRestartStrategy(RestartStrategies.noRestart())
      case TubeConfig(RestartTypes.fixed_delay, _, _, _) ⇒
        val _c = defaultCfg.fdCfg
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(_c.attempts.toInt, Time.milliseconds(_c.delay)))
      case TubeConfig(RestartTypes.failure_rate, _, _, _) ⇒
        val _c = defaultCfg.frCfg
        env.setRestartStrategy(RestartStrategies.failureRateRestart(_c.max_failures_per_interval.toInt, Time.milliseconds(_c.failure_rate_interval), Time.milliseconds(_c.delay)))
    }
  }

  /**
    * Main entry
    * @param args command line arguments
    */
  def main(args: Array[String]) {

    // If either of the loaded configuration fails, this application terminates.
    val loadedConfiguration = canThisJobBeLaunched(args)

    if (!loadedConfiguration.isDefined) System.exit(-1)

    val (commandlineCfg, defaultCfg) = loadedConfiguration.get

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    val checkpointingInterval = 50
    env.enableCheckpointing(checkpointingInterval) // checkpoint every 50 ms
    val config = env.getCheckpointConfig
    config.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION)

    setupRestartOption(env)(defaultCfg)(commandlineCfg)
    env.setParallelism(commandlineCfg.parallelism)

    implicit val actorSystem = ActorSystem("TubeActorSystem")
    implicit val actorMaterializer = ActorMaterializer()


    // Load configuration which contains the whereabouts of cerebro and
    // transmit the data over.
    nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption match {
      case Some(cerebroConfig) ⇒
        if (commandlineCfg.job_type == JobTypes.seed_users)
          runSeedSlackUsersGraph(Config.teamInfoConfig,
                                 Config.usersListConfig,
                                 cerebroConfig.seedUsersCfg,
                                 cerebroConfig.apiGatewayCfg,
                                 env).run(SlackAccessToken(commandlineCfg.token.get, Nil))
        if (commandlineCfg.job_type == JobTypes.seed_channels)
          runSeedSlackChannelsGraph(Config.teamInfoConfig,
                                    Config.channelListConfig,
                                    cerebroConfig.seedChannelsCfg,
                                    cerebroConfig.apiGatewayCfg,
                                    env).run(SlackAccessToken(commandlineCfg.token.get, Nil))
        if (commandlineCfg.job_type == JobTypes.seed_posts)
          runSeedSlackPostsGraph(Config.teamInfoConfig,
                                 Config.channelListConfig,
                                 Config.channelReadConfig,
                                 cerebroConfig.seedPostsCfg,
                                 cerebroConfig.apiGatewayCfg,
                                 env).run(SlackAccessToken(commandlineCfg.token.get, Nil))
        if (commandlineCfg.job_type == JobTypes.team_info)
          runGetSlackTeamInfo(Config.teamInfoConfig,
                              Config.emojiListConfig,
                              cerebroConfig.teamInfoCfg,
                              cerebroConfig.apiGatewayCfg,
                              env).run(SlackAccessToken(commandlineCfg.token.get, Nil))
 
      case None ⇒
        println("Cerebro's configuration is borked. Exiting.")
        System.exit(-1)
    }

    val sleepTime = 5000
    Thread.sleep(sleepTime)
    actorSystem.shutdown
  }

}
