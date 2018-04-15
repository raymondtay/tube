package nugit.tube.api.posts


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
  *
  * This specification tests for the filtering logic that is being applied to
  * the messages of a slack channel [[MessageFilter]]
  */
object MessageGenerators {

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
    fileComment1 ← genUserFileComment
    fileComment2 ← genUserFileComment
    user ← option(arbitrary[String].suchThat(!_.isEmpty))
    bot_id ← option(arbitrary[String].suchThat(!_.isEmpty))
    ts ← arbitrary[String].suchThat(!_.isEmpty)
    mentions ← listOfN(3, generateLegalSlackUserIds)
  } yield UserFileShareMessage(tpe, subtype, text, file, fileComment1 ::fileComment2 ::Nil, user, bot_id, ts, mentions)

  def genBotAttachment : Gen[BotAttachment] = for {
    fallback ← arbitrary[String].suchThat(!_.isEmpty)
    text ← arbitrary[String].suchThat(!_.isEmpty)
    pretext ← arbitrary[String].suchThat(!_.isEmpty)
    id ← arbitrary[Long]
    color ← arbitrary[String].suchThat(!_.isEmpty)
    mrkdwn_in1 ← alphaStr.suchThat(!_.isEmpty)
    mrkdwn_in2 ← alphaStr.suchThat(!_.isEmpty)
  } yield BotAttachment(fallback, text, pretext, id, color, mrkdwn_in1::mrkdwn_in2::Nil)


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
    user ← option(arbitrary[String].suchThat(!_.isEmpty))
    bot_id ← option(arbitrary[String].suchThat(!_.isEmpty))
    botAtt1 ← genBotAttachment
    botAtt2 ← genBotAttachment
    reac1 ← genReaction
    reac2 ← genReaction
    reply1 ← genReply
    reply2 ← genReply
    text ← arbitrary[String].suchThat(!_.isEmpty)
    ts ← arbitrary[String].suchThat(!_.isEmpty)
    mentions ← listOfN(3, generateLegalSlackUserIds)
  } yield BotAttachmentMessage(tpe, user, bot_id, text, botAtt1::botAtt2::Nil, ts, reac1::reac2::Nil, reply1::reply2::Nil, mentions)

  def genUserAttachmentMessage : Gen[UserAttachmentMessage] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    user ← option(arbitrary[String].suchThat(!_.isEmpty))
    text ← arbitrary[String].suchThat(!_.isEmpty)
    ts ← arbitrary[String].suchThat(!_.isEmpty)
    reac1 ← genReaction
    reac2 ← genReaction
    reply1 ← genReply
    reply2 ← genReply
    mentions ← listOfN(3, generateLegalSlackUserIds)
  } yield UserAttachmentMessage(tpe, user, text, List(Json.arr(Json.fromString("test"))), ts, reac1::reac2::Nil, reply1::reply2::Nil, mentions)

  val genFileComment : Gen[FileComment] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    subtype  ← arbitrary[String].suchThat(!_.isEmpty)
    text  ← arbitrary[String].suchThat(!_.isEmpty)
    user  ← arbitrary[String].suchThat(!_.isEmpty)
    comment  ← option(arbitrary[String].suchThat(!_.isEmpty))
    mentions  ← listOfN(5, generateLegalSlackUserIds)
    reactions  ← listOfN(5, genReaction)
  } yield FileComment(tpe, subtype, text, user, comment, mentions, reactions, "123123.123123")

  def genSievedMessagesWithNoUserMentions : Gen[SievedMessages] = for {
    as ← genBotAttachmentMessage
    bs ← genUserAttachmentMessage
    cs ← genUserFileShareMessage
    ds ← genFileComment
    es ← genWhitelistedMessageWithNoUserMentions
  } yield SievedMessages(as::Nil, bs::Nil, cs::Nil, ds::Nil, es::Nil)

  def genSievedMessagesWithUserMentions : Gen[SievedMessages] = for {
    as ← genBotAttachmentMessage
    bs ← genUserAttachmentMessage
    cs ← genUserFileShareMessage
    ds ← genFileComment
    es ← listOfN(1, genWhitelistedMessageWithUserMentions)
  } yield SievedMessages(as::Nil, bs::Nil, cs::Nil, ds::Nil, es)

  val jsonWithNoUserMentions =
    ("""
    {
      "type": "message",
      "user": "U031ZH8HL",
      "text": "good spotting...",
      "ts": "1521179654.000121"
    }
    """ ::
    """
    {
      "type": "message",
      "user": "U32RGMDU5",
      "text": "<!channel> just a gentle reminder to please close the door cause this morning,
              one of our landlord's guy just went straight inside cause the door was wide open.
              Best to make a habit to always close the door regardless if someone is still in there or not. Thanks in advance!",
      "ts": "1521169888.000221"
    } 
    """ :: Nil).map(parse(_).getOrElse(Json.Null))

  val jsonWithUserMentions =
    ("""
    {
      "type": "message",
      "user": "U024ZH7HL",
      "text": "good spotting <@U1122>, you have to thank <@U11442> for this!",
      "ts": "1521179654.000129",
      "mentions" : ["U1122"]
    }
    """ ::
    """
    {
      "type": "message",
      "user": "U32RGMDU5",
      "text": "<@U123123> just a gentle reminder to please close the door cause this morning, <@U444111> just went straight inside cause the door was wide open.  Best to make a habit to always close the door regardless if someone is still in there or not. Thanks in advance!",
      "ts": "1521169888.000221",
      "mentions" : ["U123123","U444111"]
    } 
    """ :: Nil).map(parse(_).getOrElse(Json.Null))

  val genWhitelistedMessageWithNoUserMentions : Gen[Json] = for {
    json ← oneOf(jsonWithNoUserMentions)
  } yield json

  val genWhitelistedMessageWithUserMentions : Gen[Json] = for {
    json ← oneOf(jsonWithUserMentions)
  } yield json
 
  implicit val arbGenSievedMessagesWithNoUserMentions = Arbitrary(genSievedMessagesWithNoUserMentions)
  implicit val arbGenSievedMessagesWithUserMentions = Arbitrary(genSievedMessagesWithUserMentions)

}

class MessageFilterSpecs extends mutable.Specification with ScalaCheck {override def is = sequential ^ s2"""
  Filtering of 'BotAttachmentMessages' where either "reactions" != empty OR "mentions" != empty $filterBotMessages
  Filtering of 'UserAttachmentMessages' where either "reactions" != empty OR "mentions" != empty $filterUserAttachmentMessages
  Filtering of 'UserFileShareMessages' where either "comments" != empty OR "mentions" != empty $filterUserFileShareMessages
  Filtering of 'FileComment' where either "reactions" != empty OR "mentions" != empty $filterWhitelistedMessagesWithNoUserMentions
  Filtering of 'FileComment' where either "reactions" != empty OR "mentions" != empty $filterWhitelistedMessagesWithUserMentions
  """

  def filterWhitelistedMessagesWithNoUserMentions = {
    import MessageGenerators.arbGenSievedMessagesWithNoUserMentions
    prop { (msg: SievedMessages) ⇒
      val result = MessageFilter.apply(msg)
      result.whitelistedMessages.size must be_==(0)
    }.set(minTestsOk = 10)
  }

  def filterWhitelistedMessagesWithUserMentions = {
    import MessageGenerators.arbGenSievedMessagesWithUserMentions
    prop { (msg: SievedMessages) ⇒
      val result = MessageFilter.apply(msg)
      result.whitelistedMessages.size must beBetween(1,2)
    }.set(minTestsOk = 10)
  }

  def filterBotMessages = {
    import MessageGenerators.arbGenSievedMessagesWithNoUserMentions
    prop { (msg: SievedMessages) ⇒
      val result = MessageFilter.apply(msg)
      result.botMessages.map(_.mentions.size must be_==(3))
    }.set(minTestsOk = 10)
  }

  def filterUserAttachmentMessages = {
    import MessageGenerators.arbGenSievedMessagesWithNoUserMentions
    prop { (msg: SievedMessages) ⇒
      val result = MessageFilter.apply(msg)
      result.userAttachmentMessages.map(_.mentions.size must be_==(3))
    }.set(minTestsOk = 10)
  }

  def filterUserFileShareMessages = {
    import MessageGenerators.arbGenSievedMessagesWithNoUserMentions
    prop { (msg: SievedMessages) ⇒
      val result = MessageFilter.apply(msg)
      result.userFileShareMessages.map(_.mentions.size must be_==(3))
    }.set(minTestsOk = 10)
  }

  def filterFileCommentMessages = {
    import MessageGenerators.arbGenSievedMessagesWithNoUserMentions
    prop { (msg: SievedMessages) ⇒
      val result = MessageFilter.apply(msg)
      result.fileCommentMessages.map(_.mentions.size must be_==(5))
    }.set(minTestsOk = 10)
  }

}

