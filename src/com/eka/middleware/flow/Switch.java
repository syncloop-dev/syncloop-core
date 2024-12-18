package com.eka.middleware.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.FlowBasicInfo;
import lombok.Getter;
import org.apache.logging.log4j.Level;

import com.eka.middleware.template.SnippetException;

public class Switch implements FlowBasicInfo {
	private List<Scope> cases;
	private boolean disabled = false;
	private String condition;
	private String caseLabel;
	private String switchVariable;
	private JsonObject swich;
	private String switchXpath;
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

	public Switch(JsonObject jo) {
		swich = jo;
		data = swich.get("data").asJsonObject();
		condition = swich.get("data").asJsonObject().getString("condition", null);
		String status = swich.get("data").asJsonObject().getString("status", null);
		disabled = "disabled".equals(status);
		caseLabel = swich.get("data").asJsonObject().getString("label", null);
		switchXpath = swich.get("data").asJsonObject().getString("switch", null);
		snapshot = data.getString("snap", null);
		if (snapshot != null && snapshot.equals("disabled"))
			snapshot = null;
		snapCondition = data.getString("snapCondition", null);
		comment = data.getString("comment", null);

		guid = data.getString("guid",null);
		name = swich.getString("text",null);
		type = swich.getString("type",null);
	}


	public void process(DataPipeline dp) throws SnippetException {
		Map<String, Object> snapMeta = new HashMap<String, Object>();
		snapMeta.put("switch", switchXpath);
		if(dp.isDestroyed()) {
			throw new SnippetException(dp, "User aborted the service thread", new Exception("Service runtime pipeline destroyed manually"));
		}
		if(disabled) {
			return;
		}
		dp.addErrorStack(this);
		String snap=dp.getString("*snapshot");
		boolean canSnap = false;
		if(snap!=null || snapshot!=null) {
			canSnap = true;
			//snap=snapshot;
			if(snapshot!=null && snapshot.equals("conditional") && snapCondition!=null){
				canSnap =FlowUtils.evaluateCondition(snapCondition,dp);
				if(canSnap)
					dp.put("*snapshot","enabled");
			}else
				dp.put("*snapshot","enabled");
		}
		canSnap = canSnap || dp.isRecordTrace();
		/*if(!canSnap)
			dp.drop("*snapshot");*/
		if(canSnap ) {
			dp.snapBefore(comment, guid);
		}
		try {
			String text=swich.get("data").asJsonObject().getString("text",null);
			JsonArray flows= swich.getJsonArray("children");
			String xPathValue=null;
			Object objVal=dp.getValueByPointer(switchXpath);// FlowUtils.placeXPathValue(switchXpath, dp);
			if(objVal!=null)
				xPathValue=objVal+"";
//		if(xPathValue==null)
//			throw new SnippetException(dp,"Switch label is null. xPath: "+switchXpath , new Exception("Exception in Switch block"));
			JsonObject defaultCase=null;
			JsonObject nullCase=null;
			for (JsonValue jsonValue : flows) {
				String caseLabel=jsonValue.asJsonObject().get("data").asJsonObject().getString("case",null);
				JsonObject jov=jsonValue.asJsonObject().get("data").asJsonObject();
				String status=jov.getString("status",null);
				if("disabled".equalsIgnoreCase(status))
					continue;
				if(caseLabel == null)
					throw new SnippetException(dp,"Case label is a required field. It can not be left empty. Use #null for null comparision, use !null(empty is not null) or !empty(null is also considered empty)." , new Exception("Exception in Switch CASE"));
				String xVal=null;
				if(caseLabel.startsWith("#{")) {
					dp.log("The CASE with the xPath("+caseLabel+") is risky. Please ensure that the xPath always exists and it must have a not null value. Otherwise an exception will be thrown.", Level.WARN);
					String exps[]=FlowUtils.extractExpressions(caseLabel, dp);
					if(exps.length>0) {
						String pointer=exps[0];
						Object objLablVal=dp.getValueByPointer(pointer);// FlowUtils.placeXPathValue(switchXpath, dp);
						if(objLablVal!=null)
							xVal=objLablVal+"";
					}else
						xVal=caseLabel;
				}else
					xVal=caseLabel;

				if(xVal == null)
					throw new SnippetException(dp,"The CASE with the xPath("+caseLabel+") has a null value." , new Exception("Exception in Switch CASE with reference."));

				caseLabel=xVal;
				snapMeta.put("caseLabel", caseLabel + "");
				snapMeta.put("SwitchValue", xPathValue);
				if("#null".equals(caseLabel) && xPathValue==null) {
					Scope scope=new Scope(jsonValue.asJsonObject());
					scope.process(dp);
					return;
				}else if(xPathValue!=null && xPathValue.equals(caseLabel)) {
					Scope scope=new Scope(jsonValue.asJsonObject());
					scope.process(dp);
					return;
				}else if(xPathValue!=null && caseLabel.toLowerCase().startsWith("#regex:") && FlowUtils.isMatch(caseLabel, dp, xPathValue)) {
					Scope scope=new Scope(jsonValue.asJsonObject());
					scope.process(dp);
					return;
				}else if("#default".equals(caseLabel)) {
					snapMeta.put("caseLabel", caseLabel + "");
					defaultCase=jsonValue.asJsonObject();
				}
			}
			if(defaultCase!=null) {
				Scope scope=new Scope(defaultCase.asJsonObject());
				scope.process(dp);
			}
			dp.putGlobal("*hasError", false);
		} catch (Exception e) {
			dp.putGlobal("*error", e.getMessage());
			dp.putGlobal("*hasError", true);
			throw e;
		} finally {
			if(canSnap) {
				dp.snapAfter(comment, guid, snapMeta);
				if (null != snapshot || null != snapCondition) {
					dp.drop("*snapshot");
				}
			}else if(snap!=null)
				dp.put("*snapshot",snap);
		}
	}

	public void setCases(List<Scope> cases) {
		this.cases = cases;
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
	public String getSwitchVariable() {
		return switchVariable;
	}
	public void setSwitchVariable(String switchVariable) {
		this.switchVariable = switchVariable;
	}
}
