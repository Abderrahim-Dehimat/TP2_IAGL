// CustomVisitor.java
package tpAnalyzer;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Map;

public class CustomVisitor extends VoidVisitorAdapter<Void> {
    private final Map<String, String> methodToClassMap;
    private String currentClass = "UnknownClass";

    public CustomVisitor(Map<String, String> methodToClassMap) {
        this.methodToClassMap = methodToClassMap;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
        String previousClass = currentClass;
        currentClass = classDecl.getNameAsString();
        super.visit(classDecl, arg);
        currentClass = previousClass;
    }

    @Override
    public void visit(MethodDeclaration methodDecl, Void arg) {
        super.visit(methodDecl, arg);
        String methodName = methodDecl.getNameAsString();
        methodToClassMap.put(currentClass + "." + methodName, currentClass);
    }
}