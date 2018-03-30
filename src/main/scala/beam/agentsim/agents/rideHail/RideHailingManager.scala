package beam.agentsim.agents.rideHail

import beam.agentsim.agents.BeamAgent.BeamAgentData
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import beam.agentsim
import beam.agentsim.Resource._
import beam.agentsim.ResourceManager.VehicleManager
import beam.agentsim.agents.BeamAgent.BeamAgentData
import beam.agentsim.agents.{PersonAgent, TriggerUtils}
import beam.agentsim.agents.TriggerUtils._
import beam.agentsim.agents.household.HouseholdActor.ReleaseVehicleReservation
import beam.agentsim.agents.modalBehaviors.DrivesVehicle.StartLegTrigger
import beam.agentsim.agents.rideHail.RideHailingManager._
import beam.agentsim.agents.vehicles.AccessErrorCodes.{CouldNotFindRouteToCustomer, RideHailVehicleTakenError, UnknownInquiryIdError, UnknownRideHailReservationError}
import beam.agentsim.agents.vehicles.VehicleProtocol.StreetVehicle
import beam.agentsim.agents.vehicles._
import beam.agentsim.events.SpaceTime
import beam.agentsim.events.resources.ReservationError
import beam.agentsim.scheduler.BeamAgentScheduler.{CompletionNotice, ScheduleTrigger}
import beam.agentsim.scheduler.{Trigger, TriggerWithId}
import beam.analysis.plots.{GraphRideHailingRevenue, GraphSurgePricing}
import beam.router.BeamRouter.{Location, RoutingRequest, RoutingResponse}
import beam.router.Modes.BeamMode._
import beam.router.RoutingModel
import beam.router.RoutingModel.{BeamTime, BeamTrip, DiscreteTime}
import beam.sim.{BeamServices, HasServices}
import com.eaio.uuid.UUIDGen
import com.google.common.cache.{Cache, CacheBuilder}
import com.vividsolutions.jts.geom.Envelope
import org.matsim.api.core.v01.{Coord, Id}
import org.matsim.core.utils.collections.QuadTree
import org.matsim.core.utils.geometry.CoordUtils
import org.matsim.vehicles.Vehicle
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random


//TODO: Build RHM from XML to be able to specify different kinds of TNC/Rideshare types and attributes
case class RideHailingManagerData() extends BeamAgentData


// TODO: remove name variable, as not used currently in the code anywhere?
class RideHailingManager(val name: String, val beamServices: BeamServices, val router: ActorRef, val boundingBox: Envelope, val surgePricingManager: RideHailSurgePricingManager) extends VehicleManager with HasServices {

  import scala.collection.JavaConverters._

  override val resources: collection.mutable.Map[Id[BeamVehicle], BeamVehicle] = collection.mutable.Map[Id[BeamVehicle], BeamVehicle]()

  // TODO: currently 'DefaultCostPerMile' is not used anywhere in the code, therefore commented it out -> needs to be used!
  // val DefaultCostPerMile = BigDecimal(beamServices.beamConfig.beam.agentsim.agents.rideHailing.defaultCostPerMile)
  val DefaultCostPerMinute = BigDecimal(beamServices.beamConfig.beam.agentsim.agents.rideHailing.defaultCostPerMinute)
  val radius: Double = 5000


  val rideHailAllocationManagerTimeoutInSeconds = 60; // TODO Asif: set from config

  val isBufferedRideHailAllocationMode = false

 // var rideHailResourceAllocationManager: RideHailResourceAllocationManager = new RideHailAllocationManagerBufferedImplTemplate(this)
  var rideHailResourceAllocationManager: RideHailResourceAllocationManager = new DefaultRideHailResourceAllocationManager()
  // TODO Asif: has to come from config, e.g. beam.agentsim.agents.rideHailing.allocationManager = "DEFAULT_RIDEHAIL_ALLOCATION_MANAGER"



  //val bufferedReserveRideMessages:collection.mutable.ListBuffer[ReserveRide] = new ListBuffer[ReserveRide]

  var bufferedReserveRideMessages = collection.mutable.Map[Id[RideHailingInquiry], ReserveRide]()

  var handleRideHailInquirySubmitted = collection.mutable.Set[Id[RideHailingInquiry]] ()






  //TODO improve search to take into account time when available
  private val availableRideHailingAgentSpatialIndex = {
    new QuadTree[RideHailingAgentLocation](
      boundingBox.getMinX,
      boundingBox.getMinY,
      boundingBox.getMaxX,
      boundingBox.getMaxY)
  }
  private val inServiceRideHailingAgentSpatialIndex = {
    new QuadTree[RideHailingAgentLocation](
      boundingBox.getMinX,
      boundingBox.getMinY,
      boundingBox.getMaxX,
      boundingBox.getMaxY)
  }
  private val availableRideHailVehicles = collection.concurrent.TrieMap[Id[Vehicle], RideHailingAgentLocation]()
  private val inServiceRideHailVehicles = collection.concurrent.TrieMap[Id[Vehicle], RideHailingAgentLocation]()

  /**
    * Customer inquiries awaiting reservation confirmation.
    */
  lazy val pendingInquiries: Cache[Id[RideHailingInquiry], (TravelProposal, BeamTrip)] = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build()

  private val pendingModifyPassengerScheduleAcks = collection.concurrent.TrieMap[Id[RideHailingInquiry],
    ReservationResponse]()
  private var lockedVehicles = Set[Id[Vehicle]]()

  override def receive: Receive = {
    case NotifyIterationEnds() =>
      try {
        /*val graphSurgePricing: GraphSurgePricing = new GraphSurgePricing(surgePricingManager, beamServices);
        graphSurgePricing.createGraphs()*/

        val graphRideHailingRevenue: GraphRideHailingRevenue = new GraphRideHailingRevenue();
        graphRideHailingRevenue.createGraph(surgePricingManager)
      } catch {
        // print out exceptions, otherwise hidden, leads to difficult debugging
        case e: Exception => e.printStackTrace()
      }

      surgePricingManager.updateRevenueStats()
      surgePricingManager.updateSurgePriceLevels()
      surgePricingManager.incrementIteration()

      sender() ! () // return empty object to blocking caller

    case RegisterResource(vehId: Id[Vehicle]) =>
      resources.put(agentsim.vehicleId2BeamVehicleId(vehId), beamServices.vehicles(vehId))

    case NotifyResourceIdle(vehId: Id[Vehicle], whenWhere) =>
      updateLocationOfAgent(vehId, whenWhere, false)

    case NotifyResourceInUse(vehId: Id[Vehicle], whenWhere) =>
      updateLocationOfAgent(vehId, whenWhere, false)

    case CheckInResource(vehicleId: Id[Vehicle], availableIn: Option[SpaceTime]) =>
      resources.get(agentsim.vehicleId2BeamVehicleId(vehicleId)).orElse(beamServices.vehicles.get(vehicleId)).get.driver.foreach(driver => {
        val rideHailingAgentLocation = RideHailingAgentLocation(driver, vehicleId, availableIn.get)
        makeAvailable(rideHailingAgentLocation)
        sender ! CheckInSuccess
      })




    case CheckOutResource(_) =>
      // Because the RideHail Manager is in charge of deciding which specific vehicles to assign to customers, this should never be used
      throw new RuntimeException("Illegal use of CheckOutResource, RideHailingManager is responsible for checking out vehicles in fleet.")


    case RideHailingInquiry(inquiryId, personId, customerPickUp, departAt, destination) =>
      val customerAgent = sender()
      var rideHailLocationAndShortDistance: Option[(RideHailingAgentLocation,Double)] = None

      rideHailResourceAllocationManager.getVehicleAllocation(customerPickUp,departAt,destination) match {

        case Some(vehicleAllocationResult) =>
          vehicleAllocationResult.vehicleAllocation match {
            case Some(vehicleAllocation) =>
              // TODO (RW): Test following code with stanford class
              val rideHailAgent=resources.get(agentsim.vehicleId2BeamVehicleId(vehicleAllocation.vehicleId)).orElse(beamServices.vehicles.get(vehicleAllocation.vehicleId)).get.driver.head
              val rideHailingAgentLocation=RideHailingAgentLocation(rideHailAgent, vehicleAllocation.vehicleId, vehicleAllocation.availableAt)
              val distance=CoordUtils.calcProjectedEuclideanDistance(customerPickUp,rideHailingAgentLocation.currentLocation.loc)
              rideHailLocationAndShortDistance = Some(rideHailingAgentLocation,distance)
            case None =>

          }
        case None =>
          // use default allocation manager
          rideHailLocationAndShortDistance = getClosestRideHailingAgent(customerPickUp, radius)
      }

      handleRideHailInquiry(inquiryId, personId, customerPickUp, departAt, destination,rideHailLocationAndShortDistance,Some(customerAgent))





    case RoutingResponses(customerAgent, inquiryId, personId, customerPickUp, departAt, rideHailingLocation, shortDistanceToRideHailingAgent, rideHailingAgent2CustomerResponse, rideHailing2DestinationResponse) =>
      val timesToCustomer: Vector[Long] = rideHailingAgent2CustomerResponse.itineraries.map(t => t.totalTravelTime)
      // TODO: Find better way of doing this error checking than sentry value
      val timeToCustomer = if (timesToCustomer.nonEmpty) {
        timesToCustomer.min
      } else Long.MaxValue
      // TODO: Do unit conversion elsewhere... use squants or homegrown unit conversions, but enforce
      val rideHailingFare = DefaultCostPerMinute / 60.0 * surgePricingManager.getSurgeLevel(customerPickUp, departAt.atTime.toDouble)

      val customerPlans2Costs: Map[RoutingModel.EmbodiedBeamTrip, BigDecimal] = rideHailing2DestinationResponse.itineraries.map(t => (t, rideHailingFare * t.totalTravelTime)).toMap
      val itins2Cust = rideHailingAgent2CustomerResponse.itineraries.filter(x => x.tripClassifier.equals(RIDE_HAIL))
      val itins2Dest = rideHailing2DestinationResponse.itineraries.filter(x => x.tripClassifier.equals(RIDE_HAIL))
      if (timeToCustomer < Long.MaxValue && customerPlans2Costs.nonEmpty && itins2Cust.nonEmpty && itins2Dest.nonEmpty) {
        val (customerTripPlan, cost) = customerPlans2Costs.minBy(_._2)

        //TODO: include customerTrip plan in response to reuse( as option BeamTrip can include createdTime to check if the trip plan is still valid
        //TODO: we response with collection of TravelCost to be able to consolidate responses from different ride hailing companies

        val modRHA2Cust = itins2Cust.map(l => l.copy(legs = l.legs.map(c => c.copy(asDriver = true))))
        val modRHA2Dest = itins2Dest.map(l => l.copy(legs = l.legs.zipWithIndex.map(c => c._1.copy(asDriver = c._1.beamLeg.mode == WALK,
          unbecomeDriverOnCompletion = c._2 == 2,
          beamLeg = c._1.beamLeg.copy(startTime = c._1.beamLeg.startTime + timeToCustomer),
          cost = if (c._1.beamLeg == l.legs(1).beamLeg) {
            cost
          } else {
            0.0
          }
        ))))

        val rideHailingAgent2CustomerResponseMod = RoutingResponse(modRHA2Cust)
        val rideHailing2DestinationResponseMod = RoutingResponse(modRHA2Dest)

        val travelProposal = TravelProposal(rideHailingLocation, timeToCustomer, cost, Option(FiniteDuration
        (customerTripPlan.totalTravelTime, TimeUnit.SECONDS)), rideHailingAgent2CustomerResponseMod,
          rideHailing2DestinationResponseMod)
        pendingInquiries.put(inquiryId, (travelProposal, modRHA2Dest.head.toBeamTrip()))
        log.debug(s"Found ride to hail for  person=$personId and inquiryId=$inquiryId within " +
          s"$shortDistanceToRideHailingAgent meters, timeToCustomer=$timeToCustomer seconds and cost=$$$cost")


        customerAgent match {
          case Some(customerAgent) => customerAgent ! RideHailingInquiryResponse(inquiryId, Vector(travelProposal))
          case None =>

            bufferedReserveRideMessages.get(inquiryId) match {
              case Some(ReserveRide(inquiryId, vehiclePersonIds, customerPickUp, departAt, destination)) =>
                handlePendingQuery(inquiryId, vehiclePersonIds, customerPickUp, departAt, destination)
                bufferedReserveRideMessages.remove(inquiryId);

              case _ =>
            }
          // call was made by ride hail agent itself


        }

      } else {
        log.debug(s"Router could not find route to customer person=$personId for inquiryId=$inquiryId")
        lockedVehicles -= rideHailingLocation.vehicleId


        customerAgent match {
          case Some(customerAgent) => customerAgent ! RideHailingInquiryResponse(inquiryId, Vector(), error = Option(CouldNotFindRouteToCustomer))
          case None => // TODO RW handle this
        }
      }

    case reserveRide @ ReserveRide(inquiryId, vehiclePersonIds, customerPickUp, departAt, destination) =>

      if (isBufferedRideHailAllocationMode){
        bufferedReserveRideMessages += (inquiryId -> reserveRide)
      } else {
        handlePendingQuery(inquiryId, vehiclePersonIds, customerPickUp, departAt, destination)
      }

    case ModifyPassengerScheduleAck(inquiryIDOption) =>
      completeReservation(Id.create(inquiryIDOption.get.toString, classOf[RideHailingInquiry]))

    case ReleaseVehicleReservation(_, vehId) =>
      lockedVehicles -= vehId

// TODO (RW): this needs to be called according to timeout settings
    case TriggerWithId(RideHailAllocationManagerTimeout(tick), triggerId) => {
    if (isBufferedRideHailAllocationMode){


      var map: Map[Id[RideHailingInquiry], VehicleAllocationRequest] = Map[Id[RideHailingInquiry], VehicleAllocationRequest]()
     bufferedReserveRideMessages.values.foreach{ reserveRide =>
       map += (reserveRide.inquiryId -> VehicleAllocationRequest( reserveRide.pickUpLocation,reserveRide.departAt,reserveRide.destination))


      }

      var resultMap =rideHailResourceAllocationManager.allocateBatchRequests(map)


      for (ReserveRide(inquiryId, vehiclePersonIds, customerPickUp, departAt, destination) <- bufferedReserveRideMessages.values) {

        resultMap(inquiryId).vehicleAllocation match {
          case Some(vehicleAllocation) =>
            if (!handleRideHailInquirySubmitted.contains(inquiryId)){
              val rideHailAgent=resources.get(agentsim.vehicleId2BeamVehicleId(vehicleAllocation.vehicleId)).orElse(beamServices.vehicles.get(vehicleAllocation.vehicleId)).get.driver.head
              val rideHailingAgentLocation=RideHailingAgentLocation(rideHailAgent, vehicleAllocation.vehicleId, vehicleAllocation.availableAt)
              val distance=CoordUtils.calcProjectedEuclideanDistance(customerPickUp,rideHailingAgentLocation.currentLocation.loc)
              val rideHailLocationAndShortDistance = Some(rideHailingAgentLocation,distance)

              handleRideHailInquirySubmitted += inquiryId
              handleRideHailInquiry(inquiryId, vehiclePersonIds.personId, customerPickUp, departAt, destination,rideHailLocationAndShortDistance,None)
            }

          case None =>
          // TODO: how to handle case, if no car availalbe????? -> check
        }


      }




        // TODO (RW) to make the following call compatible with default code, we need to probably manipulate

    //
      }

      // TODO (RW) add repositioning code here

   //   var scheduleTriggers= Vector[ScheduleTrigger]()

      val timerTrigger = RideHailAllocationManagerTimeout(tick + rideHailAllocationManagerTimeoutInSeconds)
      val timerMessage = ScheduleTrigger(timerTrigger, self)

   //   var scheduleTriggers= Vector[ScheduleTrigger](timerMessage)

     // scheduleTriggers :+ = timerMessage


      //beamServices.schedulerRef ! timerMessage



      beamServices.schedulerRef ! TriggerUtils.completed(triggerId,Vector(timerMessage))


    }

    case msg =>
      log.warn(s"unknown message received by RideHailingManager $msg")

  }



  private def findClosestRideHailingAgents(inquiryId: Id[RideHailingInquiry], customerPickUp: Location) = {

    val travelPlanOpt = Option(pendingInquiries.asMap.remove(inquiryId))
    val customerAgent = sender()
    /**
      * 1. customerAgent ! ReserveRideConfirmation(availableRideHailingAgentSpatialIndex, customerId, travelProposal)
      * 2. availableRideHailingAgentSpatialIndex ! PickupCustomer
      */
    val nearbyRideHailingAgents = availableRideHailingAgentSpatialIndex.getDisk(customerPickUp.getX, customerPickUp.getY,
      radius).asScala.toVector
    val closestRHA: Option[RideHailingAgentLocation] = nearbyRideHailingAgents.filter(x =>
      lockedVehicles(x.vehicleId)).find(_.vehicleId.equals(travelPlanOpt.get._1.responseRideHailing2Pickup
      .itineraries.head.vehiclesInTrip.head))
    (travelPlanOpt, customerAgent, closestRHA)
  }

  private def createCustomerInquiryResponse(personId: Id[PersonAgent], customerPickUp: Location, departAt: BeamTime, destination: Location, rideHailingLocation: RideHailingAgentLocation): (Future[Any], Future[Any]) = {
    val customerAgentBody = StreetVehicle(Id.createVehicleId(s"body-$personId"), SpaceTime((customerPickUp,
      departAt.atTime)), WALK, asDriver = true)
    val rideHailingVehicleAtOrigin = StreetVehicle(rideHailingLocation.vehicleId, SpaceTime(
      (rideHailingLocation.currentLocation.loc, departAt.atTime)), CAR, asDriver = false)
    val rideHailingVehicleAtPickup = StreetVehicle(rideHailingLocation.vehicleId, SpaceTime((customerPickUp,
      departAt.atTime)), CAR, asDriver = false)

    //TODO: Error handling. In the (unlikely) event of a timeout, this RideHailingManager will silently be
    //TODO: restarted, and probably someone will wait forever for its reply.
    implicit val timeout: Timeout = Timeout(50000, TimeUnit.SECONDS)

    // get route from ride hailing vehicle to customer
    val futureRideHailingAgent2CustomerResponse = router ? RoutingRequest(rideHailingLocation
      .currentLocation.loc, customerPickUp, departAt, Vector(), Vector(rideHailingVehicleAtOrigin))
    //XXXX: customer trip request might be redundant... possibly pass in info

    // get route from customer to destination
    val futureRideHailing2DestinationResponse = router ? RoutingRequest(customerPickUp, destination, departAt, Vector(), Vector(customerAgentBody, rideHailingVehicleAtPickup))
    (futureRideHailingAgent2CustomerResponse, futureRideHailing2DestinationResponse)
  }


  private def updateLocationOfAgent(vehicleId: Id[Vehicle], whenWhere: SpaceTime, isAvailable: Boolean) = {
    if (isAvailable) {
      availableRideHailVehicles.get(vehicleId) match {
        case Some(prevLocation) =>
          val newLocation = prevLocation.copy(currentLocation = whenWhere)
          availableRideHailingAgentSpatialIndex.remove(prevLocation.currentLocation.loc.getX, prevLocation.currentLocation.loc.getY, prevLocation)
          availableRideHailingAgentSpatialIndex.put(newLocation.currentLocation.loc.getX, newLocation.currentLocation.loc.getY, newLocation)
          availableRideHailVehicles.put(newLocation.vehicleId, newLocation)
        case None =>
      }
    } else {
      inServiceRideHailVehicles.get(vehicleId) match {
        case Some(prevLocation) =>
          val newLocation = prevLocation.copy(currentLocation = whenWhere)
          inServiceRideHailingAgentSpatialIndex.remove(prevLocation.currentLocation.loc.getX, prevLocation.currentLocation.loc.getY, prevLocation)
          inServiceRideHailingAgentSpatialIndex.put(newLocation.currentLocation.loc.getX, newLocation.currentLocation.loc.getY, newLocation)
          inServiceRideHailVehicles.put(newLocation.vehicleId, newLocation)
        case None =>
      }
    }
  }

  private def makeAvailable(agentLocation: RideHailingAgentLocation) = {
    availableRideHailVehicles.put(agentLocation.vehicleId, agentLocation)
    availableRideHailingAgentSpatialIndex.put(agentLocation.currentLocation.loc.getX,
      agentLocation.currentLocation.loc.getY, agentLocation)
    inServiceRideHailVehicles.remove(agentLocation.vehicleId)
    inServiceRideHailingAgentSpatialIndex.remove(agentLocation.currentLocation.loc.getX,
      agentLocation.currentLocation.loc.getY, agentLocation)
  }

  private def putIntoService(agentLocation: RideHailingAgentLocation) = {
    availableRideHailVehicles.remove(agentLocation.vehicleId)
    availableRideHailingAgentSpatialIndex.remove(agentLocation.currentLocation.loc.getX,
      agentLocation.currentLocation.loc.getY, agentLocation)
    inServiceRideHailVehicles.put(agentLocation.vehicleId, agentLocation)
    inServiceRideHailingAgentSpatialIndex.put(agentLocation.currentLocation.loc.getX,
      agentLocation.currentLocation.loc.getY, agentLocation)
  }

  private def handleReservation(inquiryId: Id[RideHailingInquiry], vehiclePersonId: VehiclePersonId,
                                customerPickUp: Location, destination: Location,
                                customerAgent: ActorRef, closestRideHailingAgentLocation: RideHailingAgentLocation,
                                travelProposal: TravelProposal, trip2DestPlan: Option[BeamTrip]): Unit = {

    // Modify RH agent passenger schedule and create BeamAgentScheduler message that will dispatch RH agent to do the
    // pickup
    val passengerSchedule = PassengerSchedule()
    passengerSchedule.addLegs(travelProposal.responseRideHailing2Pickup.itineraries.head.toBeamTrip.legs) // Adds
    // empty trip to customer
    passengerSchedule.addPassenger(vehiclePersonId, trip2DestPlan.get.legs.filter(_.mode == CAR)) // Adds customer's
    // actual trip to destination
    putIntoService(closestRideHailingAgentLocation)
    lockedVehicles -= closestRideHailingAgentLocation.vehicleId

    // Create confirmation info but stash until we receive ModifyPassengerScheduleAck
    val triggerToSchedule = schedule[StartLegTrigger](passengerSchedule.schedule.firstKey.startTime,
      closestRideHailingAgentLocation.rideHailAgent, passengerSchedule.schedule.firstKey)
    pendingModifyPassengerScheduleAcks.put(inquiryId, ReservationResponse(Id.create(inquiryId.toString,
      classOf[ReservationRequest]), Right(ReserveConfirmInfo(trip2DestPlan.head.legs.head, trip2DestPlan.last.legs
      .last, vehiclePersonId, triggerToSchedule))))
    closestRideHailingAgentLocation.rideHailAgent ! ModifyPassengerSchedule(passengerSchedule, Some(inquiryId))
  }

  private def completeReservation(inquiryId: Id[RideHailingInquiry]): Unit = {
    pendingModifyPassengerScheduleAcks.remove(inquiryId) match {
      case Some(response) =>
        log.debug(s"Completed reservation for $inquiryId")
        val customerRef = beamServices.personRefs(response.response.right.get.passengerVehiclePersonId.personId)
        customerRef ! response
      case None =>
        log.error(s"Vehicle was reserved by another agent for inquiry id $inquiryId")
        sender() ! ReservationResponse(Id.create(inquiryId.toString, classOf[ReservationRequest]), Left
        (RideHailVehicleTakenError))
    }

  }


  def getClosestVehiclesWithinStandardRadius(pickupLocation: Coord, radius: Double):Vector[(RideHailingAgentLocation,
    Double)] = {
    val nearbyRideHailingAgents = availableRideHailingAgentSpatialIndex.getDisk(pickupLocation.getX, pickupLocation.getY,
      radius).asScala.toVector
    val distances2RideHailingAgents = nearbyRideHailingAgents.map(rideHailingAgentLocation => {
      val distance = CoordUtils.calcProjectedEuclideanDistance(pickupLocation, rideHailingAgentLocation
        .currentLocation.loc)
      (rideHailingAgentLocation, distance)
    })
    //TODO: Possibly get multiple taxis in this block
    distances2RideHailingAgents.filterNot(x => lockedVehicles(x._1.vehicleId)).sortBy(_._2)
  }

  def getClosestRideHailingAgent(pickupLocation: Coord, radius: Double): Option[(RideHailingAgentLocation,
    Double)] = {
    getClosestVehiclesWithinStandardRadius(pickupLocation,radius).headOption
  }


  private def handlePendingQuery(inquiryId: Id[RideHailingInquiry], vehiclePersonIds: VehiclePersonId, customerPickUp: Location,
                          departAt: BeamTime, destination: Location): Unit ={
    if (pendingInquiries.asMap.containsKey(inquiryId)) {
      val (travelPlanOpt: Option[(TravelProposal, BeamTrip)], customerAgent: ActorRef, closestRHA: Option[RideHailingAgentLocation]) = findClosestRideHailingAgents(inquiryId, customerPickUp)

      closestRHA match {
        case Some((closestRideHailingAgent)) =>
          val travelProposal = travelPlanOpt.get._1
          surgePricingManager.addRideCost(departAt.atTime, travelProposal.estimatedPrice.doubleValue(), customerPickUp)


          val tripPlan = travelPlanOpt.map(_._2)
          handleReservation(inquiryId, vehiclePersonIds, customerPickUp, destination, customerAgent,
            closestRideHailingAgent, travelProposal, tripPlan)
        // We have an agent nearby, but it's not the one we originally wanted
        case _ =>
          customerAgent ! ReservationResponse(Id.create(inquiryId.toString, classOf[ReservationRequest]), Left
          (UnknownRideHailReservationError))
      }
    } else {
      sender() ! ReservationResponse(Id.create(inquiryId.toString, classOf[ReservationRequest]), Left
      (UnknownInquiryIdError))
    }
  }

  def lockedVehicle(vehicleId: Id[Vehicle]) = {
    lockedVehicles += vehicleId
  }


  private def handleRideHailInquiry(inquiryId: Id[RideHailingInquiry], personId: Id[PersonAgent],
                                    customerPickUp: Location, departAt: BeamTime, destination: Location, rideHailLocationAndShortDistance: Option[(RideHailingAgentLocation,Double)],customerAgent: Option[ActorRef]): Unit ={
    rideHailLocationAndShortDistance match {
      case Some((rideHailingLocation, shortDistanceToRideHailingAgent)) =>
        if (!isBufferedRideHailAllocationMode) {
          // only lock vehicle in immediate processing mode, in buffered processing mode we lock vehicle only when batch queries are beeing processing
          lockedVehicles += rideHailingLocation.vehicleId
        }

        // Need to have this dispatcher here for the future execution below
        import context.dispatcher

        val (futureRideHailingAgent2CustomerResponse, futureRideHailing2DestinationResponse) =
          createCustomerInquiryResponse(personId, customerPickUp, departAt, destination, rideHailingLocation)

        for {
          rideHailingAgent2CustomerResponse <- futureRideHailingAgent2CustomerResponse.mapTo[RoutingResponse]
          rideHailing2DestinationResponse <- futureRideHailing2DestinationResponse.mapTo[RoutingResponse]
        } {
          // TODO: could we just call the code, instead of sending the message here?
          self ! RoutingResponses(customerAgent, inquiryId, personId, customerPickUp, departAt, rideHailingLocation, shortDistanceToRideHailingAgent, rideHailingAgent2CustomerResponse, rideHailing2DestinationResponse)
        }
      case None =>
        // no rides to hail
        customerAgent match {
          case Some(customerAgent) => customerAgent ! RideHailingInquiryResponse(inquiryId, Vector(), error = Option(CouldNotFindRouteToCustomer))
          case None => self ! RideHailingInquiryResponse(inquiryId, Vector(), error = Option(CouldNotFindRouteToCustomer))

            // TODO RW: handle this error case where RideHailingInquiryResponse is sent back to us
        }
    }
  }


}

/**
  * BEAM
  */


object RideHailingManager {
  val RIDE_HAIL_MANAGER = "RideHailingManager";
  val log: Logger = LoggerFactory.getLogger(classOf[RideHailingManager])

  def nextRideHailingInquiryId: Id[RideHailingInquiry] = Id.create(UUIDGen.createTime(UUIDGen.newTime()).toString,
    classOf[RideHailingInquiry])

  case class NotifyIterationEnds()

  case class RideHailingInquiry(inquiryId: Id[RideHailingInquiry], customerId: Id[PersonAgent],
                                pickUpLocation: Location, departAt: BeamTime, destination: Location)

  case class TravelProposal(rideHailingAgentLocation: RideHailingAgentLocation, timesToCustomer: Long,
                            estimatedPrice: BigDecimal, estimatedTravelTime: Option[Duration],
                            responseRideHailing2Pickup: RoutingResponse, responseRideHailing2Dest: RoutingResponse)

  case class RideHailingInquiryResponse(inquiryId: Id[RideHailingInquiry], proposals: Seq[TravelProposal],
                                        error: Option[ReservationError] = None)

  case class ReserveRide(inquiryId: Id[RideHailingInquiry], customerIds: VehiclePersonId, pickUpLocation: Location,
                         departAt: BeamTime, destination: Location)

  private case class RoutingResponses(customerAgent: Option[ActorRef], inquiryId: Id[RideHailingInquiry],
                                      personId: Id[PersonAgent], customerPickUp: Location, departAt: BeamTime, rideHailingLocation: RideHailingAgentLocation,
                                      shortDistanceToRideHailingAgent: Double,
                                      rideHailingAgent2CustomerResponse: RoutingResponse,
                                      rideHailing2DestinationResponse: RoutingResponse)

  case class ReserveRideResponse(inquiryId: Id[RideHailingInquiry], data: Either[ReservationError, RideHailConfirmData])

  case class RideHailConfirmData(rideHailAgent: ActorRef, customerId: Id[PersonAgent], travelProposal: TravelProposal)

  case class RegisterRideAvailable(rideHailingAgent: ActorRef, vehicleId: Id[Vehicle], availableSince: SpaceTime)

  case class RegisterRideUnavailable(ref: ActorRef, location: Coord)

  case class RideHailingAgentLocation(rideHailAgent: ActorRef, vehicleId: Id[Vehicle], currentLocation: SpaceTime)

  case object RideUnavailableAck

  case object RideAvailableAck


  case class RepositionResponse(rnd1: RideHailingAgentLocation, rnd2: RideHailingManager.RideHailingAgentLocation,
                                rnd1Response: RoutingResponse, rnd2Response: RoutingResponse)


  case class RideHailAllocationManagerTimeout(tick: Double) extends Trigger

  def props(name: String, services: BeamServices, router: ActorRef, boundingBox: Envelope, surgePricingManager: RideHailSurgePricingManager) = {
    Props(new RideHailingManager(name, services, router, boundingBox, surgePricingManager))
  }
}