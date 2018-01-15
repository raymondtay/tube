package nugit.tube.cli

import org.specs2._
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}
import com.typesafe.config._


object ConfigurationData {

  val overrideRestartStrategyOnly = Array(
    Array("--restart-strategy", "none"),
    Array("--restart-strategy", "fixed_delay")
  )

  val overrideParallelismOnly = Array(
    Array("--parallelism", "2", "--restart-strategy", "none"),
    Array("--parallelism", "3", "--restart-strategy", "fixed_delay")
  )

  val genGoodCfg1 = for { cfg <- oneOf(overrideRestartStrategyOnly) } yield cfg
  val genGoodCfg2 = for { cfg <- oneOf(overrideParallelismOnly) } yield cfg

  implicit val arbGenGoodCfg1 = Arbitrary(genGoodCfg1)
  implicit val arbGenGoodCfg2 = Arbitrary(genGoodCfg2)
}

class CliSpecs extends mutable.Specification with ScalaCheck { override def is = s2"""
  When 'restart-strategy' is set to either 'none', 'fixed-delay' or 'failure-rate'
  -----------------------------------------------------------------------------------

  Tube returns the default count of 1 when you do not override it on the commandline $parallelismWhenOverrideNo
  Tube returns the passed-in number when you override it on the commandline $parallelismWhenOverrideYes
  """

  import CommandlineParser._
  val  minimumNumberOfTests = 200

  def parallelismWhenOverrideNo = {
    import ConfigurationData.arbGenGoodCfg1
    prop { (args : Array[String]) ⇒
      parseCommandlineArgs(args.toSeq) match {
        case Some(cfg) ⇒ cfg.parallelism == 1
        case None ⇒ false // this should not happen
      }
    }
  }

  def parallelismWhenOverrideYes = {
    import ConfigurationData.arbGenGoodCfg2
    prop { (args : Array[String]) ⇒
      parseCommandlineArgs(args.toSeq) match {
        case Some(cfg) ⇒ Set(2,3).contains(cfg.parallelism)
        case None ⇒ false // this should not happen
      }
    }
  }

}

