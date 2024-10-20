package tpAnalyzer;

import org.jgrapht.graph.SimpleGraph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class SpoonClusterHier {

    private Map<String, Map<String, Double>> couplingMatrix;
    private List<Set<String>> clusters;
    private double CP;  // Coupling threshold

    // Constructor to initialize the coupling graph and clusters
    public SpoonClusterHier(String couplingGraphPath, double cpThreshold) throws IOException {
        this.couplingMatrix = readCouplingGraph(couplingGraphPath);
        this.clusters = new ArrayList<>();
        this.CP = cpThreshold;
        initializeClusters();
    }

    // Read the coupling graph from a .dot file
    private Map<String, Map<String, Double>> readCouplingGraph(String path) throws IOException {
        Map<String, Map<String, Double>> matrix = new HashMap<>();

        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("->")) {
                String[] parts = line.split("->");
                String caller = parts[0].replaceAll("\"", "").trim();
                String[] calleeInfo = parts[1].split("\\[label=\"Couplage:");
                String callee = calleeInfo[0].replaceAll("\"", "").trim();
                String couplingValueStr = calleeInfo[1].replace("\"];", "").trim().replace(",", ".");

                // Only process valid classes
                if (isValidClass(caller) && isValidClass(callee)) {
                    try {
                        double couplingValue = Double.parseDouble(couplingValueStr);
                        matrix.putIfAbsent(caller, new HashMap<>());
                        matrix.get(caller).put(callee, couplingValue);
                    } catch (NumberFormatException e) {
                        System.out.println("Error parsing line: " + line);
                    }
                }
            }
        }
        reader.close();
        return matrix;
    }

    // Helper method to check if a class name is valid (not primitive, void, or wildcard)
    private boolean isValidClass(String className) {
        return className.contains(".") && !className.equals("java.lang.Object") &&
               !isPrimitiveOrVoid(className) && !className.equals("?");
    }

    // Check if a type is a primitive or void
    private boolean isPrimitiveOrVoid(String type) {
        return type.equals("int") || type.equals("double") || type.equals("boolean") ||
               type.equals("char") || type.equals("byte") || type.equals("short") ||
               type.equals("long") || type.equals("float") || type.equals("void");
    }

    // Initialize each class as its own separate cluster
    private void initializeClusters() {
        for (String className : couplingMatrix.keySet()) {
            Set<String> singleCluster = new HashSet<>();
            singleCluster.add(className);
            clusters.add(singleCluster);
        }
    }

    // Hierarchical clustering to merge classes until there are only 2 clusters left or no more merges are possible
    public SimpleGraph<String, EmptyEdge> GroupeClasse(SimpleGraph<String, EmptyEdge> graph, float CP, List<String[]> fusionSteps) {
        SimpleGraph<String, EmptyEdge> currentGraph = copygraph(graph);
        SimpleGraph<String, EmptyEdge> resultGraph = copygraph(currentGraph);

        while (resultGraph.vertexSet().size() > 2) {
            String[] clustersProches = ClusterProche(currentGraph);

            if (clustersProches == null) {
                break;
            }

            fusionSteps.add(clustersProches);
            resultGraph = CreateCluster(currentGraph, clustersProches);
        }

        return resultGraph;
    }

    // Fill the graph with coupling data
    public void fillGraph(SimpleGraph<String, EmptyEdge> graph) {
        for (String caller : couplingMatrix.keySet()) {
            graph.addVertex(caller);
            for (String callee : couplingMatrix.get(caller).keySet()) {
                if (!caller.equals(callee)) {
                    graph.addVertex(callee);
                    graph.addEdge(caller, callee);
                }
            }
        }
    }

    // Generate the DOT file with clustering steps
    public void generateDotFileWithSteps(String fileName, List<String[]> fusionSteps, List<Set<String>> initialClusters) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("digraph Clusters {\n");
            writer.write("  node [shape=ellipse];\n");

            // Initial clusters
            writer.write("  // Step 0: Initial Clusters\n");
            for (Set<String> cluster : initialClusters) {
                String clusterName = String.join(":", cluster);
                writer.write("  \"" + clusterName + "\" [label=\"" + clusterName + "\", color=blue];\n");
            }

            // Fusion steps
            int step = 1;
            for (String[] fusion : fusionSteps) {
                String newClusterName = fusion[0] + ":" + fusion[1];
                writer.write("\n  // Step " + step + ": Merge " + fusion[0] + " and " + fusion[1] + "\n");
                writer.write("  \"" + newClusterName + "\" [label=\"" + newClusterName + "\", style=filled, color=lightgrey];\n");
                writer.write("  \"" + fusion[0] + "\" -> \"" + newClusterName + "\" [label=\"Merge\"];\n");
                writer.write("  \"" + fusion[1] + "\" -> \"" + newClusterName + "\" [label=\"Merge\"];\n");
                step++;
            }

            writer.write("}\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Find the most closely related clusters
    private String[] ClusterProche(SimpleGraph<String, EmptyEdge> graph) {
        String[] ret = new String[2];
        double maxCoupling = 0;
        boolean found = false;

        for (String vertex : graph.vertexSet()) {
            for (EmptyEdge edge : graph.edgesOf(vertex)) {
                String target = graph.getEdgeTarget(edge);
                double couplingValue = getCouplingValue(vertex, target);

                if (couplingValue > maxCoupling && !vertex.equals(target)) {
                    maxCoupling = couplingValue;
                    ret[0] = vertex;
                    ret[1] = target;
                    found = true;
                }
            }
        }

        return found ? ret : null;
    }

    // Get the coupling value between two classes
    private double getCouplingValue(String vertex, String target) {
        String[] vertexParts = vertex.split(":");
        String[] targetParts = target.split(":");
        double totalCoupling = 0;
        int count = 0;

        for (String v : vertexParts) {
            for (String t : targetParts) {
                if (couplingMatrix.containsKey(v) && couplingMatrix.get(v).containsKey(t)) {
                    totalCoupling += couplingMatrix.get(v).get(t);
                    count++;
                }
                if (couplingMatrix.containsKey(t) && couplingMatrix.get(t).containsKey(v)) {
                    totalCoupling += couplingMatrix.get(t).get(v);
                    count++;
                }
            }
        }

        return count > 0 ? totalCoupling / count : 0;
    }

    // Create a new cluster by merging two clusters
    public SimpleGraph<String, EmptyEdge> CreateCluster(SimpleGraph<String, EmptyEdge> graph, String[] ex) {
        if (ex == null || !graph.containsVertex(ex[0]) || !graph.containsVertex(ex[1])) {
            return graph;
        }

        graph.addVertex(ex[0] + ":" + ex[1]);

        List<String[]> edgesToAdd = new ArrayList<>();
        List<String[]> edgesToRemove = new ArrayList<>();

        for (EmptyEdge edge : graph.edgesOf(ex[0])) {
            String target = graph.getEdgeTarget(edge);
            if (!target.equals(ex[1])) {
                edgesToAdd.add(new String[]{ex[0] + ":" + ex[1], target});
                edgesToRemove.add(new String[]{ex[0], target});
            }
        }

        for (EmptyEdge edge : graph.edgesOf(ex[1])) {
            String target = graph.getEdgeTarget(edge);
            if (!target.equals(ex[0])) {
                edgesToAdd.add(new String[]{ex[0] + ":" + ex[1], target});
                edgesToRemove.add(new String[]{ex[1], target});
            }
        }

        for (String[] edge : edgesToAdd) {
            graph.addEdge(edge[0], edge[1]);
        }
        for (String[] edge : edgesToRemove) {
            graph.removeEdge(edge[0], edge[1]);
        }

        graph.removeVertex(ex[0]);
        graph.removeVertex(ex[1]);

        return graph;
    }

    // Copy a graph
    private SimpleGraph<String, EmptyEdge> copygraph(SimpleGraph<String, EmptyEdge> graph) {
        SimpleGraph<String, EmptyEdge> copy = new SimpleGraph<>(EmptyEdge.class);
        for (String vertex : graph.vertexSet()) {
            copy.addVertex(vertex);
        }
        for (EmptyEdge edge : graph.edgeSet()) {
            String source = graph.getEdgeSource(edge);
            String target = graph.getEdgeTarget(edge);
            copy.addEdge(source, target);
        }
        return copy;
    }

    public static void main(String[] args) {
        try {
            String graphPath = "spoonCouplingGraph.dot";  
            double seuilCouplage = 0.01;
            SpoonClusterHier clusterHier = new SpoonClusterHier(graphPath, seuilCouplage);

            SimpleGraph<String, EmptyEdge> graph = new SimpleGraph<>(EmptyEdge.class);

            // Fill the graph with coupling data
            clusterHier.fillGraph(graph);

            List<String[]> fusionSteps = new ArrayList<>();

            SimpleGraph<String, EmptyEdge> clusteredGraph = clusterHier.GroupeClasse(graph, (float) seuilCouplage, fusionSteps);

            clusterHier.generateDotFileWithSteps("improved_clusters_step_by_step.dot", fusionSteps, clusterHier.clusters);

            System.out.println("Improved clustering complete. DOT file has been saved.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
