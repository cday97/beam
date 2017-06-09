package beam.agentsim.routing

import java.util

import akka.actor.{Actor, Props}
import beam.agentsim.routing.BeamRouter.RoutingRequest
import beam.agentsim.routing.DummyRouter.RoutingResponse
import beam.agentsim.routing.RoutingModel.DiscreteTime
import beam.agentsim.sim.AgentsimServices
import org.matsim.api.core.v01.population.{Person, PlanElement}
import org.matsim.core.router.{RoutingModule, StageActivityTypes, TripRouter}
import org.matsim.facilities.Facility

/**
  * Created by sfeygin on 2/28/17.
  */
class DummyRouter(agentsimServices: AgentsimServices, val tripRouter: TripRouter) extends RoutingModule with Actor {

  def calcRoute(fromFacility: Facility[_], toFacility: Facility[_], departureTime: Double, person: Person): java.util.LinkedList[PlanElement] = {
    new util.LinkedList[PlanElement]()
  }

  override def receive: Receive = {
    case RoutingRequest(fromFacility, toFacility, departureTime, accessMode, personId, considerTransit) =>
      val person: Person = agentsimServices.matsimServices.getScenario.getPopulation.getPersons.get(personId)
      val time = departureTime.asInstanceOf[DiscreteTime]
      sender() ! RoutingResponse(calcRoute(fromFacility, toFacility, time.atTime, person))
  }

  override def getStageActivityTypes: StageActivityTypes = new StageActivityTypes {
    override def isStageActivity(activityType: String): Boolean = true
  }


}

object DummyRouter {
  def props(agentsimServices: AgentsimServices,tripRouter: TripRouter) = Props(classOf[DummyRouter],agentsimServices,tripRouter)

  case class RoutingResponse(legs: util.LinkedList[PlanElement])

}