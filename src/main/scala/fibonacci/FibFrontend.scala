package fibonacci

import language.postfixOps
import akka.actor.Actor
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import java.util.concurrent.atomic.AtomicInteger
import akka.actor.ActorLogging
import scala.util.control.Breaks._

import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

class FibFrontend extends Actor with ActorLogging{

    var backends = IndexedSeq.empty[ActorRef]
    var jobCounter = 0
    val ChunkSize = 10
    var arrayToCompute=Array.empty[Int];
    var sumResults=0
    var computationInitiated = false
    var numSent=0;
    var numReceived=0
    var initializer : ActorRef = null

    val cluster = Cluster(context.system)

    // subscribe to cluster changes, MemberUp
    override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
    override def postStop(): Unit = cluster.unsubscribe(self)

    def receive = {

        case BackendRegistration if !backends.contains(sender()) =>
            // register for reception of the Terminated message upon termination
            context watch sender()
            // add the sender to the list of backends
            backends = backends :+ sender()
            if(computationInitiated){
                    // if this backend comes up after the computation initiation, then send a chunk to compute, otherwise wait till the computation gets initiated
                    sendArrayChunk(sender())
            }
        case Terminated(a) =>
            // exclude the terminated backend from the backends list
            backends = backends.filterNot(_ == a)

        case StartComputation(inputArray) =>
            initializer=sender()
            arrayToCompute=inputArray
            computationInitiated=true
            for (i <- backends.indices) {
                sendArrayChunk(backends(i))
                if(arrayToCompute.length==0) break // nothing left to send
            }

        case FibResult(result) =>
            numReceived+=1
            sumResults+=result
            sendArrayChunk(sender()) // send another chunk to compute

        case Stop =>
            for (i <- backends.indices) {
                backends(i) ! Stop
            }
            log.info("Stopping frontend ...")
            context.stop(self)
    }

    def sendArrayChunk(actor:ActorRef): Unit ={
        if (ChunkSize >= arrayToCompute.size) { // if it's the last chunk
            if(arrayToCompute.size>0) { // if there is anything left to compute
                actor ! StartComputation(arrayToCompute)
                arrayToCompute = Array.empty[Int]
                numSent+=1
            }
            else{
                if(numSent==numReceived) {
                    log.info("Frontend " + akka.serialization.Serialization.serializedActorPath(self) + " recieved "+numReceived+" results and sending back the result ("+sumResults+") to the initializer")
                    initializer ! FibResult(sumResults)
                }
            }
        }
        else {
            //actor ! StartComputation(arrayToCompute.slice(0,ChunkSize-1))
            actor ! StartComputation(arrayToCompute.take(ChunkSize))
            //arrayToCompute=arrayToCompute.slice(ChunkSize,arrayToCompute.length-1)
            arrayToCompute=arrayToCompute.drop(ChunkSize)
            numSent+=1
        }
    }

}

object FibFrontend {
    def main(args: Array[String]): Unit = {
        // by default, ConfigFactory looks for a configuration file called application.conf
        // Override the configuration of the port when specified as program argument
        val port = if (args.isEmpty) "0" else args(0) // port 0 means a random port
        val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
          //the roles of a node is defined in the configuration property named akka.cluster.roles, e.g. "frontend"
          withFallback(ConfigFactory.parseString("akka.cluster.roles = [frontend]")).
          withFallback(ConfigFactory.load())

        val system = ActorSystem("ClusterSystem", config)
        val frontend = system.actorOf(Props[FibFrontend], name = "frontend")
    }
}
