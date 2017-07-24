How to distribute computational tasks over an Akka Cluster
-----------

This example illustrates how to compute Fibonacci for every elements in a list of integers. There are three types of actors in the systems, i.e. *initializer*, *frontends*, and *backends*. The user runs *fibonacci.SetupNodes* to create an *initializer* actor. The initializer subscribes to the cluster changes to find all the frontend nodes. When all the expected frontends are up and available, the initializer distributes an array of integers between frontend nodes evenly. Frontend nodes split the list into evenly sized chunks and send them to the backend nodes, where the Fibonacci calculation is done. Backends calculate Fibonacci for all integers in the received list and send the sum of the results back to the frontend. When a frontend collects the results for all the list members, it sums up all the results and send it back to the initializer.

Backend nodes subscribe to the cluster membership events to find the available frontend nodes and send a registration request to them.

How to build and run
----------------------------------------

Use the following commands to run all the actros in the same JVM:

	$ git clone git://github.com/amirghaffari/Fibonacci-Akka-Cluster
	$ cd Fibonacci-Akka-Cluster
	$ sbt clean compile
	$ sbt "runMain fibonacci.SetupNodes"

Use the following commands to run the actros in separate JVMs:

	$ git clone git://github.com/amirghaffari/Fibonacci-Akka-Cluster
	$ cd Fibonacci-Akka-Cluster
	$ sbt clean compile
	$ sbt "runMain fibonacci.FibFrontend 2551"
	$ sbt "runMain fibonacci.FibBackend 2552"
	$ sbt "runMain fibonacci.FibBackend"
	$ sbt "runMain fibonacci.FibBackend"
	$ sbt "runMain fibonacci.FibFrontend"
	$ sbt "runMain fibonacci.InitiateFibComputation 2"

