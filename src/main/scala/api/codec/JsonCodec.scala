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

  // 
  // User `file_share` messages have the rule such that
  // (a) either the "user" or "bot_id" will be injected with the appropriate ids i.e. U123123 or B11333
  implicit val ufsEnc : Encoder[UserFileShareMessage] = new Encoder[UserFileShareMessage] {
    final def apply(c: UserFileShareMessage): Json = {
      var baseJsonObject : Option[JsonObject] =
        Json.obj(
          ("type"    , Json.fromString(c.`type`)),
          ("subtype" , Json.fromString(c.subtype)),
          ("text"    , Json.fromString(c.text)),
          ("file"    , c.file.asJson.asObject.fold(Json.Null)(Json.fromJsonObject(_))),
          ("comments", Json.arr(c.comments.map(_.asJson):_*)), 
          ("ts"      , Json.fromString(c.ts))
        ).asObject

      baseJsonObject = baseJsonObject.map(base ⇒  c.user.fold(base.add("user", Json.Null))(u ⇒ base.add("user", Json.fromString(u))))
      baseJsonObject = baseJsonObject.map(base ⇒  c.bot_id.fold(base.add("bot_id", Json.Null))(b ⇒ base.add("bot_id", Json.fromString(b))))
      baseJsonObject =
        c.mentions match {
          case Nil ⇒ baseJsonObject
          case xs  ⇒ baseJsonObject.map(base ⇒ base.add("mentions", Json.arr(xs.map(Json.fromString(_)): _*)))
        }
      baseJsonObject.fold(Json.Null)(Json.fromJsonObject(_))
    }
  }

  implicit val botAttachmentEnc : Encoder[BotAttachmentMessage] = new Encoder[BotAttachmentMessage] {
    final def apply(c: BotAttachmentMessage): Json = {
      var baseJsonObject : Option[JsonObject] =
        Json.obj(
          ("type"       , Json.fromString(c.`type`)),
          ("bot_id"     , c.bot_id.fold(Json.fromString(""))(bId ⇒ Json.fromString(bId))),
          ("text"       , Json.fromString(c.text)),
          ("attachments", Json.arr(c.attachments.map(_.asJson): _*)),
          ("ts"         , Json.fromString(c.ts)),
          ("reactions"  , Json.arr(c.reactions.map(_.asJson): _*)),
          ("replies"    , Json.arr(c.replies.map(_.asJson): _*))
        ).asObject
      baseJsonObject = baseJsonObject.map(base ⇒  c.user.fold(base)(u ⇒ base.add("user", Json.fromString(u))))
      baseJsonObject =
        c.mentions match {
          case Nil ⇒ baseJsonObject
          case xs  ⇒ baseJsonObject.map(base ⇒ base.add("mentions", Json.arr(xs.map(Json.fromString(_)): _*)))
        }
      baseJsonObject.fold(Json.Null)(Json.fromJsonObject(_))
    }
  }

  implicit val userAttachmentEnc : Encoder[UserAttachmentMessage] = new Encoder[UserAttachmentMessage] {
    final def apply(c: UserAttachmentMessage): Json = {
      var baseJsonObject : Option[JsonObject] =
        Json.obj(
          ("type"       , Json.fromString(c.`type`)),
          ("text"       , Json.fromString(c.text)),
          ("attachments", Json.arr(c.attachments: _*)),
          ("ts"         , Json.fromString(c.ts)),
          ("reactions"  , Json.arr(c.reactions.map(_.asJson): _*)),
          ("replies"    , Json.arr(c.replies.map(_.asJson): _*))
        ).asObject
      baseJsonObject = baseJsonObject.map(base ⇒  c.user.fold(base)(u ⇒ base.add("user", Json.fromString(u))))
      baseJsonObject =
        c.mentions match {
          case Nil ⇒ baseJsonObject
          case xs  ⇒ baseJsonObject.map(base ⇒ base.add("mentions", Json.arr(xs.map(Json.fromString(_)): _*)))
        }
      baseJsonObject.fold(Json.Null)(Json.fromJsonObject(_))
    }
  }

}

