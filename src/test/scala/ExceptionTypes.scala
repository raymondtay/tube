package nugit.tube.api

// We added exception types to control what kind of errors or not would be
// returned back to the client.
//
object ExceptionTypes extends Enumeration {
  type ExceptionType = Value
  val THROW_EXPECTED , THROW_UNEXPECTED, NO_THROW = Value
}

