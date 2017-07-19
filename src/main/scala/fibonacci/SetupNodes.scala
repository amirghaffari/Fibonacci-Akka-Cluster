package fibonacci

import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props

object SetupNodes {

    def main(args: Array[String]): Unit = {
        val NumFrontend="2"
        // starting 2 frontend nodes and 3 backend nodes
        FibFrontend.main(Seq("2551").toArray)
        FibBackend.main(Seq("2552").toArray)
        FibBackend.main(Array.empty)
        FibBackend.main(Array.empty)
        FibFrontend.main(Array.empty)
        InitiateFibComputation.main(Seq(NumFrontend).toArray)
    }
}
