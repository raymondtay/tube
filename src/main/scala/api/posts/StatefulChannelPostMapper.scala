package nugit.tube.api.posts

import org.apache.flink.metrics.{Counter, SimpleCounter}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.source._
import org.apache.flink.streaming.api.checkpoint.{CheckpointedFunction, ListCheckpointed}
import org.apache.flink.api.common.functions._
import org.apache.flink.runtime.state._

import org.slf4j.{Logger, LoggerFactory}

import cats._, data._, implicits._
import akka.stream._
import akka.actor._

import nugit.tube.api.model.ChannelPosts
import nugit.tube.api.SlackFunctions._
import slacks.core.config._
import providers.slack.models.SlackAccessToken



/**
 * A stateful streaming mapper function that emits each (channel-id, channel-posts) exactly once,
 * The state captured is the channel id and it only proceeds if the snapshot is
 * completed.
 * When the restoration fails, then a RTE is thrown and when combined with
 * tube's restart strategy (i.e. fixed-delay or failure-rate) then this
 * computation can be re-computed for this channel id only.
 *
 * The following metrics are implemented:
 * (a) mapper-channel-counter
 * (b) mapper-posts-counter
 *
 * It works in tandem with [[ChannelIdsSplittableIterator]].
 *
 * @param token slack token
 * @param slackReadCfg the configuration object that guides this mapper to connect with cerebro and/or slack's APIs
 */
class StatefulPostsRetriever(token: SlackAccessToken[String])
                            (slackReadCfg: NonEmptyList[ConfigValidation] Either SlackChannelReadConfig[String])
                            extends RichMapFunction[String, (ChannelPosts,List[String])] with ListCheckpointed[StatefulPostsRetriever] with CheckpointListener {

  private var channelId : String = _
  private var restored = false
  private var atLeastOneSnapshotComplete = false

  @transient private[this] var logger : Logger = _
  @transient private[this] var cCounter : Counter = _
  @transient private[this] var pCounter : Counter = _

  override def open(params: Configuration) : Unit = {
    logger = LoggerFactory.getLogger(classOf[StatefulPostsRetriever])
    cCounter = getRuntimeContext().getMetricGroup().counter("mapper-channel-counter")
    pCounter = getRuntimeContext().getMetricGroup().counter("mapper-posts-counter")
  }

  override def notifyCheckpointComplete(checkpointId: Long): Unit = {
    atLeastOneSnapshotComplete = true
  }

  /* Members declared in org.apache.flink.streaming.api.checkpoint.ListCheckpointed */
  override def restoreState(state: java.util.List[StatefulPostsRetriever]): Unit = {
    if (state.isEmpty || state.size() > 1) throw new RuntimeException("Unexpected recovered state size " + state.size())
    restored = true
    val s = state.get(0)
    channelId = s.channelId
    atLeastOneSnapshotComplete = s.atLeastOneSnapshotComplete
    logger.debug(s"[restoreState] Restored state is :[$channelId]")
  }

  override def snapshotState(checkpointId: Long, timestamp: Long): java.util.List[StatefulPostsRetriever] = {
    logger.debug(s"[snapshotState] Collecting current state.")
    java.util.Collections.singletonList(this)
  }

  /* Members declared in org.apache.flink.api.common.functions.MapFunction */
  override def map(channelId: String): (ChannelPosts, List[String]) = {
    cCounter.inc()

    val snapshotSleepTime = 100
    (atLeastOneSnapshotComplete, restored) match {
      case (false, _) =>
        println     ("[map] Sleeping 100 ms for snapshot to complete.")
        logger.debug("[map] Sleeping 100 ms for snapshot to complete.")
        Thread.sleep(snapshotSleepTime)
      case (true, true) =>
        println     ("[map] Recovered at least 1 snapshot and state is restored ")
        logger.debug("[map] Recovered at least 1 snapshot and state is restored ")
      case (true, false) =>
        println     ("[map] Recovered at least 1 snapshot but state is not restored")
        logger.debug("[map] Recovered at least 1 snapshot but state is not restored")
    }
    val (posts, logs) = getChannelConversationHistory(slackReadCfg)(channelId).run(token)
    this.channelId = channelId
    /* Count how many messages did we see */
    pCounter.inc(sumOfMessages(posts.posts))
    (posts, logs)
  }

  /* Int operations are closed under addition so therefore, its ∈ Monoid */
  private def sumOfMessages(datum : slacks.core.program.SievedMessages) = 
    Monoid[Int].combine(datum.botMessages.size, Monoid[Int].combine(datum.userAttachmentMessages.size,datum.userFileShareMessages.size))
  
}

