package beam.utils

import com.typesafe.scalalogging.LazyLogging
import com.vividsolutions.jts.geom.{Coordinate, Envelope, GeometryFactory, LineString}
import org.geotools.feature.simple.{SimpleFeatureBuilder, SimpleFeatureTypeBuilder}
import org.matsim.api.core.v01.network.{Link, Node}
import org.matsim.core.network.NetworkUtils
import org.matsim.core.network.io.NetworkReaderMatsimV2
import org.matsim.core.utils.geometry.geotools.MGC
import org.matsim.core.utils.gis.ShapeFileWriter
import org.opengis.feature.simple.SimpleFeature
import org.opengis.referencing.crs.CoordinateReferenceSystem

import scala.collection.JavaConverters._
import scala.util.Try;

object Network2ShapeFile extends LazyLogging {

  case class NetworkLink(
    fromNode: Node,
    toNode: Node,
    id: String,
    modes: String,
    origId: String,
    roadType: String,
    length: Double,
    freeSpeed: Double,
    capacity: Double,
    lanes: Double
  ) {

    def vividCoordTo: Coordinate = new Coordinate(toNode.getCoord.getX, toNode.getCoord.getY)
    def vividCoordFrom: Coordinate = new Coordinate(fromNode.getCoord.getX, fromNode.getCoord.getY)

    def toFeature(
      geometryFactory: GeometryFactory,
      featureBuilder: SimpleFeatureBuilder
    ): Option[SimpleFeature] = {
      val nodeCoordinates = Seq(fromNode, toNode)
        .map(node => new Coordinate(node.getCoord.getX, node.getCoord.getY))
        .toArray
      val lineString: LineString = geometryFactory.createLineString(nodeCoordinates)

      val attributes = lineString +: attributesArray

      try {
        Some(featureBuilder.buildFeature(null, attributes))
      } catch {
        case exception: Throwable =>
          logger.error(s"Can not create simple feature from Link. Exception: $exception")
          None
      }
    }

    // the order should match fields order in NetworkLink.nameToType
    private def attributesArray: Array[Object] = {
      Array(
        id,
        fromNode.getId.toString,
        toNode.getId.toString,
        modes,
        origId,
        roadType,
        length,
        freeSpeed,
        capacity,
        lanes
      ).map(_.asInstanceOf[Object])
    }
  }

  object NetworkLink {

    // the order should match values order in NetworkLink.attributesArray
    val nameToType = IndexedSeq(
      "ID"        -> classOf[java.lang.String],
      "fromID"    -> classOf[java.lang.String],
      "toID"      -> classOf[java.lang.String],
      "modes"     -> classOf[java.lang.String],
      "origid"    -> classOf[java.lang.String],
      "roadType"  -> classOf[java.lang.String],
      "length"    -> classOf[java.lang.Double],
      "freespeed" -> classOf[java.lang.Double],
      "capacity"  -> classOf[java.lang.Double],
      "lanes"     -> classOf[java.lang.Double]
    )

    def apply(link: Link): NetworkLink = {
      val linkType = NetworkUtils.getType(link)
      val modes: String = Try(link.getAllowedModes.asScala.mkString(",")) getOrElse ""
      val origId: String = Try(link.getAttributes.getAttribute("origid").toString) getOrElse ""

      NetworkLink(
        fromNode = link.getFromNode,
        toNode = link.getToNode,
        id = link.getId.toString,
        modes = modes,
        origId = origId,
        roadType = linkType,
        length = link.getLength,
        freeSpeed = link.getFreespeed,
        capacity = link.getCapacity,
        lanes = link.getNumberOfLanes
      )
    }
  }

  private def createFeatureBuilder(crs: CoordinateReferenceSystem): SimpleFeatureBuilder = {
    val typeBuilder = new SimpleFeatureTypeBuilder()
    typeBuilder.setName("link")
    typeBuilder.setCRS(crs)

    typeBuilder.add("the_geom", classOf[LineString])
    NetworkLink.nameToType.foreach {
      case (name, memberType) =>
        typeBuilder.add(name, memberType)
    }

    new SimpleFeatureBuilder(typeBuilder.buildFeatureType())
  }

  def networkToShapeFile(
    matsimNetworkPath: String,
    outputShapeFilePath: String,
    crs: CoordinateReferenceSystem,
    networkFilter: NetworkLink => Boolean
  ): Unit = {
    val network = NetworkUtils.createNetwork()
    val reader = new NetworkReaderMatsimV2(network)
    reader.readFile(matsimNetworkPath)
    logger.info(s"Read $matsimNetworkPath");

    val featureBuilder = createFeatureBuilder(crs)
    val geometryFactory = new GeometryFactory()
    val networkLinks = NetworkUtils.getSortedLinks(network).map(link => NetworkLink(link))
    logger.info(s"Read ${networkLinks.length} network links from network file")

    val features = networkLinks
      .filter(networkFilter)
      .flatMap(_.toFeature(geometryFactory, featureBuilder))
    logger.info(s"Got ${features.length} features to write to shape file")

    logger.info("Writing features to shape file $outputShapeFilePath ...");
    ShapeFileWriter.writeGeometries(features.toSeq.asJava, outputShapeFilePath);
    logger.info("Done");
  }

  def main(args: Array[String]): Unit = {
    val matsimNetworkPath = "/mnt/data/work/beam/beam/test/input/sf-light/r5/physsim-network.xml"
    val outputShapeFilePath = "/mnt/data/work/beam/beam/test/input/sf-light/r5/output-physsim-network.shp"
    val crsString = "epsg:26910"
    val crs = MGC.getCRS(crsString)

    val envelope = new Envelope(546253.221, 551299.898, 4180795.862, 4176718.516)
    def filter(networkLink: NetworkLink): Boolean = {
      envelope.contains(networkLink.vividCoordTo) || envelope.contains(networkLink.vividCoordFrom)
    }

    networkToShapeFile(matsimNetworkPath, outputShapeFilePath, crs, filter)
  }
}
