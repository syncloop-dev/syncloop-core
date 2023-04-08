package com.eka.middleware.pub.util.postman;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;

public class GetBody {


    public static class NodeProperties {
        private String type;
        private Map<String, NodeProperties> properties;
        private NodeProperties items;
    }

    static class Node {

        private String text;
        private String type;
        private List<Node> children;

    }


    public static List getJstreeFromSchema(NodeProperties nodeProperties) {
        Map<String, NodeProperties> datapipeline = nodeProperties.properties;
        Object[] data = toJson(datapipeline);
        Object jsonJstreeObj = data[0];
        Object jsonObj = data[1];
        return ((List)jsonJstreeObj);
    }


    private static Object[] toJson(Map<String, NodeProperties> jsonSchemaObjs) {
        List<Node> jsonJstreeObj = new ArrayList<>();
        Map<String, Object> jsonObj = new HashMap<>();

        jsonSchemaObjs.entrySet().forEach(jsonSchemaObj -> {
            NodeProperties propVal = jsonSchemaObj.getValue();
            String propName = jsonSchemaObj.getKey();
            Node jjtOBJ = new Node();

            jjtOBJ.text = jsonSchemaObj.getKey();
            jjtOBJ.children = new ArrayList<>();

            if (propVal.type.equalsIgnoreCase("object")) {
                jjtOBJ.type = "document";
                Object[] jsonOA = toJson(propVal.properties);
                jjtOBJ.children = (List<Node>)jsonOA[0];
                jsonObj.put(propName, jsonOA[1]);
            } else if (propVal.type.equalsIgnoreCase("array")) {
                jjtOBJ.type = "documentList";
                if (null != propVal.items && (propVal.items.type.equalsIgnoreCase("object") || propVal.items.type.equalsIgnoreCase("array"))) {
                    Object[] jsonOA = toJson(propVal.items.properties);
                    jjtOBJ.children = (List<Node>)jsonOA[0];
                    jsonObj.put(propName, jsonOA[1]);
                }
            } else { // pending to add condition when its value array
                jjtOBJ.type = propVal.type;
                if (propVal.type == "array") {
                    jsonObj.put(propName, new ArrayList<>());
                    if (StringUtils.isNotBlank(propVal.items.type))
                        propVal.items.type = "string";
                    jjtOBJ.type = propVal.items.type + "List";
                    switch (propVal.items.type) {
                        case "integer":
                            jsonObj.put(propName, 0);
                            break;
                        case "number":
                            jsonObj.put(propName, 0.0);
                            break;
                        default:
                            jsonObj.put(propName, "");
                    }
                } else {
                    switch (propVal.type) {
                        case "string":
                            jsonObj.put(propName, "");
                            break;
                        case "integer":
                            jsonObj.put(propName, 0);
                            break;
                        case "number":
                            jsonObj.put(propName, 0.0);
                            break;
                    }
                }
            }
            jsonJstreeObj.add(jjtOBJ);
        });

        return new Object[]{jsonJstreeObj, jsonObj};
    }



    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static String getJsonSchema(JsonNode properties) throws JsonProcessingException {
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");

        schema.set("properties", properties);

        ObjectMapper jacksonObjectMapper = new ObjectMapper();
        String schemaString = jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        return schemaString;
    }

    static ObjectNode createProperty(JsonNode jsonData) throws IOException {
        ObjectNode propObject = OBJECT_MAPPER.createObjectNode();

        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = jsonData.fields();

        while (fieldsIterator.hasNext()) {
            Map.Entry<String, JsonNode> field = fieldsIterator.next();

            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();
            JsonNodeType fieldType = fieldValue.getNodeType();

            ObjectNode property = processJsonField(fieldValue, fieldType, fieldName);
            if (!property.isEmpty()) {
                propObject.set(fieldName, property);
            }
        }
        return propObject;
    }

    private static ObjectNode processJsonField(JsonNode fieldValue, JsonNodeType fieldType, String fieldName)
            throws IOException {
        ObjectNode property = OBJECT_MAPPER.createObjectNode();

        switch (fieldType) {

            case ARRAY:
                property.put("type", "array");

                if (fieldValue.isEmpty()) {
                    break;
                }

                // Get first element of the array
                JsonNodeType typeOfArrayElements = fieldValue.get(0).getNodeType();
                if (typeOfArrayElements.equals(JsonNodeType.OBJECT)) {
                    ObjectNode arraySchema = OBJECT_MAPPER.createObjectNode();
                    arraySchema.put("type", "object");
                    arraySchema.set("properties", createProperty(fieldValue.get(0)));
                    property.set("items", arraySchema);
                } else {
                    property.set("items", processJsonField(fieldValue.get(0), typeOfArrayElements, fieldName));
                }

                break;
            case BOOLEAN:
                property.put("type", "boolean");
                break;

            case NUMBER:
                property.put("type", "number");
                break;

            case OBJECT:
                property.put("type", "object");
                property.set("properties", createProperty(fieldValue));
                break;

            case STRING:
                property.put("type", "string");
                break;
            default:
                break;
        }
        return property;
    }

    public static String getJsonSchema(String jsonDocument) throws IllegalArgumentException, IOException {
        Map<String, Object> map = OBJECT_MAPPER.readValue(jsonDocument, new TypeReference<Map<String, Object>>() {});
        return getJsonSchema(map);
    }

    public static String getJsonSchema(Map<String, Object> jsonDocument) throws IllegalArgumentException, IOException {

        JsonNode properties = createProperty(OBJECT_MAPPER.convertValue(jsonDocument, JsonNode.class));
        return getJsonSchema(properties);

    }

}
