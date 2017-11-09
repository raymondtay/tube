package nugit.tube

import org.specs2._
import org.scalacheck._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}

class MainSpec extends mutable.Specification with TestTrait with ScalaCheck {
  val  minimumNumberOfTests = 200
  "this is a test" >> prop{ (xs: List[Int]) =>
    validateSizeOfList(xs)
  }.set(minTestsOk = minimumNumberOfTests, workers = 1)

}
