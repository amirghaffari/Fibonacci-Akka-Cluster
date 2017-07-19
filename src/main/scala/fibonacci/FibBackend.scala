package fibonacci

import language.postfixOps
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.RootActorPath
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import com.typesafe.config.ConfigFactory
import akka.actor.ActorLogging

class FibBackend extends Actor with ActorLogging{

    val cluster = Cluster(context.system)

    // subscribe to cluster changes, MemberUp
    override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
    override def postStop(): Unit = cluster.unsubscribe(self)

    def receive = {

        case state: CurrentClusterState =>
            state.members.filter(_.status == MemberStatus.Up) foreach register

        case MemberUp(m) => register(m)

        case StartComputation(inputArray) =>
            log.info("Backend " + akka.serialization.Serialization.serializedActorPath(self)+ " got an array with size "+inputArray.size)
            sender()! FibResult(applyFib(inputArray))

        case Stop =>
            log.info("Stopping backend ...")
            context.stop(self)
    }

    def applyFib(input: Array[Int]): Int = {
        var sum=0;
        for (i <- input)
            sum+=tailFib(i)
        return input.length;
    }

    // non-tail-recursive Fibonacci, bad performance
    def fib( n : Int) : Int = n match {
        case 0 | 1 => n
        case _ => fib( n-1 ) + fib( n-2 )
    }

    //tail-recursive Fibonacci
    def tailFib( n : Int) : Int = {
        def fib_help( n: Int, a:Int, b:Int): Int = n match {
            case 0 => a
            case _ => fib_help( n-1, b, a+b )
        }
        return fib_help( n, 0, 1)
    }

    def register(member: Member): Unit =
        if (member.hasRole("frontend")){
            // actor path ends with "user"+actor name which here is "frontend"
            // For example locally, member.address is akka.tcp://ClusterSystem@127.0.0.1:2551 and RootActorPath adds "/" at the end
            // sends the BackendRegistration message to the new frontend nodes
            val path = RootActorPath(member.address) / "user" / "frontend"
            log.info("Backend " + akka.serialization.Serialization.serializedActorPath(self)+ " sending BackendRegistration to "+path)
            context.actorSelection(RootActorPath(member.address) / "user" / "frontend") ! BackendRegistration
        }
}

object FibBackend {
    def main(args: Array[String]): Unit = {
        // Override the configuration of the port when specified as program argument
        val port = if (args.isEmpty) "0" else args(0) // if no port specified in the config, use 0 which means to use a random port
        val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
          withFallback(ConfigFactory.parseString("akka.cluster.roles = [backend]")).
          withFallback(ConfigFactory.load())

        val system = ActorSystem("ClusterSystem", config)
        system.actorOf(Props[FibBackend], name = "backend")
    }
}
