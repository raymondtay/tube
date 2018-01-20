package nugit.tube.api.posts

import org.apache.flink.util.SplittableIterator

import cats._, implicits._
import cats.data.NonEmptyList
import cats.data.Validated._

import scala.collection.JavaConverters._

import nugit.tube.configuration.CerebroSeedPostsConfig
import slacks.core.config._

/**
 * Intended to be invoked by the runner of the seed-posts where it would
 * essentially split the data into n-partitions
 *
 * @param cerebroSeedPostsConfig configuration object for seeding posts; we extract the [[tube.cerebro.seed.posts.partition.size]] value
 * @param channelIds list of channel ids
 */
class ChannelIdsSplittableIterator(val channelIds: List[String])
                                  (cerebroPostsConfig: CerebroSeedPostsConfig)extends SplittableIterator[String] {
  private[this] val partition : Int = cerebroPostsConfig.parSize

  override def getMaximumNumberOfSplits() : Int = partition
  override def split(numberOfSplits : Int) : Array[java.util.Iterator[String]] = channelIds.grouped(partition).toArray.map(_.iterator.asJava)

  /* Developer does not need to implement this */
  override def hasNext : Boolean = ???

  /* Developer does not need to implement this */
  override def next = ???
}

