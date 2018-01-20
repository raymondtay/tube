package nugit.tube.api.posts

import org.apache.flink.streaming.api.functions.source._
import org.apache.flink.annotation.PublicEvolving
import org.apache.flink.api.common.state.{ListState,ListStateDescriptor}
import org.apache.flink.api.common.typeutils.base.StringSerializer
import org.apache.flink.runtime.state.FunctionInitializationContext
import org.apache.flink.runtime.state.FunctionSnapshotContext
import org.apache.flink.streaming.api.checkpoint.{CheckpointedFunction, ListCheckpointed}
import org.apache.flink.util.{Preconditions, SplittableIterator}
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
 * It works in tandem with [[ChannelIdsSplittableIterator]].
 */
class StatefulPostsRetriever(token: SlackAccessToken[String])
                            (slackReadCfg: NonEmptyList[ConfigValidation] Either SlackChannelReadConfig[String])
                            (implicit actorSystem : ActorSystem, actorMaterializer : ActorMaterializer)
                            extends MapFunction[String, (ChannelPosts,List[String])] with ListCheckpointed[StatefulPostsRetriever] with CheckpointListener {

  private var channelId : String = _
  private var restored = false
  private var atLeastOneSnapshotComplete = false

  @transient private[this] val logger = LoggerFactory.getLogger(classOf[StatefulPostsRetriever])

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
    val snapshotSleepTime = 100
    (atLeastOneSnapshotComplete, restored) match {
      case (false, _) =>
        logger.debug("[map] Sleeping 100 ms for snapshot to complete.")
        Thread.sleep(snapshotSleepTime)
      case (true, true) =>
        logger.debug("[map] Recovered at least 1 snapshot and state is restored ")
      case (true, false) =>
        logger.debug("[map] Recovered at least 1 snapshot but state is not restored")
        throw new RuntimeException("Intended failure, to trigger restore")
    }
    val (posts, logs) = getChannelConversationHistory(slackReadCfg)(channelId).run(token)
    this.channelId = channelId
    (posts, logs)
  }
}

