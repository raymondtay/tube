package nugit.tube.api.channels

import org.apache.flink.api.common.functions.AggregateFunction
import providers.slack.models._

class ChannelAggregator extends AggregateFunction[SlackChannel, List[SlackChannel], List[SlackChannel]] {
  override def createAccumulator(): List[SlackChannel] = Nil

  override def merge(xs: List[SlackChannel], ys: List[SlackChannel]): List[SlackChannel] = xs ++ ys

  override def getResult(accumulator: List[SlackChannel]): List[SlackChannel] = accumulator

  override def add(value: SlackChannel, accumulator: List[SlackChannel]): List[SlackChannel] = value :: accumulator
}
