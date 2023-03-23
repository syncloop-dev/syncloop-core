package com.eka.middleware.pub.util;

import com.eka.middleware.heap.HashMap;
import com.eka.middleware.pub.util.postman.*;
import com.eka.middleware.service.ServiceUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.nimbusds.jose.shaded.gson.Gson;
import net.minidev.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static com.eka.middleware.pub.util.ImportSwagger.*;

public class ImportPostman {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        PostmanCollection postmanCollection = new Gson().fromJson(new FileReader("E:/formatx.json"), PostmanCollection.class);
        createFlowServices("E:\\Nature9_Work\\ekamw-distributions\\integration\\middleware\\tenants\\default\\packages\\ec2\\server\\", postmanCollection.getItem());
        createFlowServicesClient("E:\\Nature9_Work\\ekamw-distributions\\integration\\middleware\\tenants\\default\\packages\\ec2\\client",postmanCollection.getItem());

    }
    static void createFlowServices(String folder, List<PostmanItems> item) throws Exception {
        for (PostmanItems postmanItems : item) {
            if (postmanItems.getItem() != null && !postmanItems.getItem().isEmpty()) {
                String slug = ServiceUtils.toServiceSlug(postmanItems.getName());
                createFlowServices(folder + File.separator + slug, postmanItems.getItem());
            } else {
                generateServerStub(folder, postmanItems);
            }
        }
    }
    static void createFlowServicesClient(String folder, List<PostmanItems> item) throws Exception {
        for (PostmanItems postmanItems : item) {
            if (postmanItems.getItem() != null && !postmanItems.getItem().isEmpty()) {
                String slug = ServiceUtils.toServiceSlug(postmanItems.getName());
                createFlowServices(folder + File.separator + slug, postmanItems.getItem());}
            else{
                String method = postmanItems.getRequest().getMethod();
                generateClientLib(folder, "", postmanItems, method);
            }
        }
    }
    public static void generateServerStub(String folderPath, PostmanItems postmanItems) throws Exception {

        String servicePath = folderPath + File.separator + ServiceUtils.toServiceSlug(postmanItems.getName()) + ".flow";

        File file = new File(servicePath);
        FlowService flowService;
        if (file.exists() && false) {
            flowService = new Gson().fromJson(new FileReader(file), FlowService.class);
        } else {
            flowService = new FlowService("", "", Sets.newHashSet("consumers"), Sets.newHashSet("developers"));
        }
        List<Object> inputs = flowService.getInput();

        inputs.clear();

        List<Object> outputs = flowService.getOutput();
        inputs.clear();

        if (postmanItems.getRequest() != null) {
            List<Object> headers = getRequestHeaders(postmanItems.getRequest().getHeader());
            List<Object> query = getRequestQuery(postmanItems.getRequest().getUrl().getQuery());
            List<Object> path = getRequestPathParameters(postmanItems.getRequest().getUrl().getPath());

            addRequestBody(inputs, postmanItems, false);
            if (headers.size() > 0)
                inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
            if (path.size() > 0)
                inputs.add(ImportSwagger.createDocument("pathParameters", path, ""));
            if (query.size() > 0)
                inputs.add(ImportSwagger.createDocument("queryParameters", query, ""));
        }
        addResponseBody(outputs, postmanItems, false);
        String json = new Gson().toJson(flowService.getFlow());

        saveFlow(servicePath, json);
    }
    public static String getRequestBody(PostmanItems item) throws IOException {

        String jsonSchema = null;

        PostmanItemRequest request = item.getRequest();
        if (request != null) {
            PostmanRequestItemBody requestBody = request.getBody();
            if (requestBody != null && StringUtils.isNotBlank(requestBody.getRaw())) {
                String raw = requestBody.getRaw();
                Gson gson = new Gson();
                Map map = gson.fromJson(raw, Map.class);
                jsonSchema = getJsonSchema(map);
            }
        }
        return jsonSchema;
    }
    private static String getJsonSchema(JsonNode properties) throws JsonProcessingException {
        final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");

        schema.set("properties", properties);

        ObjectMapper jacksonObjectMapper = new ObjectMapper();
        String schemaString = jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        return schemaString;
    }
    private static ObjectNode createProperty(JsonNode jsonData) throws IOException {

        final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    public static String getJsonSchema(Map<String, Object> jsonDocument) throws IllegalArgumentException, IOException {
        final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        JsonNode properties = createProperty(OBJECT_MAPPER.convertValue(jsonDocument, JsonNode.class));
        return getJsonSchema(properties);
    }
    public static String addResponses(PostmanItems postmanItems) throws IOException {

        Map<String, Object> map = new HashMap<>();
        if (postmanItems.getResponse()!=null) {
            if (postmanItems.getResponse().isEmpty()) {
                map.put("*200", new HashMap<>());
            } else {
                for (PostmanItemResponse response : postmanItems.getResponse()) {
                    if (response != null) {
                        int code = response.getCode();
                        String requestBody = response.getBody();
                        if (StringUtils.isNotBlank(requestBody)) {
                            Gson gson = new Gson();
                            Object object = gson.fromJson(requestBody, Object.class);
                            map.put("*" + code, object);
                        }
                    }
                }
            }
        }
        return getJsonSchema(map);
    }
    private static void addRequestBody(List<Object> inputs, PostmanItems rbpostmanItems, boolean isClient)
            throws IOException {

        String payloadParam = "*payload";
        if (isClient) {
            payloadParam = "payload";
        }
        String requestBody = getRequestBody(rbpostmanItems);

        if (StringUtils.isNotBlank(requestBody)) {

            GetBody.NodeProperties map = new Gson().fromJson(requestBody, GetBody.NodeProperties.class);

            List<Object> objects = Lists.newArrayList(GetBody.getJstreeFromSchema(map));

            Map<String, Object> objectMap = createDocument(payloadParam, objects, "description");
            inputs.addAll(Lists.newArrayList(objectMap));
        }
    }
    private static List<Object> getRequestHeaders(List<PostmanRequestHeaders> headers) {
        if (null == headers) {
            return Lists.newArrayList();
        }
        List<Object> document = new ArrayList();
        for (PostmanRequestHeaders header : headers) {
            Map<String, Object> documentParam = new HashMap();
            Map<String, Object> data = new HashMap();
            documentParam.put("text", header.getKey());
            documentParam.put("type", "string");

            data.put("isRequiredField", true);
            documentParam.put("data", data);
            boolean add = document.add(documentParam);
        }
        return document;
    }
    private static List<Object> getRequestQuery(List<UrlQuery> queries) {
        if (null == queries) {
            return Lists.newArrayList();
        }
        List<Object> document = new ArrayList();
        for (UrlQuery header : queries) {
            Map<String, Object> documentParam = new HashMap();
            Map<String, Object> data = new HashMap();
            documentParam.put("text", header.getKey());
            documentParam.put("type", "string");

            data.put("fieldDescription", ImportSwagger.encodeBas64(header.getDescription()));
            data.put("isRequiredField", true);
            documentParam.put("data", data);
            document.add(documentParam);
        }
        return document;
    }
    private static List<Object> getRequestPathParameters(List<String> path) {
        if (null == path) {
            return Lists.newArrayList();
        }
        List<Object> document = new ArrayList();
        for (String header : path) {
            if (header.indexOf("}}") < 0) {
                continue;
            }
            header = header.replaceAll(Pattern.quote("{{"), "").replaceAll("}}", "");
            Map<String, Object> documentParam = new HashMap();
            Map<String, Object> data = new HashMap();
            documentParam.put("text", header);
            documentParam.put("type", "string");
            data.put("isRequiredField", true);
            documentParam.put("data", data);
            document.add(documentParam);
        }
        return document;
    }
    private static void saveFlow(String filePath, String json) {

        File file = new File(filePath);
        boolean mkdirs = file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            IOUtils.write(json.getBytes(), fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static String getJsonSchema(String jsonDocument) throws IllegalArgumentException, IOException {
        Map<String, Object> map = OBJECT_MAPPER.readValue(jsonDocument, new TypeReference<Map<String, Object>>() {
        });
        return getJsonSchema(map);
    }
    private static void addResponseBody(List<Object> output, PostmanItems rbpostmanItems, boolean isClient)
            throws IOException {

        String responseBody = addResponses(rbpostmanItems);
        if (StringUtils.isNotBlank(responseBody)) {
            GetBody.NodeProperties map = new Gson().fromJson(responseBody, GetBody.NodeProperties.class);
            output.addAll(GetBody.getJstreeFromSchema(map));
        }
    }
    private static void generateClientLib(String folder, String alias, PostmanItems postmanItems, String method) throws IOException {

        String servicePath = folder + File.separator + ServiceUtils.toServiceSlug(postmanItems.getName()) + ".flow";

        File file = new File(servicePath);
        FlowService flowService;
        if (file.exists() && false) {
            flowService = new Gson().fromJson(new FileReader(file), FlowService.class);
        } else {
            flowService = new FlowService("", "", Sets.newHashSet("consumers"), Sets.newHashSet("developers"));
        }
        List<Object> inputs = flowService.getInput();
        List<Object> outputs = flowService.getOutput();
        List<Object> flowSteps = flowService.getFlowSteps();

        if (postmanItems.getRequest() != null) {
            List<Object> headers = getRequestHeaders(postmanItems.getRequest().getHeader());
            List<Object> query = getRequestQuery(postmanItems.getRequest().getUrl().getQuery());
            List<Object> path = getRequestPathParameters(postmanItems.getRequest().getUrl().getPath());

            addRequestBody(inputs, postmanItems, true);

            if (headers.size() > 0)
                inputs.add(createDocument("requestHeaders", headers, ""));
            if (path.size() > 0)
                inputs.add(createDocument("pathParameters", path, ""));
            if (query.size() > 0)
                inputs.add(createDocument("queryParameters", query, ""));
        }

        Map<String, Object> intiMapStep = createMapStep(flowSteps, "Resolving Parameters");

        createVariables(intiMapStep, "basePath", "#{basePath}", Evaluate.EEV, "string");

        intiMapStep = createMapStep(flowSteps, "Initializing Parameters");

        alias=StringUtils.join(postmanItems.getRequest().getUrl().getPath(),"/");

        String updatedAlias = alias.replaceAll(Pattern.quote("{{"), "#\\{").replaceAll(Pattern.quote("}}"), "\\}");

        createVariables(intiMapStep, "method", method, null, "string");
        createVariables(intiMapStep, "url", "#{basePath}" + updatedAlias.replaceAll(Pattern.quote("{"), "#{pathParameters/"), Evaluate.EEV, "string");
        dropVariables(intiMapStep, "basePath", "string");
        dropVariables(intiMapStep, "pathParameters", "document");

        if (!"GET".equalsIgnoreCase(method)) {

            Map<String, Object> invokeStepToJson = createInvokeStep(flowSteps, "service", "packages/middleware/pub/json/toJson", "Initialize");
            createPreInvokeMapping(invokeStepToJson, "copy", "document", "/payload", "document", "/root");
            createPostInvokeMapping(invokeStepToJson, "copy", "string", "/jsonString", "string", "/body");
        }

        Map<String, Object> invokeStepRequest = createInvokeStep(flowSteps, "service", "packages/middleware/pub/client/http/request", "Initialize");

        createPreInvokeMapping(invokeStepRequest, "copy", "document", "/requestHeaders", "document", "/headers");
        createPreInvokeMapping(invokeStepRequest, "copy", "document", "/queryParameters", "document", "/urlParameters");
        createPreInvokeMapping(invokeStepRequest, "copy", "string", "/url", "string", "/url");
        createPreInvokeMapping(invokeStepRequest, "copy", "string", "/method", "string", "/method");

        createPostInvokeMapping(invokeStepRequest, "copy", "document", "/respHeaders", "document", "/respHeaders");
        createPostInvokeMapping(invokeStepRequest, "copy", "string", "/respPayload", "string", "/respPayload");
        createPostInvokeMapping(invokeStepRequest, "copy", "string", "/error", "string", "/error");
        createPostInvokeMapping(invokeStepRequest, "copy", "byteList", "/bytes", "byteList", "/bytes");
        createPostInvokeMapping(invokeStepRequest, "copy", "javaObject", "/inputStream", "javaObject", "/inputStream");
        createPostInvokeMapping(invokeStepRequest, "copy", "string", "/statusCode", "string", "/statusCode");

        dropVariables(invokeStepRequest, "method", "string");
        dropVariables(invokeStepRequest, "url", "string");
        dropVariables(invokeStepRequest, "headers", "document");
        dropVariables(invokeStepRequest, "urlParameters", "document");

        if (!"GET".equalsIgnoreCase(method)) {
            createPreInvokeMapping(invokeStepRequest, "copy", "string", "/body", "string", "/payload");
            dropVariables(invokeStepRequest, "payload", "string");
        }

        Map<String, Object> switchMapping = createSwitch(flowSteps, "switch", "SWITCH", "Checking status for response");
        addData(switchMapping, "switch", "statusCode");

        List<PostmanItemResponse> response = postmanItems.getResponse();

        List<Object> cases = Lists.newArrayList();
        if(response!=null) {
            for (PostmanItemResponse resp : response) {

                String code = resp.getCode() + "";
                Map<String, Object> sequenceMapping = createCase(cases, "CASE", "Handling " + code + " response");
                addData(sequenceMapping, "case", code);

                List<Object> contentTypeSwitch = Lists.newArrayList();
                addContentTypeHandler(contentTypeSwitch, intiMapStep, resp);
                addChildren(sequenceMapping, contentTypeSwitch);
            }
        }
        addChildren(switchMapping, cases);
        addResponseBody(outputs, postmanItems, true);


     /*   String json = new Gson().toJson(flowService.getFlow());
        Map<String, Object> map = new Gson().fromJson(json, Map.class);
        flowService.setDeveloper("developer");
        flowService.setConsumer("consumer");
        map.put("consumer", flowService.getConsumer());
        map.put("developer", flowService.getDeveloper());
        JSONObject jsonObject = new JSONObject(map);
        String updatedJson = jsonObject.toString();
        saveFlow(servicePath, updatedJson);*/

        // Add multiple consumer groups
        String json = new Gson().toJson(flowService.getFlow());
        Map<String, Object> map = new Gson().fromJson(json, Map.class);

        List<String> consumerGroups = new ArrayList<>();
        consumerGroups.add("consumers");
        map.put("consumers", StringUtils.join(consumerGroups,","));


        List<String> developerGroups = new ArrayList<>();
        developerGroups.add("developers");
        map.put("developers", StringUtils.join(developerGroups,","));

        JSONObject jsonObject = new JSONObject(map);
        String updatedJson = jsonObject.toString();
        saveFlow(servicePath, updatedJson);


        System.out.println("end");
    }
    public static Map<String, Object> addContentTypeHandler(List<Object> contentTypeSwitch, Map<String, Object> intiMapStep, PostmanItemResponse postmanItemResponse) {

        String code = postmanItemResponse.getCode()+"";

        Map<String, Object> switchContentTypeMapping = createSwitch(contentTypeSwitch,"switch","SWITCH", "Checking content type for response");
        addData(switchContentTypeMapping, "switch", "respHeaders/Content-Type");

        List<Object> contentTypeCases = com.beust.jcommander.internal.Lists.newArrayList();
        String body = postmanItemResponse.getBody();

        if (null != body) {
            {
                Map<String, Object> sequenceContentTypeMapping = createCase(contentTypeCases,"CASE", "Handling JSON response");
                addData(sequenceContentTypeMapping, "case", "application/json");

                List<Object> jsonConversionCase = com.beust.jcommander.internal.Lists.newArrayList();
                createVariables(intiMapStep, "rootName_Error", "Error", null, "string");

                Map<String, Object> invokeStepToJson = createInvokeStep(jsonConversionCase,"service","packages/middleware/pub/json/fromJson", "Initialize");

                createPreInvokeMapping(invokeStepToJson, "copy", "string", "/respPayload", "string", "/jsonString");
                createPreInvokeMapping(invokeStepToJson, "copy", "string", "/rootName_Error", "string", "/rootName");
                createPostInvokeMapping(invokeStepToJson, "copy", "document", "/output", "document", "/*" + code);
                addChildren(sequenceContentTypeMapping, jsonConversionCase);
            }
            {
                Map<String, Object> sequenceContentTypeMapping = createCase(contentTypeCases,"CASE", "Handling XML response");
                addData(sequenceContentTypeMapping, "case", "application/xml");

                List<Object> jsonConversionCase = com.beust.jcommander.internal.Lists.newArrayList();
                Map<String, Object> invokeStepToJson = createInvokeStep(jsonConversionCase,"service","packages/middleware/pub/xml/fromXML", "Initialize");

                createPreInvokeMapping(invokeStepToJson, "copy", "string", "/respPayload", "string", "/xml");
                createPostInvokeMapping(invokeStepToJson, "copy", "document/document", "/output/root", "document/document", "/*" + code + "/root");
                addChildren(sequenceContentTypeMapping, jsonConversionCase);
            }
        }
        addChildren(switchContentTypeMapping, contentTypeCases);
        System.out.println("finished content");
        return switchContentTypeMapping;
    }

  /*  public static Map<String, String> asClientLib(String folderPath, byte[] postmanIntem) throws Exception {

        File folder = new File(folderPath);
        folder.mkdirs();

        PostmanItems items = null;
        Map<String, String> response = new HashMap();

        items = Json.mapper().readValue(postmanIntem, PostmanItems.class);
        Map<String, Object> flow = null;
        List<PostmanItems> item = items.getItem();

        for (PostmanItems entry : item) {

            String alias ="";
            String pi = entry.getName();
            String method = entry.getRequest().getMethod();
            // PathItem pi = entry.getValue();
            String op = items.getRequest().getMethod();
            flow = generateClientLib(alias, op, items, "GET");
            if (flow != null) {
                String opId = ServiceUtils.normalizeUri(pi == null ? ServiceUtils.normalizeApiPathName("get", alias) : pi);
                String json = Json.pretty().writeValueAsString(flow);
                saveFlow(folderPath + ServiceUtils.normalizeApiPath(alias) + File.separator + opId + ".flow", json);
                response.put("GET" + alias, opId);
            }
           // op = pi.getPost();
            flow = generateClientLib(alias, op, items, "POST");
            if (flow != null) {
                String opId = ServiceUtils.normalizeUri(pi== null ? ServiceUtils.normalizeApiPathName("post", alias) : pi);
                String json = Json.pretty().writeValueAsString(flow);
                saveFlow(folderPath + ServiceUtils.normalizeApiPath(alias) + File.separator + opId + ".flow", json);
                response.put("POST" + alias, opId);
            }

            //op = pi.getDelete();
            flow = generateClientLib(alias, op, items, "DELETE");
            if (flow != null) {
                String opId = ServiceUtils.normalizeUri(pi == null ? ServiceUtils.normalizeApiPathName("delete", alias) : pi);
                String json = Json.pretty().writeValueAsString(flow);
                saveFlow(folderPath + ServiceUtils.normalizeApiPath(alias) + File.separator + opId + ".flow", json);
                response.put("DELETE" + alias, opId);
            }

            //op = pi.getPatch();
            flow = generateClientLib(alias, op, items, "PATCH");
            if (flow != null) {
                String opId = ServiceUtils.normalizeUri(pi == null ? ServiceUtils.normalizeApiPathName("patch", alias) : pi);
                String json = Json.pretty().writeValueAsString(flow);
                saveFlow(folderPath + ServiceUtils.normalizeApiPath(alias) + File.separator + opId + ".flow", json);
                response.put("PATCH" + alias, opId);
            }

           // op = pi.getPut();
            flow = generateClientLib(alias, op, items, "PUT");
            if (flow != null) {
                String opId = ServiceUtils.normalizeUri(pi == null ? ServiceUtils.normalizeApiPathName("put", alias) : pi);
                String json = Json.pretty().writeValueAsString(flow);
                saveFlow(folderPath + ServiceUtils.normalizeApiPath(alias) + File.separator + opId + ".flow", json);
                response.put("PUT" + alias, opId);
            }
        }
        return response;
    }*/



}