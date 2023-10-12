package com.eka.middleware.flow;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.FlowBasicInfo;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;
import com.google.common.collect.Maps;
import lombok.Getter;

public class TCFBlock implements FlowBasicInfo {
	private Scope TRY;
	private Scope CATCH;
	private Scope FINALLY;
	private String label;
	private boolean disabled=false;
	private String condition;
	private JsonObject tcfBlock;
	private String comment;
	private JsonObject data=null;
	private String snapshot=null;
	private String snapCondition=null;

	@Getter
	private String name;

	@Getter
	private String type;

	@Getter
	private String guid;

	public TCFBlock(JsonObject jo) {
		tcfBlock=jo;	
		data=tcfBlock.get("data").asJsonObject();
		condition=data.getString("condition",null);
		String status=data.getString("status",null);
		disabled="disabled".equals(status);
		label=data.getString("label",null);
		comment=data.getString("comment",null);
		snapshot=data.getString("snap",null);
		if(snapshot!=null && snapshot.equals("disabled"))
			snapshot=null;
		snapCondition=data.getString("snapCondition",null);

		guid = data.getString("guid",null);
		name = tcfBlock.getString("text",null);
		type = tcfBlock.getString("type",null);
	}
	
	public void process(DataPipeline dp) throws SnippetException {
		if(dp.isDestroyed())
			throw new SnippetException(dp, "User aborted the service thread", new Exception("Service runtime pipeline destroyed manually"));
		if(disabled)
			return;
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
		if(!canSnap)
			dp.drop("*snapshot");
		if(canSnap && snap==null) {
			dp.snapBefore(comment, guid);
		}
		try {
			JsonArray scopes=tcfBlock.getJsonArray("children");
			for (JsonValue scope : scopes) {
				String text=scope.asJsonObject().getString("text",null);
				switch(text) {
					case "TRY":
						TRY=new Scope(scope.asJsonObject());
						break;
					case "CATCH":
						CATCH=new Scope(scope.asJsonObject());
						break;
					case "FINALLY":
						FINALLY=new Scope(scope.asJsonObject());
						break;
				}
			}
			try {
				TRY.process(dp);
			} catch (Exception e) {
				dp.putGlobal("lastErrorDump", ServiceUtils.getExceptionMap(e));
				CATCH.process(dp);
			}finally {
				FINALLY.process(dp);
			}
			dp.putGlobal("hasError", false);
		} catch (Exception e) {
			dp.putGlobal("error", e.getMessage());
			dp.putGlobal("hasError", true);
			throw e;
		} finally {
			if(canSnap) {
				dp.snapAfter(comment, guid, Maps.newHashMap());
				dp.drop("*snapshot");
			}else if(snap!=null)
				dp.put("*snapshot",snap);
		}
	}
	
	public Scope getTRY() {
		return TRY;
	}
	public void setTRY(Scope tRY) {
		TRY = tRY;
	}
	public Scope getCATCH() {
		return CATCH;
	}
	public void setCATCH(Scope cATCH) {
		CATCH = cATCH;
	}
	public Scope getFINALLY() {
		return FINALLY;
	}
	public void setFINALLY(Scope fINALLY) {
		FINALLY = fINALLY;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
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
	
}
