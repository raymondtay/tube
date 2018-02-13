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
        {"ok":true,
         "channels":[
           {"id":"C024Z5MQT","name":"general","is_channel":true,"created":1391648421,
            "creator":"U024Z5MQP","is_archived":false,"is_general":true,"unlinked":0,
            "name_normalized":"general","is_shared":false,"is_org_shared":false,"is_member":true,
            "is_private":false,"is_mpim":false,"members":["U024Z5MQP","U024ZCABY","U024ZCR04","U024ZH7HL","U0250SQLD","U02518S6S","U029A9L6M","U029ACXNZ","U02EJ9QKJ","U02MR8EG8","U02PY6S73","U030MHXHX","U034URXDR","U03C98L5C","U03CKFGU5","U047EAUB4","U0483ASQP","U049K6V1G","U04MGHVRY","U0790EWUW","U086LTM6W","U08GD90CC","U08TDQVNG","U0AM39YTX","U0CDW37RA","U0CE9A2E5","U0DATFFH9","U0F3F6F38","U0FB8THB8","U0FJKS5MM","U0G1H4L3E","U0GAZLRPW","U0L251X5W","U0LPSJQR0","U0PL0HUHG","U0RBSN9D1","U0X3L1PS7","U10H6PUSJ","U17RGMDU4","U193XDML7","U1NG7CPBK","U1NGC3ZPT","U1SF636UB","U23D7H5MZ","U2748C06S","U2FQG2G9F","U2M8UH9SM","U2Q2U37SA","U2YAZS40Y","U2Z0ARK2P","U31B3PV17","U37BF9457","U39R1AT9D","U3ACT6Z2P","U3LRTQ8G1","U3NND6PV1","U3RUCKH5J","U41CSF56Z","U43LNT57T","U43Q2RJ8H","U497EFER0","U4AFYEWBG","U4B93DBDX","U4BUQR94L","U4U2WKX7X","U4W385673","U543VFD3Q","U56JZMQ0Y","U575BN3H9","U577BHBNW","U58LY38Q6","U5K7JUATE","U5TEUA60Z","U5UG5NU6T","U5ZV5797E","U642GGK9R","U664CEM4L","U66T9CNBG","U6QFZ585N","U6R7SU9P0","U74K31TA9","U7JKEFHM0","U7SG2QG2D","U7V7V7NFM","U81GPG5HV"],"topic":{"value":"the day @thu was dethroned https:\/\/nugit.slack.com\/archives\/general\/p1476945193000075","creator":"U04MGHVRY","last_set":1499417480},"purpose":{"value":"The #general channel is for team-wide communication and announcements. All team members are in this channel.","creator":"","last_set":0},"previous_names":[],"num_members":38},
           {"id":"C024Z65M7","name":"dev-log","is_channel":true,"created":1391651270,
            "creator":"U024Z5MQP","is_archived":true,"is_general":false,"unlinked":0,
            "name_normalized":"dev-log","is_shared":false,"is_org_shared":false,"is_member":false,
            "is_private":false,"is_mpim":false,"members":[],"topic":{"value":"Updates on Github commits across all Nugit repositories.","creator":"U024Z5MQP","last_set":1400065716},
            "purpose":{"value":"Updates on Github commits across all Nugit repositories","creator":"U024Z5MQP","last_set":1400065746},"previous_names":["dev"],"num_members":0}
           ],
           "response_metadata":{"next_cursor":"dGVhbTpDMDI0WkNWOFg="}
        }
        """
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}

/**
  * This class represents what typically happens when Slack returns an error
  * message when an invalid oauth token is presented.
  * If the implementation does not require any changes, then simply extend it
  * to whatever that's appropriate for the tests.
  */
class InvalidAuthTokenHttpService extends HttpService {
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

class FakeChannelListingErrorHttpService extends InvalidAuthTokenHttpService
class FakeChannelHistoryErrorHttpService extends InvalidAuthTokenHttpService
class FakeChannelConversationHistoryErrorHttpService extends InvalidAuthTokenHttpService
class FakeGetAllUsersErrorHttpService extends InvalidAuthTokenHttpService
class FakeTeamAlgosErrorHttpService extends InvalidAuthTokenHttpService

class FakeChannelHistoryHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) : Kleisli[Future,String,HttpResponse] = Kleisli{
    (_uri: String) ⇒
        val jsonData = """
        {"ok":true,
         "messages":[
           {"text":"Hello. Another new user going by the name of Tracey Rountree (<mailto:tracey.rountree@netbooster.com|tracey.rountree@netbooster.com>) has passed through the Nugit gates. We've also added them to Mailchimp's Nugit Users list.  ","username":"Zapier","bot_id":"B0VD275DX","type":"message","subtype":"bot_message","ts":"1511157663.000229","reactions":[{"name":"tada","users":["U81GPG5HV"],"count":1}]},
           {"text":"","bot_id":"B139D7CUV","attachments":[{"fallback":"Varun received a :bulb: 5 bonus from Paul: +5 @varun for completing all sprint tasks two weeks ago #speedofbusiness #trailblazer","text":"*+5* @varun for completing all sprint tasks two weeks ago #speedofbusiness #trailblazer\n<https:\/\/bonus.ly\/bonuses\/5a126c9475d0770b6382dbf8?utm_source=bonus.ly&amp;utm_medium=chat&amp;utm_campaign=slack#add-on|Add on to this bonus?>","pretext":"<https:\/\/bonus.ly\/bonuses\/5a126c9475d0770b6382dbf8?utm_source=bonus.ly&amp;utm_medium=chat&amp;utm_campaign=slack|Varun received a bonus from Paul>","id":1,"color":"33CC66","mrkdwn_in":["text"]}],"type":"message","subtype":"bot_message","ts":"1511156887.000150"}
        ],
        "has_more":false,
        "pin_count":27,
        "response_metadata":{"next_cursor":""}
        }
        """
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

class FakeGetAllUsersHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
    (_uri: String) ⇒ 
        val jsonData = """
        {"ok":true,"offset":"U0483ASQP","members":[{"id":"USLACKBOT","team_id":"T024Z5MQM","name":"slackbot","deleted":false,"color":"757575","real_name":"slackbot","tz":null,"tz_label":"Pacific Standard Time","tz_offset":-28800,"profile":{"first_name":"slackbot","last_name":"","image_24":"https:\/\/a.slack-edge.com\/0180\/img\/slackbot_24.png","image_32":"https:\/\/a.slack-edge.com\/41b0a\/img\/plugins\/slackbot\/service_32.png","image_48":"https:\/\/a.slack-edge.com\/41b0a\/img\/plugins\/slackbot\/service_48.png","image_72":"https:\/\/a.slack-edge.com\/0180\/img\/slackbot_72.png","image_192":"https:\/\/a.slack-edge.com\/66f9\/img\/slackbot_192.png","image_512":"https:\/\/a.slack-edge.com\/1801\/img\/slackbot_512.png","avatar_hash":"sv1444671949","always_active":true,"display_name":"slackbot","real_name":"slackbot","real_name_normalized":"slackbot","display_name_normalized":"slackbot","fields":null,"team":"T024Z5MQM"},"is_admin":false,"is_owner":false,"is_primary_owner":false,"is_restricted":false,"is_ultra_restricted":false,"is_bot":false,"updated":0,"is_app_user":false},{"id":"U024Z5MQP","team_id":"T024Z5MQM","name":"veron","deleted":true,"profile":{"first_name":"Veronica","last_name":"Ng","image_24":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169203855_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169203855_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169203855_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169203855_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169203855_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169203855_original.jpg","skype":"veronism","phone":"+65 9068 1341","title":"Product designer","avatar_hash":"OS2169203855","real_name":"Veronica Ng","display_name":"veron","real_name_normalized":"Veronica Ng","display_name_normalized":"veron","team":"T024Z5MQM"},"is_bot":false,"updated":1504244239,"is_app_user":false},{"id":"U024ZCABY","team_id":"T024Z5MQM","name":"seb","deleted":true,"profile":{"first_name":"Seb","last_name":"Deckers","image_24":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169393759_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169393759_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169393759_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169393759_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169393759_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169393759_original.jpg","skype":"sebastiaan.deckers","phone":"+6590464382","avatar_hash":"OS2169393759","real_name":"Seb Deckers","display_name":"seb","real_name_normalized":"Seb Deckers","display_name_normalized":"seb","team":"T024Z5MQM"},"is_bot":false,"updated":1504244240,"is_app_user":false},{"id":"U024ZCR04","team_id":"T024Z5MQM","name":"alyssa","deleted":true,"profile":{"image_24":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169572754_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169572754_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169572754_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169572754_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169572754_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2014-02-06\/2169572754_original.jpg","first_name":"alyssa","avatar_hash":"OS2169572754","real_name":"alyssa","display_name":"alyssa","real_name_normalized":"alyssa","display_name_normalized":"alyssa","team":"T024Z5MQM"},"is_bot":false,"updated":1504244240,"is_app_user":false},{"id":"U024ZH7HL","team_id":"T024Z5MQM","name":"sando","deleted":false,"color":"3c989f","real_name":"Dave Sanderson","tz":"Asia\/Kuala_Lumpur","tz_label":"Singapore Standard Time","tz_offset":28800,"profile":{"first_name":"Dave","last_name":"Sanderson","image_24":"https:\/\/avatars.slack-edge.com\/2014-03-12\/2214628878_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2014-03-12\/2214628878_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2014-03-12\/2214628878_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2014-03-12\/2214628878_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2014-03-12\/2214628878_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2014-03-12\/2214628878_original.jpg","skype":"DAVESANDO","phone":"+6592320144","title":"CEO","avatar_hash":"OS2214628878","status_text":"","status_emoji":"","real_name":"Dave Sanderson","display_name":"sando","real_name_normalized":"Dave Sanderson","display_name_normalized":"sando","team":"T024Z5MQM"},"is_admin":true,"is_owner":true,"is_primary_owner":true,"is_restricted":false,"is_ultra_restricted":false,"is_bot":false,"updated":1512190186,"is_app_user":false},{"id":"U0250SQLD","team_id":"T024Z5MQM","name":"alanho","deleted":true,"profile":{"first_name":"Alan","last_name":"Ho","skype":"alanchho","phone":"+852-97774211","title":"NUG48","image_24":"https:\/\/avatars.slack-edge.com\/2016-02-17\/21707531381_bf7556c06f58eb1f1c1d_24.png","image_32":"https:\/\/avatars.slack-edge.com\/2016-02-17\/21707531381_bf7556c06f58eb1f1c1d_32.png","image_48":"https:\/\/avatars.slack-edge.com\/2016-02-17\/21707531381_bf7556c06f58eb1f1c1d_48.png","image_72":"https:\/\/avatars.slack-edge.com\/2016-02-17\/21707531381_bf7556c06f58eb1f1c1d_72.png","image_192":"https:\/\/avatars.slack-edge.com\/2016-02-17\/21707531381_bf7556c06f58eb1f1c1d_192.png","image_original":"https:\/\/avatars.slack-edge.com\/2016-02-17\/21707531381_bf7556c06f58eb1f1c1d_original.png","image_512":"https:\/\/avatars.slack-edge.com\/2016-02-17\/21707531381_bf7556c06f58eb1f1c1d_512.png","image_1024":"https:\/\/avatars.slack-edge.com\/2016-02-17\/21707531381_bf7556c06f58eb1f1c1d_512.png","avatar_hash":"bf7556c06f58","real_name":"Alan Ho","display_name":"alanho","real_name_normalized":"Alan Ho","display_name_normalized":"alanho","team":"T024Z5MQM"},"is_bot":false,"updated":1504244245,"is_app_user":false},{"id":"U02518S6S","team_id":"T024Z5MQM","name":"jenny","deleted":true,"profile":{"first_name":"Jenny","last_name":"Shen","skype":"sjenny6","phone":"9775 3482","title":"UX\/UI Designer","image_24":"https:\/\/avatars.slack-edge.com\/2014-02-10\/2171301276_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2014-02-10\/2171301276_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2014-02-10\/2171301276_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2014-02-10\/2171301276_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2014-02-10\/2171301276_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2014-02-10\/2171301276_original.jpg","avatar_hash":"OS2171301276","real_name":"Jenny Shen","display_name":"jenny","real_name_normalized":"Jenny Shen","display_name_normalized":"jenny","team":"T024Z5MQM"},"is_bot":false,"updated":1504244246,"is_app_user":false},{"id":"U029A9L6M","team_id":"T024Z5MQM","name":"raman","deleted":true,"profile":{"image_24":"https:\/\/avatars.slack-edge.com\/2014-04-28\/2316327825_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2014-04-28\/2316327825_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2014-04-28\/2316327825_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2014-04-28\/2316327825_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2014-04-28\/2316327825_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2014-04-28\/2316327825_original.jpg","first_name":"Raman","last_name":"Shalupau","skype":"ksaitor","phone":"98354935","avatar_hash":"OS2316327825","real_name":"Raman Shalupau","display_name":"raman","real_name_normalized":"Raman Shalupau","display_name_normalized":"raman","team":"T024Z5MQM"},"is_bot":false,"updated":1504244278,"is_app_user":false},{"id":"U029ACXNZ","team_id":"T024Z5MQM","name":"xuanji","deleted":true,"profile":{"image_24":"https:\/\/avatars.slack-edge.com\/2014-04-29\/2317979390_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2014-04-29\/2317979390_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2014-04-29\/2317979390_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2014-04-29\/2317979390_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2014-04-29\/2317979390_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2014-04-29\/2317979390_original.jpg","first_name":"Li Xuanji","phone":"96959264","title":"Chief Science Officer","avatar_hash":"OS2317979390","real_name":"Li Xuanji","display_name":"xuanji","real_name_normalized":"Li Xuanji","display_name_normalized":"xuanji","team":"T024Z5MQM"},"is_bot":false,"updated":1504244278,"is_app_user":false},{"id":"U02EJ9QKJ","team_id":"T024Z5MQM","name":"terry","deleted":true,"profile":{"first_name":"Terry","last_name":"Chia","avatar_hash":"gd53cae58ac4","real_name":"Terry Chia","display_name":"terry","real_name_normalized":"Terry Chia","display_name_normalized":"terry","image_24":"https:\/\/secure.gravatar.com\/avatar\/d53cae58ac466ce69927dea16930dcc0.jpg?s=24&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0022-24.png","image_32":"https:\/\/secure.gravatar.com\/avatar\/d53cae58ac466ce69927dea16930dcc0.jpg?s=32&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0022-32.png","image_48":"https:\/\/secure.gravatar.com\/avatar\/d53cae58ac466ce69927dea16930dcc0.jpg?s=48&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0022-48.png","image_72":"https:\/\/secure.gravatar.com\/avatar\/d53cae58ac466ce69927dea16930dcc0.jpg?s=72&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0022-72.png","image_192":"https:\/\/secure.gravatar.com\/avatar\/d53cae58ac466ce69927dea16930dcc0.jpg?s=192&d=https%3A%2F%2Fa.slack-edge.com%2F7fa9%2Fimg%2Favatars%2Fava_0022-192.png","image_512":"https:\/\/secure.gravatar.com\/avatar\/d53cae58ac466ce69927dea16930dcc0.jpg?s=512&d=https%3A%2F%2Fa.slack-edge.com%2F7fa9%2Fimg%2Favatars%2Fava_0022-512.png","team":"T024Z5MQM"},"is_bot":false,"updated":1504244290,"is_app_user":false},{"id":"U02MR8EG8","team_id":"T024Z5MQM","name":"mickael","deleted":true,"profile":{"first_name":"Micka\u00ebl","last_name":"Dehez","title":"\u00af\\_(\u30c4)_\/\u00af","skype":"mickaeldehez","phone":"93880809","avatar_hash":"gf5d2c998ef3","real_name":"Micka\u00ebl Dehez","display_name":"mickael","real_name_normalized":"Mickael Dehez","display_name_normalized":"mickael","image_24":"https:\/\/secure.gravatar.com\/avatar\/f5d2c998ef3291049a86dd55afc842c9.jpg?s=24&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0001-24.png","image_32":"https:\/\/secure.gravatar.com\/avatar\/f5d2c998ef3291049a86dd55afc842c9.jpg?s=32&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0001-32.png","image_48":"https:\/\/secure.gravatar.com\/avatar\/f5d2c998ef3291049a86dd55afc842c9.jpg?s=48&d=https%3A%2F%2Fa.slack-edge.com%2F0180%2Fimg%2Favatars%2Fava_0001-48.png","image_72":"https:\/\/secure.gravatar.com\/avatar\/f5d2c998ef3291049a86dd55afc842c9.jpg?s=72&d=https%3A%2F%2Fa.slack-edge.com%2F3654%2Fimg%2Favatars%2Fava_0001-72.png","image_192":"https:\/\/secure.gravatar.com\/avatar\/f5d2c998ef3291049a86dd55afc842c9.jpg?s=192&d=https%3A%2F%2Fa.slack-edge.com%2F7fa9%2Fimg%2Favatars%2Fava_0001-192.png","image_512":"https:\/\/secure.gravatar.com\/avatar\/f5d2c998ef3291049a86dd55afc842c9.jpg?s=512&d=https%3A%2F%2Fa.slack-edge.com%2F7fa9%2Fimg%2Favatars%2Fava_0001-512.png","team":"T024Z5MQM"},"is_bot":false,"updated":1504244297,"is_app_user":false},{"id":"U02PY6S73","team_id":"T024Z5MQM","name":"mikelin","deleted":true,"profile":{"first_name":"Mike","last_name":"Lin","title":"Front End Engineer","skype":"private message?","phone":"97810276","image_24":"https:\/\/avatars.slack-edge.com\/2014-10-16\/2815873682_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2014-10-16\/2815873682_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2014-10-16\/2815873682_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2014-10-16\/2815873682_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2014-10-16\/2815873682_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2014-10-16\/2815873682_original.jpg","avatar_hash":"OS2815873682","status_text":"","status_emoji":"","guest_invited_by":"","guest_channels":"[]","real_name":"Mike Lin","display_name":"mikelin","real_name_normalized":"Mike Lin","display_name_normalized":"mikelin","team":"T024Z5MQM"},"is_bot":false,"updated":1513116165,"is_app_user":false},{"id":"U02V5HABC","team_id":"T024Z5MQM","name":"famousevo","deleted":true,"profile":{"first_name":"Evo","last_name":"[Famous Labs]","title":"Founder @ Famous Labs. I ship right things at the right time.","skype":"famouslabs","phone":"","image_24":"https:\/\/avatars.slack-edge.com\/2015-05-08\/4789033668_55c6ae2eadfa4e4b93a0_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2015-05-08\/4789033668_55c6ae2eadfa4e4b93a0_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2015-05-08\/4789033668_55c6ae2eadfa4e4b93a0_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2015-05-08\/4789033668_55c6ae2eadfa4e4b93a0_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2015-05-08\/4789033668_55c6ae2eadfa4e4b93a0_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2015-05-08\/4789033668_55c6ae2eadfa4e4b93a0_original.jpg","avatar_hash":"55c6ae2eadfa","guest_channels":"[]","guest_invited_by":"U024ZH7HL","real_name":"Evo [Famous Labs]","display_name":"famousevo","real_name_normalized":"Evo [Famous Labs]","display_name_normalized":"famousevo","team":"T024Z5MQM"},"is_bot":false,"updated":1504244301,"is_app_user":false},{"id":"U030MHXHX","team_id":"T024Z5MQM","name":"adrian","deleted":true,"profile":{"first_name":"Adrian","last_name":"Quek","image_24":"https:\/\/avatars.slack-edge.com\/2016-09-08\/77482260544_c8ef6ac9bc6f208761f2_24.png","image_32":"https:\/\/avatars.slack-edge.com\/2016-09-08\/77482260544_c8ef6ac9bc6f208761f2_32.png","image_48":"https:\/\/avatars.slack-edge.com\/2016-09-08\/77482260544_c8ef6ac9bc6f208761f2_48.png","image_72":"https:\/\/avatars.slack-edge.com\/2016-09-08\/77482260544_c8ef6ac9bc6f208761f2_72.png","image_192":"https:\/\/avatars.slack-edge.com\/2016-09-08\/77482260544_c8ef6ac9bc6f208761f2_72.png","image_original":"https:\/\/avatars.slack-edge.com\/2016-09-08\/77482260544_c8ef6ac9bc6f208761f2_original.png","skype":"adrian.quek.intertouch","title":"Pythonista","avatar_hash":"c8ef6ac9bc6f","image_512":"https:\/\/avatars.slack-edge.com\/2016-09-08\/77482260544_c8ef6ac9bc6f208761f2_72.png","image_1024":"https:\/\/avatars.slack-edge.com\/2016-09-08\/77482260544_c8ef6ac9bc6f208761f2_72.png","phone":"97548991","status_text":"","status_emoji":"","real_name":"Adrian Quek","display_name":"adrian","real_name_normalized":"Adrian Quek","display_name_normalized":"adrian","team":"T024Z5MQM"},"is_bot":false,"updated":1504244304,"is_app_user":false},{"id":"U034URXDR","team_id":"T024Z5MQM","name":"dorothy","deleted":false,"color":"d58247","real_name":"Dorothy Poon","tz":"Asia\/Kuala_Lumpur","tz_label":"Singapore Standard Time","tz_offset":28800,"profile":{"first_name":"Dorothy","last_name":"Poon","image_24":"https:\/\/avatars.slack-edge.com\/2017-09-26\/248021127543_b77c4a00a1dc7b490949_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2017-09-26\/248021127543_b77c4a00a1dc7b490949_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2017-09-26\/248021127543_b77c4a00a1dc7b490949_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2017-09-26\/248021127543_b77c4a00a1dc7b490949_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2017-09-26\/248021127543_b77c4a00a1dc7b490949_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2017-09-26\/248021127543_b77c4a00a1dc7b490949_original.jpg","title":"Product Evangelist","skype":"","phone":"+65 9877 5366","image_512":"https:\/\/avatars.slack-edge.com\/2017-09-26\/248021127543_b77c4a00a1dc7b490949_512.jpg","image_1024":"https:\/\/avatars.slack-edge.com\/2017-09-26\/248021127543_b77c4a00a1dc7b490949_1024.jpg","avatar_hash":"b77c4a00a1dc","status_text":"","status_emoji":":octocat:","real_name":"Dorothy Poon","display_name":"dorothy","real_name_normalized":"Dorothy Poon","display_name_normalized":"dorothy","team":"T024Z5MQM"},"is_admin":false,"is_owner":false,"is_primary_owner":false,"is_restricted":false,"is_ultra_restricted":false,"is_bot":false,"updated":1506428724,"is_app_user":false},{"id":"U034VRLKU","team_id":"T024Z5MQM","name":"maneesh","deleted":true,"profile":{"first_name":"Maneesh","last_name":"Mishra","avatar_hash":"g584c776db20","guest_channels":"[\"G4KEH3WCX\"]","guest_invited_by":"U024ZH7HL","real_name":"Maneesh Mishra","display_name":"maneesh","real_name_normalized":"Maneesh Mishra","display_name_normalized":"maneesh","image_24":"https:\/\/secure.gravatar.com\/avatar\/584c776db20ae689eea264160c4615a5.jpg?s=24&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0005-24.png","image_32":"https:\/\/secure.gravatar.com\/avatar\/584c776db20ae689eea264160c4615a5.jpg?s=32&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0005-32.png","image_48":"https:\/\/secure.gravatar.com\/avatar\/584c776db20ae689eea264160c4615a5.jpg?s=48&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0005-48.png","image_72":"https:\/\/secure.gravatar.com\/avatar\/584c776db20ae689eea264160c4615a5.jpg?s=72&d=https%3A%2F%2Fa.slack-edge.com%2F66f9%2Fimg%2Favatars%2Fava_0005-72.png","image_192":"https:\/\/secure.gravatar.com\/avatar\/584c776db20ae689eea264160c4615a5.jpg?s=192&d=https%3A%2F%2Fa.slack-edge.com%2F7fa9%2Fimg%2Favatars%2Fava_0005-192.png","image_512":"https:\/\/secure.gravatar.com\/avatar\/584c776db20ae689eea264160c4615a5.jpg?s=512&d=https%3A%2F%2Fa.slack-edge.com%2F7fa9%2Fimg%2Favatars%2Fava_0005-512.png","team":"T024Z5MQM"},"is_bot":false,"updated":1504244306,"is_app_user":false},{"id":"U03C98L5C","team_id":"T024Z5MQM","name":"remy","deleted":true,"profile":{"first_name":"Remy","last_name":"REY-DE BARROS","image_24":"https:\/\/avatars.slack-edge.com\/2015-01-15\/3415302197_0ef07c2dbbae167954e2_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2015-01-15\/3415302197_0ef07c2dbbae167954e2_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2015-01-15\/3415302197_0ef07c2dbbae167954e2_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2015-01-15\/3415302197_0ef07c2dbbae167954e2_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2015-01-15\/3415302197_0ef07c2dbbae167954e2_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2015-01-15\/3415302197_0ef07c2dbbae167954e2_original.jpg","avatar_hash":"0ef07c2dbbae","real_name":"Remy REY-DE BARROS","display_name":"remy","real_name_normalized":"Remy REY-DE BARROS","display_name_normalized":"remy","team":"T024Z5MQM"},"is_bot":false,"updated":1504244309,"is_app_user":false},{"id":"U03CKFGU5","team_id":"T024Z5MQM","name":"norbert","deleted":true,"profile":{"first_name":"Norbert","last_name":"Kleijn","image_24":"https:\/\/avatars.slack-edge.com\/2015-01-16\/3429557828_0be53960e928340c2178_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2015-01-16\/3429557828_0be53960e928340c2178_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2015-01-16\/3429557828_0be53960e928340c2178_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2015-01-16\/3429557828_0be53960e928340c2178_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2015-01-16\/3429557828_0be53960e928340c2178_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2015-01-16\/3429557828_0be53960e928340c2178_original.jpg","skype":"norbertkleijn","phone":"+6582284375","avatar_hash":"0be53960e928","real_name":"Norbert Kleijn","display_name":"norbert","real_name_normalized":"Norbert Kleijn","display_name_normalized":"norbert","team":"T024Z5MQM"},"is_bot":false,"updated":1504244309,"is_app_user":false},{"id":"U047E9SQ6","team_id":"T024Z5MQM","name":"nugbot","deleted":true,"profile":{"bot_id":"B047E8TTN","image_24":"https:\/\/avatars.slack-edge.com\/2015-05-04\/4713448516_f2a3bf2a285546967f56_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2015-05-04\/4713448516_f2a3bf2a285546967f56_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2015-05-04\/4713448516_f2a3bf2a285546967f56_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2015-05-04\/4713448516_f2a3bf2a285546967f56_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2015-05-04\/4713448516_f2a3bf2a285546967f56_72.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2015-05-04\/4713448516_f2a3bf2a285546967f56_original.jpg","avatar_hash":"f2a3bf2a2855","real_name":"nugbot","display_name":"","real_name_normalized":"nugbot","display_name_normalized":"","team":"T024Z5MQM"},"is_bot":true,"updated":1504244325,"is_app_user":false},{"id":"U047EAUB4","team_id":"T024Z5MQM","name":"lala","deleted":true,"profile":{"bot_id":"B047DRFQS","image_24":"https:\/\/avatars.slack-edge.com\/2015-04-01\/4252368542_3f64f50e1694d0376b13_24.jpg","image_32":"https:\/\/avatars.slack-edge.com\/2015-04-01\/4252368542_3f64f50e1694d0376b13_32.jpg","image_48":"https:\/\/avatars.slack-edge.com\/2015-04-01\/4252368542_3f64f50e1694d0376b13_48.jpg","image_72":"https:\/\/avatars.slack-edge.com\/2015-04-01\/4252368542_3f64f50e1694d0376b13_72.jpg","image_192":"https:\/\/avatars.slack-edge.com\/2015-04-01\/4252368542_3f64f50e1694d0376b13_192.jpg","image_original":"https:\/\/avatars.slack-edge.com\/2015-04-01\/4252368542_3f64f50e1694d0376b13_original.jpg","avatar_hash":"3f64f50e1694","real_name":"lala","display_name":"","real_name_normalized":"lala","display_name_normalized":"","team":"T024Z5MQM"},"is_bot":true,"updated":1504244325,"is_app_user":false}],"cache_ts":1513299076,"response_metadata":{"next_cursor":""}}"""

      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}


class FakeChannelConversationHistoryHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
    (_uri: String) ⇒ 
        val jsonData = """
        {
   "has_more" : true,
   "messages" : [
      {
         "type" : "message",
         "ts" : "1513078965.000241",
         "user" : "U0F3F6F38",
         "text" : "<@U04MGHVRY> <https://blogs.microsoft.com/on-the-issues/?p=56086>",
         "attachments" : [
            {
               "ts" : 1512990007,
               "title_link" : "https://blogs.microsoft.com/on-the-issues/?p=56086",
               "title" : "AI for Earth can be a game-changer for our planet - Microsoft on the Issues",
               "service_name" : "Microsoft on the Issues",
               "text" : "On the two-year anniversary of the Paris climate accord, the world’s government, civic and business leaders are coming together in Paris to discuss one of the most important issues and opportunities of our time, climate change. I’m excited to lead the Microsoft delegation at these meetings. While the experts’ warnings are dire, at Microsoft we...",
               "thumb_height" : 576,
               "service_icon" : "https://mscorpmedia.azureedge.net/mscorpmedia/2017/08/favicon-599dd744b8cac.jpg",
               "thumb_width" : 1024,
               "thumb_url" : "https://mscorpmedia.azureedge.net/mscorpmedia/2017/12/AI4Earth-1024x576.jpg",
               "fallback" : "Microsoft on the Issues: AI for Earth can be a game-changer for our planet - Microsoft on the Issues",
               "id" : 1,
               "from_url" : "https://blogs.microsoft.com/on-the-issues/?p=56086"
            }
         ]
      },
        {
           "type" : "message",
           "bot_id" : "B0VD275DX",
           "text" : "Hello. Another new user going by the name of Nancy Bernard (<mailto:nancy.bernard@mindshareworld.com|nancy.bernard@mindshareworld.com>) has passed through the Nugit gates. We've also added them to Mailchimp's Nugit Users list.  ",
           "username" : "Zapier",
           "ts" : "1513073709.000326",
           "subtype" : "bot_message"
        },
        {
           "type" : "message",
           "bot_id" : "B3DGC7129",
           "attachments" : [
              {
                 "image_url" : "https://media3.giphy.com/media/R54jhpzpARmVy/giphy-downsized.gif",
                 "footer" : "Posted using /giphy",
                 "image_height" : 194,
                 "image_width" : 320,
                 "fallback" : "giphy: https://giphy.com/gifs/shake-fist-angry-girl-shakes-R54jhpzpARmVy",
                 "image_bytes" : 759548,
                 "id" : 1,
                 "title" : "shakes fist",
                 "title_link" : "https://giphy.com/gifs/shake-fist-angry-girl-shakes-R54jhpzpARmVy",
                 "is_animated" : true
              }
           ],
           "text" : "",
           "user" : "U5TEUA60Z",
           "ts" : "1512625849.000036",
           "reactions" : [
              {
                 "name" : "joy",
                 "count" : 1,
                 "users" : [
                    "U7V7V7NFM"
                 ]
              }
           ]
        },
      {
         "comment" : {
            "timestamp" : 1512717689,
            "created" : 1512717689,
            "comment" : "you mean `waifu`?",
            "user" : "U0PL0HUHG",
            "is_intro" : false,
            "id" : "Fc8BKSAGPL"
         },
         "ts" : "1512717689.000010",
         "file" : {
            "thumb_360_h" : 166,
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "is_public" : true,
            "thumb_480_h" : 222,
            "original_h" : 637,
            "thumb_960_h" : 443,
            "filetype" : "jpg",
            "original_w" : 1380,
            "thumb_360_w" : 360,
            "editable" : false,
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_720_w" : 720,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_800_w" : 800,
            "size" : 429223,
            "thumb_800_h" : 369,
            "timestamp" : 1512717302,
            "external_type" : "",
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "is_external" : false,
            "id" : "F8CMV2GTH",
            "name" : "Image uploaded from iOS.jpg",
            "thumb_960_w" : 960,
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_720_h" : 332,
            "public_url_shared" : false,
            "username" : "",
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "ims" : [],
            "user" : "U024ZH7HL",
            "mode" : "hosted",
            "thumb_1024_w" : 1024,
            "groups" : [],
            "created" : 1512717302,
            "image_exif_rotation" : 1,
            "display_as_bot" : false,
            "thumb_480_w" : 480,
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "thumb_1024_h" : 473,
            "channels" : [
               "C024Z5MQT"
            ],
            "pretty_type" : "JPEG",
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg"
         },
         "text" : "<@U0PL0HUHG> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: you mean `waifu`?",
         "type" : "message",
         "subtype" : "file_comment",
         "is_intro" : false
      },
      {
         "is_intro" : false,
         "subtype" : "file_comment",
         "type" : "message",
         "text" : "<@U4BUQR94L> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: .",
         "file" : {
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_720_h" : 332,
            "thumb_960_w" : 960,
            "public_url_shared" : false,
            "username" : "",
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "user" : "U024ZH7HL",
            "mode" : "hosted",
            "ims" : [],
            "groups" : [],
            "thumb_1024_w" : 1024,
            "created" : 1512717302,
            "image_exif_rotation" : 1,
            "display_as_bot" : false,
            "thumb_480_w" : 480,
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_1024_h" : 473,
            "channels" : [
               "C024Z5MQT"
            ],
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "pretty_type" : "JPEG",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_360_h" : 166,
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "thumb_480_h" : 222,
            "is_public" : true,
            "original_h" : 637,
            "filetype" : "jpg",
            "thumb_960_h" : 443,
            "editable" : false,
            "original_w" : 1380,
            "thumb_360_w" : 360,
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "thumb_800_w" : 800,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_720_w" : 720,
            "thumb_800_h" : 369,
            "timestamp" : 1512717302,
            "size" : 429223,
            "external_type" : "",
            "id" : "F8CMV2GTH",
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "is_external" : false,
            "name" : "Image uploaded from iOS.jpg"
         },
         "ts" : "1512717607.000060",
         "comment" : {
            "comment" : ".",
            "reactions" : [
               {
                  "users" : [
                     "U5ZV5797E"
                  ],
                  "name" : "robot_face",
                  "count" : 1
               }
            ],
            "created" : 1512717607,
            "timestamp" : 1512717607,
            "is_intro" : false,
            "id" : "Fc8BM09PS8",
            "user" : "U4BUQR94L"
         }
      },
      {
         "comment" : {
            "user" : "U3ACT6Z2P",
            "is_intro" : false,
            "id" : "Fc8BHL2F0T",
            "timestamp" : 1512717491,
            "comment" : "oh yes, I thought it was cut off :neutral_face:",
            "created" : 1512717491
         },
         "ts" : "1512717491.000024",
         "text" : "<@U3ACT6Z2P> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: oh yes, I thought it was cut off :neutral_face:",
         "file" : {
            "name" : "Image uploaded from iOS.jpg",
            "external_type" : "",
            "id" : "F8CMV2GTH",
            "is_external" : false,
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "thumb_800_h" : 369,
            "timestamp" : 1512717302,
            "size" : 429223,
            "thumb_800_w" : 800,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_720_w" : 720,
            "editable" : false,
            "thumb_360_w" : 360,
            "original_w" : 1380,
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "filetype" : "jpg",
            "thumb_960_h" : 443,
            "is_public" : true,
            "thumb_480_h" : 222,
            "original_h" : 637,
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_360_h" : 166,
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "thumb_1024_h" : 473,
            "channels" : [
               "C024Z5MQT"
            ],
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "pretty_type" : "JPEG",
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "display_as_bot" : false,
            "image_exif_rotation" : 1,
            "thumb_480_w" : 480,
            "groups" : [],
            "thumb_1024_w" : 1024,
            "created" : 1512717302,
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "mode" : "hosted",
            "user" : "U024ZH7HL",
            "ims" : [],
            "username" : "",
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_720_h" : 332,
            "thumb_960_w" : 960,
            "public_url_shared" : false
         },
         "type" : "message",
         "subtype" : "file_comment",
         "is_intro" : false
      },
      {
         "file" : {
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "image_exif_rotation" : 1,
            "display_as_bot" : false,
            "thumb_480_w" : 480,
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "channels" : [
               "C024Z5MQT"
            ],
            "thumb_1024_h" : 473,
            "pretty_type" : "JPEG",
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "username" : "",
            "thumb_960_w" : 960,
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_720_h" : 332,
            "public_url_shared" : false,
            "thumb_1024_w" : 1024,
            "groups" : [],
            "created" : 1512717302,
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "ims" : [],
            "mode" : "hosted",
            "user" : "U024ZH7HL",
            "size" : 429223,
            "timestamp" : 1512717302,
            "thumb_800_h" : 369,
            "thumb_720_w" : 720,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_800_w" : 800,
            "name" : "Image uploaded from iOS.jpg",
            "external_type" : "",
            "is_external" : false,
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "id" : "F8CMV2GTH",
            "thumb_480_h" : 222,
            "is_public" : true,
            "original_h" : 637,
            "thumb_360_h" : 166,
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "thumb_360_w" : 360,
            "original_w" : 1380,
            "editable" : false,
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_960_h" : 443,
            "filetype" : "jpg"
         },
         "text" : "<@U58LY38Q6> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: for each of the labels to the left I suppose",
         "type" : "message",
         "subtype" : "file_comment",
         "is_intro" : false,
         "comment" : {
            "comment" : "for each of the labels to the left I suppose",
            "created" : 1512717461,
            "timestamp" : 1512717461,
            "is_intro" : false,
            "id" : "Fc8BLZNZ1S",
            "user" : "U58LY38Q6"
         },
         "ts" : "1512717461.000198"
      },
      {
         "ts" : "1512717349.000052",
         "comment" : {
            "id" : "Fc8CFX88GN",
            "is_intro" : false,
            "user" : "U3ACT6Z2P",
            "comment" : "hm acting as what? :sweat_smile:",
            "created" : 1512717349,
            "timestamp" : 1512717349
         },
         "text" : "<@U3ACT6Z2P> commented on <@U024ZH7HL>’s file <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">: hm acting as what? :sweat_smile:",
         "file" : {
            "thumb_800_w" : 800,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_720_w" : 720,
            "timestamp" : 1512717302,
            "thumb_800_h" : 369,
            "size" : 429223,
            "id" : "F8CMV2GTH",
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "is_external" : false,
            "external_type" : "",
            "name" : "Image uploaded from iOS.jpg",
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "comments_count" : 5,
            "mimetype" : "image/jpeg",
            "thumb_360_h" : 166,
            "original_h" : 637,
            "thumb_480_h" : 222,
            "is_public" : true,
            "filetype" : "jpg",
            "thumb_960_h" : 443,
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "editable" : false,
            "thumb_360_w" : 360,
            "original_w" : 1380,
            "thumb_480_w" : 480,
            "display_as_bot" : false,
            "image_exif_rotation" : 1,
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "pretty_type" : "JPEG",
            "channels" : [
               "C024Z5MQT"
            ],
            "thumb_1024_h" : 473,
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "public_url_shared" : false,
            "thumb_720_h" : 332,
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "thumb_960_w" : 960,
            "username" : "",
            "user" : "U024ZH7HL",
            "mode" : "hosted",
            "ims" : [],
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "created" : 1512717302,
            "groups" : [],
            "thumb_1024_w" : 1024
         },
         "subtype" : "file_comment",
         "is_intro" : false,
         "type" : "message"
      },
      {
         "file" : {
            "size" : 429223,
            "timestamp" : 1512717302,
            "thumb_800_h" : 369,
            "thumb_720_w" : 720,
            "title" : "Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\"",
            "thumb_800_w" : 800,
            "name" : "Image uploaded from iOS.jpg",
            "external_type" : "",
            "is_external" : false,
            "url_private_download" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/download/image_uploaded_from_ios.jpg",
            "id" : "F8CMV2GTH",
            "is_public" : true,
            "thumb_480_h" : 222,
            "original_h" : 637,
            "thumb_360_h" : 166,
            "mimetype" : "image/jpeg",
            "comments_count" : 5,
            "permalink" : "https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg",
            "thumb_480" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_480.jpg",
            "thumb_360_w" : 360,
            "original_w" : 1380,
            "editable" : false,
            "permalink_public" : "https://slack-files.com/T024Z5MQM-F8CMV2GTH-e2fa366d26",
            "thumb_160" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_160.jpg",
            "thumb_960_h" : 443,
            "filetype" : "jpg",
            "thumb_800" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_800.jpg",
            "thumb_960" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_960.jpg",
            "image_exif_rotation" : 1,
            "display_as_bot" : false,
            "thumb_480_w" : 480,
            "thumb_1024" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_1024.jpg",
            "thumb_720" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_720.jpg",
            "thumb_1024_h" : 473,
            "channels" : [
               "C024Z5MQT"
            ],
            "pretty_type" : "JPEG",
            "thumb_80" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_80.jpg",
            "username" : "",
            "thumb_960_w" : 960,
            "thumb_720_h" : 332,
            "thumb_360" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_360.jpg",
            "public_url_shared" : false,
            "thumb_1024_w" : 1024,
            "groups" : [],
            "created" : 1512717302,
            "thumb_64" : "https://files.slack.com/files-tmb/T024Z5MQM-F8CMV2GTH-779e71594f/image_uploaded_from_ios_64.jpg",
            "url_private" : "https://files.slack.com/files-pri/T024Z5MQM-F8CMV2GTH/image_uploaded_from_ios.jpg",
            "ims" : [],
            "mode" : "hosted",
            "user" : "U024ZH7HL"
         },
         "text" : "<@U024ZH7HL> uploaded a file: <https://nugit.slack.com/files/U024ZH7HL/F8CMV2GTH/image_uploaded_from_ios.jpg|Left is Singapore, right is China. The survey question is, \"I am comfortable with the idea of artificial intelligence/machines acting as...\">",
         "user" : "U024ZH7HL",
         "subtype" : "file_share",
         "upload_reply_to" : "5B892FB5-2AAB-41AD-ADD5-007A6E60C320",
         "ts" : "1512717302.000055",
         "display_as_bot" : false,
         "type" : "message",
         "upload" : true,
         "bot_id" : null,
         "username" : "sando"
      },
 
        {
         "type" : "message",
         "attachments" : [
            {
               "id" : 1,
               "title_link" : "https://www.fastcodesign.com/90153387/inside-pinterests-12-person-ai-team-that-is-taking-on-google",
               "from_url" : "https://www.fastcodesign.com/90153387/inside-pinterests-12-person-ai-team-that-is-taking-on-google",
               "image_height" : 250,
               "text" : "Google has hundreds of researchers working on visual machine perception. Pinterest has a fraction of that. Here’s how the pinning service could still win the race to master visual search.",
               "ts" : 1513004403,
               "service_icon" : "https://www.fastcodesign.com/apple-touch-icon.png?v=2",
               "fallback" : "Co.Design: Inside Pinterest’s 12-Person AI Team That Is Taking On Google",
               "image_bytes" : 321483,
               "image_url" : "https://images.fastcompany.net/image/upload/w_1280,f_auto,q_auto,fl_lossy/wp-cms/uploads/sites/4/2017/12/p-1-pinterest-ai-deep-dive.jpg",
               "title" : "Inside Pinterest’s 12-Person AI Team That Is Taking On Google",
               "image_width" : 444,
               "service_name" : "Co.Design"
            }
         ],
         "user" : "U0F3F6F38",
         "text" : "<https://www.fastcodesign.com/90153387/inside-pinterests-12-person-ai-team-that-is-taking-on-google>",
         "reactions" : [
            {
               "count" : 1,
               "users" : [
                  "U58LY38Q6"
               ],
               "name" : "clap"
            }
         ],
         "ts" : "1513041522.000212"
        },
 
        {
           "type" : "message",
           "bot_id" : "B0VD275DX",
           "attachments" : [
              {
                 "ts" : 1512990007,
                 "title_link" : "https://blogs.microsoft.com/on-the-issues/?p=56086",
                 "title" : "AI for Earth can be a game-changer for our planet - Microsoft on the Issues",
                 "service_name" : "Microsoft on the Issues",
                 "text" : "On the two-year anniversary of the Paris climate accord, the world’s government, civic and business leaders are coming together in Paris to discuss one of the most important issues and opportunities of our time, climate change. I’m excited to lead the Microsoft delegation at these meetings. While the experts’ warnings are dire, at Microsoft we...",
                 "thumb_height" : 576,
                 "service_icon" : "https://mscorpmedia.azureedge.net/mscorpmedia/2017/08/favicon-599dd744b8cac.jpg",
                 "thumb_width" : 1024,
                 "thumb_url" : "https://mscorpmedia.azureedge.net/mscorpmedia/2017/12/AI4Earth-1024x576.jpg",
                 "fallback" : "Microsoft on the Issues: AI for Earth can be a game-changer for our planet - Microsoft on the Issues",
                 "id" : 1,
                 "from_url" : "https://blogs.microsoft.com/on-the-issues/?p=56086"
              }
           ],
           "text" : "Test text",
           "username" : "Xapier",
           "ts" : "1513073709.000326",
           "subtype" : "bot_message"
        }
 
     ],
     "ok" : true,
     "response_metadata" : {
        "next_cursor" : ""
     },
     "pin_count" : 2 }""" 

      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, jsonData))
      )
  }
}

class FakeTeamInfoHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  var state = 0
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
    (_uri: String) ⇒ 
      state += 1 /* kind of a hack, but ignoring for now.*/
      val jsonData =
      if (state == 1) """
        {
          "ok": true,
          "team": {
              "id": "T12345",
              "name": "My Team",
              "domain": "example",
              "email_domain": "example.com",
              "icon": {
                  "image_34": "https:\/\/...",
                  "image_44": "https:\/\/...",
                  "image_68": "https:\/\/...",
                  "image_88": "https:\/\/...",
                  "image_102": "https:\/\/...",
                  "image_132": "https:\/\/...",
                  "image_default": true
              },
             "enterprise_id": "E1234A12AB",
             "enterprise_name": "Umbrella Corporation"
          }
        }"""
      else """
        { "ok": true,
          "emoji": {
            "bowtie": "https:\/\/my.slack.com\/emoji\/bowtie\/46ec6f2bb0.png",
            "squirrel": "https:\/\/my.slack.com\/emoji\/squirrel\/f35f40c0e0.png",
            "shipit": "alias:squirrel"}
        }"""
 
    Future.successful(
      HttpResponse(entity = HttpEntity(`application/json`, jsonData))
    )
  }
}


