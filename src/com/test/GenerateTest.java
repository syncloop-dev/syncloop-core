package com.test;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.commons.lang3.StringUtils;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import java.util.List;

import com.google.common.collect.Lists;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
public class GenerateTest {


    public static void main(String[] args) {

    }


    /**
     * @param path
     * @param alias
     * @param server
     * @return
     * @throws Exception
     */
    public static String swagger(String path, String alias, String server) throws Exception {
        //path = "D:\\git\\ekamw-distributions\\RESTWrapper.flow";
        String method = alias.split("/")[0];

        File file = new File(path);

        JsonObject mainflowJsonObject = javax.json.Json.createReader(new FileInputStream(file)).readObject();
        JsonValue JsonInputValue = mainflowJsonObject.get("latest").asJsonObject().get("input");
        JsonValue JsonOutputValue = mainflowJsonObject.get("latest").asJsonObject().get("output");

        JsonValue apiInfo = mainflowJsonObject.get("latest").asJsonObject().get("api_info");

        Boolean enableServiceDocumentValidation = mainflowJsonObject.get("latest").asJsonObject().getBoolean("enableServiceDocumentValidation", false);
        if (!enableServiceDocumentValidation) {
            enableServiceDocumentValidation = mainflowJsonObject.getBoolean("enableServiceDocumentValidation", false);
        }

        OpenAPI swagger = parse(JsonInputValue, JsonOutputValue, apiInfo,"FlowAPI", "1.0.0",
                alias.replaceAll(method, ""), method, file.getName().replaceAll(".flow", ""), enableServiceDocumentValidation, server);

        String openApi = Json.pretty().writeValueAsString(swagger);
        return openApi;
    }

    public static OpenAPI parse(JsonValue JsonInputValue, JsonValue JsonOutputValue, JsonValue apiInfo, String title, String version,
                                String alias, String method, String fileName, Boolean enableServiceDocumentValidation, String serverUrl) {
        final OpenAPI swagger = new OpenAPI();

        Server server = new Server();
        server.description("Syncloop Server Machine");
        server.setUrl(serverUrl);

        swagger.setServers(Lists.newArrayList(server));

        final Info info=new Info();
        info.setVersion("latest");
        if (null == apiInfo) {
            info.setDescription("");
            info.setTitle(title);

        } else {
            info.setDescription(apiInfo.asJsonObject().getString("description"));
            info.setTitle(apiInfo.asJsonObject().getString("title"));
        }

        Operation op = new Operation();
        op.setOperationId(fileName);
        op.setSummary(info.getTitle());
        op.setDescription(info.getDescription());

        swagger.setInfo(info);

        final Components comps = new Components();

        SecurityScheme securityScheme = new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                .name("Authorization");

        comps.addSecuritySchemes("ApiKeyAuth" , securityScheme);

        SecurityRequirement securityRequirement = new SecurityRequirement();
        securityRequirement.addList("ApiKeyAuth", Lists.newArrayList("syncloop"));
        op.security(Lists.newArrayList(securityRequirement));

        swagger.components(comps);
        Paths paths = new Paths();
        PathItem pi = new PathItem();

        pi.operation(HttpMethod.valueOf(method.toUpperCase()), op);
        paths.addPathItem(alias, pi);
        RequestBody body = new RequestBody();

        Parameter parameter = new Parameter();

        swagger.setPaths(paths);
        JsonArray jva = JsonInputValue.asJsonArray();
        if (jva != null) {
            boolean isPayloadPresent = false;
            for (JsonValue jsonValue : jva) {
                JsonObject jo = jsonValue.asJsonObject();
                if (jo.getString("text").equals("*requestHeaders")) {
                    addParameters(op, jo.get("children"), "header", enableServiceDocumentValidation);
                } else if (jo.getString("text").equals("*pathParameters")) {
                    addParameters(op, jo.get("children"), "path", enableServiceDocumentValidation);
                } else if (jo.getString("text").equals("*payload")) {
                    isPayloadPresent = true;
                    Schema schema = new Schema();
                    schema.setName(jo.getString("text"));
                    schema.setType("object");
                    Content content = new Content();
                    MediaType mt = new MediaType();
                    mt.schema(schema);
                    content.addMediaType("application/json", mt);
                    body.setContent(content);

                    if (enableServiceDocumentValidation) {
                        JsonObject object = jo.get("data").asJsonObject();
                        if (null != object && object.size() > 0 && null != object.get("isRequiredField")) {
                            body.setRequired(object.getBoolean("isRequiredField"));
                        } else {
                            body.required(false);
                        }
                    }

                    body.setDescription(addFieldDescription(jo));
                    addBody(schema, jo.get("children"), enableServiceDocumentValidation);
                } else if (!jo.getString("type").contains("doc")) {
                    setParameter(op, jo, "query", enableServiceDocumentValidation);
                }
            }
            if(!isPayloadPresent){
                body=null;
            }
        }



        ApiResponses apiResponses = new ApiResponses();
///		ApiResponse apiresp = new ApiResponse();
        op.setResponses(apiResponses);

        op.setRequestBody(body);

        //JsonValue responses = JsonOutputValue.asJsonObject().get("responses");
        JsonArray jvo = JsonOutputValue.asJsonArray();
//		if (responses != null) {
//			jvo = responses.asJsonArray();
//		}

        if (jvo != null) {
            for (JsonValue jsonValue : jvo) {
                JsonObject jo = jsonValue.asJsonObject();
                ApiResponse resp = new ApiResponse();
                Schema schema = new Schema();
                //schema.setName(jo.getString("text"));
                apiResponses.addApiResponse(jo.getString("text").replaceAll(Pattern.quote("*"), ""), resp);
                schema.setType("object");
                Content content = new Content();
                MediaType mt = new MediaType();
                mt.schema(schema);
                content.addMediaType("application/json", mt);
                resp.setContent(content);
                resp.description(addFieldDescription(jo));
                addBody(schema, jo.get("children"), enableServiceDocumentValidation);
            }
        }

        return swagger;
    }

    private static String addFieldDescription(JsonObject jo) {
        String description = "";
        if (null != jo.asJsonObject().get("data") && null != jo.asJsonObject().get("data").asJsonObject().getJsonString("fieldDescription")) {
            description = new String(Base64.getDecoder().decode(jo.asJsonObject().get("data").asJsonObject().getJsonString("fieldDescription").getString()
                    .getBytes(StandardCharsets.UTF_8)));
        }
        return description;
    }

    private static void addBody(Schema schema, JsonValue jsonValue, boolean enableServiceDocumentValidation) {
        if (jsonValue == null)
            return;
        JsonArray jva = jsonValue.asJsonArray();
        if (!jva.isEmpty()) {
            List<String> requiredFields = Lists.newArrayList();
            for (JsonValue jsonVal : jva) {
                JsonObject jo = jsonVal.asJsonObject();
                JsonValue jvdata= jo.get("data");
                JsonObject jvObject=jo;
                if(jvdata!=null)
                    jvObject=jvdata.asJsonObject();
                final String type = jo.getString("type").toUpperCase();
                String name = jo.getString("text");
                String desc=jvObject.getString("fieldDescription", "");
                if(desc.trim().length()>0)
                    desc=new String(Base64.getDecoder().decode(desc));

                Boolean isRequired = Boolean.valueOf(jvObject.getBoolean("isRequiredField", false)) && enableServiceDocumentValidation;
                if (isRequired) {
                    requiredFields.add(name);
                }
                Schema childSchema = new Schema();
                childSchema.setName(name);
                childSchema.setDescription(desc);
                childSchema.setType(type.toLowerCase());
                //childSchema.setRequired(Lists.newArrayList("name"));

                schema.addProperty(name, childSchema);
                switch (type) {
                    case "INTEGER":
                    case "NUMBER":
                    case "STRING":
                    case "OBJECT":
                    case "BOOLEAN": {
                        break;
                    }
                    case "DOCUMENTLIST": {
                        childSchema.setType("array");
                        Schema items = new Schema();
                        items.setType("object");
                        childSchema.items(items);
                        addBody(items, jo.get("children"), enableServiceDocumentValidation);
                        //schema = childSchema;
                        break;
                    }
                    case "BOOLEANLIST":
                    case "NUMBERLIST":
                    case "STRINGLIST": {
                        childSchema.setType("array");
                        Schema items = new Schema();
                        items.setType(type.toLowerCase().replaceAll("list", ""));
                        childSchema.items(items);

                        break;
                    }
                    case "JAVAOBJECTLIST": {
                        childSchema.setType("array");
                        Schema items = new Schema();
                        items.setType("object");
                        childSchema.items(items);

                        break;
                    }
                    case "JAVAOBJECT": {
                        childSchema.setType("object");
                        break;
                    }
                    case "DOCUMENT": {
                        childSchema.setType("object");
                        addBody(childSchema, jo.get("children"), enableServiceDocumentValidation);
                        //schema = childSchema;
                        break;
                    }
                    case "DATELIST": {
                        childSchema.setType("array");
                        Schema items = new Schema();
                        items.setType("string");
                        items.format("date");

                        childSchema.items(items);

                        break;
                    }
                    case "DATE": {
                        childSchema.setType("string");
                        childSchema.format("date");
                        break;
                    }
                }
            }
            schema.setRequired(requiredFields);
        }
    }

    private static void addParameters(Operation pi, JsonValue jsonValue, String in, Boolean enableServiceDocumentValidation) {
        if (jsonValue == null)
            return;
        JsonArray jva = jsonValue.asJsonArray();
        if (!jva.isEmpty()) {
            for (JsonValue jsonVal : jva) {
                JsonObject jo = jsonVal.asJsonObject();
                setParameter(pi, jo, in, enableServiceDocumentValidation);
            }
        }
    }

    private static void setParameter(Operation pi, JsonObject jo, String in, Boolean enableServiceDocumentValidation) {
        JsonValue jvdata= jo.get("data");
        JsonObject jvObject=jo;
        if(jvdata!=null)
            jvObject=jvdata.asJsonObject();
        PathParameter pp = new PathParameter();

        if (!"path".equals(in) && enableServiceDocumentValidation) {
            JsonObject object = jo.get("data").asJsonObject();
            if (null != object && object.size() > 0 && null != object.get("isRequiredField")) {
                pp.setRequired(object.getBoolean("isRequiredField"));
            } else {
                pp.setRequired(false);
            }
        }

        Schema schema = new Schema();
        //schema.setType(jo.getString("type"));
        parameterType(schema, jo.getString("type"));

        pp.setSchema(schema);
        pi.addParametersItem(pp);
        pp.setName(jo.getString("text"));
        String desc=jvObject.getString("fieldDescription", "");
        if(desc.trim().length()>0)
            desc=new String(Base64.getDecoder().decode(desc));
        pp.description(desc);
        pp.setIn(in);
    }

    private static void parameterType(Schema schema, String type) {
        switch (type.toUpperCase()) {
            case "INTEGER":
            case "NUMBER":
            case "STRING":
            case "OBJECT":
            case "BOOLEAN": {
                schema.setType(type.toLowerCase());
                break;
            }
            case "DOCUMENTLIST", "JAVAOBJECTLIST": {
                schema.setType("array");
                Schema items = new Schema();
                items.setType("object");
                schema.items(items);
                //schema = childSchema;
                break;
            }
            case "BOOLEANLIST":
            case "NUMBERLIST":
            case "STRINGLIST": {
                schema.setType("array");
                Schema items = new Schema();
                items.setType(type.toLowerCase().replaceAll("list", ""));
                schema.items(items);

                break;
            }
            case "JAVAOBJECT", "DOCUMENT": {
                schema.setType("object");
                break;
            }
            case "DATELIST": {
                schema.setType("array");
                Schema items = new Schema();
                items.setType("string");
                items.format("date");

                schema.items(items);

                break;
            }
            case "DATE": {
                schema.setType("string");
                schema.format("date");
                break;
            }
        }
    }



}
