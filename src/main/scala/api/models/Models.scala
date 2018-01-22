package nugit.tube.api.model

import slacks.core.program.SievedMessages
import providers.slack.models.User

/* This will be reified to JSON. See [[PostSink]] */
case class ChannelPosts(channel: String, posts: SievedMessages) extends Serializable

/* Representation objects received from cerebro
 * and when cerebro is ok:
 * (a) {"received" : <some number>}
 * (b) {"message" : [ ... <json objects> ]} this latter format is driven by
 *     Python Flask (which drives Cerebro) and we haven't decided what to do
 *     with these errors just yet.
 *
 */
case class CerebroOK(received : Int) extends Serializable
case class CerebroNOK(message : List[io.circe.JsonObject]) /* As long as we see this structure */ extends Serializable

case class Users(users : List[User])

