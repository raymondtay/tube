package nugit.tube

import org.specs2._
import org.scalacheck._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}

class MainSpec extends mutable.Specification with TestTrait with ScalaCheck {
  "this is a test" >> prop{ (xs: List[Int]) => 
    validateSizeOfList(xs) 
  }.set(minTestsOk = 200, workers = 1)

}
