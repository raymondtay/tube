package nugit.tube.configuration

import org.specs2._
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}
import com.typesafe.config._


/**
restart-strategy.none.attempts : 1
restart-strategy.none.delay : 3s

restart-strategy.fixed-delay.attempts : 1
restart-strategy.fixed-delay.delay : 10s

restart-strategy.failure-rate.max_failures_per_interval: 1
restart-strategy.failure-rate.failure_rate_interval : 5 min
restart-strategy.failure-rate.delay : 10 s

*/

object ConfigurationData { 

  val goodData1 = List(
    """tube.restart-strategy = "none"""",
    """tube.restart-strategy = "fixed-delay"""",
    """tube.restart-strategy = "failure-rate""""
  ).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val goodData2 = List(
    """
    restart-strategy.none.attempts : 1
    restart-strategy.none.delay : 3s
    restart-strategy.fixed-delay.attempts : 1
    restart-strategy.fixed-delay.delay : 10s
    restart-strategy.failure-rate.max_failures_per_interval: 1
    restart-strategy.failure-rate.failure_rate_interval : 5 min
    restart-strategy.failure-rate.delay : 10 s
    """
  ).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val badCfg1 = List(
    """tube.invalid.restart-strategy = "failure-rate"""",
    """tube = "failure-rate""""
  ).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val badCfg2 = List(
    """
    restart-strategy.none.attempts : 1
    restart-strategy.fixed-delay.attempts : 1
    restart-strategy.failure-rate.attempts : 1
    """
  ).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val genGoodCfg1 = for { cfg <- oneOf(goodData1) } yield cfg
  val genBadCfg1 = for { cfg <- oneOf(badCfg1) } yield cfg
  val genGoodCfg2 = for { cfg <- oneOf(goodData2) } yield cfg
  val genBadCfg2 = for { cfg <- oneOf(badCfg2) } yield cfg

  implicit val arbGenGoodCfg2 = Arbitrary(genGoodCfg2)
  implicit val arbGenBadCfg2 = Arbitrary(genBadCfg2)
  implicit val arbGenGoodCfg1 = Arbitrary(genGoodCfg1)
  implicit val arbGenBadCfg1 = Arbitrary(genBadCfg1)
}

class ConfigurationSpecs extends mutable.Specification with ScalaCheck { override def is = s2"""
  Tube is only friendly with restart-strategies that are aligned with Apache Flink $restartStrategySpecs1
  Tube catches non friendly restart-strategies $restartStrategySpecs2
  Tube validates the valid configurations in restart-strategies $catchWhenValid
  Tube catches the invalid configurations in restart-strategies $catchWhenInValid
  """

  val  minimumNumberOfTests = 200

  def restartStrategySpecs1 = {
    import ConfigurationData.arbGenGoodCfg1
    prop { (config : Config) ⇒
      ConfigValidator.validateStrategy(config).toEither.isRight
    }
  }

  def restartStrategySpecs2 = {
    import ConfigurationData.arbGenBadCfg1
    prop { (config : Config) ⇒
      ConfigValidator.validateStrategy(config).toEither.isLeft
    }
  }

  def catchWhenValid = {
    import ConfigurationData.arbGenGoodCfg2
    prop { (config : Config) ⇒
      ConfigValidator.loadDefaults(config).isRight
    }
  }

  def catchWhenInValid = {
    import ConfigurationData.arbGenBadCfg2
    import cats.data.Validated._
    prop { (config : Config) ⇒
      ConfigValidator.loadDefaults(config) match {
        case Left(errors) ⇒ true // this is expected
        case Right(obj) ⇒ obj match {
          case Invalid(errors2) => true // this is expected
          case Valid(theConfig) => // unexpected!
            false
        }
      }
    }
  }
}

