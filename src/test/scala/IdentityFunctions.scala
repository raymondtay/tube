package nugit.tube.api

import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction

/**
  * Identity mapper where `a -> a` 
  */
class IdentityMapper[T] extends MapFunction[T, T] {
  override def map(value: T) : T = value
}

class NoOpSink[T] extends SinkFunction[T] {
  override def invoke(value: T) : Unit = { }
}


