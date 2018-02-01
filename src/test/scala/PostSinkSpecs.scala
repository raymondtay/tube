package nugit.tube.api.posts

import io.circe._
import org.slf4j.{Logger, LoggerFactory}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.api.common.typeinfo._
import org.apache.flink.configuration._
import org.apache.flink.streaming.api.functions.sink._
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster
import org.apache.flink.streaming.util.TestStreamEnvironment
import org.apache.flink.test.util.TestBaseUtils

import org.specs2._
import org.specs2.specification.BeforeAfterAll
import org.scalacheck._
import Arbitrary._
import Gen.{alphaStr, fail, sequence, nonEmptyContainerOf, choose, pick, mapOf, listOfN, oneOf}
import slacks.core.config.Config
import scala.collection.JavaConverters._

import nugit.tube.configuration.{ApiGatewayConfig,CerebroSeedPostsConfig}
import nugit.tube.api.model._
import nugit.tube.api._
import slacks.core.program.SievedMessages

import scala.concurrent.duration._

import org.http4s._
import org.http4s.Method._
import org.http4s.Status.Ok
import cats.effect._
import org.http4s.client._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.client.blaze._


/**
  * Specification for testing [[PostSink]] 
  * @author Raymond Tay
  */
class PostSinkSpecs extends Specification with ScalaCheck with BeforeAfterAll {override def is = sequential ^ s2"""
  Flink would push data to `PostSink`, should xfer json data to RESTful Cerebro $verifySinkCanPostToRemoteNoErrors
  Flink would push data to `PostSink`, should xfer json data to RESTful Cerebro (Cerebro returns expected errors)    $verifySinkCanPostToRemoteExpectedErrors
  Flink would push data to `PostSink`, should xfer json data to RESTful Cerebro (Cerebro returns un-expected errors) $verifySinkCanPostToRemoteUnexpectedErrors
  """
  import ExceptionTypes._
  var service : HttpService[IO] = _
  var client: Client[IO] = _

  override def beforeAll(): Unit = {
    service = HttpService[IO] {
      case r ⇒ Response[IO](Ok).withBody(r.body)
    }
    client = Client.fromHttpService(service)
  }

  override def afterAll(): Unit = {
    client.shutdownNow()
  }

  def verifySinkCanPostToRemoteNoErrors = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    // configure your test environment
    env.setParallelism(1)

    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
      case Some(cfg) ⇒ 
        env
          .fromCollection(PostSinkSpecData.data)
          .map(new IdentityMapper[(ChannelPosts, List[String])])
          .addSink(new PostSinkInTest(cfg.seedPostsCfg, cfg.apiGatewayCfg, NO_THROW))
    }

    // we must not see any errors
    env.execute() must not (throwA[Throwable])
  }

  def verifySinkCanPostToRemoteExpectedErrors = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    // configure your test environment
    env.setParallelism(1)

    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
      case Some(cfg) ⇒ 
        env
          .fromCollection(PostSinkSpecData.data)
          .map(new IdentityMapper[(ChannelPosts, List[String])])
          .addSink(new PostSinkInTest(cfg.seedPostsCfg, cfg.apiGatewayCfg, THROW_EXPECTED))
    }

    // we must see errors
    env.execute() must throwA[org.apache.flink.runtime.client.JobExecutionException]
  }

  def verifySinkCanPostToRemoteUnexpectedErrors = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    // configure your test environment
    env.setParallelism(1)

    (nugit.tube.configuration.ConfigValidator.loadCerebroConfig(Config.config).toOption : @unchecked) match {
      case Some(cfg) ⇒ 
        env
          .fromCollection(PostSinkSpecData.data)
          .map(new IdentityMapper[(ChannelPosts, List[String])])
          .addSink(new PostSinkInTest(cfg.seedPostsCfg, cfg.apiGatewayCfg, THROW_UNEXPECTED))
    }

    // we must see errors
    env.execute() must throwA[org.apache.flink.runtime.client.JobExecutionException]
  }

}

object PostSinkSpecData {
  implicit val typeInfo = TypeInformation.of(classOf[(ChannelPosts, List[String])])
  val data : List[(ChannelPosts, List[String])]= (ChannelPosts("fake-channel-id", SievedMessages(Nil, Nil, Nil)), Nil) :: Nil
}

//
// The reason to use this subtyping approach is because Sinks are potentially
// run on local or remote Flink which means that fields and state needs to be
// serializable over the wire.
//
class PostSinkInTest(cerebroCfg: CerebroSeedPostsConfig, gatewayCfg: ApiGatewayConfig, exceptionType: ExceptionTypes.ExceptionType) extends PostSink(cerebroCfg, gatewayCfg) {
  import _root_.io.circe.literal._
  import _root_.io.circe.generic.auto._
  import _root_.io.circe.syntax._
  import ExceptionTypes._

  val service = exceptionType match {
    case NO_THROW         ⇒ HttpService[IO] { case r ⇒ Response[IO](Ok).withBody(CerebroOK(1).asJson.noSpaces) }
    case THROW_EXPECTED   ⇒ HttpService[IO] { case r ⇒ Response[IO](Ok).withBody(CerebroNOK(Nil).asJson.noSpaces) }
    case THROW_UNEXPECTED ⇒ HttpService[IO] { case r ⇒ Response[IO](Ok).withBody("""{"errors": "unexpected return from cerebro."}""") }
  }

  override def open(params: Configuration) : Unit = {
    logger = LoggerFactory.getLogger(classOf[PostSinkInTest])
    httpClient = Client.fromHttpService(service)
    pCounter = getRuntimeContext().getMetricGroup().counter("sink-posts-counter")
  }

  override def close() : Unit = {
    httpClient.shutdownNow()
  }
}

