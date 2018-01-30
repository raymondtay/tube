package nugit.tube.api.posts

import org.specs2._
import org.specs2.specification.AfterAll
import org.scalacheck._
import Arbitrary._
import Gen.{alphaStr, fail, sequence, nonEmptyContainerOf, choose, pick, mapOf, listOfN, oneOf}
import Prop.{forAll, throws, AnyOperators}
import slacks.core.config.Config
import scala.collection.JavaConverters._

object ChannelIdsData {
  val size = 10
  val step = 1
  def channelIdsLTsize : Gen[(List[String], Int)] = for {
    xs <- listOfN[String](size, alphaStr suchThat (!_.isEmpty))
  } yield (xs, xs.size + step)

  def channelIdsEQsize : Gen[(List[String], Int)] = for {
    xs <- listOfN[String](size, alphaStr suchThat (!_.isEmpty))
  } yield (xs, xs.size)

  def channelIdsGTsize : Gen[(List[String], Int)] = for {
    xs <- listOfN[String](size, alphaStr suchThat (!_.isEmpty))
  } yield (xs, xs.size - step)

  implicit val arbGenGoodCfg1 = Arbitrary(channelIdsLTsize)
  implicit val arbGenGoodCfg2 = Arbitrary(channelIdsEQsize)
  implicit val arbGenGoodCfg3 = Arbitrary(channelIdsGTsize)
}

class ChannelIdsSplitterSpecs extends mutable.Specification with ScalaCheck {override def is = sequential ^ s2"""
  Number of spilts must be as in the configuration $splitEQconfiguration
  Catch RTE when empty containers are passed-in. $whenDataIsEmpty
  Catch RTE when data is less than requested splits $whenDataLTSplits
  When data is partitioned perfectly by the numberOfSplits $whenSplitsEQData
  When data is NOT partitioned perfectly by the numberOfSplits $whenSplitsGTData
  """

  def splitEQconfiguration = {
    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
      case Some(cfg) ⇒
        val x = new ChannelIdsSplittableIterator("fake-channel-id"::Nil)(cfg.seedPostsCfg)
        x.getMaximumNumberOfSplits() must be_==(cfg.seedPostsCfg.parSize)
    }
  }

  def whenDataIsEmpty = {
    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
      case Some(cfg) ⇒
        new ChannelIdsSplittableIterator(Nil)(cfg.seedPostsCfg) must throwA[RuntimeException]
    }
  }

  def whenDataLTSplits = {
    import ChannelIdsData.arbGenGoodCfg1
    prop { (pair: (scala.collection.immutable.List[String],Int)) ⇒
      (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
        case Some(cfg) ⇒
          val splitter = new ChannelIdsSplittableIterator(pair._1)(cfg.seedPostsCfg)
          val numberOfSplits = pair._2
          splitter.split(numberOfSplits) must throwA[RuntimeException]
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  def whenSplitsEQData = {
    import ChannelIdsData.arbGenGoodCfg2
    prop { (pair: (scala.collection.immutable.List[String],Int)) ⇒
      (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
        case Some(cfg) ⇒
          val splitter = new ChannelIdsSplittableIterator(pair._1)(cfg.seedPostsCfg)
          val numberOfSplits = pair._2
          splitter.split(numberOfSplits).size must be_==(numberOfSplits) 
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  def whenSplitsGTData = {
    import ChannelIdsData.arbGenGoodCfg3
    prop { (pair: (scala.collection.immutable.List[String],Int)) ⇒
      (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
        case Some(cfg) ⇒
          val splitter = new ChannelIdsSplittableIterator(pair._1)(cfg.seedPostsCfg)
          val numberOfSplits = pair._2
          val iterators = splitter.split(numberOfSplits).toList.map(_.asScala.toList)
          iterators.size must be_==(numberOfSplits)
          iterators.reverse.head.size > iterators.head.size
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  val minimumNumberOfTests = 100
}

