package com.eka.middleware.flow;

import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.FlowBasicInfo;
import com.eka.middleware.template.SnippetException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

public class Api implements FlowBasicInfo {
	private boolean disabled=false;
	private boolean sync=true;
	private String fqn;
	private Transformer transformer;
	private String condition;
	private String label;
	private JsonObject api;
	private boolean evaluateCondition;
	private String comment;
	private JsonArray transformers;
	private JsonObject data=null;
	private JsonArray createList;
	private JsonArray dropList;
	private String requestMethod;
	private String snapshot=null;
	private String snapCondition=null;

	@Getter
	private String name;

	@Getter
	private String type;

	@Getter
	private String guid;

	public Api(JsonObject jo) {
		api=jo;
		data=api.get("data").asJsonObject();
		condition=data.getString("condition",null);
		String status=data.getString("status",null);
		disabled="disabled".equals(status);
		label=data.getString("label",null);
		evaluateCondition=data.getBoolean("evaluate",false);
		comment=data.getString("comment",null);
		snapshot=data.getString("snap",null);
		if(snapshot!=null && snapshot.equals("disabled"))
			snapshot=null;
		snapCondition=data.getString("snapCondition",null);
		requestMethod=data.getString("requestMethod","sync");
//		System.out.println(data.isNull("transformers"));
		if(!data.isNull("transformers"))
			transformers=data.getJsonArray("transformers");
		if(!data.isNull("createList"))
			createList=data.getJsonArray("createList");
		if(!data.isNull("dropList"))
			dropList=data.getJsonArray("dropList");

		guid = data.getString("guid",null);
		name = api.getString("text",null);
		type = api.getString("type",null);

	}
	public void process(DataPipeline dp) throws SnippetException {
		dp.addErrorStack(this);
		if(dp.isDestroyed())
			throw new SnippetException(dp, "User aborted the service thread", new Exception("Service runtime pipeline destroyed manually"));
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
		if(!canSnap)
			dp.drop("*snapshot");
		if(canSnap && snap==null) {
			dp.snap(comment);
		}
		if(disabled)
			return;
		//if(createList!=null)
		//FlowUtils.setValue(createList, dp);
		if(transformers!=null)
			FlowUtils.mapBefore(transformers, dp);
		String serviceFqn=data.getString("fqn", null);
		if (StringUtils.isBlank(serviceFqn)) {
			serviceFqn = api.getString("text",null);
		}
		if(serviceFqn!=null && serviceFqn.trim().length()>8) {
			if("async".equals(requestMethod))
				dp.applyAsync(serviceFqn.trim()+".main",transformers);
			else
				dp.apply(serviceFqn.trim()+".main");
			if(transformers!=null)
				FlowUtils.mapAfter(transformers, dp);
		}
		if(createList!=null)
			FlowUtils.setValue(createList, dp);
		if(dropList!=null)
			FlowUtils.dropValue(dropList, dp);// setValue(dropList, dp);

		if(canSnap) {
			dp.snap(comment);
			dp.drop("*snapshot");
		}else if(snap!=null)
			dp.put("*snapshot",snap);
	}

	public boolean isDisabled() {
		return disabled;
	}
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
	public boolean isSync() {
		return sync;
	}
	public void setSync(boolean sync) {
		this.sync = sync;
	}
	public String getFqn() {
		return fqn;
	}
	public void setFqn(String fqn) {
		this.fqn = fqn;
	}

	public String getCondition() {
		return condition;
	}
	public void setCondition(String condition) {
		this.condition = condition;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	public Transformer getTransformer() {
		return transformer;
	}
	public void setTransformer(Transformer transformer) {
		this.transformer = transformer;
	}
}
