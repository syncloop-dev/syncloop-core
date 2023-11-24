package com.eka.middleware.flow;

import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.FlowBasicInfo;
import com.eka.middleware.template.SnippetException;
import com.google.common.collect.Maps;
import lombok.Getter;

public class IfElse implements FlowBasicInfo {
	private List<Scope> conditions;
	private boolean disabled = false;
	private String condition;
	private String caseLabel;
	// private String switchVariable;
	private JsonObject ifelse;
	// private String switchXpath;
	private String snapshot = null;
	private String snapCondition = null;
	private JsonObject data = null;
	private String comment;

	@Getter
	private String name;

	@Getter
	private String type;

	@Getter
	private String guid;

	public IfElse(JsonObject jo) {
		ifelse = jo;
		data = ifelse.get("data").asJsonObject();
		condition = ifelse.get("data").asJsonObject().getString("condition", null);
		String status = ifelse.get("data").asJsonObject().getString("status", null);
		disabled = "disabled".equals(status);
		caseLabel = ifelse.get("data").asJsonObject().getString("label", null);
		// switchXpath=ifelse.get("data").asJsonObject().getString("switch",null);
		snapshot = data.getString("snap", null);
		if (snapshot != null && snapshot.equals("disabled"))
			snapshot = null;
		snapCondition = data.getString("snapCondition", null);
		comment = data.getString("comment", null);

		guid = data.getString("guid",null);
		name = ifelse.getString("text",null);
		type = ifelse.getString("type",null);
	}

	public void process(DataPipeline dp) throws SnippetException {
		Map<String, Object> snapMeta = Maps.newHashMap();
		if (dp.isDestroyed()) {
			throw new SnippetException(dp, "User aborted the service thread",
					new Exception("Service runtime pipeline destroyed manually"));
		}
		if (disabled) {
			return;
		}
		dp.addErrorStack(this);
		String snap = dp.getString("*snapshot");
		boolean canSnap = false;
		if (snap != null || snapshot != null) {
			canSnap = true;
			// snap=snapshot;
			if (snapshot != null && snapshot.equals("conditional") && snapCondition != null) {
				canSnap = FlowUtils.evaluateCondition(snapCondition, dp);
				if (canSnap)
					dp.put("*snapshot", "enabled");
			} else
				dp.put("*snapshot", "enabled");
		}
		/*if (!canSnap)
			dp.drop("*snapshot");*/
		if (canSnap ) {
			dp.snapBefore(comment, guid);
		}
		try {
			//String text = ifelse.get("data").asJsonObject().getString("text", null);
			JsonArray flows = ifelse.getJsonArray("children");

			for (JsonValue jsonValue : flows) {
				String ifLogic = jsonValue.asJsonObject().get("data").asJsonObject().getString("ifcondition", null);
				boolean result = false;

				JsonObject jov=jsonValue.asJsonObject().get("data").asJsonObject();
				String status=jov.getString("status",null);
				if("disabled".equalsIgnoreCase(status))
					continue;

				// ifLogic=xVal;
				snapMeta.put("IF_ELSE_CONDITION", ifLogic);
				if ("#default".equals(ifLogic.trim()) || "#else".equals(ifLogic.trim())) {
					Scope scope = new Scope(jsonValue.asJsonObject());
					scope.process(dp);
					break;
				} else {
					result = FlowUtils.evaluateCondition(ifLogic, dp);
					snapMeta.put("IF_ELSE_CONDITION_EVAL", result);
					if (result) {
						Scope scope = new Scope(jsonValue.asJsonObject());
						scope.process(dp);
						break;
					}
				}
			}
			dp.putGlobal("*hasError", false);
		} catch (Exception e) {
			dp.putGlobal("*error", e.getMessage());
			dp.putGlobal("*hasError", true);
			throw e;
		} finally {
			if (canSnap) {
				dp.snapAfter(comment, guid, snapMeta);
				if (null != snapshot || null != snapCondition) {
					dp.drop("*snapshot");
				}
			} else if (snap != null)
				dp.put("*snapshot", snap);
		}
	}

	public List<Scope> getCases() {
		return conditions;
	}

	public void setCases(List<Scope> cases) {
		this.conditions = cases;
	}

	public boolean isDisabled() {
		return disabled;
	}

	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}

	public String getCondition() {
		return condition;
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	public String getLabel() {
		return caseLabel;
	}

	public void setLabel(String label) {
		this.caseLabel = label;
	}
}
