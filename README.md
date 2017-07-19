How to distribute computational tasks over an Akka Cluster
-----------

This example illustrates how to distribute a list of numbers over an Akka Cluster to compute Fibonacci for every elements in the list. The user runs *fibonacci.SetupNodes* to create an *initializer* actor. The initializer subscribes to the cluster changes to find all the frontend nodes. When all the expected frontends are available, the initializer distributes an array of numbers between frontends evenly. Frontend nodes split the list into evenly sized chunks and send them to the backend nodes and collect back the results. Frontends collect all the results from the backends and send sum of the results back to the initializer.

Backend nodes subscribe to the cluster membership events to find frontends in the cluster and send a registration request to them. Backends do the computation by calculating Fibonacci for all elements in the received list and send the sum of the results back to the appropriate frontend.

How to build and run
----------------------------------------

Use the following commands to run all the actros in the same JVM:

	$ sbt clean compile
	$ sbt "runMain fibonacci.SetupNodes"

Use the following commands to run the actros in separate JVMs:

	$ sbt clean compile
	$ sbt "runMain fibonacci.FibFrontend 2551"
	$ sbt "runMain fibonacci.FibBackend 2552"
	$ sbt "runMain fibonacci.FibBackend"
	$ sbt "runMain fibonacci.FibBackend"
	$ sbt "runMain fibonacci.FibFrontend"
	$ sbt "runMain fibonacci.InitiateFibComputation 2"

