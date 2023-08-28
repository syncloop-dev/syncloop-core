package com.eka.middleware.flow;

import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.apache.logging.log4j.Level;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;

public class Switch {
	private List<Scope> cases;
	private boolean disabled=false;
	private String condition;
	private String caseLabel;
	private String switchVariable;
	private JsonObject swich;
	private String switchXpath;
	private String snapshot=null;
	private String snapCondition=null;
	private JsonObject data=null;
	private String comment;
	public Switch(JsonObject jo) {
		swich=jo;		
		data=swich.get("data").asJsonObject();
		condition=swich.get("data").asJsonObject().getString("condition",null);
		String status=swich.get("data").asJsonObject().getString("status",null);
		disabled="disabled".equals(status);
		caseLabel=swich.get("data").asJsonObject().getString("label",null);
		switchXpath=swich.get("data").asJsonObject().getString("switch",null);
		snapshot=data.getString("snap",null);
		if(snapshot!=null && snapshot.equals("disabled"))
			snapshot=null;
		snapCondition=data.getString("snapCondition",null);
		comment=data.getString("comment",null);
	}

	public void process(DataPipeline dp) throws SnippetException {
		if (dp.isDestroyed())
			throw new SnippetException(dp, "User aborted the service thread", new Exception("Service runtime pipeline destroyed manually"));
		if (disabled)
			return;

		String snap = dp.getString("*snapshot");
		boolean canSnap = false;


		if (snap != null || snapshot != null) {
			canSnap = true;
			if (snapshot != null && snapshot.equals("conditional") && snapCondition != null) {
				canSnap = FlowUtils.evaluateCondition(snapCondition, dp);
				if (canSnap)
					dp.put("*snapshot", "enabled");
			} else {
				dp.put("*snapshot", "enabled");
			}
		}

		if (!canSnap)
			dp.drop("*snapshot");

		if (canSnap && snap == null) {
			dp.snap(comment);
		}

		String text = swich.get("data").asJsonObject().getString("text", null);
		JsonArray flows = swich.getJsonArray("children");
		String xPathValue = null;
		Object objVal = dp.getValueByPointer(switchXpath);
		boolean caseMatched = false;
		if (objVal != null) {
			 caseMatched = false;

			if (objVal instanceof List) {
				List<?> list = (List<?>) objVal;

				for (Object listItem : list) {
					 xPathValue = listItem.toString();

					for (JsonValue jsonValue : flows) {
						JsonObject jsonData = jsonValue.asJsonObject().get("data").asJsonObject();
						String caseLabel = jsonData.getString("case", null);

						if (caseLabel == null) {
							throw new SnippetException(dp, "Case label is required and cannot be empty.", new Exception("Exception in Switch CASE"));
						}

						if ("#default".equals(caseLabel)) {
							// Skip the default case during this loop
							continue;
						}

						String xVal = caseLabel;

						if (caseLabel.startsWith("#{")) {
							dp.log("The CASE with the xPath(" + caseLabel + ") is risky...", Level.WARN);
							String exps[] = FlowUtils.extractExpressions(caseLabel);

							if (exps.length > 0) {
								String pointer = exps[0];
								Object objLablVal = dp.getValueByPointer(pointer);

								if (objLablVal != null) {
									xVal = objLablVal.toString();
								}
							} else {
								xVal = caseLabel;
							}
						}

						if (xVal == null) {
							throw new SnippetException(dp, "The CASE with the xPath(" + caseLabel + ") has a null value.", new Exception("Exception in Switch CASE with reference."));
						}

						if (xPathValue.equals(xVal)) {
							Scope scope = new Scope(jsonValue.asJsonObject());
							scope.process(dp);
							caseMatched = true;
							break;
						}
					}

					if (caseMatched) {
						break;
					}
				}
			} else {
				 xPathValue = objVal.toString();

				for (JsonValue jsonValue : flows) {
					JsonObject jsonData = jsonValue.asJsonObject().get("data").asJsonObject();
					String caseLabel = jsonData.getString("case", null);

					if (caseLabel == null) {
						throw new SnippetException(dp, "Case label is required and cannot be empty.", new Exception("Exception in Switch CASE"));
					}

					if ("#default".equals(caseLabel)) {
						continue;
					}

					String xVal = caseLabel;
					if (caseLabel.startsWith("#{")) {
						dp.log("The CASE with the xPath(" + caseLabel + ") is risky...", Level.WARN);
						String exps[] = FlowUtils.extractExpressions(caseLabel);

						if (exps.length > 0) {
							String pointer = exps[0];
							Object objLablVal = dp.getValueByPointer(pointer);

							if (objLablVal != null) {
								xVal = objLablVal.toString();
							}
						} else {
							xVal = caseLabel;
						}
					}

					if (xVal == null) {
						throw new SnippetException(dp, "The CASE with the xPath(" + caseLabel + ") has a null value.", new Exception("Exception in Switch CASE with reference."));
					}

					if (xPathValue.equals(xVal)) {
						Scope scope = new Scope(jsonValue.asJsonObject());
						scope.process(dp);
						caseMatched = true;
						break;
					}
				}
			}

			if (!caseMatched) {
				JsonObject defaultCase = null;
				for (JsonValue jsonValue : flows) {
					JsonObject jsonData = jsonValue.asJsonObject().get("data").asJsonObject();
					String caseLabel = jsonData.getString("case", null);

					if ("#default".equals(caseLabel)) {
						defaultCase = jsonValue.asJsonObject();
						break;
					}
				}

				if (defaultCase != null) {
					Scope scope = new Scope(defaultCase);
					scope.process(dp);
				}
			}

			if (canSnap) {
				dp.snap(comment);
				dp.drop("*snapshot");
			} else if (snap != null) {
				dp.put("*snapshot", snap);
			}
		}
	}

		public List<Scope> getCases() {
		return cases;
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
