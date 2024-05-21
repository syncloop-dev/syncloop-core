package com.eka.middleware.sdk;

import com.google.common.collect.Sets;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

public class BinderUtils {

    public static JsonEntity convert(String json) {

        JsonEntity mainEntity = new JsonEntity();
        mainEntity.setType("object");

        JsonArray jva = javax.json.Json.createReader(new StringReader(json)).readArray();
        if (jva != null) {

            for (JsonValue jsonValue : jva) {

                JsonObject jo = jsonValue.asJsonObject();

                JsonEntity jsonEntity = new JsonEntity();
                jsonEntity.setTitle(jo.getString("text"));

                final String type = jo.getString("type").toUpperCase();

                switch (type) {
                    case "INTEGER":
                    case "NUMBER":
                    case "STRING":
                    case "OBJECT":
                    case "BOOLEAN":
                    {
                        jsonEntity.setType(type.toLowerCase());
                        break;
                    }
                    case "DOCUMENTLIST": {
                        jsonEntity.setType("array");
                        JsonEntity items = new JsonEntity();
                        items.setType("object");
                        jsonEntity.setItems(items);
                        addBody(items, jo.get("children"), true);
                        // schema = childSchema;
                        break;
                    }
                    case "BOOLEANLIST":
                    case "NUMBERLIST":
                    case "STRINGLIST": {
                        jsonEntity.setType("array");
                        JsonEntity items = new JsonEntity();
                        items.setType(type.toLowerCase().replaceAll("list", ""));
                        jsonEntity.setItems(items);

                        break;
                    }

                    case "JAVAOBJECTLIST": {
                        jsonEntity.setType("array");
                        JsonEntity items = new JsonEntity();
                        items.setType("object");
                        jsonEntity.setItems(items);

                        break;
                    }
                    case "JAVAOBJECT": {
                        jsonEntity.setType("object");
                        break;
                    }
                    case "DOCUMENT": {
                        jsonEntity.setType("object");
                        // schema.setType("array");
                        JsonEntity items = new JsonEntity();
                        items.setType("object");

                        jo.get("children");

                        //addBody(schema, jo.get("children"), enableServiceDocumentValidation);
                        //schema = childSchema;
                        break;
                    }
                    case "DATELIST": {
                        jsonEntity.setType("array");
                        JsonEntity items = new JsonEntity();
                        items.setType("string");
                        items.setFormat("date");

                        items.setItems(items);

                        break;
                    }
                    case "DATE": {
                        jsonEntity.setType("string");
                        jsonEntity.setFormat("date");
                        break;
                    }
                }

                // schema.setType("object");


                mainEntity.addEntity(jsonEntity.getTitle(), jsonEntity);

                if (true) {
                    JsonObject object = jo.get("data").asJsonObject();
                    if (null != object && object.size() > 0 && null != object.get("isRequiredField")) {
                        if (object.getBoolean("isRequiredField")) {
                            mainEntity.addRequired(jsonEntity.getTitle());
                        }
                    }
                }
                jsonEntity.setDescription(addFieldDescription(jo));
                addBody(jsonEntity, jo.get("children"), true);

            }
        }

        return mainEntity;

    }

    private static String addFieldDescription(JsonObject jo) {
        String description = "";
        if (null != jo.asJsonObject().get("data") && null != jo.asJsonObject().get("data").asJsonObject().getJsonString("fieldDescription")) {
            description = new String(Base64.getDecoder().decode(jo.asJsonObject().get("data").asJsonObject().getJsonString("fieldDescription").getString()
                    .getBytes(StandardCharsets.UTF_8)));
        }
        return description;
    }

    private static void addBody(JsonEntity schema, JsonValue jsonValue, boolean enableServiceDocumentValidation) {
        if (jsonValue == null)
            return;
        JsonArray jva = jsonValue.asJsonArray();
        if (!jva.isEmpty()) {
            Set<String> requiredFields = Sets.newHashSet();
            for (JsonValue jsonVal : jva) {
                JsonObject jo = jsonVal.asJsonObject();
                JsonValue jvdata = jo.get("data");
                JsonObject jvObject = jo;
                if (jvdata != null)
                    jvObject = jvdata.asJsonObject();
                final String type = jo.getString("type").toUpperCase();
                String name = jo.getString("text");
                String desc = jvObject.getString("fieldDescription", "");
                if (desc.trim().length() > 0)
                    desc = new String(Base64.getDecoder().decode(desc));

                Boolean isRequired = Boolean.valueOf(jvObject.getBoolean("isRequiredField", false)) && enableServiceDocumentValidation;
                if (isRequired) {
                    requiredFields.add(name);
                }
                JsonEntity childSchema = new JsonEntity();
                childSchema.setTitle(name);
                childSchema.setDescription(desc);
                childSchema.setType(type.toLowerCase());

                schema.setDescription(addFieldDescription(jo));
                schema.addEntity(name, childSchema);

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
                        JsonEntity items = new JsonEntity();
                        items.setType("object");
                        childSchema.setItems(items);
                        addBody(items, jo.get("children"), enableServiceDocumentValidation);
                        //schema = childSchema;
                        break;
                    }
                    case "BOOLEANLIST":
                    case "NUMBERLIST":
                    case "INTEGERLIST":
                    case "STRINGLIST": {
                        childSchema.setType("array");
                        JsonEntity items = new JsonEntity();
                        items.setType(type.toLowerCase().replaceAll("list", ""));
                        childSchema.setItems(items);

                        break;
                    }

                    case "JAVAOBJECTLIST": {
                        childSchema.setType("array");
                        JsonEntity items = new JsonEntity();
                        items.setType("object");
                        childSchema.setItems(items);

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
                        JsonEntity items = new JsonEntity();
                        items.setType("string");
                        items.setFormat("date");

                        childSchema.setItems(items);

                        break;
                    }
                    case "DATE": {
                        childSchema.setType("string");
                        childSchema.setFormat("date");
                        break;
                    }
                }
            }
            schema.setRequired(requiredFields);
        }
    }
}
