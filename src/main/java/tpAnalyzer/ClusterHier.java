package tpAnalyzer;

import org.jgrapht.graph.SimpleGraph;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ClusterHier {

    private Map<String, Map<String, Double>> couplingMatrix;
    private List<Set<String>> clusters;
    private double CP;  // Seuil de couplage

    // Constructeur pour initialiser le graphe de couplage et les clusters
    public ClusterHier(String couplingGraphPath, double cpThreshold) throws IOException {
        this.couplingMatrix = readCouplingGraph(couplingGraphPath);
        this.clusters = new ArrayList<>();
        this.CP = cpThreshold;
        initializeClusters();
    }

    // Lecture du graphe de couplage à partir d'un fichier .dot
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

                try {
                    double couplingValue = Double.parseDouble(couplingValueStr);
                    matrix.putIfAbsent(caller, new HashMap<>());
                    matrix.get(caller).put(callee, couplingValue);
                } catch (NumberFormatException e) {
                    System.out.println("Erreur de format sur la ligne : " + line);
                }
            }
        }
        reader.close();
        return matrix;
    }

    // Initialiser chaque classe comme un cluster séparé
    private void initializeClusters() {
        for (String className : couplingMatrix.keySet()) {
            Set<String> singleCluster = new HashSet<>();
            singleCluster.add(className);
            clusters.add(singleCluster);
        }
    }

    // Remplir le graphe avec les données de couplage
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

    // Regroupement hiérarchique pour fusionner les clusters jusqu'à atteindre M/2 clusters
    public SimpleGraph<String, EmptyEdge> GroupeClasse(SimpleGraph<String, EmptyEdge> graph, float CP, List<String[]> fusionSteps) {
        SimpleGraph<String, EmptyEdge> currentGraph = HierarchieCluster(graph, 0);
        SimpleGraph<String, EmptyEdge> resultGraph = copygraph(currentGraph);
        int index = 1;

        while (resultGraph.vertexSet().size() > currentGraph.vertexSet().size() / 2) {
            String[] clustersProches = ClusterProche(currentGraph);

            if (clustersProches == null) {
                break;
            }

            fusionSteps.add(clustersProches);
            resultGraph = CreateCluster(currentGraph, clustersProches);
            index++;
        }

        return resultGraph;
    }

    // Clustering hiérarchique basé sur un certain nombre d'étapes
    public SimpleGraph<String, EmptyEdge> HierarchieCluster(SimpleGraph<String, EmptyEdge> graph, int steps) {
        SimpleGraph<String, EmptyEdge> currentGraph = copygraph(graph);

        for (int i = 0; i < steps; i++) {
            String[] mostCoupled = ClusterProche(currentGraph);
            if (mostCoupled == null) {
                break;
            }
            currentGraph = CreateCluster(currentGraph, mostCoupled);
        }
        return currentGraph;
    }

    // Génération du fichier DOT avec les étapes du clustering
    public void generateDotFileWithSteps(String fileName, List<String[]> fusionSteps, List<Set<String>> initialClusters) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("digraph Clusters {\n");
            writer.write("  node [shape=ellipse];\n");

            // Clusters initiaux
            writer.write("  // Étape 0 : Clusters initiaux\n");
            for (Set<String> cluster : initialClusters) {
                String clusterName = String.join(":", cluster);
                writer.write("  \"" + clusterName + "\" [label=\"" + clusterName + "\", color=blue];\n");
            }

            // Étapes de fusion
            int step = 1;
            for (String[] fusion : fusionSteps) {
                String newClusterName = fusion[0] + ":" + fusion[1];
                writer.write("\n  // Étape " + step + ": Fusion de " + fusion[0] + " et " + fusion[1] + "\n");
                writer.write("  \"" + newClusterName + "\" [label=\"" + newClusterName + "\", style=filled, color=lightgrey];\n");
                writer.write("  \"" + fusion[0] + "\" -> \"" + newClusterName + "\" [label=\"Fusion\"];\n");
                writer.write("  \"" + fusion[1] + "\" -> \"" + newClusterName + "\" [label=\"Fusion\"];\n");
                step++;
            }

            writer.write("}\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Copier un graphe
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

    // Trouver les clusters les plus proches
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

    // Obtenir la valeur de couplage entre deux clusters
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

    // Fusionner deux clusters dans le graphe
    public SimpleGraph<String, EmptyEdge> CreateCluster(SimpleGraph<String, EmptyEdge> graph, String[] ex) {
        if (ex == null || !graph.containsVertex(ex[0]) || !graph.containsVertex(ex[1])) {
            return graph;
        }

        graph.addVertex(ex[0] + ":" + ex[1]);

        // Fusion des arêtes des deux clusters
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

        // Appliquer les modifications
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

    public static void main(String[] args) {
        try {
            String graphPath = "/home/abdou/eclipse-workspace/tpAnalyser/couplingGraph.dot";
            double seuilCouplage = 0.01;
            ClusterHier clusterHier = new ClusterHier(graphPath, seuilCouplage);

            SimpleGraph<String, EmptyEdge> graph = new SimpleGraph<>(EmptyEdge.class);

            // Remplir le graphe avec les données de couplage
            clusterHier.fillGraph(graph);

            // List pour enregistrer les étapes de fusion
            List<String[]> fusionSteps = new ArrayList<>();

            // Exécution du clustering
            SimpleGraph<String, EmptyEdge> clusteredGraph = clusterHier.GroupeClasse(graph, (float) seuilCouplage, fusionSteps);

            // Générer le fichier DOT avec les étapes de fusion
            clusterHier.generateDotFileWithSteps("clusters_step_by_step.dot", fusionSteps, clusterHier.clusters);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}