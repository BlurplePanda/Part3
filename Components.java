import java.util.*;

//=============================================================================
//   Finding Components
//   Finds all the strongly connected subgraphs in the graph
//   Constructs a Map recording the number of the subgraph for each Stop
//=============================================================================

public class Components{

    // Based on Kosaraju's_algorithm
    // https://en.wikipedia.org/wiki/Kosaraju%27s_algorithm
    // Use a visited set to record which stops have been visited

    
    public static Map<Stop,Integer> findComponents(Graph graph) {
        Map<Stop, Integer> components = new HashMap<>();
        for (Stop stop : graph.getStops()) {
            components.put(stop, -1);
        }
        int componentNum = 0;
        List<Stop> nodeList = new ArrayList<>();
        Set<Stop> visited = new HashSet<>();

        for (Stop stop : graph.getStops()) {
            if (!visited.contains(stop)) {
                forwardVisit(stop, nodeList, visited);
            }
        }

        Collections.reverse(nodeList);
        for (Stop stop : nodeList) {
            if (components.get(stop) == -1) {
                backwardVisit(stop, componentNum, components);
                componentNum++;
            }
        }


        return components;
    }

    /**
     * Search forward from node, putting node on nodeList after visiting everything it can get to.
     * @param stop the stop (node) to start from
     * @param nodeList the list to add to after visiting
     * @param visited the stops that have been visited already
     */
    private static void forwardVisit(Stop stop, List<Stop> nodeList, Set<Stop> visited) {
        if (!visited.contains(stop)) {
            visited.add(stop);
            for (Edge edge : stop.getOutEdges()) {
                Stop neighbour = edge.toStop();
                forwardVisit(neighbour, nodeList, visited);
            }
            nodeList.add(stop);
        }
    }

    private static void backwardVisit(Stop stop, int componentNum, Map<Stop, Integer> components) {
        if (components.get(stop) == -1) {
            components.put(stop, componentNum);
            for (Edge edge : stop.getInEdges()) {
                Stop backNeighbour = edge.fromStop();
                backwardVisit(backNeighbour, componentNum, components);
            }
        }
    }





}
