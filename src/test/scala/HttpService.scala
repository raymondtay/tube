package nugit.tube.api

import providers.slack.algebra._
import slacks.core.models.Token
import providers.slack.models.SlackAccessToken
import slacks.core.program.HttpService
import slacks.core.config.SlackAccessConfig

import akka.actor._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}

// Service stubs for testing purposes
//

class FakeChannelListingHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) : Kleisli[Future,String,HttpResponse] = Kleisli{
    (_uri: String) ⇒
        val jsonData = """
        {"ok":true,"channels":[{"id":"C024Z5MQT","name":"general","is_channel":true,"created":1391648421,"creator":"U024Z5MQP","is_archived":false,"is_general":true,"unlinked":0,"name_normalized":"general","is_shared":false,"is_org_shared":false,"is_member":true,"is_private":false,"is_mpim":false,"members":["U024Z5MQP","U024ZCABY","U024ZCR04","U024ZH7HL","U0250SQLD","U02518S6S","U029A9L6M","U029ACXNZ","U02EJ9QKJ","U02MR8EG8","U02PY6S73","U030MHXHX","U034URXDR","U03C98L5C","U03CKFGU5","U047EAUB4","U0483ASQP","U049K6V1G","U04MGHVRY","U0790EWUW","U086LTM6W","U08GD90CC","U08TDQVNG","U0AM39YTX","U0CDW37RA","U0CE9A2E5","U0DATFFH9","U0F3F6F38","U0FB8THB8","U0FJKS5MM","U0G1H4L3E","U0GAZLRPW","U0L251X5W","U0LPSJQR0","U0PL0HUHG","U0RBSN9D1","U0X3L1PS7","U10H6PUSJ","U17RGMDU4","U193XDML7","U1NG7CPBK","U1NGC3ZPT","U1SF636UB","U23D7H5MZ","U2748C06S","U2FQG2G9F","U2M8UH9SM","U2Q2U37SA","U2YAZS40Y","U2Z0ARK2P","U31B3PV17","U37BF9457","U39R1AT9D","U3ACT6Z2P","U3LRTQ8G1","U3NND6PV1","U3RUCKH5J","U41CSF56Z","U43LNT57T","U43Q2RJ8H","U497EFER0","U4AFYEWBG","U4B93DBDX","U4BUQR94L","U4U2WKX7X","U4W385673","U543VFD3Q","U56JZMQ0Y","U575BN3H9","U577BHBNW","U58LY38Q6","U5K7JUATE","U5TEUA60Z","U5UG5NU6T","U5ZV5797E","U642GGK9R","U664CEM4L","U66T9CNBG","U6QFZ585N","U6R7SU9P0","U74K31TA9","U7JKEFHM0","U7SG2QG2D","U7V7V7NFM","U81GPG5HV"],"topic":{"value":"the day @thu was dethroned https:\/\/nugit.slack.com\/archives\/general\/p1476945193000075","creator":"U04MGHVRY","last_set":1499417480},"purpose":{"value":"The #general channel is for team-wide communication and announcements. All team members are in this channel.","creator":"","last_set":0},"previous_names":[],"num_members":38},{"id":"C024Z65M7","name":"dev-log","is_channel":true,"created":1391651270,"creator":"U024Z5MQP","is_archived":true,"is_general":false,"unlinked":0,"name_normalized":"dev-log","is_shared":false,"is_org_shared":false,"is_member":false,"is_private":false,"is_mpim":false,"members":[],"topic":{"value":"Updates on Github commits across all Nugit repositories.","creator":"U024Z5MQP","last_set":1400065716},"purpose":{"value":"Updates on Github commits across all Nugit repositories","creator":"U024Z5MQP","last_set":1400065746},"previous_names":["dev"],"num_members":0}],"response_metadata":{"next_cursor":"dGVhbTpDMDI0WkNWOFg="}}
        """
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}

class FakeChannelListingErrorHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import providers.slack.models.{SlackError,JsonCodec}
  import scala.concurrent.Future

  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) : Kleisli[Future,String,HttpResponse] = Kleisli{
    (_uri: String) ⇒
      import io.circe.syntax._
      import io.circe._, io.circe.generic.semiauto._
      implicit val errorEnc : Encoder[SlackError] = deriveEncoder[SlackError]
 
      val jsonData = SlackError(false, "invalid_auth").asJson.toString
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}

class FakeChannelHistoryErrorHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import providers.slack.models.{SlackError,JsonCodec}
  import scala.concurrent.Future

  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) : Kleisli[Future,String,HttpResponse] = Kleisli{
    (_uri: String) ⇒
      import io.circe.syntax._
      import io.circe._, io.circe.generic.semiauto._
      implicit val errorEnc : Encoder[SlackError] = deriveEncoder[SlackError]
      val jsonData = SlackError(false, "invalid_auth").asJson.toString
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}

class FakeChannelHistoryHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) : Kleisli[Future,String,HttpResponse] = Kleisli{
    (_uri: String) ⇒
        val jsonData = """
        {"ok":true,"messages":[{"text":"Hello. Another new user going by the name of Tracey Rountree (<mailto:tracey.rountree@netbooster.com|tracey.rountree@netbooster.com>) has passed through the Nugit gates. We've also added them to Mailchimp's Nugit Users list.  ","username":"Zapier","bot_id":"B0VD275DX","type":"message","subtype":"bot_message","ts":"1511157663.000229","reactions":[{"name":"tada","users":["U81GPG5HV"],"count":1}]},{"text":"","bot_id":"B139D7CUV","attachments":[{"fallback":"Varun received a :bulb: 5 bonus from Paul: +5 @varun for completing all sprint tasks two weeks ago #speedofbusiness #trailblazer","text":"*+5* @varun for completing all sprint tasks two weeks ago #speedofbusiness #trailblazer\n<https:\/\/bonus.ly\/bonuses\/5a126c9475d0770b6382dbf8?utm_source=bonus.ly&amp;utm_medium=chat&amp;utm_campaign=slack#add-on|Add on to this bonus?>","pretext":"<https:\/\/bonus.ly\/bonuses\/5a126c9475d0770b6382dbf8?utm_source=bonus.ly&amp;utm_medium=chat&amp;utm_campaign=slack|Varun received a bonus from Paul>","id":1,"color":"33CC66","mrkdwn_in":["text"]}],"type":"message","subtype":"bot_message","ts":"1511156887.000150"}],"has_more":false,"pin_count":27,"response_metadata":{"next_cursor":""}}"""
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}

class FakeOAuthHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) : Kleisli[Future,String,HttpResponse]= Kleisli{
    (_uri: String) ⇒
      val token = SlackAccessToken(Token("xoxp-", "fake"), "read" :: Nil)
      import io.circe._, io.circe.syntax._, io.circe.generic.auto._
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, token.asJson.noSpaces.toString))
      )
  }
}

