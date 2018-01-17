package nugit.tube.api.codec

import nugit.tube.api.model._
import providers.slack.models.{UserFileShareMessage, BotAttachmentMessage, UserAttachmentMessage, JsonCodec ⇒ SlackJsonCodec}
import slacks.core.program.SievedMessages

object JsonCodec {
  import io.circe._, io.circe.syntax._, io.circe.generic.semiauto._
  import cats._, implicits._
  import SlackJsonCodec._

  implicit val slacksPostsEncoder : Encoder[ChannelPosts] = deriveEncoder[ChannelPosts]
  implicit val slackSievedMessagesEncoder : Encoder[SievedMessages] = new Encoder[SievedMessages] {
    final def apply(a: SievedMessages) : Json = 
        a.botMessages.asJson.deepMerge(
        a.userFileShareMessages.asJson).deepMerge(
        a.userAttachmentMessages.asJson)
  }

  implicit val ufsEnc : Encoder[UserFileShareMessage] = deriveEncoder[UserFileShareMessage]
  implicit val botAttachmentEnc : Encoder[BotAttachmentMessage] = deriveEncoder[BotAttachmentMessage]
  implicit val userAttachmentEnc : Encoder[UserAttachmentMessage] = deriveEncoder[UserAttachmentMessage]
 
}

