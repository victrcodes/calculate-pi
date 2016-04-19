import scala.concurrent.duration._

import akka.actor._
import akka.routing.RoundRobinRouter
import com.typesafe.config.ConfigFactory

object Pi extends App {

	calculate(workerNumber = 8, elementNumber = 10000, messageNumber = 10000)

	/**
	 * Calculate Pi
	 *
	 * @param workerNumber	Number of workers
	 * @param elementNumber	Number of elements
	 * @param messageNumber	Number of messages
	 */
	def calculate(workerNumber: Int, elementNumber: Int, messageNumber: Int) {

		//Create an Akka system
		val system = ActorSystem("Pi", ConfigFactory.load())

		//Create the result listener, which will print the result and shutdown the system
		val listener = system.actorOf(Props[Listener], name = "listener")

		//Create the master
		val master = system.actorOf(Props(new Master(workerNumber, messageNumber, elementNumber, listener)), name = "master")

		//Start the calculation
		master ! Calculate

	}

	sealed trait PiMessage
	case class Work(start: Int, elementNumber: Int) extends PiMessage
	case class Result(value: Double) extends PiMessage
	case class PiApprox(pi: Double, duration: Duration)

	/**
	 * Worker actor
	 */
	class Worker extends Actor {

		def receive = {
			case Work(start, elementNumber) =>
				sender ! Result(calculatePiFor(start, elementNumber)) //perform the work
		}

		final def calc(i: Int, max: Int, acc: Double): Double = {
			if (i == max)
				acc
			else
				calc(i + 1, max, acc + 4.0 * (1 - (i % 2) * 2) / (2 * i + 1))
		}

		def calculatePiFor(start: Int, elementNumber: Int): Double = calc(start, start + elementNumber, 0.0)

	}

	/**
	 * Master actor
	 *
	 * @param workerNumber	Number of workers
	 * @param messageNumber	Number of messages
	 * @param elementNumber	Number of elements
	 * @param listener		Listener actor reference
	 */
	class Master(workerNumber: Int, messageNumber: Int, elementNumber: Int, listener: ActorRef) extends Actor {

		val start: Long = System.currentTimeMillis
		val workerRouter = context.actorOf(Props[Worker].withRouter(RoundRobinRouter(workerNumber)), name = "workerRouter")

		//Using become to avoid mutable state in the actor
		override def preStart = {
			context.become(myReceive(0.0, 0))
		}

		def receive = { case _ => }

		//Default receive method override
		def myReceive(pi: Double, resultNumber: Int): Receive = {
			case Calculate =>
				for (i <- 0 until messageNumber) workerRouter ! Work(i * elementNumber, elementNumber)
			case Result(value) =>
				val newPi = pi + value
				val newResultNumber = resultNumber + 1
				if (newResultNumber == messageNumber) {
					//Send the result to the listener
					listener ! PiApprox(pi, duration = (System.currentTimeMillis - start).millis)
					//Stops this actor and all its supervised children
					context.stop(self)
				} else {
					//Immutable transition
					context.become(myReceive(newPi, newResultNumber))
				}
		}

	}

	/**
	 * Listener actor
	 */
	class Listener extends Actor {

		def receive = {
			case PiApprox(pi, duration) =>
				println(s"\nPi approximation: \t$pi\nCalculation time: \t$duration\n")
				context.system.shutdown()
		}

	}

	case object Calculate extends PiMessage

}