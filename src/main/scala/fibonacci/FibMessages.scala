package fibonacci

final case class FibResult(result: Int)
final case class FaildMessage(reason: String)
case object BackendRegistration
case object StartDistribution
case object StartComputation
final case class StartComputation(inputArray: Array[Int])
case object Stop
