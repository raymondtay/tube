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
  if (channelIds.isEmpty) throw new RuntimeException("Expecting a non-empty container")

  private[this] val partition : Int = cerebroPostsConfig.parSize

  override def getMaximumNumberOfSplits() : Int = partition

  /**
    * The last of the splitted iterators would bear the burden of carrying the
    * extra load. If there is more splits than there is data, we throw a RTE
    * because it means there's a logical error since [[SplittableIterator]] is
    * likely defined by the developer otherwise we check whether the splits
    * requested partitions the data perfectly; if not, the burden is shouldered
    * by the last iterator in that container.
    */
  override def split(numberOfSplits : Int) : Array[java.util.Iterator[String]] = {
    if (channelIds.size < numberOfSplits) {
      throw new RuntimeException(s"You cannot have more splits (i.e. $numberOfSplits) than there is data (i.e. ${channelIds.size}). Reduce the parallelism for this job")
    }

    val sizeOfPartition = (channelIds.size / numberOfSplits)
    if ((channelIds.size % numberOfSplits) == 0) {
      channelIds.sliding(sizeOfPartition,sizeOfPartition).toArray.map(_.iterator.asJava)
    } else {
      val t = channelIds.sliding(sizeOfPartition,sizeOfPartition).toArray
      val last = t.tail.reverse.head
      val head = t.head
      val rem = t.tail.reverse.tail
      (rem :+ (head ++ last)).map(_.iterator.asJava).toArray
    }
  }

  /* Developer does not need to implement this */
  override def hasNext : Boolean = ???

  /* Developer does not need to implement this */
  override def next = ???
}

