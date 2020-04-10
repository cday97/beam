package beam.agentsim.infrastructure.geozone

private[geozone] sealed trait Hexagon[+T] {
  def index: GeoIndex
  def totalNumberOfBuckets: Int
  def totalNumberOfCoordinates: Int
  def split(bucketsGoal: Int): Seq[HexagonBranch]
}

private[geozone] case class HexagonBranch(index: GeoIndex, children: IndexedSeq[Hexagon[_]]) extends Hexagon[HexagonBranch] {
  override lazy val totalNumberOfCoordinates: Int = children.map(_.totalNumberOfCoordinates).sum

  override lazy val totalNumberOfBuckets: Int = children.map(_.totalNumberOfBuckets).sum

  def chooseOneToSplit(bucketsGoal: Int): Int = {
    TopDownEqualDemandsSplitter.chooseOneToSplit(children, bucketsGoal, totalNumberOfBuckets)
  }

  override def split(bucketsGoal: Int): Seq[HexagonBranch] = {
    val position = chooseOneToSplit(bucketsGoal)
    val element = children(position)
    element.split(bucketsGoal)
  }

}

private[geozone] case class HexagonLeaf(index: GeoIndex, points: Set[WgsCoordinate]) extends Hexagon[HexagonLeaf] {

  override lazy val totalNumberOfCoordinates: Int = points.size

  override lazy val totalNumberOfBuckets: Int = 1

  override def split(bucketsGoal: Int): Seq[HexagonBranch] = {
    val resultIndex = H3Wrapper.getChildren(index)
    val pointsAndNewIndexes: Map[GeoIndex, Set[WgsCoordinate]] = points.toSeq
      .map { point =>
        val newIndex = H3Wrapper.getIndex(point, index.resolution + 1)
        newIndex -> point
      }
      .groupBy(_._1)
      .mapValues(_.map(_._2).toSet)
    resultIndex.toSeq.map { index =>
      val leaf: HexagonLeaf = HexagonLeaf(index, pointsAndNewIndexes.getOrElse(index, Set.empty))
      HexagonBranch(index, IndexedSeq(leaf))
    }
  }

}
