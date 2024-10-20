package tpAnalyzer;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class SpoonAnalyzer {
    private CtModel model;
    private Map<String, Map<String, Integer>> classCoupling = new HashMap<>();
    private StringBuilder callGraphBuilder = new StringBuilder("digraph CallGraph {\n");
    private int totalClassRelations = 0;

    public SpoonAnalyzer(String sourceCodePath) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceCodePath);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        this.model = launcher.buildModel();
    }

    public void analyzeApp() {
        for (CtClass<?> ctClass : model.getElements(new TypeFilter<>(CtClass.class))) {
            String className = ctClass.getQualifiedName();
            for (CtMethod<?> method : ctClass.getMethods()) {
                analyzeMethod(className, method);
            }
        }
    }

    private void analyzeMethod(String className, CtMethod<?> method) {
        String methodName = method.getSimpleName();
        method.getElements(new TypeFilter<>(CtTypeReference.class)).forEach(typeRef -> {
            if (typeRef.getTypeDeclaration() != null) {
                String calledClassName = typeRef.getQualifiedName();
                if (isValidClass(calledClassName) && !calledClassName.equals(className)) {
                    recordClassCoupling(className, calledClassName);
                    callGraphBuilder.append("\"").append(className).append(" : ").append(methodName)
                            .append("\" -> \"").append(calledClassName).append("\";\n");
                }
            }
        });
    }

    private boolean isValidClass(String className) {
        return className.contains(".") && !className.equals("java.lang.Object") &&
               !isPrimitiveOrVoid(className) && !className.equals("?");
    }

    private boolean isPrimitiveOrVoid(String type) {
        return type.equals("int") || type.equals("double") || type.equals("boolean") ||
               type.equals("char") || type.equals("byte") || type.equals("short") ||
               type.equals("long") || type.equals("float") || type.equals("void");
    }

    private void recordClassCoupling(String callerClass, String calledClass) {
        classCoupling.putIfAbsent(callerClass, new HashMap<>());
        Map<String, Integer> targetMap = classCoupling.get(callerClass);
        targetMap.put(calledClass, targetMap.getOrDefault(calledClass, 0) + 1);
        totalClassRelations++;
    }

    public String generateCallGraph() {
        callGraphBuilder.append("}");
        return callGraphBuilder.toString();
    }

    public String generateClassCouplingGraph() {
        StringBuilder dotBuilder = new StringBuilder("digraph CouplingGraph {\n");
        for (String classA : classCoupling.keySet()) {
            for (String classB : classCoupling.get(classA).keySet()) {
                double couplingValue = computeClassCoupling(classA, classB);
                dotBuilder.append("\"").append(classA).append("\" -> \"").append(classB)
                        .append("\" [label=\"Couplage: ").append(String.format("%.2f", couplingValue)).append("\"];\n");
            }
        }
        dotBuilder.append("}");
        return dotBuilder.toString();
    }

    private double computeClassCoupling(String classA, String classB) {
        Map<String, Integer> targetsFromA = classCoupling.getOrDefault(classA, new HashMap<>());
        int couplingAB = targetsFromA.getOrDefault(classB, 0);
        return (double) couplingAB / totalClassRelations;
    }

    public void saveDotFile(String dotContent, String fileName) {
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            fileWriter.write(dotContent);
        } catch (IOException e) {
            System.err.println("Erreur lors de la sauvegarde du fichier DOT : " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String sourceCodePath = "/home/abdou/Téléchargements/visitorDesignPattern";
        SpoonAnalyzer analyzer = new SpoonAnalyzer(sourceCodePath);
        
        analyzer.analyzeApp();
        
        String callGraph = analyzer.generateCallGraph();
        analyzer.saveDotFile(callGraph, "spoonCallGraph.dot");
        
        String couplingGraph = analyzer.generateClassCouplingGraph();
        analyzer.saveDotFile(couplingGraph, "spoonCouplingGraph.dot");
        
        System.out.println("Analysis complete. DOT files have been saved.");
    }
}