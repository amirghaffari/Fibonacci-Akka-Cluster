package fibonacci

import com.typesafe.config.ConfigFactory
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

import akka.actor.Actor
import akka.actor.Cancellable
import akka.actor.RootActorPath
import akka.actor.ActorPath
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import scala.concurrent.duration._

class InitiateFibComputation(expectedFrontend: Int) extends Actor with ActorLogging{

    val cluster = Cluster(context.system)
    var frontends = IndexedSeq.empty[ActorPath]
    val random = scala.util.Random
    val inputArray = (for (i <- 1 to 1000) yield random.nextInt(100)).toArray
    var cancellable:Cancellable=null
    var cancel=false
    var numSent=0;
    var numReceived=0
    var sumResults=0
    var startTime: Long=0

    log.info("initializer running...")
    // subscribe to cluster changes, MemberUp
    override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember])
    override def postStop(): Unit = cluster.unsubscribe(self)

    def receive = {
        case MemberUp(member) if member.hasRole("frontend") =>
            val path = RootActorPath(member.address) / "user" / "frontend"
            frontends = frontends :+ path
            log.info("Added a frontend with path: " + path)
            log.info("frontends list size: " + frontends.size)

        case UnreachableMember(member) if member.hasRole("frontend") =>
            val address=RootActorPath(member.address) / "user" / "frontend"
            frontends = frontends.filterNot(_ == address)
            log.info("A frontend with the path: " + address+" failed")

        case StartComputation =>
            cancellable.cancel() // no more checking is needed. All the frontends are up and ready to start the computation
            log.info("starting computation ...")
            startTime = System.currentTimeMillis
            val chunkSize=inputArray.size/frontends.size
            val rem=inputArray.size%frontends.size
            for (i <- frontends.indices) {
                if((i+1)!=frontends.size) {
                    context.actorSelection(frontends(i)) ! StartComputation(inputArray.slice(i * chunkSize, (i + 1) * chunkSize ))
                }
                else {
                    // the last chunk
                    context.actorSelection(frontends(i)) ! StartComputation(inputArray.slice(i * chunkSize, (i + 1) * chunkSize + rem ))
                }
            }

        case FibResult(result) =>
            numReceived+=1
            sumResults+=result
            if(numReceived==frontends.size) { // if all the sent chunks have been computed and received, show the result and shutdown the computation
                println("Result is: " + sumResults)
                println("Elapsed time: %1d ms".format(System.currentTimeMillis - startTime))

                for (i <- frontends.indices) {
                        context.actorSelection(frontends(i)) ! Stop
                }
                log.info("Stoping initializer ...")
                context.stop(self)
            }

        case StartDistribution =>
            val system = ActorSystem()
            import system.dispatcher
            // check every 1 second to see if all frontends are up, if so start the Fib computation
            cancellable=system.scheduler.schedule(1.seconds, 1.seconds){
                log.info ("Scheduled check...")
                if (frontends.size==expectedFrontend && cancel==false) {
                    cancel=true
                    println ("All expected frontends ("+expectedFrontend+") are up. Let's start the computation!")
                    self ! StartComputation
                }
            }

    } // end of receive
}

object InitiateFibComputation {
    def main(args: Array[String]): Unit = {
        val numFrontendStr = if (args.isEmpty) "0" else args(0)
        val numFrontend = numFrontendStr.toInt
        val config = ConfigFactory.load()
        val system = ActorSystem("ClusterSystem", config)
        val initializer = system.actorOf(Props(classOf[InitiateFibComputation],numFrontend), name = "initializer")
        println("Start distribution to initializer...")
        initializer!StartDistribution
    }
}
