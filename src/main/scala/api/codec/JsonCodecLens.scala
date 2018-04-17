package nugit.tube.api.codec

/**
 * This object contains the sieve logic for validate whether the fields and
 * values we are interested in can be discovered.
 * See [[StatefulChannelPostMapper]] for an example of its usage.
 */
object JsonCodecLens {
  import cats._, data._, implicits._
  import io.circe.optics.JsonPath._

  def isReactionsFieldPresent : Reader[io.circe.Json, Option[Vector[io.circe.Json]]] = Reader{(json: io.circe.Json) ⇒ root.reactions.arr.getOption(json) }

  def isRepliesFieldPresent : Reader[io.circe.Json, Option[Vector[io.circe.Json]]] = Reader{(json: io.circe.Json) ⇒ root.replies.arr.getOption(json) }

  def isMentionsFieldPresent : Reader[io.circe.Json, Option[Vector[io.circe.Json]]] = Reader{(json: io.circe.Json) ⇒ root.mentions.arr.getOption(json) }

}

