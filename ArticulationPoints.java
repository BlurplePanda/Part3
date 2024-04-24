import java.util.*;

//=============================================================================
//   Finding Articulation Points
//   Finds and returns a collection of all the articulation points in the undirected
//   graph.
//   Uses the algorithm from the lectures, but modified to cope with a not completely
//   connected graph. For a not fully connected graph, an articulation point is one
//   that would break a currently connected component into two or more components
//=============================================================================

public class ArticulationPoints{

    /**
     * Return a collection of all the Stops in the graph that are articulation points.
     */
    public static Collection<Stop> findArticulationPoints(Graph graph) {
        Map<Stop, Integer> depths = new HashMap<>();
        for (Stop stop : graph.getStops()) {
            depths.put(stop, -1);
        }
        Set<Stop> aPoints = new HashSet<>();
        for (Stop stop : graph.getStops()) {
            if (depths.get(stop) == -1) {
                int numSubTrees = 0;
                Stop start = stop;
                depths.put(start, 0);
                for (Stop neighbour : start.getNeighbours()) {
                    if (depths.get(neighbour) == -1) {
                        recArtPts(neighbour, 1, start, aPoints, depths);
                        numSubTrees++;
                    }
                }
                if (numSubTrees > 1) {
                    aPoints.add(start);
                }
            }
        }

        return aPoints;
    }

    private static int recArtPts(Stop node, int depth, Stop fromNode, Set<Stop> aPoints, Map<Stop, Integer> depths) {
        depths.put(node, depth);
        int reachBack = depth;
        for (Stop neighbour : node.getNeighbours()) {
            if (neighbour == fromNode) {
                continue;
            }
            else if (depths.get(neighbour) != -1) {
                reachBack = Math.min(depths.get(neighbour), reachBack);
            }
            else {
                int childReach = recArtPts(neighbour, depth+1, node, aPoints, depths);
                if (childReach >= depth) {
                    aPoints.add(node);
                }
                reachBack = Math.min(childReach, reachBack);

            }
        }
        return reachBack;
    }




}
