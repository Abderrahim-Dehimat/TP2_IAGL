package tpAnalyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class AppAnalyzer {

    private Map<String, Map<String, Integer>> classCoupling = new HashMap<>();
    private Map<String, String> methodToClassMap = new HashMap<>();
    private int totalClassRelations = 0;
    private StringBuilder callGraphBuilder = new StringBuilder("digraph CallGraph {\n");

    private static String sourceCodePath;
    private JavaSymbolSolver symbolSolver;

    // Constructeur de l'analyseur qui initialise le résolveur de symboles
    public AppAnalyzer(String path) {
        sourceCodePath = path;
        setupSymbolSolver();
    }

    // Configuration du résolveur de symboles pour analyser le code Java
    private void setupSymbolSolver() {
        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
        combinedSolver.add(new ReflectionTypeSolver());
        combinedSolver.add(new JavaParserTypeSolver(Paths.get(sourceCodePath)));

        symbolSolver = new JavaSymbolSolver(combinedSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Entrez le chemin du dossier à analyser :");
        String path = scanner.nextLine();

        AppAnalyzer analyzer = new AppAnalyzer(path);
        try {
            analyzer.analyzeApp();

            String callGraph = analyzer.generateCallGraph();
            System.out.println("Call Graph :\n" + callGraph);
            analyzer.saveDotFile(callGraph, "callGraph.dot");

            String couplingGraph = analyzer.generateClassCouplingGraph();
            System.out.println("Coupling Graph :\n" + couplingGraph);
            analyzer.saveDotFile(couplingGraph, "couplingGraph.dot");

            System.out.println("Les fichiers DOT ont été sauvegardés sous 'callGraph.dot' et 'couplingGraph.dot'.");

        } catch (IOException e) {
            System.err.println("Erreur lors de l'analyse de l'application : " + e.getMessage());
        }
    }

    // Analyse de l'application en deux passes : collecte et analyse
    public void analyzeApp() throws IOException {
        // Collecte des classes et méthodes
        Files.walk(Paths.get(sourceCodePath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(this::collectClassAndMethodInfo);

        // Analyse des appels de méthodes et du couplage entre classes
        Files.walk(Paths.get(sourceCodePath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(this::analyzeJavaFile);
    }

    private void collectClassAndMethodInfo(Path filePath) {
        try (FileInputStream in = new FileInputStream(filePath.toFile())) {
            CompilationUnit cu = StaticJavaParser.parse(in);
            new ClassAndMethodCollector().visit(cu, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void analyzeJavaFile(Path filePath) {
        try (FileInputStream in = new FileInputStream(filePath.toFile())) {
            CompilationUnit cu = StaticJavaParser.parse(in);
            new MethodCallCollector().visit(cu, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Classe interne pour collecter les informations sur les classes et méthodes
    private class ClassAndMethodCollector extends VoidVisitorAdapter<Void> {
        private String currentClass = "UnknownClass";

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            String previousClass = currentClass;
            currentClass = n.getNameAsString();
            super.visit(n, arg);
            currentClass = previousClass;
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            String methodName = n.getNameAsString();
            methodToClassMap.put(currentClass + "." + methodName, currentClass);
        }

        @Override
        public void visit(ConstructorDeclaration n, Void arg) {
            super.visit(n, arg);
            String constructorName = n.getNameAsString();
            methodToClassMap.put(currentClass + "." + constructorName, currentClass);
        }

        @Override
        public void visit(InitializerDeclaration n, Void arg) {
            super.visit(n, arg);
            String initializerName = "initializer";
            methodToClassMap.put(currentClass + "." + initializerName, currentClass);
        }
    }

    // Classe interne pour analyser les appels de méthodes
    private class MethodCallCollector extends VoidVisitorAdapter<Void> {
        private String currentClass = "UnknownClass";
        private String currentMethod = "UnknownMethod";

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            String previousClass = currentClass;
            currentClass = n.getNameAsString();
            super.visit(n, arg);
            currentClass = previousClass;
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            String previousMethod = currentMethod;
            currentMethod = n.getNameAsString();
            super.visit(n, arg);
            currentMethod = previousMethod;
        }

        @Override
        public void visit(ConstructorDeclaration n, Void arg) {
            String previousMethod = currentMethod;
            currentMethod = "constructor";
            super.visit(n, arg);
            currentMethod = previousMethod;
        }

        @Override
        public void visit(InitializerDeclaration n, Void arg) {
            String previousMethod = currentMethod;
            currentMethod = "initializer";
            super.visit(n, arg);
            currentMethod = previousMethod;
        }

        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);

            String callerClass = currentClass;
            String callerMethod = currentMethod;
            String calledMethod = methodCall.getNameAsString();
            String calledClass = "UnknownClass";

            try {
                ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
                calledClass = resolvedMethod.declaringType().getClassName();
                calledMethod = resolvedMethod.getName();
            } catch (Exception e) {
                for (Map.Entry<String, String> entry : methodToClassMap.entrySet()) {
                    if (entry.getKey().endsWith("." + calledMethod)) {
                        calledClass = entry.getValue();
                        break;
                    }
                }
            }

            if (isStandardJavaMethod(calledMethod)) {
                calledClass = getStandardJavaClass(calledMethod);
            }

            recordClassCoupling(callerClass, calledClass, callerMethod, calledMethod);
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);

            String callerClass = currentClass;
            String callerMethod = currentMethod;
            String calledClass = n.getType().getNameAsString();
            String calledMethod = "constructor";

            recordClassCoupling(callerClass, calledClass, callerMethod, calledMethod);
        }

        private boolean isStandardJavaMethod(String methodName) {
            return Arrays.asList("println", "asList", "getClass", "getSimpleName").contains(methodName);
        }

        private String getStandardJavaClass(String methodName) {
            switch (methodName) {
                case "println":
                    return "System.out";
                case "asList":
                    return "Arrays";
                case "getClass":
                case "getSimpleName":
                    return "Object";
                default:
                    return "UnknownClass";
            }
        }
    }

    // Enregistrement des relations de couplage entre classes
    private void recordClassCoupling(String callerClass, String calledClass, String callerMethod, String calledMethod) {
        if (isJavaStandardClass(callerClass) || isJavaStandardClass(calledClass)) {
            return;
        }

        classCoupling.putIfAbsent(callerClass, new HashMap<>());
        Map<String, Integer> targetMap = classCoupling.get(callerClass);

        targetMap.put(calledClass, targetMap.getOrDefault(calledClass, 0) + 1);
        totalClassRelations++;

        callGraphBuilder.append("\"").append(callerClass).append(" : ").append(callerMethod)
                .append("\" -> \"").append(calledClass).append(" : ").append(calledMethod).append("\";\n");
    }

    private boolean isJavaStandardClass(String className) {
        return className.equals("System.out") ||
                className.equals("Arrays") ||
                className.equals("Object") ||
                className.startsWith("java.") ||
                className.startsWith("javax.");
    }

    private double computeClassCoupling(String classA, String classB) {
        Map<String, Integer> targetsFromA = classCoupling.getOrDefault(classA, new HashMap<>());
        int couplingAB = targetsFromA.getOrDefault(classB, 0);
        return (double) couplingAB / totalClassRelations;
    }

    // Génération du graphe d'appel
    public String generateCallGraph() {
        callGraphBuilder.append("}");
        return callGraphBuilder.toString();
    }

    // Génération du graphe de couplage
    public String generateClassCouplingGraph() {
        StringBuilder dotBuilder = new StringBuilder("digraph CouplingGraph {\n");
        DecimalFormat df = new DecimalFormat("#.##");

        for (String classA : classCoupling.keySet()) {
            for (String classB : classCoupling.get(classA).keySet()) {
                double couplingValue = computeClassCoupling(classA, classB);
                dotBuilder.append("\"").append(classA).append("\" -> \"").append(classB)
                        .append("\" [label=\"Couplage: ").append(df.format(couplingValue)).append("\"];\n");
            }
        }

        dotBuilder.append("}");
        return dotBuilder.toString();
    }

    // Sauvegarde du fichier DOT
    public void saveDotFile(String dotContent, String fileName) {
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            fileWriter.write(dotContent);
        } catch (IOException e) {
            System.err.println("Erreur lors de la sauvegarde du fichier DOT : " + e.getMessage());
        }
    }
}