package com.eka.middleware.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
//import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Context;

import com.eka.middleware.heap.HashMap;
import com.eka.middleware.pooling.ScriptEngineContextManager;
import com.eka.middleware.pub.util.document.Function;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.SystemException;

public class FlowUtils {
    private static Logger LOGGER = LogManager.getLogger(FlowUtils.class);
    private static final ScriptEngineManager factory = new ScriptEngineManager();
    private static final ScriptEngine engine = factory.getEngineByName("graal.js");

    public static String placeXPathValue(String xPaths, DataPipeline dp) throws SnippetException {
        try {
            String xPathValues = xPaths;
            String params[] = extractExpressions(xPaths);// xPaths.split(Pattern.quote("}"));
            if (params != null)
                for (String param : params) {
                    // if (param.contains("#{")) {
                    // param = param.split(Pattern.quote("#{"))[1];// replace("#{", "");
                    String val = dp.getValueByPointer(param) + "";
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
            sb.append(jsonValueLogger.toString());
            sb.append(jsonOpLogger.toString());
            sb.append(jsonValueLogger.toString());
            throw new SnippetException(dp, "Error while applying transformers.\n" + sb.toString(), e);
        }
    }

    public static String[] extractExpressions(String string) {
        String expressions[] = StringUtils.substringsBetween(string, "#{", "}");
        return expressions;
    }

    public static void setValue(JsonArray createList, DataPipeline dp) throws SnippetException {
        for (JsonValue jsonValue : createList) {
            String path = jsonValue.asJsonObject().getString("path", null);
            String typePath = jsonValue.asJsonObject().getString("typePath", null);
            String value = jsonValue.asJsonObject().getString("value", null);
            String evaluate = jsonValue.asJsonObject().getString("evaluate", null);
            if (evaluate != null && evaluate.trim().length() > 0) {
                Map<String, String> map = new HashMap<String, String>();
                String expressions[] = extractExpressions(value);
                if (null != expressions) {
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
							for (String expressionKey : expressions) {
								String expressionValue = dp.getValueByPointer(expressionKey) + "";
								if (expressionValue == null || expressionValue.equals("null")) {
									map.put(expressionKey, KeywordResolver.find(expressionKey, dp));
								} else {
									map.put(expressionKey, expressionValue);
								}
							}
							break;
						case "EPV": // Evaluate Package Variable
							for (String expressionKey : expressions) {
								String expressionValue = dp.getMyPackageConfig(expressionKey);
								if (expressionValue != null)
									map.put(expressionKey, expressionValue);
							}
							break;
					}
					for (String expressionKey : expressions) {
						if (map.get(expressionKey) != null)
							value = value.replace("#{" + expressionKey + "}", map.get(expressionKey));
						else
							throw new SnippetException(dp, "Property not set properly",
									new Exception("Could not resolve expression '#{" + expressionKey + "}'."));
					}
				}
            }

            String tokens[] = typePath.split(Pattern.quote("/"));
            String typeOfVariable = tokens[tokens.length - 1];
            if (!typeOfVariable.toUpperCase().contains("LIST") && !typeOfVariable.equals("string")) {
                try {
                    String threadSafeName = dp.getUniqueThreadName();
                    Object typeVal = eval(value, threadSafeName, typeOfVariable);
                    dp.setValueByPointer(path, typeVal, typePath);
                } catch (Exception e) {
                    throw new SnippetException(dp,
                            "Could not evaluate: value='" + value + "' and Type='" + typeOfVariable + "'", e);
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

                    String typePath = leader.getOutTypePath();
                    String tokens[] = typePath.split(Pattern.quote("/"));
                    String typeOfVariable = tokens[tokens.length - 1];
                    // Each mapping line can have a different function but will never be executed in
                    // concurrently. So we create a new function for each map using leader line id.
                    String functionName = "jsFunc_" + leader.getId() + "_applyLogic";
                    boolean isFunctionAvailable = false;

                    String jsFunction = function;
                    expressions = extractExpressions(function);
                    try {
                        Context ctx = ScriptEngineContextManager.findContext(threadSafeName);
                        if (ctx == null)
                            ctx = ScriptEngineContextManager.getContext(threadSafeName);
                        synchronized (ctx) {
                            try {
                                isFunctionAvailable = (boolean) eval("(" + functionName + "!=null);", ctx, "boolean");
                            } catch (Exception e) {
                                isFunctionAvailable = false;
                            }
                            if (!isFunctionAvailable || expressions != null) {
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
                            else
                                val = eval(functionName + "(" + val + ");", ctx, typeOfVariable);

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
                    return (int) ctx.eval("js", js).asLong();//(int)Bodmas.eval(js);//
                case "number":
                    return ctx.eval("js", js).asDouble();
                case "boolean":
                    return ctx.eval("js", js).asBoolean();
                case "byte":
                    return ctx.eval("js", js).asByte();
                case "object":
                    return ctx.eval("js", js);
                default:
                    return ctx.eval("js", js);
            }
        } catch (Exception e) {
            // System.out.println(e.getMessage());
            if (!e.getMessage().contains("applyLogic is not defined"))
                e.printStackTrace();
            return null;
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

    public static void validateDocuments(DataPipeline dp, JsonValue jv) throws SnippetException {
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
                validationParser(jsonValue, null, null, dp);
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

    private static void validationParser(JsonValue jsonValue, String key, String typePath, DataPipeline dp)
            throws SnippetException {

        String type = jsonValue.asJsonObject().getString("type");

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
                dataValidator(data, jv, k, typePath, dp);
            }
        } else
            dataValidator(data, jv, key, typePath, dp);
    }

    private static void dataValidator(JsonObject data, JsonValue jv, String key, String typePath, DataPipeline dp) throws SnippetException {
        if (jv != null && !jv.asJsonArray().isEmpty()) {
            JsonArray jva = jv.asJsonArray();
            for (JsonValue jsonVal : jva) {
                validationParser(jsonVal, key, typePath, dp);
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