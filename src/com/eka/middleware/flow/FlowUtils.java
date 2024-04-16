package com.eka.middleware.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
//import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.shaded.gson.JsonElement;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import com.eka.middleware.heap.HashMap;
import com.eka.middleware.pooling.ScriptEngineContextManager;
import com.eka.middleware.pub.util.document.Function;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.MapUtils;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.SystemException;

public class FlowUtils {
    private static Logger LOGGER = LogManager.getLogger(FlowUtils.class);
    private static final ScriptEngineManager factory = new ScriptEngineManager();
    private static final ScriptEngine engine = factory.getEngineByName("graal.js");

    // Regular expression to find the innermost nested keys
    private static final String regex = "#\\{([^{}]+)\\}";
    private static final Pattern pattern = Pattern.compile(regex);

    public static String placeXPathValue(String xPaths, DataPipeline dp) throws SnippetException {
        try {
            String xPathValues = xPaths;
            Object obj=resolveExpressions(xPathValues, dp);
            Integer size = null;
            String val = "";
            if (null != obj && obj instanceof Map) {
                size = ((Map) obj).size();
            } else if (null != obj && (obj instanceof List)) {
                size = ((List) obj).size();
            } else  {
                val = obj + "";
            }

            if (null != size) {
                if (size == 0) {
                    val = "null";
                } else {
                    val = "true";
                }
            }
            
            xPathValues=val;
            return xPathValues;
            /*
            
            String params[] = extractExpressions(xPaths, dp);// xPaths.split(Pattern.quote("}"));
            if (params != null)
                for (String param : params) {
                    // if (param.contains("#{")) {
                    // param = param.split(Pattern.quote("#{"))[1];// replace("#{", "");
                    Object obj = dp.getValueByPointer(param);
                    String val = "";
                    Integer size = null;
                    if (null != obj && obj instanceof Map) {
                        size = ((Map) obj).size();
                    } else if (null != obj && (obj instanceof List)) {
                        size = ((List) obj).size();
                    } else  {
                        val = dp.getValueByPointer(param) + "";
                    }

                    if (null != size) {
                        if (size == 0) {
                            val = "null";
                        } else {
                            val = "true";
                        }
                    }

                    String value = val.replace("\"", "");
                    // System.out.println(value);
                    xPathValues = xPathValues.replace("#{" + param + "}", value);// cond=evaluatedParam+"="+value;
                    // }
                }
            return xPathValues;*/
        } catch (Exception e) {
            ServiceUtils.printException(dp, "Something went wrong while parsing xpath(" + xPaths + ")", e);
            throw new SnippetException(dp, "Something went wrong while parsing xpath(" + xPaths + ")", e);
        }
    }

    public static String placeXPathInternalVariables(String xPaths, DataPipeline dp) throws SnippetException {
        try {
            String xPathValues = xPaths;
            String params[] = extractExpressions(xPaths, dp);// xPaths.split(Pattern.quote("}"));
            if (params != null)
                for (String param : params) {
                    // if (param.contains("#{")) {
                    // param = param.split(Pattern.quote("#{"))[1];// replace("#{", "");
                    String val = KeywordResolver.find(param, dp);
                    String value = val.replace("\"", "");
                    // System.out.println(value);
                    xPathValues = xPathValues.replace("#{" + param + "}", value);// cond=evaluatedParam+"="+value;
                    // }
                }
            return xPathValues;
        } catch (Exception e) {
            ServiceUtils.printException(dp, "Something went wrong while parsing xpath(" + xPaths + ")", e);
            throw new SnippetException(dp, "Something went wrong while parsing xpath(" + xPaths + ")", e);
        }
    }

    public static boolean evaluateCondition(String condition, DataPipeline dp) throws SnippetException {
        if (StringUtils.isBlank(condition) || Boolean.parseBoolean(condition)) {
            return true;
        }
        String con = null;
        try {
            con = placeXPathValue(condition, dp);
            String threadSafeName = dp.getUniqueThreadName();
            return (boolean) eval(con, threadSafeName, "boolean");
        } catch (Exception e) {
            ServiceUtils.printException(dp, "Something went wrong while parsing condition(" + condition + "), " + con, e);
            throw new SnippetException(dp, "Something went wrong while parsing condition(" + condition + "), " + con,
                    e);

        }
    }

    public static void map(JsonArray transformers, DataPipeline dp) throws SnippetException {
        map(transformers, dp, null);
    }

    public static void mapBefore(JsonArray transformers, DataPipeline dp) throws SnippetException {
        map(transformers, dp, "in");
    }

    public static void mapAfter(JsonArray transformers, DataPipeline dp) throws SnippetException {
        map(transformers, dp, "out");
    }

    private static void map(JsonArray transformers, DataPipeline dp, String direction) throws SnippetException {
        JsonOp jsonValueLogger = null;
        JsonOp jsonOpLogger = null;
        try {
            Map<String, List<JsonOp>> map = split(transformers, direction);
            List<JsonOp> leaders = map.get("leaders");
            List<JsonOp> followers = map.get("followers");
            List<JsonOp> follows = new ArrayList<JsonOp>();
            boolean successful = true;

            for (JsonOp jsonValue : leaders) {
                jsonValueLogger = jsonValue;
                String loop_id = jsonValue.getLoop_id();
                if (loop_id != null && loop_id.startsWith("loop_id")) {
                    int index = 0;
                    while (successful) {
                        follows.clear();
                        for (JsonOp jsonOp : followers) {
                            jsonOpLogger = jsonOp;
                            follows.add(jsonOp.clone());
                        }
                        successful = transform(follows, loop_id, index + "", dp);
                        index++;
                    }
                } else {
                    transform(jsonValue, dp);
                }
            }
        } catch (Exception e) {
            StringBuffer sb = new StringBuffer();
            sb.append(jsonValueLogger + "");
            sb.append(jsonOpLogger + "");
            throw new SnippetException(dp, "Error while applying transformers.\n" + sb.toString(), e);
        }
    }

    public static String[] extractExpressions(String string, DataPipeline dataPipeline) {
        String expressions[] = StringUtils.substringsBetween(string, "#{", "}");
        return expressions;
    }

    public static String resolveExpressions(String input, final Object parentMap) {
        // Continuously find and replace innermost keys
        boolean found;
        do {
            Matcher matcher = pattern.matcher(input);
            StringBuffer sb = new StringBuffer();
            found = false;

            while (matcher.find()) {
                found = true;
                String key = matcher.group(1); // Extract the key
                Object value = MapUtils.getValueByPointer(key, parentMap);
                if(value==null)
                	value="null";
                if(value instanceof Map || value instanceof List) {
                	value=ServiceUtils.toJson(value);
                }
                ; // Get its value
                if(value.equals("null") && parentMap instanceof DataPipeline) {
                    DataPipeline dp=(DataPipeline)parentMap;
                    value=KeywordResolver.find(key, (DataPipeline)parentMap);
                    if(value==null)
                        throw new RuntimeException("Property not set for the key: "+key);
                    Object valueByPointer = dp.getValueByPointer(key);
                    //String expressionValue = typeOfVariable.equalsIgnoreCase("stringList") ? ServiceUtils.toJson(valueByPointer) : valueByPointer + "";
                }
                matcher.appendReplacement(sb, value.toString()); // Replace the key with its value
            }
            matcher.appendTail(sb);
            input = sb.toString();
        } while (found);

        return input;
    }

    public static void setValue(JsonArray createList, DataPipeline dp) throws SnippetException {
        for (JsonValue jsonValue : createList) {
            String path = jsonValue.asJsonObject().getString("path", null);
            String typePath = jsonValue.asJsonObject().getString("typePath", null);
            String value = jsonValue.asJsonObject().getString("value", null);
            String evaluate = jsonValue.asJsonObject().getString("evaluate", null);
            String tokens[] = typePath.split(Pattern.quote("/"));
            String typeOfVariable = tokens[tokens.length - 1];

            if (evaluate != null && evaluate.trim().length() > 0) {
                Map<String, String> map = new HashMap<String, String>();
                String expressions[] = extractExpressions(value, dp);
                if (expressions != null) {
					switch (evaluate) {
						case "ELV": // Evaluate Local Variable
							for (String expressionKey : expressions) {
								String expressionValue = dp.getMyConfig(expressionKey);
								if (expressionValue != null)
									map.put(expressionKey, expressionValue);
							}
							break;
						case "EGV": // Evaluate Global Variable
							for (String expressionKey : expressions) {
								String expressionValue = dp.getGlobalConfig(expressionKey);
								if (expressionValue != null)
									map.put(expressionKey, expressionValue);
							}
							break;
						case "EEV": // Evaluate Expression Variable
                            value = resolveExpressions(value, dp);
							/*for (String expressionKey : expressions) {
                                Object valueByPointer = dp.getValueByPointer(expressionKey);
								String expressionValue = typeOfVariable.equalsIgnoreCase("stringList") ? ServiceUtils.toJson(valueByPointer) : valueByPointer + "";
								if (expressionValue == null || expressionValue.equals("null")) {
									map.put(expressionKey, KeywordResolver.find(expressionKey, dp));
								} else {
									map.put(expressionKey, expressionValue);
								}
							}*/
							break;
						case "EPV": // Evaluate Package Variable
							for (String expressionKey : expressions) {
								String expressionValue = dp.getMyPackageConfig(expressionKey);
								if (expressionValue != null)
									map.put(expressionKey, expressionValue);
							}
							break;
					}
                    if(!evaluate.equals("EEV")) {
                        for (String expressionKey : expressions) {
                            if (map.get(expressionKey) != null)
                                value = value.replace("#{" + expressionKey + "}", map.get(expressionKey));
                            else
                                throw new SnippetException(dp, "Property not set properly",
                                        new Exception("Could not resolve expression '#{" + expressionKey + "}' for " + path + "."));
                        }
                    }
				}
            }


            if ((!typeOfVariable.toUpperCase().contains("LIST") ||
                    typeOfVariable.toUpperCase().equals("INTEGERLIST") ||
                    typeOfVariable.toUpperCase().equals("STRINGLIST") ||
                    typeOfVariable.toUpperCase().equals("NUMBERLIST") ||
                    typeOfVariable.toUpperCase().equals("BOOLEANLIST")) && !typeOfVariable.equals("string") && evaluate != null && evaluate.trim().length() > 0) {
                try {
                    String threadSafeName = dp.getUniqueThreadName();
                    Object typeVal = eval(value, threadSafeName, typeOfVariable);
                    dp.setValueByPointer(path, typeVal, typePath);
                } catch (Exception e) {
                    throw new SnippetException(dp,
                            "Could not evaluate: path = '" + path + "' value='" + value + "' and Type='" + typeOfVariable + "'", e);
                }
            } else
                dp.setValueByPointer(path, value, typePath);
        }
    }

    public static void dropValue(JsonArray dropList, DataPipeline dp) {
        for (JsonValue jsonValue : dropList) {
            String path = jsonValue.asJsonObject().getString("path", null);
            path = ("//" + path + "//").replace("///", "").replace("//", "");
            dp.drop(path);
            // String typePath=jsonValue.asJsonObject().getString("typePath",null);
            String tokens[] = path.split("/");
            String key = tokens[tokens.length - 1];
            String parentPath = (path + "_#").replace(key + "_#", "");
            Map<String, Object> map = (Map<String, Object>) dp.getAsMap(parentPath);
            if (map != null)
                map.remove(key);
        }
    }

    private static boolean transform(List<JsonOp> followers, String loop_id, String index, DataPipeline dp)
            throws Exception {
        boolean successful = true;
        try {
            List<JsonOp> follows = getFollowersById(followers, loop_id, index);
            if (follows.size() == 0)
                successful = false;
            for (JsonOp jsonOp : follows) {
                String f_loop_id = jsonOp.getLoop_id();
                if (f_loop_id != null && f_loop_id.startsWith("loop_id")) {
                    int f_index = 0;
                    while (successful) {
                        successful = transform(followers, loop_id, f_index + "", dp);
                        f_index++;
                    }
                } else
                    successful = transform(jsonOp, dp);
            }

        } catch (Exception e) {
            throw new Exception("");
        }
        return successful;
    }


    private static boolean transform(JsonOp leader, DataPipeline dp) throws Exception {
        boolean successful = true;
        String op = leader.getOp();
        try {
            boolean canCopy = true;
            if (leader.getCondition() != null && leader.getCondition().trim().length() > 0)
                canCopy = evaluateCondition(leader.getCondition(), dp);
            if (canCopy) {
                String expressions[] = null;
                String function = leader.getJsFunction();

                if (function != null && function.trim().length() > 0) {
                    String threadSafeName = dp.getUniqueThreadName();
                    try {
                        function = new String(Base64.getDecoder().decode(function));
                        function = function.replaceAll("[\\r\\n]+", "");
                    } catch (Exception e) {
                        ServiceUtils.printException(dp, function, e);
                        throw e;
                    }

                    String inPath = leader.getInTypePath();
                    String inTokens[] = inPath.split(Pattern.quote("/"));
                    String typeOfInVariable = inTokens[inTokens.length - 1];

                    String typePath = leader.getOutTypePath();
                    String tokens[] = typePath.split(Pattern.quote("/"));
                    String typeOfVariable = tokens[tokens.length - 1];
                    // Each mapping line can have a different function but will never be executed in
                    // concurrently. So we create a new function for each map using leader line id.
                    String functionName = "jsFunc_" + leader.getId() + "_applyLogic";
                    boolean isFunctionAvailable = false;

                    String jsFunction = function;
                    expressions = extractExpressions(function, dp);
                    try {
                        Context ctx = ScriptEngineContextManager.findContext(threadSafeName);
                        if (ctx == null)
                            ctx = ScriptEngineContextManager.getContext(threadSafeName);
                        synchronized (ctx) {
                            isFunctionAvailable = (leader.getApplyFunction()!=null && leader.getApplyFunction().trim().length()>1 && !leader.getApplyFunction().trim().toLowerCase().equals("none"));
                            if (isFunctionAvailable || expressions != null) {
                                if (expressions != null)
                                    for (String expressionKey : expressions)
                                        jsFunction = function.replace("#{" + expressionKey + "}",
                                                "" + dp.getValueByPointer(expressionKey));
                                jsFunction = "var " + functionName + "=function(val){" + jsFunction + "};";

                                // if(!typeOfVariable.toUpperCase().contains("LIST") &&
                                // !typeOfVariable.equals("string"))

                                eval(jsFunction, ctx, "string");
                            }
                            Object val = dp.getValueByPointer(leader.getFrom());
                            // System.out.println(val.getClass());
                            if (val == null)
                                val = eval(functionName + "();", ctx, typeOfVariable);
                            else if (typeOfVariable.equals("string"))
                                val = eval(functionName + "('" + val + "');", ctx, typeOfVariable);
                            else {
                                Object jdata = val.toString();
                                if (typeOfInVariable.equalsIgnoreCase("number") ||
                                        typeOfInVariable.equalsIgnoreCase("boolean") ||
                                        typeOfInVariable.equalsIgnoreCase("integer")) {
                                    switch (typeOfInVariable.toLowerCase()) {
                                        case "number":
                                            jdata = Double.parseDouble(val.toString());
                                            break;
                                        case "boolean":
                                            jdata = Boolean.parseBoolean(val.toString());
                                            break;
                                        case "integer":
                                            jdata = Integer.parseInt(val.toString());
                                            break;
                                    }
                                } else {
                                    jdata=ServiceUtils.toJson(val);
                                }
                            	val = eval(functionName + "(" + jdata + ");", ctx, typeOfVariable);//ServiceUtils.toJson(val)
                            }
                            if (val != null)
                                dp.setValueByPointer(leader.getTo(), val, leader.getOutTypePath());
                        }
                    } catch (Exception e) {
                        ServiceUtils.printException(dp, "Some problem executing this javascript: " + function, e);
                        throw e;
                    }
                } else
                    successful = copy(leader.getFrom(), leader.getTo(), leader.getOutTypePath(), dp);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ServiceUtils.printException(dp,
                    "Failed to perform " + op + " from '" + leader.getFrom() + "' to '" + leader.getTo() + "'", e);
            throw new Exception(
                    "Failed to perform " + op + " from '" + leader.getFrom() + "' to '" + leader.getTo() + "'");
        }
        return successful;
    }

    private static Object eval(String js, String name) throws Exception {
        return eval(js, name, "string");
    }

    private static Object eval(String js, String name, String returnType) throws Exception {

        if (name != null) {
            Context ctx = ScriptEngineContextManager.getContext(name);
            synchronized (ctx) {
                return eval(js, ctx, returnType);
            }
        } else
            return null;
    }

    private static Object eval(String js, Context ctx, String returnType) {
        try {
            switch (returnType) {
                case "string":
                    return ctx.eval("js", js).asString();
                case "integer":
                	Value value=ctx.eval("js", js);
                	if(value.fitsInDouble())
                		return (int) value.asDouble();
                    if(value.fitsInLong())
                        return (int) value.asLong();//(int)Bodmas.eval(js);//
                	else {
                        String v = value.asString();
//                        return (int) value.asInt();//(int)Bodmas.eval(js);//
                        return Integer.parseInt(v);
                    }
                case "number":
                    Value doubleVal=ctx.eval("js", js);
                    if(doubleVal.fitsInDouble())
                        return doubleVal.asDouble();
                    else {
                        return Double.parseDouble(doubleVal.asString());
                    }

                case "boolean":
                    return ctx.eval("js", js).asBoolean();
                case "byte":
                    return ctx.eval("js", js).asByte();
                case "integerList":
                    Value result = ctx.eval("js", js);
                    if (result.hasArrayElements()) {
                        int size = (int)result.getArraySize();
                        Integer[] intArray = new Integer[size];
                        ArrayList<Integer> integers = new ArrayList<>();
                        for (int i = 0; i < intArray.length; i++) {
                            Value v = result.getArrayElement(i);
                            if(v.fitsInInt()) {
                                integers.add(v.asInt());
                            } else {
                                String val = v.asString();
                                Double dVal = Double.parseDouble(val);
                                integers.add(dVal.intValue());
                            }

                        }
                        return integers;
                    }
                    return null;
                case "stringList":
                    result = ctx.eval("js", js);
                    if (result.hasArrayElements()) {
                        int size = (int)result.getArraySize();
                        String[] array = new String[size];
                        ArrayList<String> strings = new ArrayList<>();
                        for (int i = 0; i < array.length; i++) {
                            strings.add(result.getArrayElement(i).asString());
                        }
                        return strings;
                    }
                    return null;
                case "numberList":
                    result = ctx.eval("js", js);
                    if (result.hasArrayElements()) {
                        int size = (int)result.getArraySize();
                        Double[] array = new Double[size];
                        ArrayList<Double> doubles = new ArrayList<>();
                        for (int i = 0; i < array.length; i++) {
                            Value v = result.getArrayElement(i);
                            if(v.fitsInDouble()) {
                                doubles.add(v.asDouble());
                            } else {
                                String val = v.asString();
                                Double dVal = Double.parseDouble(val);
                                doubles.add(dVal);
                            }
                        }
                        return doubles;
                    }
                    return null;
                case "booleanList":
                    result = ctx.eval("js", js);
                    if (result.hasArrayElements()) {
                        int size = (int)result.getArraySize();
                        Boolean[] array = new Boolean[size];
                        ArrayList<Boolean> booleans = new ArrayList<>();
                        for (int i = 0; i < array.length; i++) {
                            booleans.add(result.getArrayElement(i).asBoolean());
                        }
                        return booleans;
                    }
                    return null;
                case "object":
                default:
                	result=ctx.eval("js", js);
                    return ServiceUtils.convertPolyglotValue(result);
            }
        } catch (Exception e) {
            // System.out.println(e.getMessage());
            if (!e.getMessage().contains("applyLogic is not defined"))
                e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void resetJSCB(String resource) throws SystemException {
        try {
            String jsClass = "cb_" + (resource.hashCode() & 0xfffffff);
            ScriptEngineContextManager.getContext(jsClass);
            // String varObj = "var " + jsClass + "={}";
            // ScriptEngineContextManager.removeContext(jsClass);
            // eval(varObj);
        } catch (Exception e) {
            throw new SystemException("EKA_MWS_1003", e);
        }
    }

    public static Map<String, List<JsonOp>> split(JsonArray transformers, String direction) throws Exception {
        String follow = "";
        List<JsonOp> leaders = new ArrayList<>();
        List<JsonOp> followers = new ArrayList<>();
        for (JsonValue jsonValue : transformers) {
            follow = jsonValue.asJsonObject().getString("follow", null);
            boolean canProceed = true;
            String direct = jsonValue.asJsonObject().getString("direction", null);
            if (direction != null && direction.trim().length() > 0 && direct != null && direct.trim().length() > 0
                    && !direction.equals(direct))
                canProceed = false;
            if (canProceed) {
                if (follow != null && follow.startsWith("loop_id"))
                    followers.add(new JsonOp(jsonValue));
                else
                    leaders.add(new JsonOp(jsonValue));
            }
        }
        Map<String, List<JsonOp>> map = new HashMap<>();
        map.put("leaders", leaders);
        map.put("followers", followers);
        return map;
    }

    private static List<JsonOp> getFollowersById(List<JsonOp> followers, String loop_id, String index) {
        List<JsonOp> followersById = new ArrayList<JsonOp>();
        for (JsonOp jsonValue : followers) {
            String follow = jsonValue.getFollow();
            jsonValue.applyIndex(index, loop_id);
            if (loop_id.equals(follow))
                followersById.add(jsonValue);
        }
        return followersById;
    }

    private static boolean copyOp(JsonValue copyOp, DataPipeline dp) {
        String from = copyOp.asJsonObject().getString("from");
        String to = copyOp.asJsonObject().getString("to");
        String toTypePath = copyOp.asJsonObject().getString("outTypePath");
        return copy(from, to, toTypePath, dp);
    }

    private static boolean copy(String from, String to, String toTypePath, DataPipeline dp) {
        Object val = dp.getValueByPointer(from);
        if (val == null)
            return false;
        dp.setValueByPointer(to, val, toTypePath);
        return true;
    }

    public static void validateDocuments(DataPipeline dp, JsonValue jv, Boolean validationRequired) throws SnippetException {
         JsonArray jva = jv.asJsonArray();
        if (jva.isEmpty())
            return;

        /*
         * Exception e=new
         * Exception("Service document validation is enabled but Input/Output document is null or empty"
         * ); ServiceUtils.
         * printException("Service document validation is enabled but Input/Output document is null or empty"
         * , e); throw new SnippetException(dp,
         * "Service document validation is enabled but Input/Output document is null or empty"
         * , e);
         */

        final Map<String, String> mapPointers = new HashMap<>();
        final Map<String, JsonObject> mapPointerData = new HashMap<>();
        if (jva != null) {
            // outPutData=
            for (JsonValue jsonValue : jva) {
                //getKeyTypePair(jsonValue, null, null, mapPointers, mapPointerData);
                validationParser(jsonValue, null, null, dp, validationRequired);
            }
        }/*
		dp.log("Document pointers:-");// , Level.TRACE);
		mapPointers.forEach((k, v) -> dp.log(k + " : " + v));// , Level.TRACE));
		dp.log("Document data:-", Level.TRACE);
		mapPointerData.forEach((k, v) -> dp.log(k + " : " + v.toString()));// , Level.TRACE));

		Set<String> keys = mapPointers.keySet();
		for (String key : keys) {
			String typePath = mapPointers.get(key);
			JsonObject data = mapPointerData.get(key);
			functionalValidation(dp, data, key, typePath);
		}*/
    }

    private static void functionalValidation(DataPipeline dp, JsonObject data, String key, String typePath)
            throws SnippetException {
        if (data != null && !data.isEmpty()) {
            Map<String, Object> m = ServiceUtils.jsonToMap(data.toString());
            final Map<String, String> dataMap = new HashMap<String, String>();
            m.forEach((k, v) -> {
                if (v != null && (v + "").trim().length() > 0)
                    dataMap.put(k, v + "");
            });
            Object object = dp.getValueByPointer(key);
            Function.validate(dp, key, typePath, dataMap, object);
        }
    }

    private static void validationParser(JsonValue jsonValue, String key, String typePath, DataPipeline dp, Boolean validationRequired)
            throws SnippetException {

        JsonArray assignList = null;
        if (jsonValue.asJsonObject().get("data") != null){
            assignList = jsonValue.asJsonObject().get("data").asJsonObject().getJsonArray("assignList");
        }
        String type = jsonValue.asJsonObject().getString("type");

        String dpKey=jsonValue.asJsonObject().getString("text");
        Object val=dp.get(dpKey);

        if (type.equals("document")) {
            JsonArray childrenArray = jsonValue.asJsonObject().getJsonArray("children");
            processChildren(childrenArray,dpKey,dp);
        }

        if (null != assignList && (null == val || StringUtils.isBlank(val.toString()))) {
            setValue(assignList, dp);
        }

        if (key == null)
            key = jsonValue.asJsonObject().getString("text");
        else
            key += "/" + jsonValue.asJsonObject().getString("text");

        if (typePath == null)
            typePath = type;
        else
            typePath += "/" + type;
        JsonObject data = jsonValue.asJsonObject().getJsonObject("data");
        JsonValue jv = jsonValue.asJsonObject().get("children");
        List<Object> list = null;
        if (type.endsWith("List")) {
            Object o = dp.get(key);
            if (o instanceof String[]) {
                list = Arrays.stream((String[]) o).collect(Collectors.toList());
            } else {
                list = (List<Object>) o;
            }
        }
        if (list != null && list.size() > 0) {
            for (int i = 0; i < list.size(); i++) {
                String k = key + "/" + i;
                if (validationRequired) {
                    dataValidator(data, jv, k, typePath, dp, true);
                }
            }
        } else {
            if (validationRequired)
                dataValidator(data, jv, key, typePath, dp, true);
        }
    }

    /**
     * @param childrenArray
     * @param dp
     * @throws SnippetException
     */
    private static void processChildren(JsonArray childrenArray, String parentKey ,DataPipeline dp) throws SnippetException {
        for (JsonValue jsonValue : childrenArray) {
            if (jsonValue instanceof JsonObject) {

                JsonArray assignList = null;
                if (jsonValue.asJsonObject().get("data") != null){
                    assignList = jsonValue.asJsonObject().get("data").asJsonObject().getJsonArray("assignList");
                }
                String type = jsonValue.asJsonObject().getString("type");

                String dpKey=parentKey + "/" + jsonValue.asJsonObject().getString("text");
                Object val=dp.getValueByPointer(dpKey);

                if (type.equals("document")) {
                    processChildren(jsonValue.asJsonObject().getJsonArray("children"), dpKey, dp);
                }

                if (null != assignList && (null == val || StringUtils.isBlank(val.toString()))) {
                    setValue(assignList, dp);
                }

            }
        }
    }
    private static void dataValidator(JsonObject data, JsonValue jv, String key, String typePath, DataPipeline dp, Boolean validationRequired) throws SnippetException {
        if (jv != null && !jv.asJsonArray().isEmpty()) {
            JsonArray jva = jv.asJsonArray();
            for (JsonValue jsonVal : jva) {
                validationParser(jsonVal, key, typePath, dp, validationRequired);
            }
        } else {
            functionalValidation(dp, data, key, typePath);
        }
    }

    /*
     * public static String getAbsolutePath(String pointer, String typePath) {
     * pointer = "//" + pointer; pointer = pointer.replace("///", "").replace("//",
     * "").replace("#", ""); String tokenizeType[] = typePath.split("/"); String[]
     * tokenize = pointer.split("/"); String absPath = ""; if (tokenizeType.length
     * == 1) absPath = pointer; else { for (int i = 0; i < tokenize.length; i++) {
     * String key = tokenize[i]; String type = tokenizeType[i]; absPath=key;
     * if(type.endsWith("List")) { absPath+="" } } } }
     */

    private static void getKeyTypePair(JsonValue jsonValue, String key, String typePath,
                                       Map<String, String> mapPointers, Map<String, JsonObject> mapPointerData) {
        if (key == null)
            key = jsonValue.asJsonObject().getString("text");
        else
            key += "/" + jsonValue.asJsonObject().getString("text");
        if (typePath == null)
            typePath = jsonValue.asJsonObject().getString("type");
        else
            typePath += "/" + jsonValue.asJsonObject().getString("type");

        JsonObject data = jsonValue.asJsonObject().getJsonObject("data");
        mapPointers.put(key, typePath);
        mapPointerData.put(key, data);
        JsonValue jv = jsonValue.asJsonObject().get("children");
        if (jv != null) {
            JsonArray jva = jv.asJsonArray();
            for (JsonValue jsonVal : jva) {
                getKeyTypePair(jsonVal, key, typePath, mapPointers, mapPointerData);
            }
        }
    }

    public static boolean patternMatches(String text, String regexPattern) {
        return Pattern.compile(regexPattern).matcher(text).matches();
    }

}