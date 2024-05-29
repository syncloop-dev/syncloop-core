package com.eka.middleware.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
//import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.eka.middleware.heap.HashMap;
import com.eka.middleware.flow.FlowUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class MapUtils {
	public static Object getValueByPointer(String pointer,final Object parentMap) {
		if (pointer == null || pointer.trim().length() == 0)
			return null;
		pointer = pointer.trim();
		Object obj = null;

		if (pointer.contains("#{")) {
			pointer = FlowUtils.resolveExpressions(pointer, parentMap);
		}

		pointer = "//" + pointer;
		pointer = pointer.replace("///", "").replace("//", "").replace("#", "");
		Map<String, Object> map = null;
		List<Object> arrayList = null;
		boolean isNumeric = false;
		String[] tokenize = pointer.split("/");
		for (String key : tokenize) {
			isNumeric = NumberUtils.isCreatable(key);
			if (obj != null && !isNumeric)
				map = (Map<String, Object>) obj;
			else if (isNumeric && (obj instanceof List || obj instanceof ArrayList))
				arrayList = (List<Object>) obj;
			if (map != null)
				obj = map.get(key);
			else if (arrayList != null) {
				int index = Integer.parseInt(key);
				if (arrayList.size() > index)
					obj = arrayList.get(index);
				else
					obj = null;
			} else if (isNumeric && obj != null) {
				int index = Integer.parseInt(key);
				obj = ((Object[]) obj)[index];
			} else
				if(parentMap instanceof DataPipeline)
					obj = ((DataPipeline)parentMap).get(key);
				else
					obj = ((Map)parentMap).get(key);

			map = null;
			arrayList = null;
			if (obj == null)
				return null;
		}
		try {
			ArrayDeque dVal = (ArrayDeque) obj;
			if (dVal != null && dVal.size() > 0)
				return dVal.getFirst();
		} catch (Exception e) {
			return obj;
		}
		return null;
	}
	
	public static void setValueByPointer(String pointer, Object value, String outTypePath, final Object parentMap) {
		Object obj = null;
		String path = "";
		Object preObj = parentMap;
		pointer = "//" + pointer;

		if (pointer.contains("#{")) {
			pointer = FlowUtils.resolveExpressions(pointer, parentMap);
		}

		pointer = pointer.replace("///", "").replace("//", "").replace("#", "");
		boolean isNumeric = false;
		String[] pointerTokens = pointer.split("/");
		String[] typeTokens = outTypePath.split("/");
		int tokenCount = pointerTokens.length;
		int typeIndex = 0;

		String valueType = (typeTokens[typeTokens.length - 1]).toLowerCase();
		switch (valueType) {
		case "integer":
			value = Integer.parseInt(value + "");
			break;
		case "number":
			value = Double.parseDouble(value + "");
			break;
		case "boolean":
			value = Boolean.parseBoolean(("1".equals(value + "") ? "true" : value + ""));
			break;
		}

		Object currentPayload=null;
		if (tokenCount == 1) {
			if(parentMap instanceof DataPipeline) {
				((DataPipeline)parentMap).put(pointerTokens[0], value);
			}
			else {
				((Map)parentMap).put(pointerTokens[0], value);
			}
			return;
		} else if(parentMap instanceof DataPipeline) {
			currentPayload=((DataPipeline)parentMap).getMap();
		}

		String key;
		for (int i = 0; i < tokenCount - 1; i++) {
			key = pointerTokens[i];
			isNumeric = NumberUtils.isCreatable(key);
			if (i == 0)
				path = key;
			else
				path += "/" + key;
			obj = getValueByPointer(path, currentPayload);
			if (obj == null) {
				if (preObj.getClass().toString().contains("ArrayList") && isNumeric) {
					int index = Integer.parseInt(key);
					if (index < 0) {
						index = Integer.MAX_VALUE;
					}
					List<Object> list = (List) preObj;
					Map<String, Object> map = new HashMap<String, Object>();
					if (list.size() > index)
						list.add(index, map);
					else
						list.add(map);
					obj = map;
					preObj = obj;
				} else if (typeTokens[typeIndex].toUpperCase().endsWith("LIST")) {
					List<Object> list = new ArrayList<Object>();
					if (preObj.getClass().toString().contains("DataPipeline")) {
						DataPipeline dp = (DataPipeline) preObj;
						dp.put(key, list);
						preObj = list;
					} else {
						Map<String, Object> map = (Map<String, Object>) preObj;
						map.put(key, list);
						preObj = list;
					}
				} else if (typeTokens[typeIndex].endsWith("document")) {
					obj = new HashMap<String, Object>();
					if (preObj.getClass().toString().contains("DataPipeline")) {
						DataPipeline dp = (DataPipeline) preObj;
						dp.put(key, obj);
					} else
						((Map) preObj).put(key, obj);
					preObj = obj;
				} else {
					preObj = new ArrayList<>();
					if(parentMap instanceof DataPipeline)
						((DataPipeline)parentMap).put(key, preObj);
					else
						((Map)parentMap).put(key, preObj);
				}
			} else {
				preObj = obj;
			}
			if (!isNumeric)
				typeIndex++;
		}
		key = pointerTokens[tokenCount - 1];
		isNumeric = NumberUtils.isCreatable(key);
		if (isNumeric) {
			List<Object> newObject = null;

			int index = Integer.parseInt(key);
			if (index < 0) {
				index = Integer.MAX_VALUE;
			}
			key = pointerTokens[tokenCount - 2];
			if (preObj != null){// && ((List<Object>) preObj).size() > index) {
				newObject = ((List<Object>) preObj);
			} else {//It may never enter the else block and even if it does then not sure what's the point. preObj will become a local unused object.
				switch (valueType) {
				case "integerlist":
				case "numberlist":
				case "booleanlist":
				case "stringlist":
				case "objectlist":
					newObject = new ArrayList<>();
					break;
				}
			}

			if ( index < newObject.size()) {
				newObject.remove(index);
				newObject.add(index, value);
			} else {
				newObject.add(value);
			}
			preObj = newObject;
		} else
			((Map) preObj).put(key, value);
	}
}
