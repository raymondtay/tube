package nugit.tube.api.codec


import io.circe._, io.circe.parser._, io.circe.syntax._, io.circe.generic.semiauto._
import cats._, implicits._

import org.specs2._
import org.specs2.matcher.JsonMatchers
import org.specs2.specification.AfterAll
import org.scalacheck._
import Arbitrary._
import Gen._
import Prop.{forAll, throws, AnyOperators}

import slacks.core.config.Config
import slacks.core.program.SievedMessages
import scala.collection.JavaConverters._
import providers.slack.models.{FileComment, Reaction, Reply, BotAttachment, UserFile, UserFileComment, UserFileShareMessage, BotAttachmentMessage, UserAttachmentMessage, JsonCodec ⇒ SlackJsonCodec}

import nugit.tube.api.model.ChannelPosts

/**
  * The APIs that are like containerOf, listOf (its cousins and derivatives) are suffering from this:
  * https://github.com/rickynils/scalacheck/issues/89
  *
  * A possible work around:
  * https://github.com/rickynils/scalacheck/pull/370
  */
object JsonCodecGenerators {

  def generateLegalSlackUserIds : Gen[String] = for {
    suffix ← alphaNumStr.suchThat(!_.isEmpty)
  } yield s"<@U${suffix}>"

  def generateThumbs360 = oneOf("http://bogus.com/a.png", "","http://anotherbogus.com/444.jpg")
  def generateThumbsPdf = oneOf("http://bogus.com/a.pdf", "","http://anotherbogus.com/444.pdf")
  def generateThumbsVid = oneOf("http://bogus.com/a.mp4", "","http://anotherbogus.com/444.mp4")

 
  def genUserFile : Gen[UserFile] = for {
    filetype ← arbitrary[String].suchThat(!_.isEmpty)
    id ← arbitrary[String].suchThat(!_.isEmpty)
    title ← arbitrary[String].suchThat(!_.isEmpty)
    url_private ← arbitrary[String].suchThat(!_.isEmpty)
    external_type ← arbitrary[String].suchThat(!_.isEmpty)
    timestamp ← arbitrary[Long]
    pretty_type ← arbitrary[String].suchThat(!_.isEmpty)
    name ← arbitrary[String].suchThat(!_.isEmpty)
    mimetype ← arbitrary[String].suchThat(!_.isEmpty)
    permalink ← arbitrary[String].suchThat(!_.isEmpty)
    created ← arbitrary[Long]
    mode ← arbitrary[String].suchThat(!_.isEmpty)
    thumb360 ← option(generateThumbs360)
    thumbPdf ← option(generateThumbsPdf)
    thumbVid ← option(generateThumbsVid)
  } yield UserFile(filetype, id, title, url_private, external_type, timestamp, pretty_type, name, mimetype, permalink, created, mode, thumb360, thumbPdf, thumbVid)

  def genUserFileComment : Gen[UserFileComment] = for {
    id ← arbitrary[String].suchThat(!_.isEmpty)
    timestamp ← arbitrary[Long]
    user ← arbitrary[String].suchThat(!_.isEmpty)
  } yield UserFileComment(id, timestamp, user)

  def genUserFileShareMessage : Gen[UserFileShareMessage] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    subtype ← arbitrary[String].suchThat(!_.isEmpty)
    text ← arbitrary[String].suchThat(!_.isEmpty)
    file ← genUserFile
    fileInitialComment ← arbitrary[String].suchThat(!_.isEmpty)
    fileComment1 ← genUserFileComment
    fileComment2 ← genUserFileComment
    user ← option(arbitrary[String].suchThat(!_.isEmpty))
    bot_id ← option(arbitrary[String].suchThat(!_.isEmpty))
    ts ← arbitrary[String].suchThat(!_.isEmpty)
  } yield UserFileShareMessage(tpe, subtype, text, file, fileComment1 ::fileComment2 ::Nil, fileInitialComment, user, bot_id, ts, Nil)

  val attachmentData =
    """
      "attachments": [
      {
        "fallback": "blah blah blah",
        "text": "blah blah blah",
        "id": 1,
        "color": "33CC66",
        "mrkdwn_in": [
        "text"
        ]
      }
      ]
    """ ::
    """
      "attachments": [
      {
        "fallback": "blah blah blah",
        "text": "blah blah blah",
        "id": 1,
        "color": "33CC66",
        "mrkdwn_in": [
        "text"
        ]
      }
      ]
    """ :: Nil map(parse(_).getOrElse(Json.Null))
 
  def genBotAttachment : Gen[_root_.io.circe.Json] = for {
   json ← oneOf(attachmentData)
  } yield json

  def genReaction : Gen[Reaction] = for {
    name ← arbitrary[String].suchThat(!_.isEmpty)
    user1 ← alphaStr.suchThat(!_.isEmpty)
    user2 ← alphaStr.suchThat(!_.isEmpty)
  } yield Reaction(name, user1::user2::Nil)

  def genReply : Gen[Reply] = for {
    ts ← arbitrary[String].suchThat(!_.isEmpty)
    user ← arbitrary[String].suchThat(!_.isEmpty)
  } yield Reply(ts, user)

  def genBotAttachmentMessage : Gen[BotAttachmentMessage] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    subtype ← arbitrary[String].suchThat(!_.isEmpty)
    user ← option(arbitrary[String].suchThat(!_.isEmpty))
    bot_id ← option(arbitrary[String].suchThat(!_.isEmpty))
    botAtt ← genBotAttachment
    reac1 ← genReaction
    reac2 ← genReaction
    reply1 ← genReply
    reply2 ← genReply
    text ← arbitrary[String].suchThat(!_.isEmpty)
    ts ← arbitrary[String].suchThat(!_.isEmpty)
  } yield BotAttachmentMessage(tpe, subtype, user, bot_id, text, Vector(botAtt).some, ts, reac1::reac2::Nil, reply1::reply2::Nil, Nil)

  def genUserAttachmentMessage : Gen[UserAttachmentMessage] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    user ← option(arbitrary[String].suchThat(!_.isEmpty))
    text ← arbitrary[String].suchThat(!_.isEmpty)
    ts ← arbitrary[String].suchThat(!_.isEmpty)
    reac1 ← genReaction
    reac2 ← genReaction
    reply1 ← genReply
    reply2 ← genReply
  } yield UserAttachmentMessage(tpe, user, text, List(Json.arr(Json.fromString("test"))), ts, reac1::reac2::Nil, reply1::reply2::Nil, Nil)

  val genFileComment : Gen[FileComment] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    subtype  ← arbitrary[String].suchThat(!_.isEmpty)
    text  ← arbitrary[String].suchThat(!_.isEmpty)
    user  ← arbitrary[String].suchThat(!_.isEmpty)
    comment  ← option(arbitrary[String].suchThat(!_.isEmpty))
    mentions  ← listOfN(5, generateLegalSlackUserIds)
    reactions  ← listOfN(5, genReaction)
  } yield FileComment(tpe, subtype, text, user, comment, mentions, reactions, "123123.123123")

  val genJsonMessage : Gen[Json] = for {
    json ← oneOf(parse("{}").getOrElse(Json.Null) :: Nil)
  } yield json

  def genSievedMessages : Gen[SievedMessages] = for {
    as ← genBotAttachmentMessage
    bs ← genUserAttachmentMessage
    cs ← genUserFileShareMessage
    ds ← genFileComment
    es ← genJsonMessage
  } yield SievedMessages(as::Nil, bs::Nil, cs::Nil, ds::Nil, es::Nil)

  def genChannelPostsMessage : Gen[ChannelPosts] = for {
    channelId ← arbitrary[String].suchThat(!_.isEmpty)
    sievedMsgs ← genSievedMessages
  } yield ChannelPosts(channelId, sievedMsgs)

  val jsonWithInvalidReactions = // "reactions" field must be not top-level (i.e nested) objects
    """{ "key" : {"reactions" : [1,2]}}""" ::
    """{ "key" : {"reactions" : []}}""" ::
    """{ "key" : {}""" :: Nil map (parse(_).getOrElse(Json.Null))

  val jsonWithValidReactions = // "reactions" field must be top-level objects
    """{ "reactions" : [1,2]}""" ::
    """{ "reactions" : []}""" :: Nil map (parse(_).getOrElse(Json.Null))

  val jsonWithInvalidMentions = // "mentions" field must be not top-level (i.e nested) objects
    """{ "key" : {"mentions" : [1,2]}}""" ::
    """{ "key" : {"mentions" : []}}""" ::
    """{ "key" : {}""" :: Nil map (parse(_).getOrElse(Json.Null))

  val jsonWithValidMentions = // "mentions" field must be top-level objects
    """{ "mentions" : ["<@U11122> good job!","<@U11122> good job again!"]}""" ::
    """{ "mentions" : []}""" :: Nil map (parse(_).getOrElse(Json.Null))

  val jsonWithInvalidReplies = // "replies" field must be not top-level (i.e nested) objects
    """{ "key" : {"replies" : ["thanks","goodbye"]}}""" ::
    """{ "key" : {"replies" : []}}""" ::
    """{ "key" : {}""" :: Nil map (parse(_).getOrElse(Json.Null))

  val jsonWithValidReplies = // "replies" field must be top-level objects
    """{ "replies" : ["thanks","goodbye"]}""" ::
    """{ "replies" : []}""" :: Nil map (parse(_).getOrElse(Json.Null))

  val genJsonForNestedReactionsField = for {
    json ← oneOf(jsonWithInvalidReactions)
  } yield json

  val genJsonForToplevelReactionsField = for {
    json ← oneOf(jsonWithValidReactions)
  } yield json

  val genJsonForNestedMentionsField = for {
    json ← oneOf(jsonWithInvalidMentions)
  } yield json

  val genJsonForToplevelMentionsField = for {
    json ← oneOf(jsonWithValidMentions)
  } yield json

  val genJsonForNestedRepliesField = for {
    json ← oneOf(jsonWithInvalidReplies)
  } yield json

  val genJsonForToplevelRepliesField = for {
    json ← oneOf(jsonWithValidReplies)
  } yield json

  implicit val arbGenJsonToplevelReactionsField = Arbitrary(genJsonForToplevelReactionsField)
  implicit val arbGenJsonNestedReactionsField = Arbitrary(genJsonForNestedReactionsField)
  implicit val arbGenJsonToplevelMentionsField = Arbitrary(genJsonForToplevelMentionsField)
  implicit val arbGenJsonNestedMentionsField = Arbitrary(genJsonForNestedMentionsField)
  implicit val arbGenJsonToplevelRepliesField = Arbitrary(genJsonForToplevelRepliesField)
  implicit val arbGenJsonNestedRepliesField = Arbitrary(genJsonForNestedRepliesField)
  implicit val arbGenUserFile = Arbitrary(genUserFile)
  implicit val arbGenUserFileShareMessage = Arbitrary(genUserFileShareMessage)
  implicit val arbGenBotAttachmentMessage = Arbitrary(genBotAttachmentMessage)
  implicit val arbGenUserAttachmentMessage = Arbitrary(genUserAttachmentMessage)
  implicit val arbGenChannelPostsMessage = Arbitrary(genChannelPostsMessage)
}

class JsonCodecSpecs extends mutable.Specification with ScalaCheck {override def is = sequential ^ s2"""
  Generate 'UserFile' object as valid json $genUserFileJson
  Generate 'UserFileShareMessage' object as valid json $genUserFileShareMessageJson
  Generate 'UserAttachmentMessage' object as valid json $genUserAttachmentMessageJson
  Generate 'BotAttachmentMessage' object as valid json $genBotAttachmentMessageJson
  Generate 'ChannelPosts' object as valid json $genChannelPostsJson
  Validate that invalid "reactions" json returns 'false' $validateNestedReactionsField
  Validate that valid "reactions" json returns 'true' $validateToplevelReactionsField
  Validate that invalid "mentions" json returns 'false' $validateNestedMentionsField
  Validate that valid "mentions" json returns 'true' $validateToplevelMentionsField
  Validate that invalid "replies" json returns 'false' $validateNestedRepliesField
  Validate that valid "replies" json returns 'true' $validateToplevelRepliesField
  """

  import SlackJsonCodec._

  def validateNestedReactionsField = {
    import JsonCodecGenerators.arbGenJsonNestedReactionsField
    import JsonCodecLens._
    prop { (msg: _root_.io.circe.Json) ⇒
      isReactionsFieldPresent(msg) must beNone
    }.set(minTestsOk = 1)
  }

  def validateToplevelReactionsField = {
    import JsonCodecGenerators.arbGenJsonToplevelReactionsField
    import JsonCodecLens._
    prop { (msg: _root_.io.circe.Json) ⇒
      isReactionsFieldPresent(msg) must beSome
    }.set(minTestsOk = 1)
  }

  def validateNestedMentionsField = {
    import JsonCodecGenerators.arbGenJsonNestedMentionsField
    import JsonCodecLens._
    prop { (msg: _root_.io.circe.Json) ⇒
      isMentionsFieldPresent(msg) must beNone
    }.set(minTestsOk = 1)
  }

  def validateToplevelMentionsField = {
    import JsonCodecGenerators.arbGenJsonToplevelMentionsField
    import JsonCodecLens._
    prop { (msg: _root_.io.circe.Json) ⇒
      isMentionsFieldPresent(msg) must beSome
    }.set(minTestsOk = 1)
  }

  def validateNestedRepliesField = {
    import JsonCodecGenerators.arbGenJsonNestedRepliesField
    import JsonCodecLens._
    prop { (msg: _root_.io.circe.Json) ⇒
      isRepliesFieldPresent(msg) must beNone
    }.set(minTestsOk = 1)
  }

  def validateToplevelRepliesField = {
    import JsonCodecGenerators.arbGenJsonToplevelRepliesField
    import JsonCodecLens._
    prop { (msg: _root_.io.circe.Json) ⇒
      isRepliesFieldPresent(msg) must beSome
    }.set(minTestsOk = 1)
  }

  def genUserFileJson = {
    import JsonCodecGenerators.arbGenUserFile
    prop { (msg: UserFile) ⇒
      msg.asJson(SlackJsonCodec.slackUserFileEnc) must not beNull
    }.set(minTestsOk = 1)
  }

  def genUserFileShareMessageJson = {
    import JsonCodecGenerators.arbGenUserFileShareMessage
    prop { (msg: UserFileShareMessage) ⇒
      msg.asJson(JsonCodec.ufsEnc) must not beNull
    }.set(minTestsOk = 1)
  }

  def genBotAttachmentMessageJson = {
    import JsonCodecGenerators.arbGenBotAttachmentMessage
    prop { (msg: BotAttachmentMessage) ⇒
      msg.asJson(JsonCodec.botAttachmentEnc) must not beNull
    }.set(minTestsOk = 1)
  }

  def genUserAttachmentMessageJson = {
    import JsonCodecGenerators.arbGenUserAttachmentMessage
    prop { (msg: UserAttachmentMessage) ⇒
      msg.asJson(JsonCodec.userAttachmentEnc) must not beNull
    }.set(minTestsOk = 1)
  }

  def genChannelPostsJson = {
    import JsonCodecGenerators.arbGenChannelPostsMessage
    prop { (msg: ChannelPosts) ⇒
      msg.asJson(JsonCodec.slacksPostsEncoder) must not beNull
    }.set(minTestsOk = 1)
  }

}

