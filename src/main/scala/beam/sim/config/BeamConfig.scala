// generated by tscfg 0.8.1 on Sun Aug 27 19:05:38 PDT 2017
// source: src/main/resources/beam-template.conf

package beam.sim.config

case class BeamConfig(
  akka              : BeamConfig.Akka,
  beam              : BeamConfig.Beam,
  matsim            : BeamConfig.Matsim,
  my_custom_mailbox : BeamConfig.MyCustomMailbox
)
object BeamConfig {
  case class Akka(
    actor    : BeamConfig.Akka.Actor,
    loglevel : java.lang.String,
    remote   : BeamConfig.Akka.Remote
  )
  object Akka {
    case class Actor(
      debug : BeamConfig.Akka.Actor.Debug
    )
    object Actor {
      case class Debug(
        unhandled : java.lang.String
      )
      object Debug {
        def apply(c: com.typesafe.config.Config): BeamConfig.Akka.Actor.Debug = {
          BeamConfig.Akka.Actor.Debug(
            unhandled = if(c.hasPathOrNull("unhandled")) c.getString("unhandled") else "on"
          )
        }
      }
            
      def apply(c: com.typesafe.config.Config): BeamConfig.Akka.Actor = {
        BeamConfig.Akka.Actor(
          debug = BeamConfig.Akka.Actor.Debug(c.getConfig("debug"))
        )
      }
    }
          
    case class Remote(
      log_sent_messages : java.lang.String
    )
    object Remote {
      def apply(c: com.typesafe.config.Config): BeamConfig.Akka.Remote = {
        BeamConfig.Akka.Remote(
          log_sent_messages = if(c.hasPathOrNull("log-sent-messages")) c.getString("log-sent-messages") else "on"
        )
      }
    }
          
    def apply(c: com.typesafe.config.Config): BeamConfig.Akka = {
      BeamConfig.Akka(
        actor    = BeamConfig.Akka.Actor(c.getConfig("actor")),
        loglevel = if(c.hasPathOrNull("loglevel")) c.getString("loglevel") else "INFO",
        remote   = BeamConfig.Akka.Remote(c.getConfig("remote"))
      )
    }
  }
        
  case class Beam(
    agentsim     : BeamConfig.Beam.Agentsim,
    basePackage  : java.lang.String,
    events       : BeamConfig.Beam.Events,
    outputs      : BeamConfig.Beam.Outputs,
    rideHailing  : BeamConfig.Beam.RideHailing,
    routing      : BeamConfig.Beam.Routing,
    sharedInputs : java.lang.String
  )
  object Beam {
    case class Agentsim(
      debugEnabled   : scala.Int,
      numAgents      : scala.Int,
      simulationName : java.lang.String
    )
    object Agentsim {
      def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Agentsim = {
        BeamConfig.Beam.Agentsim(
          debugEnabled   = if(c.hasPathOrNull("debugEnabled")) c.getInt("debugEnabled") else 0,
          numAgents      = if(c.hasPathOrNull("numAgents")) c.getInt("numAgents") else 100,
          simulationName = if(c.hasPathOrNull("simulationName")) c.getString("simulationName") else "dev"
        )
      }
    }
          
    case class Events(
      filterDist          : scala.Int,
      pathTraversalEvents : scala.List[java.lang.String]
    )
    object Events {
      def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Events = {
        BeamConfig.Beam.Events(
          filterDist          = if(c.hasPathOrNull("filterDist")) c.getInt("filterDist") else 10000,
          pathTraversalEvents = $_L$_str(c.getList("pathTraversalEvents"))
        )
      }
    }
          
    case class Outputs(
      defaultLoggingLevel     : scala.Int,
      eventsFileOutputFormats : java.lang.String,
      explodeEventsIntoFiles  : scala.Boolean,
      outputDirectory         : java.lang.String,
      overrideLoggingLevels   : scala.List[BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm],
      writeEventsInterval     : scala.Int,
      writePlansInterval      : scala.Int
    )
    object Outputs {
      case class OverrideLoggingLevels$Elm(
        classname : java.lang.String,
        value     : scala.Int
      )
      object OverrideLoggingLevels$Elm {
        def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm = {
          BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm(
            classname = if(c.hasPathOrNull("classname")) c.getString("classname") else "beam.playground.metasim.events.ActionEvent",
            value     = if(c.hasPathOrNull("value")) c.getInt("value") else 1
          )
        }
      }
            
      def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Outputs = {
        BeamConfig.Beam.Outputs(
          defaultLoggingLevel     = if(c.hasPathOrNull("defaultLoggingLevel")) c.getInt("defaultLoggingLevel") else 1,
          eventsFileOutputFormats = if(c.hasPathOrNull("eventsFileOutputFormats")) c.getString("eventsFileOutputFormats") else "csv",
          explodeEventsIntoFiles  = c.hasPathOrNull("explodeEventsIntoFiles") && c.getBoolean("explodeEventsIntoFiles"),
          outputDirectory         = if(c.hasPathOrNull("outputDirectory")) c.getString("outputDirectory") else "/Users/critter/Documents/beam/beam-output/",
          overrideLoggingLevels   = $_LBeamConfig_Beam_Outputs_OverrideLoggingLevels$Elm(c.getList("overrideLoggingLevels")),
          writeEventsInterval     = if(c.hasPathOrNull("writeEventsInterval")) c.getInt("writeEventsInterval") else 1,
          writePlansInterval      = if(c.hasPathOrNull("writePlansInterval")) c.getInt("writePlansInterval") else 0
        )
      }
      private def $_LBeamConfig_Beam_Outputs_OverrideLoggingLevels$Elm(cl:com.typesafe.config.ConfigList): scala.List[BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm] = {
        import scala.collection.JavaConverters._  
        cl.asScala.map(cv => BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm(cv.asInstanceOf[com.typesafe.config.ConfigObject].toConfig)).toList
      }
    }
          
    case class RideHailing(
      defaultCostPerMile   : scala.Double,
      defaultCostPerMinute : scala.Double
    )
    object RideHailing {
      def apply(c: com.typesafe.config.Config): BeamConfig.Beam.RideHailing = {
        BeamConfig.Beam.RideHailing(
          defaultCostPerMile   = if(c.hasPathOrNull("defaultCostPerMile")) c.getDouble("defaultCostPerMile") else 1.25,
          defaultCostPerMinute = if(c.hasPathOrNull("defaultCostPerMinute")) c.getDouble("defaultCostPerMinute") else 0.75
        )
      }
    }
          
    case class Routing(
      baseDate    : java.lang.String,
      gtfs        : BeamConfig.Beam.Routing.Gtfs,
      r5          : BeamConfig.Beam.Routing.R5,
      routerClass : java.lang.String
    )
    object Routing {
      case class Gtfs(
        crs           : java.lang.String,
        operatorsFile : java.lang.String,
        outputDir     : java.lang.String
      )
      object Gtfs {
        def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Routing.Gtfs = {
          BeamConfig.Beam.Routing.Gtfs(
            crs           = if(c.hasPathOrNull("crs")) c.getString("crs") else "epsg:26910",
            operatorsFile = if(c.hasPathOrNull("operatorsFile")) c.getString("operatorsFile") else "src/main/resources/GTFSOperators.csv",
            outputDir     = if(c.hasPathOrNull("outputDir")) c.getString("outputDir") else "/Users/critter/Documents/beam/beam-output//gtfs"
          )
        }
      }
            
      case class R5(
        departureWindow : scala.Int,
        directory       : java.lang.String
      )
      object R5 {
        def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Routing.R5 = {
          BeamConfig.Beam.Routing.R5(
            departureWindow = if(c.hasPathOrNull("departureWindow")) c.getInt("departureWindow") else 15,
            directory       = if(c.hasPathOrNull("directory")) c.getString("directory") else "/Users/critter/Dropbox/ucb/vto/beam-developers//model-inputs/r5"
          )
        }
      }
            
      def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Routing = {
        BeamConfig.Beam.Routing(
          baseDate    = if(c.hasPathOrNull("baseDate")) c.getString("baseDate") else "2016-10-17T00:00:00-07:00",
          gtfs        = BeamConfig.Beam.Routing.Gtfs(c.getConfig("gtfs")),
          r5          = BeamConfig.Beam.Routing.R5(c.getConfig("r5")),
          routerClass = if(c.hasPathOrNull("routerClass")) c.getString("routerClass") else "beam.router.r5.R5RoutingWorker"
        )
      }
    }
          
    def apply(c: com.typesafe.config.Config): BeamConfig.Beam = {
      BeamConfig.Beam(
        agentsim     = BeamConfig.Beam.Agentsim(c.getConfig("agentsim")),
        basePackage  = if(c.hasPathOrNull("basePackage")) c.getString("basePackage") else "beam",
        events       = BeamConfig.Beam.Events(c.getConfig("events")),
        outputs      = BeamConfig.Beam.Outputs(c.getConfig("outputs")),
        rideHailing  = BeamConfig.Beam.RideHailing(c.getConfig("rideHailing")),
        routing      = BeamConfig.Beam.Routing(c.getConfig("routing")),
        sharedInputs = if(c.hasPathOrNull("sharedInputs")) c.getString("sharedInputs") else "/Users/critter/Dropbox/ucb/vto/beam-developers/"
      )
    }
  }
        
  case class Matsim(
    modules : BeamConfig.Matsim.Modules
  )
  object Matsim {
    case class Modules(
      changeMode            : BeamConfig.Matsim.Modules.ChangeMode,
      controler             : BeamConfig.Matsim.Modules.Controler,
      global                : BeamConfig.Matsim.Modules.Global,
      network               : BeamConfig.Matsim.Modules.Network,
      parallelEventHandling : BeamConfig.Matsim.Modules.ParallelEventHandling,
      planCalcScore         : BeamConfig.Matsim.Modules.PlanCalcScore,
      plans                 : BeamConfig.Matsim.Modules.Plans,
      qsim                  : BeamConfig.Matsim.Modules.Qsim,
      strategy              : BeamConfig.Matsim.Modules.Strategy,
      transit               : BeamConfig.Matsim.Modules.Transit
    )
    object Modules {
      case class ChangeMode(
        modes : java.lang.String
      )
      object ChangeMode {
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.ChangeMode = {
          BeamConfig.Matsim.Modules.ChangeMode(
            modes = if(c.hasPathOrNull("modes")) c.getString("modes") else "car,pt"
          )
        }
      }
            
      case class Controler(
        eventsFileFormat : java.lang.String,
        firstIteration   : scala.Int,
        lastIteration    : scala.Int,
        mobsim           : java.lang.String,
        outputDirectory  : java.lang.String
      )
      object Controler {
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.Controler = {
          BeamConfig.Matsim.Modules.Controler(
            eventsFileFormat = if(c.hasPathOrNull("eventsFileFormat")) c.getString("eventsFileFormat") else "xml",
            firstIteration   = if(c.hasPathOrNull("firstIteration")) c.getInt("firstIteration") else 0,
            lastIteration    = if(c.hasPathOrNull("lastIteration")) c.getInt("lastIteration") else 0,
            mobsim           = if(c.hasPathOrNull("mobsim")) c.getString("mobsim") else "metasim",
            outputDirectory  = if(c.hasPathOrNull("outputDirectory")) c.getString("outputDirectory") else "/Users/critter/Documents/beam/beam-output//pt-tutorial"
          )
        }
      }
            
      case class Global(
        coordinateSystem : java.lang.String,
        randomSeed       : scala.Int
      )
      object Global {
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.Global = {
          BeamConfig.Matsim.Modules.Global(
            coordinateSystem = if(c.hasPathOrNull("coordinateSystem")) c.getString("coordinateSystem") else "Atlantis",
            randomSeed       = if(c.hasPathOrNull("randomSeed")) c.getInt("randomSeed") else 4711
          )
        }
      }
            
      case class Network(
        inputNetworkFile : java.lang.String
      )
      object Network {
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.Network = {
          BeamConfig.Matsim.Modules.Network(
            inputNetworkFile = if(c.hasPathOrNull("inputNetworkFile")) c.getString("inputNetworkFile") else "/Users/critter/Dropbox/ucb/vto/beam-developers//model-inputs/dev/multimodalnetwork.xml"
          )
        }
      }
            
      case class ParallelEventHandling(
        estimatedNumberOfEvents : scala.Int,
        numberOfThreads         : scala.Int,
        oneThreadPerHandler     : scala.Boolean,
        synchronizeOnSimSteps   : scala.Boolean
      )
      object ParallelEventHandling {
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.ParallelEventHandling = {
          BeamConfig.Matsim.Modules.ParallelEventHandling(
            estimatedNumberOfEvents = if(c.hasPathOrNull("estimatedNumberOfEvents")) c.getInt("estimatedNumberOfEvents") else 1000000000,
            numberOfThreads         = if(c.hasPathOrNull("numberOfThreads")) c.getInt("numberOfThreads") else 1,
            oneThreadPerHandler     = c.hasPathOrNull("oneThreadPerHandler") && c.getBoolean("oneThreadPerHandler"),
            synchronizeOnSimSteps   = c.hasPathOrNull("synchronizeOnSimSteps") && c.getBoolean("synchronizeOnSimSteps")
          )
        }
      }
            
      case class PlanCalcScore(
        BrainExpBeta   : scala.Long,
        earlyDeparture : scala.Long,
        lateArrival    : scala.Long,
        learningRate   : scala.Long,
        parameterset   : scala.List[BeamConfig.Matsim.Modules.PlanCalcScore.Parameterset$Elm],
        performing     : scala.Long,
        traveling      : scala.Long,
        waiting        : scala.Long
      )
      object PlanCalcScore {
        case class Parameterset$Elm(
          activityType                    : java.lang.String,
          priority                        : scala.Int,
          scoringThisActivityAtAll        : scala.Boolean,
          `type`                          : java.lang.String,
          typicalDuration                 : java.lang.String,
          typicalDurationScoreComputation : java.lang.String
        )
        object Parameterset$Elm {
          def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.PlanCalcScore.Parameterset$Elm = {
            BeamConfig.Matsim.Modules.PlanCalcScore.Parameterset$Elm(
              activityType                    = if(c.hasPathOrNull("activityType")) c.getString("activityType") else "Home",
              priority                        = if(c.hasPathOrNull("priority")) c.getInt("priority") else 1,
              scoringThisActivityAtAll        = !c.hasPathOrNull("scoringThisActivityAtAll") || c.getBoolean("scoringThisActivityAtAll"),
              `type`                          = if(c.hasPathOrNull("type")) c.getString("type") else "activityParams",
              typicalDuration                 = if(c.hasPathOrNull("typicalDuration")) c.getString("typicalDuration") else "01:00:00",
              typicalDurationScoreComputation = if(c.hasPathOrNull("typicalDurationScoreComputation")) c.getString("typicalDurationScoreComputation") else "uniform"
            )
          }
        }
              
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.PlanCalcScore = {
          BeamConfig.Matsim.Modules.PlanCalcScore(
            BrainExpBeta   = if(c.hasPathOrNull("BrainExpBeta")) c.getDuration("BrainExpBeta", java.util.concurrent.TimeUnit.MILLISECONDS) else 2,
            earlyDeparture = if(c.hasPathOrNull("earlyDeparture")) c.getDuration("earlyDeparture", java.util.concurrent.TimeUnit.MILLISECONDS) else 0,
            lateArrival    = if(c.hasPathOrNull("lateArrival")) c.getDuration("lateArrival", java.util.concurrent.TimeUnit.MILLISECONDS) else -18,
            learningRate   = if(c.hasPathOrNull("learningRate")) c.getDuration("learningRate", java.util.concurrent.TimeUnit.MILLISECONDS) else 1,
            parameterset   = $_LBeamConfig_Matsim_Modules_PlanCalcScore_Parameterset$Elm(c.getList("parameterset")),
            performing     = if(c.hasPathOrNull("performing")) c.getDuration("performing", java.util.concurrent.TimeUnit.MILLISECONDS) else 6,
            traveling      = if(c.hasPathOrNull("traveling")) c.getDuration("traveling", java.util.concurrent.TimeUnit.MILLISECONDS) else -6,
            waiting        = if(c.hasPathOrNull("waiting")) c.getDuration("waiting", java.util.concurrent.TimeUnit.MILLISECONDS) else 0
          )
        }
        private def $_LBeamConfig_Matsim_Modules_PlanCalcScore_Parameterset$Elm(cl:com.typesafe.config.ConfigList): scala.List[BeamConfig.Matsim.Modules.PlanCalcScore.Parameterset$Elm] = {
          import scala.collection.JavaConverters._  
          cl.asScala.map(cv => BeamConfig.Matsim.Modules.PlanCalcScore.Parameterset$Elm(cv.asInstanceOf[com.typesafe.config.ConfigObject].toConfig)).toList
        }
      }
            
      case class Plans(
        inputPlansFile : java.lang.String
      )
      object Plans {
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.Plans = {
          BeamConfig.Matsim.Modules.Plans(
            inputPlansFile = if(c.hasPathOrNull("inputPlansFile")) c.getString("inputPlansFile") else "/Users/critter/Dropbox/ucb/vto/beam-developers//model-inputs/dev/population-500.xml"
          )
        }
      }
            
      case class Qsim(
        endTime        : java.lang.String,
        snapshotperiod : java.lang.String,
        startTime      : java.lang.String
      )
      object Qsim {
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.Qsim = {
          BeamConfig.Matsim.Modules.Qsim(
            endTime        = if(c.hasPathOrNull("endTime")) c.getString("endTime") else "30:00:00",
            snapshotperiod = if(c.hasPathOrNull("snapshotperiod")) c.getString("snapshotperiod") else "00:00:00",
            startTime      = if(c.hasPathOrNull("startTime")) c.getString("startTime") else "00:00:00"
          )
        }
      }
            
      case class Strategy(
        ModuleProbability_1    : scala.Double,
        ModuleProbability_3    : scala.Double,
        Module_1               : java.lang.String,
        Module_3               : java.lang.String,
        maxAgentPlanMemorySize : scala.Int
      )
      object Strategy {
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.Strategy = {
          BeamConfig.Matsim.Modules.Strategy(
            ModuleProbability_1    = if(c.hasPathOrNull("ModuleProbability_1")) c.getDouble("ModuleProbability_1") else 0.7,
            ModuleProbability_3    = if(c.hasPathOrNull("ModuleProbability_3")) c.getDouble("ModuleProbability_3") else 0.1,
            Module_1               = if(c.hasPathOrNull("Module_1")) c.getString("Module_1") else "BestScore",
            Module_3               = if(c.hasPathOrNull("Module_3")) c.getString("Module_3") else "TimeAllocationMutator",
            maxAgentPlanMemorySize = if(c.hasPathOrNull("maxAgentPlanMemorySize")) c.getInt("maxAgentPlanMemorySize") else 5
          )
        }
      }
            
      case class Transit(
        transitModes        : java.lang.String,
        transitScheduleFile : java.lang.String,
        useTransit          : scala.Boolean,
        vehiclesFile        : java.lang.String
      )
      object Transit {
        def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules.Transit = {
          BeamConfig.Matsim.Modules.Transit(
            transitModes        = if(c.hasPathOrNull("transitModes")) c.getString("transitModes") else "pt",
            transitScheduleFile = if(c.hasPathOrNull("transitScheduleFile")) c.getString("transitScheduleFile") else "transitschedule.xml",
            useTransit          = c.hasPathOrNull("useTransit") && c.getBoolean("useTransit"),
            vehiclesFile        = if(c.hasPathOrNull("vehiclesFile")) c.getString("vehiclesFile") else "transitVehicles.xml"
          )
        }
      }
            
      def apply(c: com.typesafe.config.Config): BeamConfig.Matsim.Modules = {
        BeamConfig.Matsim.Modules(
          changeMode            = BeamConfig.Matsim.Modules.ChangeMode(c.getConfig("changeMode")),
          controler             = BeamConfig.Matsim.Modules.Controler(c.getConfig("controler")),
          global                = BeamConfig.Matsim.Modules.Global(c.getConfig("global")),
          network               = BeamConfig.Matsim.Modules.Network(c.getConfig("network")),
          parallelEventHandling = BeamConfig.Matsim.Modules.ParallelEventHandling(c.getConfig("parallelEventHandling")),
          planCalcScore         = BeamConfig.Matsim.Modules.PlanCalcScore(c.getConfig("planCalcScore")),
          plans                 = BeamConfig.Matsim.Modules.Plans(c.getConfig("plans")),
          qsim                  = BeamConfig.Matsim.Modules.Qsim(c.getConfig("qsim")),
          strategy              = BeamConfig.Matsim.Modules.Strategy(c.getConfig("strategy")),
          transit               = BeamConfig.Matsim.Modules.Transit(c.getConfig("transit"))
        )
      }
    }
          
    def apply(c: com.typesafe.config.Config): BeamConfig.Matsim = {
      BeamConfig.Matsim(
        modules = BeamConfig.Matsim.Modules(c.getConfig("modules"))
      )
    }
  }
        
  case class MyCustomMailbox(
    mailbox_type : java.lang.String
  )
  object MyCustomMailbox {
    def apply(c: com.typesafe.config.Config): BeamConfig.MyCustomMailbox = {
      BeamConfig.MyCustomMailbox(
        mailbox_type = if(c.hasPathOrNull("mailbox-type")) c.getString("mailbox-type") else "akka.dispatch.UnboundedDequeBasedMailbox"
      )
    }
  }
        
  def apply(c: com.typesafe.config.Config): BeamConfig = {
    BeamConfig(
      akka              = BeamConfig.Akka(c.getConfig("akka")),
      beam              = BeamConfig.Beam(c.getConfig("beam")),
      matsim            = BeamConfig.Matsim(c.getConfig("matsim")),
      my_custom_mailbox = BeamConfig.MyCustomMailbox(c.getConfig("my-custom-mailbox"))
    )
  }

  private def $_L$_str(cl:com.typesafe.config.ConfigList): scala.List[java.lang.String] = {
    import scala.collection.JavaConverters._  
    cl.asScala.map(cv => $_str(cv)).toList
  }
  private def $_expE(cv:com.typesafe.config.ConfigValue, exp:java.lang.String) = {
    val u: Any = cv.unwrapped
    new java.lang.RuntimeException(cv.origin.lineNumber +
      ": expecting: " + exp + " got: " +
      (if (u.isInstanceOf[java.lang.String]) "\"" + u + "\"" else u))
  }
  private def $_str(cv:com.typesafe.config.ConfigValue) =
    java.lang.String.valueOf(cv.unwrapped())
}
      
