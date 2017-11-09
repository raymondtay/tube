package nugit.tube

import cats._, data._, implicits._

/**
  * Dummy source file to test circle-ci
  */
trait TestTrait  {
  def validateSizeOfList[A](xs : List[A]) : Boolean = xs match {
    case Nil => 0 == 0
    case _ => xs.length  == xs.length
  }
}
