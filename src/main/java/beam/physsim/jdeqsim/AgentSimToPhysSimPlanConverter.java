package beam.physsim.jdeqsim;

import akka.actor.ActorRef;
import beam.agentsim.agents.vehicles.BeamVehicleType;
import beam.agentsim.events.PathTraversalEvent;
import beam.analysis.IterationStatsProvider;
import beam.analysis.physsim.PhyssimCalcLinkSpeedDistributionStats;
import beam.analysis.physsim.PhyssimCalcLinkSpeedStats;
import beam.analysis.physsim.PhyssimNetworkComparisonEuclideanVsLengthAttribute;
import beam.analysis.physsim.PhyssimNetworkLinkLengthDistribution;
import beam.calibration.impl.example.CountsObjectiveFunction;
import beam.physsim.cchRoutingAssignment.OsmInfoHolder;
import beam.physsim.cchRoutingAssignment.RoutingFrameworkTravelTimeCalculator;
import beam.physsim.cchRoutingAssignment.RoutingFrameworkWrapperImpl;
import beam.router.BeamRouter;
import beam.router.FreeFlowTravelTime;
import beam.sim.BeamConfigChangesObservable;
import beam.sim.BeamConfigChangesObserver;
import beam.sim.BeamServices;
import beam.sim.config.BeamConfig;
import beam.sim.metrics.MetricsSupport;
import beam.sim.population.AttributesOfIndividual;
import beam.sim.population.PopulationAdjustment;
import beam.sim.population.PopulationAdjustment$;
import beam.utils.*;
import com.conveyal.r5.transit.TransportNetwork;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.TravelTimeCalculatorConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Time;
import org.matsim.households.Household;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author asif and rwaraich.
 */
public class AgentSimToPhysSimPlanConverter implements BasicEventHandler, MetricsSupport, IterationStatsProvider, BeamConfigChangesObserver {

    public static final String CAR = "car";
    public static final String BUS = "bus";
    private static final String DUMMY_ACTIVITY = "DummyActivity";
    private final PhyssimCalcLinkSpeedStats linkSpeedStatsGraph;
    private final PhyssimCalcLinkSpeedDistributionStats linkSpeedDistributionStatsGraph;
    private final PhyssimNetworkLinkLengthDistribution physsimNetworkLinkLengthDistribution;
    private final PhyssimNetworkComparisonEuclideanVsLengthAttribute physsimNetworkEuclideanVsLengthAttribute;
    private final ActorRef router;
    private final OutputDirectoryHierarchy controlerIO;
    private final Logger log = LoggerFactory.getLogger(AgentSimToPhysSimPlanConverter.class);
    private final Scenario agentSimScenario;
    private Population jdeqsimPopulation;
    private TravelTime aggregatedTravelTime;
    private final BeamServices beamServices;
    private final BeamConfigChangesObservable beamConfigChangesObservable;

    private AgentSimPhysSimInterfaceDebugger agentSimPhysSimInterfaceDebugger;

    // suppliers are used for sake of laziness
    private final Supplier<RoutingFrameworkTravelTimeCalculator> routingFrameworkTravelTimeCalculator;

    private BeamConfig beamConfig;
    private final Random rand = MatsimRandom.getRandom();
    private final boolean agentSimPhysSimInterfaceDebuggerEnabled;

    private final List<CompletableFuture> completableFutures = new ArrayList<>();

    final Map<String, Boolean> caccVehiclesMap = new TreeMap<>();
    private final Map<Integer, Mean> binSpeed = new HashMap<>();

    private TravelTime prevTravelTime = new FreeFlowTravelTime();

    private final Random rnd;

    private Map<Id<Person>, Household> personToHouseHold;

    private final List<PathTraversalEvent> traversalEventsForPhysSimulation = new LinkedList<>();

    public AgentSimToPhysSimPlanConverter(EventsManager eventsManager,
                                          TransportNetwork transportNetwork,
                                          OutputDirectoryHierarchy controlerIO,
                                          Scenario scenario,
                                          BeamServices beamServices,
                                          BeamConfigChangesObservable beamConfigChangesObservable) {
        eventsManager.addHandler(this);
        this.beamServices = beamServices;
        this.controlerIO = controlerIO;
        this.router = beamServices.beamRouter();
        this.beamConfig = beamServices.beamConfig();
        this.rand.setSeed(beamConfig.matsim().modules().global().randomSeed());
        this.beamConfigChangesObservable = beamConfigChangesObservable;
        agentSimScenario = scenario;
        agentSimPhysSimInterfaceDebuggerEnabled = beamConfig.beam().physsim().jdeqsim().agentSimPhysSimInterfaceDebugger().enabled();

        if (agentSimPhysSimInterfaceDebuggerEnabled) {
            log.warn("AgentSimPhysSimInterfaceDebugger is enabled");
            agentSimPhysSimInterfaceDebugger = new AgentSimPhysSimInterfaceDebugger(beamServices.geo(), transportNetwork);
        }
        preparePhysSimForNewIteration();


        linkSpeedStatsGraph = new PhyssimCalcLinkSpeedStats(agentSimScenario.getNetwork(), controlerIO, beamConfig);
        linkSpeedDistributionStatsGraph = new PhyssimCalcLinkSpeedDistributionStats(agentSimScenario.getNetwork(), controlerIO, beamConfig);
        physsimNetworkLinkLengthDistribution = new PhyssimNetworkLinkLengthDistribution(agentSimScenario.getNetwork(), controlerIO, beamConfig);
        physsimNetworkEuclideanVsLengthAttribute = new PhyssimNetworkComparisonEuclideanVsLengthAttribute(agentSimScenario.getNetwork(), controlerIO, beamConfig);
        beamConfigChangesObservable.addObserver(this);
        rnd = new Random(beamConfig.matsim().modules().global().randomSeed());

        routingFrameworkTravelTimeCalculator = Suppliers.memoize(() -> new RoutingFrameworkTravelTimeCalculator(
                beamServices,
                new OsmInfoHolder(beamServices),
                new RoutingFrameworkWrapperImpl(beamServices)
        ))::get;
    }


    private void preparePhysSimForNewIteration() {
        jdeqsimPopulation = PopulationUtils.createPopulation(agentSimScenario.getConfig());
        buildPersonToHousehold();
    }

    public void buildPersonToHousehold() {
        personToHouseHold = beamServices.matsimServices().getScenario().getHouseholds().getHouseholds().values().stream().flatMap(h -> h.getMemberIds().stream().map(m -> new AbstractMap.SimpleEntry<Id<Person>, Household>(m, h)))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    private void setupActorsAndRunPhysSim(IterationEndsEvent iterationEndsEvent) {
        // I don't use single class `UpdateTravelTime` here and make decision in `BeamRouter` because
        // below we have `linkStatsGraph.notifyIterationEnds` call which internally will call `BeamCalcLinkStats.addData`
        // which may change an internal state of travel time calculator (and it happens concurrently in CompletableFuture)
        //################################################################################################################
        Collection<? extends Link> links = agentSimScenario.getNetwork().getLinks().values();
        int maxHour = (int) TimeUnit.SECONDS.toHours(agentSimScenario.getConfig().travelTimeCalculator().getMaxTime()) + 1;

        int iterationNumber = iterationEndsEvent.getIteration();

        Map<String, double[]> travelTimeMap;
        TravelTime travelTimeFromPhysSim;
        VolumesAnalyzer volumesAnalyzer;

        String physSimName = beamConfig.beam().physsim().name();

        switch (physSimName) {
            case "JDEQSim":
            case "BPRSim":
            case "PARBPRSim":
                log.info("{} started", physSimName);

                RelaxationExperiment sim = RelaxationExperiment$.MODULE$.apply(beamConfig, agentSimScenario, jdeqsimPopulation,
                        beamServices, controlerIO, caccVehiclesMap, beamConfigChangesObservable, iterationNumber, rnd);
                log.info("RelaxationExperiment is {}, type is {}", sim.getClass().getSimpleName(), beamConfig.beam().physsim().relaxation().type());
                SimulationResult result = sim.run(prevTravelTime);
                travelTimeFromPhysSim = result.travelTime();
                volumesAnalyzer = result.volumesAnalyzer().getOrElse(this::dummyVolumesAnalyzer);
                // Safe travel time to reuse it on the next PhysSim iteration
                prevTravelTime = travelTimeFromPhysSim;

                travelTimeMap = TravelTimeCalculatorHelper.GetLinkIdToTravelTimeArray(links,
                        travelTimeFromPhysSim, maxHour);

                if (beamConfig.beam().debug().debugEnabled()) {
                    log.info(DebugLib.getMemoryLogMessage("Memory Use After Phys Sim: "));
                }

                log.info("{} End", physSimName);
                break;
            case "CCHRoutingAssignment":
                travelTimeMap = routingFrameworkTravelTimeCalculator.get().generateLink2TravelTimes(traversalEventsForPhysSimulation, iterationNumber, links, maxHour);
                travelTimeFromPhysSim = TravelTimeCalculatorHelper.CreateTravelTimeCalculator(beamConfig.beam().agentsim().timeBinSize(), travelTimeMap);
                volumesAnalyzer = dummyVolumesAnalyzer();
                log.warn("For CCHRoutingAssignment physsim the iteration x.linkstats.csv.gz is going to contain wrong" +
                        " volumes (1.0 for all the entries)");
                break;
            default:
                throw new RuntimeException(String.format("Unknown physsim type: %s", physSimName));
        }

        String objectiveFunction = beamConfig.beam().calibration().objectiveFunction();
        if (this.controlerIO != null
                && objectiveFunction.toLowerCase().contains("counts")) {
            try {
                String outPath =
                        controlerIO
                                .getIterationFilename(iterationNumber, "countscompare.txt");
                double countsError = CountsObjectiveFunction.evaluateFromRun(outPath);
                log.info("counts Error: " + countsError);
            } catch (Exception e) {
                log.error("exception {}", e.getMessage());
            }
        }

        TravelTime freeFlow = new FreeFlowTravelTime();
        int nBins = 0;
        int nBinsWithUnexpectedlyLowSpeed = 0;
        for (Map.Entry<String, double[]> entry : travelTimeMap.entrySet()) {
            int hour = 0;
            Link link = agentSimScenario.getNetwork().getLinks().get(Id.createLinkId(entry.getKey()));
            for (double linkTravelTime : entry.getValue()) {
                double speed = link.getLength() / linkTravelTime;
                if (speed < beamConfig.beam().physsim().quick_fix_minCarSpeedInMetersPerSecond()) {
                    double linkTravelTime1 = travelTimeFromPhysSim.getLinkTravelTime(link, hour * 60.0 * 60.0, null, null);
                    double freeFlowTravelTime = freeFlow.getLinkTravelTime(link, hour * 60.0 * 60.0, null, null);
                    log.debug("{} {} {}", linkTravelTime, linkTravelTime1, freeFlowTravelTime);
                    nBinsWithUnexpectedlyLowSpeed++;
                }
                hour++;
                nBins++;
            }
        }
        if (nBinsWithUnexpectedlyLowSpeed > 0) {
            log.error("Iteration {} had {} link speed bins (of {}) with speed smaller than {}.", iterationNumber, nBinsWithUnexpectedlyLowSpeed, nBins, beamConfig.beam().physsim().quick_fix_minCarSpeedInMetersPerSecond());
        }

        TravelTime travelTimeForR5 = travelTimeFromPhysSim;
        int startingIterationForTravelTimesMSA = beamConfig.beam().routing().startingIterationForTravelTimesMSA();
        if (startingIterationForTravelTimesMSA <= iterationNumber) {
            travelTimeMap = processTravelTime(links, travelTimeMap, maxHour);
            travelTimeForR5 = aggregatedTravelTime;
        }

        int lastIteration = beamConfig.matsim().modules().controler().lastIteration();
        // We write travel time map on 0-th iteration or (iterationNumber + 1) % writeEventsInterval because this travel time will be used in the next iteration
        // It's needed to be in sync with `RouteDumper` and allow us to reproduce routes calculation
        if ((iterationNumber == lastIteration) || beamConfig.beam().outputs().writeEventsInterval() > 0 &&
                iterationNumber % beamConfig.beam().outputs().writeEventsInterval() == 0) {
            String filePath = beamServices.matsimServices().getControlerIO().getIterationFilename(iterationNumber, "travel_time_map.bin");
            try {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
                    oos.writeObject(travelTimeMap);
                }
            } catch (Exception ex) {
                log.error("Can't write travel time map", ex);
            }
        }

        router.tell(new BeamRouter.TryToSerialize(travelTimeMap), ActorRef.noSender());
        router.tell(new BeamRouter.UpdateTravelTimeRemote(travelTimeMap), ActorRef.noSender());
        //################################################################################################################

        writeTravelTime(travelTimeForR5, volumesAnalyzer, iterationEndsEvent);

        router.tell(new BeamRouter.UpdateTravelTimeLocal(travelTimeForR5), ActorRef.noSender());

        completableFutures.add(CompletableFuture.runAsync(() -> linkSpeedStatsGraph.notifyIterationEnds(iterationNumber, travelTimeFromPhysSim)));

        completableFutures.add(CompletableFuture.runAsync(() -> linkSpeedDistributionStatsGraph.notifyIterationEnds(iterationNumber, travelTimeFromPhysSim)));

        completableFutures.add(CompletableFuture.runAsync(() -> physsimNetworkLinkLengthDistribution.notifyIterationEnds(iterationNumber)));

        completableFutures.add(CompletableFuture.runAsync(() -> physsimNetworkEuclideanVsLengthAttribute.notifyIterationEnds(iterationNumber)));

        writeIterationCsv(iterationNumber);

        if (iterationNumber == lastIteration) {
            try {
                CompletableFuture allOfLinStatFutures = CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]));
                log.info("Waiting started on link stats file dump.");
                allOfLinStatFutures.get(20, TimeUnit.MINUTES);
                log.info("Link stats file dump completed.");

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("Error while generating link stats.", e);
            }
        }
        traversalEventsForPhysSimulation.clear();
    }

    private VolumesAnalyzer dummyVolumesAnalyzer() {
        return new VolumesAnalyzer(3600, 120 * 3600, agentSimScenario.getNetwork()) {
            final double[] dummyVolumeArray = IntStream.range(0, 121).mapToDouble(x -> 1.0).toArray();

            @Override
            public double[] getVolumesPerHourForLink(Id<Link> linkId) {
                return dummyVolumeArray;
            }
        };
    }

    private void writeTravelTime(TravelTime travelTimeForR5, VolumesAnalyzer volumesAnalyzer, IterationEndsEvent iterationEndsEvent) {
        TravelTimeCalculatorConfigGroup cfg = new TravelTimeCalculatorConfigGroup();
        int endTimeInSeconds = (int) Time.parseTime(beamConfig.beam().agentsim().endTime());
        cfg.setMaxTime(endTimeInSeconds);
        Network network = agentSimScenario.getNetwork();
        BeamCalcLinkStats linkStats = new BeamCalcLinkStats(network, cfg);
        linkStats.addData(volumesAnalyzer, travelTimeForR5);
        linkStats.writeFile(controlerIO.getIterationFilename(iterationEndsEvent.getIteration(), "linkstats.csv.gz"));
    }

    private boolean shouldWritePlans(int iterationNumber) {
        return shouldWriteInIteration(iterationNumber, beamConfig.beam().physsim().writePlansInterval());
    }

    private boolean shouldWriteInIteration(int iterationNumber, int interval) {
        return interval == 1 || (interval > 0 && iterationNumber % interval == 0);
    }

    private void writePhyssimPlans(IterationEndsEvent event) {
        if (shouldWritePlans(event.getIteration())) {
            final String plansFilename = controlerIO.getIterationFilename(event.getIteration(), "physsimPlans.xml.gz");
            completableFutures.add(CompletableFuture.runAsync(() -> new PopulationWriter(jdeqsimPopulation).write(plansFilename)));
        }
    }

    public static boolean isPhyssimMode(String mode) {
        return mode.equalsIgnoreCase(CAR) || mode.equalsIgnoreCase(BUS);
    }

    private boolean isCarMode(String mode) {
        return mode.equalsIgnoreCase(CAR);
    }

    @Override
    public void handleEvent(Event event) {
        if (agentSimPhysSimInterfaceDebuggerEnabled) {
            agentSimPhysSimInterfaceDebugger.handleEvent(event);
        }

        if (event instanceof PathTraversalEvent) {
            handlePathTraversalEvent((PathTraversalEvent) event);
        }
    }

    private void handlePathTraversalEvent(PathTraversalEvent pte) {
        String mode = pte.mode().value();

        if (isCarMode(mode)) {
            double departureTime = pte.departureTime();
            double travelTime = pte.arrivalTime() - departureTime;

            if (travelTime > 0.0) {
                double speed = pte.legLength() / travelTime;
                int bin = (int) departureTime / beamConfig.beam().physsim().linkStatsBinSize();
                Mean mean = binSpeed.getOrDefault(bin, new Mean());
                mean.increment(speed);
            }
        }
        // pt sampling
        // TODO: if requested, add beam.physsim.ptSamplingMode (pathTraversal | busLine), which controls if instead of filtering outWriter
        // pathTraversal, a busLine should be filtered out, avoiding jumping buses in visualization (but making traffic flows less precise).

        if (mode.equalsIgnoreCase(BUS) && rand.nextDouble() > beamConfig.beam().physsim().ptSampleSize()) {
            return;
        }
        if (isPhyssimMode(mode)) {
            traversalEventsForPhysSimulation.add(pte);

            String driverId = pte.driverId();
            String vehicleId = pte.vehicleId().toString();

            String vehicleType = pte.vehicleType();
            Id<BeamVehicleType> beamVehicleTypeId = Id.create(vehicleType, BeamVehicleType.class);
            boolean isCaccEnabled = beamServices.beamScenario().vehicleTypes().get(beamVehicleTypeId).get().isCaccEnabled();
            caccVehiclesMap.put(vehicleId, isCaccEnabled);

            addPTEtoPhysSimPlans(pte, vehicleId, driverId, 0);

            double fractionOfEvents = beamConfig.beam().physsim().duplicatePTE().fractionOfEventsToDuplicate();
            if (mode.equalsIgnoreCase(CAR)) {
                long numberOfDuplicates = MathUtils.roundUniformly(fractionOfEvents);
                for (int i = 0; i < numberOfDuplicates; i++) {
                    int departureTimeShift = getDepartureTimeShift();
                    String clonedVehicleId = vehicleId + "_clone" + i;
                    String clonedDriverId = driverId + "_clone" + i;
                    addPTEtoPhysSimPlans(pte, clonedVehicleId, clonedDriverId, departureTimeShift);
                }
            }
        }
    }

    private int getDepartureTimeShift() {
        int minShift = Math.min(beamConfig.beam().physsim().duplicatePTE().departureTimeShiftMin(),
                beamConfig.beam().physsim().duplicatePTE().departureTimeShiftMax());
        int maxShift = Math.max(beamConfig.beam().physsim().duplicatePTE().departureTimeShiftMin(),
                beamConfig.beam().physsim().duplicatePTE().departureTimeShiftMax());
        int shiftSize = Math.abs(maxShift - minShift);

        return minShift + rand.nextInt(shiftSize);
    }

    private void addPTEtoPhysSimPlans(PathTraversalEvent pte, String vehicleId, String driverId, Integer departureTimeShift) {
        // For every PathTraversalEvent which has PhysSim mode (CAR or BUS) we create
        // - If person does not exist, we create Person from `vehicleId`. For that person we create plan, set it to selected plan and add attributes from the original person
        // - Create leg
        // - Create dummy activity
        final Person person = initializePersonAndPlanIfNeeded(Id.createPersonId(vehicleId), Id.createPersonId(driverId));
        final Plan plan = person.getSelectedPlan();
        final Leg leg = createLeg(pte, departureTimeShift);

        if (leg == null) {
            return;
        }

        Activity previousActivity = jdeqsimPopulation.getFactory().createActivityFromLinkId(DUMMY_ACTIVITY, leg.getRoute().getStartLinkId());
        // math max in case of negative departure time shift
        previousActivity.setEndTime(Math.max(0, pte.departureTime() + departureTimeShift));
        plan.addActivity(previousActivity);
        plan.addLeg(leg);
    }

    private void writeIterationCsv(int iteration) {
        String path = controlerIO.getIterationFilename(iteration, "agentSimAverageSpeed.csv");

        List<String> rows = binSpeed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> (entry.getKey() + 1) + "," + entry.getValue().getResult())
                .collect(Collectors.toList());

        FileUtils.writeToFile(path, Option.apply("timeBin,averageSpeed"), StringUtils.join(rows, "\n"), Option.empty());
        binSpeed.clear();
    }

    private Person initializePersonAndPlanIfNeeded(Id<Person> vehicleId, Id<Person> driverId) {
        // Beam in PhysSim part (JDEQSim) simulates vehicles, not people!
        // So, we have to create _person_ who actually is vehicle.
        final Person alreadyInitedPerson = jdeqsimPopulation.getPersons().get(vehicleId);
        if (alreadyInitedPerson == null) {
            Person person = jdeqsimPopulation.getFactory().createPerson(vehicleId);
            Plan plan = jdeqsimPopulation.getFactory().createPlan();
            plan.setPerson(person);
            person.addPlan(plan);
            person.setSelectedPlan(plan);
            jdeqsimPopulation.addPerson(person);
            final Person originalPerson = agentSimScenario.getPopulation().getPersons().get(driverId);
            final Person personToCopyFrom = originalPerson == null ? agentSimScenario.getPopulation().getPersons().get(vehicleId) : originalPerson;
            // Try to copy person's attributes from original `agentSimScenario` to the created one. Attributes are important because they are used during R5 routing
            if (personToCopyFrom != null) {
                try {
                    Attributes attributes = personToCopyFrom.getAttributes();
                    Stream<String> keys = Arrays.stream(attributes.toString().split("\\{ key=")).filter(x -> x.contains(";")).map(z -> z.split(";")[0]);
                    keys.forEach(key -> person.getAttributes().putAttribute(key, attributes.getAttribute(key)));
                    final Household hh = personToHouseHold.get(personToCopyFrom.getId());
                    final AttributesOfIndividual attributesOfIndividual = PopulationAdjustment$.MODULE$.createAttributesOfIndividual(beamServices.beamScenario(), beamServices.matsimServices().getScenario().getPopulation(), personToCopyFrom, hh);
                    person.getCustomAttributes().put(PopulationAdjustment.BEAM_ATTRIBUTES(), attributesOfIndividual);
                } catch (Exception ex) {
                    log.error("Could not create attributes for person " + vehicleId, ex);
                }
            }
            return person;
        } else {
            return alreadyInitedPerson;
        }
    }

    private Leg createLeg(PathTraversalEvent pte, Integer departureTimeShift) {
        List<Id<Link>> linkIds = new ArrayList<>();

        for (Object linkObjId : pte.linkIdsJava()) {
            Id<Link> linkId = Id.createLinkId(linkObjId.toString());
            linkIds.add(linkId);
        }

        Map<Id<Link>, ? extends Link> networkLinks = agentSimScenario.getNetwork().getLinks();
        for (Id<Link> linkId : linkIds) {
            if (!networkLinks.containsKey(linkId)) {
                throw new RuntimeException("Link not found: " + linkId);
            }
        }

        if (linkIds.size() == 0) {
            return null;
        }
        // end of hack

        Route route = RouteUtils.createNetworkRoute(linkIds, agentSimScenario.getNetwork());
        //Removing first and last link
        linkIds.removeAll(Lists.newArrayList(route.getStartLinkId(), route.getEndLinkId()));
        double length = linkIds.stream().mapToDouble(linkId -> networkLinks.get(linkId).getLength()).sum();
        route.setDistance(length);

        Leg leg = jdeqsimPopulation.getFactory().createLeg(CAR);
        leg.setDepartureTime(pte.departureTime());
        leg.setTravelTime(0);
        leg.setRoute(route);
        leg.getAttributes().putAttribute("travel_time", pte.arrivalTime() - pte.departureTime());
        // math.max in case of negative departureTimeShift
        leg.getAttributes().putAttribute("departure_time", Math.max(0, pte.departureTime() + departureTimeShift));
        leg.getAttributes().putAttribute("event_time", Math.max(0, pte.time() + departureTimeShift));
        return leg;
    }

    public void startPhysSim(IterationEndsEvent iterationEndsEvent, TravelTime initialTravelTime) {
        aggregatedTravelTime = initialTravelTime;
        if (initialTravelTime != null) {
            prevTravelTime = initialTravelTime;
        }
        createLastActivityOfDayForPopulation();
        writePhyssimPlans(iterationEndsEvent);
        long start = System.currentTimeMillis();
        setupActorsAndRunPhysSim(iterationEndsEvent);
        log.info("PhysSim for iteration {} took {} ms", iterationEndsEvent.getIteration(), System.currentTimeMillis() - start);
        preparePhysSimForNewIteration();
    }

    private void createLastActivityOfDayForPopulation() {
        for (Person p : jdeqsimPopulation.getPersons().values()) {
            Plan plan = p.getSelectedPlan();
            if (!plan.getPlanElements().isEmpty()) {
                Leg leg = (Leg) plan.getPlanElements().get(plan.getPlanElements().size() - 1);
                plan.addActivity(jdeqsimPopulation.getFactory().createActivityFromLinkId(DUMMY_ACTIVITY, leg.getRoute().getEndLinkId()));
            }
        }
    }

    @Override
    public Map<String, Double> getSummaryStats() {
        return new HashMap<>();
    }

    public Map<String, double[]> processTravelTime(Collection<? extends Link> links, Map<String, double[]> currentTravelTimeMap, int maxHour) {
        int binSize = beamConfig.beam().agentsim().timeBinSize();
        TravelTime currentTravelTime = TravelTimeCalculatorHelper.CreateTravelTimeCalculator(binSize, currentTravelTimeMap);

        if (aggregatedTravelTime == null) {
            aggregatedTravelTime = currentTravelTime;
            return currentTravelTimeMap;
        } else {
            Map<String, double[]> map = TravelTimeCalculatorHelper.GetLinkIdToTravelTimeAvgArray(links, currentTravelTime, aggregatedTravelTime, maxHour);
            aggregatedTravelTime = TravelTimeCalculatorHelper.CreateTravelTimeCalculator(binSize, map);
            return map;
        }
    }

    @Override
    public void update(BeamConfigChangesObservable observable, BeamConfig updatedBeamConfig) {
        this.beamConfig = updatedBeamConfig;
    }
}