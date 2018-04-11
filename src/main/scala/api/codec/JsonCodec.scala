package nugit.tube.api.codec

import nugit.tube.api.model._
import providers.slack.models.{FileComment, UserFile, UserFileShareMessage, BotAttachmentMessage, UserAttachmentMessage, JsonCodec ⇒ SlackJsonCodec}
import slacks.core.program.SievedMessages

object JsonCodec {
  import io.circe._, io.circe.syntax._, io.circe.generic.semiauto._
  import cats._, implicits._
  import SlackJsonCodec._

  implicit val slacksPostsEncoder : Encoder[ChannelPosts] = deriveEncoder[ChannelPosts]
  implicit val slacksFileCommentEncoder : Encoder[FileComment] = deriveEncoder[FileComment]
  implicit val slackSievedMessagesEncoder : Encoder[SievedMessages] = new Encoder[SievedMessages] {
    final def apply(a: SievedMessages) : Json = {
      ( a.botMessages.asJson.asArray
        |@| a.userFileShareMessages.asJson.asArray
        |@| a.userAttachmentMessages.asJson.asArray
        |@| a.fileCommentMessages.asJson.asArray
        |@| a.whitelistedMessages.toVector.some
      ).map(_ ++ _ ++ _ ++ _ ++ _).map(Json.fromValues) match {
          case Some(mergedResult) ⇒ mergedResult
          case None ⇒ Json.arr()
        }
    }
  }

  implicit val ufsEnc : Encoder[UserFileShareMessage] = deriveEncoder[UserFileShareMessage]
  implicit val botAttachmentEnc : Encoder[BotAttachmentMessage] = deriveEncoder[BotAttachmentMessage]
  implicit val userAttachmentEnc : Encoder[UserAttachmentMessage] = deriveEncoder[UserAttachmentMessage]

}

