package org.visallo.javaCodeIngest;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.vertexium.*;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorkData;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorker;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.core.model.properties.VisalloProperties;

import java.io.InputStream;

import static org.vertexium.util.IterableUtils.singleOrDefault;

@Name("Java - .class")
@Description("Extracts data from Java .class files")
public class ClassFileGraphPropertyWorker extends GraphPropertyWorker {
    private static final String MULTI_VALUE_KEY = ClassFileGraphPropertyWorker.class.getName();

    @Override
    public void execute(InputStream in, GraphPropertyWorkData data) throws Exception {
        VisalloProperties.MIME_TYPE.addPropertyValue(data.getElement(), MULTI_VALUE_KEY, "application/x-java-class", data.createPropertyMetadata(getUser()), data.getVisibility(), getAuthorizations());

        Vertex jarVertex = singleOrDefault(((Vertex) data.getElement()).getVertices(Direction.BOTH, JavaCodeIngestOntology.EDGE_LABEL_JAR_CONTAINS, getAuthorizations()), null);

        String fileName = VisalloProperties.FILE_NAME.getOnlyPropertyValue(data.getElement());

        JavaClass javaClass = new ClassParser(in, fileName).parse();
        ConstantPoolGen constants = new ConstantPoolGen(javaClass.getConstantPool());

        Vertex classVertex = createClassVertex(javaClass, data);
        if (jarVertex != null) {
            getGraph().addEdge(jarVertex, classVertex, JavaCodeIngestOntology.EDGE_LABEL_JAR_CONTAINS, data.getProperty().getVisibility(), getAuthorizations());
        }

        for (Method method : javaClass.getMethods()) {
            createMethodVertex(method, classVertex, javaClass, constants, data);
        }
        for (Field field : javaClass.getFields()) {
            createFieldVertex(field, classVertex, javaClass, data);
        }

        getGraph().flush();
    }

    private Vertex createClassVertex(JavaClass javaClass, GraphPropertyWorkData data) {
        String className = javaClass.getClassName();
        VertexBuilder classVertexBuilder = createClassVertexBuilder(className, data);
        if (javaClass.isInterface()) {
            VisalloProperties.CONCEPT_TYPE.setProperty(classVertexBuilder, JavaCodeIngestOntology.CONCEPT_TYPE_INTERFACE, data.getProperty().getVisibility());
        } else {
            VisalloProperties.CONCEPT_TYPE.setProperty(classVertexBuilder, JavaCodeIngestOntology.CONCEPT_TYPE_CLASS, data.getProperty().getVisibility());
        }
        Vertex classVertex = classVertexBuilder.save(getAuthorizations());

        String containsClassEdgeId = JavaCodeIngestIdGenerator.createFileContainsClassEdgeId((Vertex) data.getElement(), classVertex);
        getGraph().addEdge(containsClassEdgeId, (Vertex) data.getElement(), classVertex, JavaCodeIngestOntology.EDGE_LABEL_CLASS_FILE_CONTAINS_CLASS, data.getProperty().getVisibility(), getAuthorizations());

        return classVertex;
    }

    private Vertex createClassVertex(String className, GraphPropertyWorkData data) {
        VertexBuilder classVertexBuilder = createClassVertexBuilder(className, data);
        return classVertexBuilder.save(getAuthorizations());
    }

    private VertexBuilder createClassVertexBuilder(String className, GraphPropertyWorkData data) {
        int i;
        while ((i = className.lastIndexOf('[')) > 0) {
            className = className.substring(0, i);
        }
        String classId = JavaCodeIngestIdGenerator.createClassId(className);
        VertexBuilder vertexBuilder = getGraph().prepareVertex(classId, data.getVisibility());
        data.setVisibilityJsonOnElement(vertexBuilder);
        JavaCodeIngestOntology.CLASS_NAME.addPropertyValue(vertexBuilder, MULTI_VALUE_KEY, className, data.createPropertyMetadata(getUser()), data.getVisibility());
        return vertexBuilder;
    }

    private void createMethodVertex(Method method, Vertex classVertex, JavaClass javaClass, ConstantPoolGen constants, GraphPropertyWorkData data) {
        String methodId = JavaCodeIngestIdGenerator.createMethodId(javaClass, method);
        VertexBuilder vertexBuilder = getGraph().prepareVertex(methodId, data.getVisibility());
        data.setVisibilityJsonOnElement(vertexBuilder);
        JavaCodeIngestOntology.METHOD_NAME.addPropertyValue(vertexBuilder, MULTI_VALUE_KEY, method.getName(), data.createPropertyMetadata(getUser()), data.getVisibility());
        JavaCodeIngestOntology.METHOD_SIGNATURE.addPropertyValue(vertexBuilder, MULTI_VALUE_KEY, method.getSignature(), data.createPropertyMetadata(getUser()), data.getVisibility());
        VisalloProperties.CONCEPT_TYPE.setProperty(vertexBuilder, JavaCodeIngestOntology.CONCEPT_TYPE_METHOD, data.createPropertyMetadata(getUser()), data.getVisibility());
        Vertex methodVertex = vertexBuilder.save(getAuthorizations());

        String classContainsMethodEdgeId = JavaCodeIngestIdGenerator.createClassContainsMethodEdgeId(classVertex, methodVertex);
        Edge edge = getGraph().addEdge(classContainsMethodEdgeId, classVertex, methodVertex, JavaCodeIngestOntology.EDGE_LABEL_CLASS_CONTAINS, data.getVisibility(), getAuthorizations());
        data.setVisibilityJsonOnElement(edge, getAuthorizations());

        // return type
        if (!method.getReturnType().toString().equals("void")) {
            Vertex returnTypeVertex = createClassVertex(method.getReturnType().toString(), data);
            String returnTypeEdgeId = JavaCodeIngestIdGenerator.createReturnTypeEdgeId(methodVertex, returnTypeVertex);
            edge = getGraph().addEdge(returnTypeEdgeId, methodVertex, returnTypeVertex, JavaCodeIngestOntology.EDGE_LABEL_METHOD_RETURN_TYPE, data.getVisibility(), getAuthorizations());
            data.setVisibilityJsonOnElement(edge, getAuthorizations());
            createClassReferencesEdge(classVertex, returnTypeVertex, data);
        }

        // arguments
        for (int i = 0; i < method.getArgumentTypes().length; i++) {
            Type argumentType = method.getArgumentTypes()[i];
            String argumentName = "arg" + i;
            Vertex argumentTypeVertex = createClassVertex(argumentType.toString(), data);
            String argumentEdgeId = JavaCodeIngestIdGenerator.createArgumentEdgeId(methodVertex, argumentTypeVertex, argumentName);
            edge = getGraph().addEdge(argumentEdgeId, methodVertex, argumentTypeVertex, JavaCodeIngestOntology.EDGE_LABEL_METHOD_ARGUMENT, data.getVisibility(), getAuthorizations());
            data.setVisibilityJsonOnElement(edge, getAuthorizations());
            JavaCodeIngestOntology.ARGUMENT_NAME.addPropertyValue(edge, MULTI_VALUE_KEY, argumentName, data.createPropertyMetadata(getUser()), data.getVisibility(), getAuthorizations());
            createClassReferencesEdge(classVertex, argumentTypeVertex, data);
        }

        // method invokes
        MethodGen mg = new MethodGen(method, javaClass.getClassName(), constants);
        if (mg.isAbstract() || mg.isNative()) {
            return;
        }
        ConstantPoolGen constantPool = mg.getConstantPool();
        for (InstructionHandle ih = mg.getInstructionList().getStart(); ih != null; ih = ih.getNext()) {
            Instruction i = ih.getInstruction();
            if (i instanceof InvokeInstruction) {
                InvokeInstruction ii = (InvokeInstruction) i;
                String methodClassName = ii.getClassName(constantPool);
                String methodName = ii.getMethodName(constantPool);
                String methodSignature = ii.getSignature(constantPool);
                String invokedMethodId = JavaCodeIngestIdGenerator.createMethodId(methodClassName, methodName, methodSignature);
                VertexBuilder invokedMethodVertexBuilder = getGraph().prepareVertex(invokedMethodId, data.getVisibility());
                data.setVisibilityJsonOnElement(invokedMethodVertexBuilder);
                JavaCodeIngestOntology.METHOD_SIGNATURE.addPropertyValue(vertexBuilder, MULTI_VALUE_KEY, method.getSignature(), data.createPropertyMetadata(getUser()), data.getVisibility());
                VisalloProperties.CONCEPT_TYPE.setProperty(invokedMethodVertexBuilder, JavaCodeIngestOntology.CONCEPT_TYPE_METHOD, data.createPropertyMetadata(getUser()), data.getVisibility());
                Vertex invokedMethodVertex = invokedMethodVertexBuilder.save(getAuthorizations());

                String methodInvokesMethodEdgeId = JavaCodeIngestIdGenerator.createMethodInvokesMethodEdgeId(methodVertex, invokedMethodVertex);
                edge = getGraph().addEdge(methodInvokesMethodEdgeId, methodVertex, invokedMethodVertex, JavaCodeIngestOntology.EDGE_LABEL_INVOKED, data.getVisibility(), getAuthorizations());
                data.setVisibilityJsonOnElement(edge, getAuthorizations());

                Vertex invokeMethodClassVertex = createClassVertex(methodClassName, data);
                createClassReferencesEdge(classVertex, invokeMethodClassVertex, data);
            }
        }
    }

    private void createFieldVertex(Field field, Vertex classVertex, JavaClass javaClass, GraphPropertyWorkData data) {
        String fieldId = JavaCodeIngestIdGenerator.createFieldId(javaClass, field);
        VertexBuilder vertexBuilder = getGraph().prepareVertex(fieldId, data.getVisibility());
        data.setVisibilityJsonOnElement(vertexBuilder);
        JavaCodeIngestOntology.FIELD_NAME.addPropertyValue(vertexBuilder, MULTI_VALUE_KEY, field.getName(), data.createPropertyMetadata(getUser()), data.getVisibility());
        VisalloProperties.CONCEPT_TYPE.setProperty(vertexBuilder, JavaCodeIngestOntology.CONCEPT_TYPE_FIELD, data.createPropertyMetadata(getUser()), data.getVisibility());
        Vertex fieldVertex = vertexBuilder.save(getAuthorizations());

        String classContainsFieldEdgeId = JavaCodeIngestIdGenerator.createClassContainsFieldEdgeId(classVertex, fieldVertex);
        Edge edge = getGraph().addEdge(classContainsFieldEdgeId, classVertex, fieldVertex, JavaCodeIngestOntology.EDGE_LABEL_CLASS_CONTAINS, data.getVisibility(), getAuthorizations());
        data.setVisibilityJsonOnElement(edge, getAuthorizations());

        Vertex fieldTypeVertex = createClassVertex(field.getType().toString(), data);
        String fieldTypeEdgeId = JavaCodeIngestIdGenerator.createFieldTypeEdgeId(fieldVertex, fieldTypeVertex);
        edge = getGraph().addEdge(fieldTypeEdgeId, fieldVertex, fieldTypeVertex, JavaCodeIngestOntology.EDGE_LABEL_FIELD_TYPE, data.getVisibility(), getAuthorizations());
        data.setVisibilityJsonOnElement(edge, getAuthorizations());
        createClassReferencesEdge(classVertex, fieldTypeVertex, data);
    }

    private void createClassReferencesEdge(Vertex classVertex, Vertex typeVertex, GraphPropertyWorkData data) {
        String classReferencesEdgeId = JavaCodeIngestIdGenerator.createClassReferencesEdgeId(classVertex, typeVertex);
        Edge edge = getGraph().addEdge(classReferencesEdgeId, classVertex, typeVertex, JavaCodeIngestOntology.EDGE_LABEL_CLASS_REFERENCES, data.getVisibility(), getAuthorizations());
        data.setVisibilityJsonOnElement(edge, getAuthorizations());
    }

    @Override
    public boolean isHandled(Element element, Property property) {
        if (property == null) {
            return false;
        }

        if (!property.getName().equals(VisalloProperties.RAW.getPropertyName())) {
            return false;
        }

        String fileName = VisalloProperties.FILE_NAME.getOnlyPropertyValue(element);
        if (fileName != null && fileName.endsWith(".class")) {
            return true;
        }

        String jarEntryName = JavaCodeIngestOntology.JAR_ENTRY_NAME.getOnlyPropertyValue(element);
        if (jarEntryName != null && jarEntryName.endsWith(".class")) {
            return true;
        }

        return false;

    }
}
