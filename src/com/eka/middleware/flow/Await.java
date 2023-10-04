package com.eka.middleware.flow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.FlowBasicInfo;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;
import com.fasterxml.jackson.databind.node.BooleanNode;
import lombok.Getter;

public class Await implements FlowBasicInfo {
	private boolean disabled = false;
//	private String inputArrayPath;
//	private String outPutArrayPath;
	private String condition;
	private JsonObject await;
	private String label;
	private String comment;
	private JsonObject data;
	private String indexVar = "*index";
//	private String outArrayType = "document";
	private String snapshot=null;
	private String snapCondition=null;
	private long timeout_seconds_each_thread=60;

	@Getter
	private String name;

	@Getter
	private String type;

	@Getter
	private String guid;

	public Await(JsonObject jo) {
		await = jo;
		data = await.get("data").asJsonObject();
		setCondition(data.getString("condition", null));
		String status = data.getString("status", null);
		disabled = "disabled".equals(status);
		setLabel(data.getString("label", null));
		comment = data.getString("comment", null);
//		inputArrayPath = data.getString("inArray", null);
//		outPutArrayPath = data.getString("outArray", null);
		snapshot=data.getString("snap",null);
		if(snapshot!=null && snapshot.equals("disabled"))
			snapshot=null;
		snapCondition=data.getString("snapCondition",null);
		indexVar = data.getString("indexVar", "*index");
		String timeOut = data.getString("timeout_seconds_each_thread", null);
		if(timeOut!=null && timeOut.trim().length()>0) {
			try {
				timeout_seconds_each_thread=Long.parseLong(timeOut);
				if(timeout_seconds_each_thread<=0)
					timeout_seconds_each_thread=Long.MAX_VALUE;
			} catch (Exception e) {
				ServiceUtils.printException("On Await step timeout seconds value is not set properly hence setting default value of '"+timeout_seconds_each_thread+"'", e);
			}
		}
//		outArrayType = data.getString("outArrayType", "document");

		guid = data.getString("guid",null);
		name = await.getString("text",null);
		type = await.getString("type",null);
	}

	public void process(DataPipeline dp) throws SnippetException {
		if(dp.isDestroyed())
			throw new SnippetException(dp, "User aborted the service thread", new Exception("Service runtime pipeline destroyed manually"));
		if (disabled)
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
			final List<Map<String, Object>> list = dp.getFuture();
			if (list == null || list.size()<=0)
				return;
			final AtomicBoolean continueLoop=new AtomicBoolean();
			continueLoop.set(true);
			final AtomicInteger listSize=new AtomicInteger(list.size());
			final Long timeout_ms=timeout_seconds_each_thread*1000;
			while(continueLoop.get()) {
				try {
					//final DataPipeline dp=this;
					final AtomicInteger index=new AtomicInteger(0);
					//dp.put(indexVar, index.get());
					list.forEach(map->{
						//futureList.add(map);
						dp.clearServicePayload();
						int indexValue=index.getAndIncrement();
						dp.getServicePayload().put(indexVar, indexValue);
						Map<String, Object> asyncOutputDoc=map;
						final Map<String, Object> metaData=(Map<String, Object>) asyncOutputDoc.get("*metaData");
						final JsonArray transformers=(JsonArray) asyncOutputDoc.get("*futureTransformers");
						String status=(String)metaData.get("status");
						String batchID=(String)metaData.get("batchId");
						Long timeOut=(Long)metaData.get("*timeout_ms");
						Long timedOut=0l;
						Long startTime=(Long)metaData.get("*start_time_ms");
						Boolean closed=(Boolean)metaData.get("*Closed");
						if(timeOut==null)
							metaData.put("*timeout_ms", timeout_ms);
						if(startTime!=null) {
							timedOut=(long)(((timeout_ms)+startTime)-System.currentTimeMillis());
						}
						if(startTime!=null && timedOut<=0 && metaData.get("*timedout")==null) {
							listSize.decrementAndGet();
							metaData.put("*timedout",Boolean.TRUE);
						}
						if(startTime!=null && timedOut>0 && !Boolean.TRUE.equals(closed)) {
							try {
								Thread.sleep(1);
								if(!"Active".equals(status)) {
									listSize.decrementAndGet();
									metaData.put("*Closed",Boolean.TRUE);
								}
								if("Completed".equals(status)) {

									//dp.clearServicePayload();
									//servicePayload.clear();
									asyncOutputDoc.forEach((k,v)->{
										if(k!=null & v!=null)
											dp.getServicePayload().put(k, v);
									});
									dp.getServicePayload().put("asyncOutputDoc", asyncOutputDoc);
									if(transformers!=null)
										FlowUtils.mapAfter(transformers, dp);

									//dp.getServicePayload().put(indexVar, );
									JsonArray flows = await.getJsonArray("children");
									for (JsonValue jsonValue : flows) {
										final String type = jsonValue.asJsonObject().getString("type");
										JsonObject jov=jsonValue.asJsonObject().get("data").asJsonObject();
										String stepStatus=jov.getString("status",null);
										if(!"disabled".equals(stepStatus))
											switch (type) {
												case "try-catch":
													TCFBlock tcfBlock = new TCFBlock(jsonValue.asJsonObject());
													tcfBlock.process(dp);
													break;
												case "sequence":
												case "group":
													Scope scope = new Scope(jsonValue.asJsonObject());
													scope.process(dp);
													break;
												case "switch":
													Switch swich = new Switch(jsonValue.asJsonObject());
													swich.process(dp);
													break;
												case "ifelse":
													IfElse ifElse = new IfElse(jsonValue.asJsonObject());
													ifElse.process(dp);
													break;
												case "loop":
												case "foreach":
													Loop loop = new Loop(jsonValue.asJsonObject());
													loop.process(dp);
													break;
												case "repeat":
												case "redo":
													Repeat repeat = new Repeat(jsonValue.asJsonObject());
													repeat.process(dp);
													break;
												case "invoke":
												case "service":
													Api api = new Api(jsonValue.asJsonObject());
													api.process(dp);
													break;
												case "map":
												case "transformer":
													Transformer transformer = new Transformer(jsonValue.asJsonObject());
													transformer.process(dp);
													break;
											}
									}
									//dp.drop("asyncOutputDoc");
									//dp.clearServicePayload();
								}
								if("Failed".equals(status))
									dp.log("Batch ID : "+batchID+" "+status);
							} catch (Exception e) {
								try {
									ServiceUtils.printException(ServiceUtils.toJson(asyncOutputDoc), e);
								} catch (Exception e2) {
									ServiceUtils.printException("Nested exception in await", e);
								}
							}finally {
								//index.getAndIncrement();
								dp.clearServicePayload();
							}
						}
					});
				}catch (Exception e) {
					ServiceUtils.printException("Internal error inside async service call", e);
				}finally {
					dp.drop("asyncOutputDoc");
					if(listSize.get()<=0)
						continueLoop.set(false);
				}
			}
			dp.putGlobal("hasError", false);
		} catch (Exception e) {
			dp.putGlobal("error", e.getMessage());
			dp.putGlobal("hasError", true);
			throw e;
		} finally {
			if(canSnap) {
				dp.snapAfter(comment, guid);
				dp.drop("*snapshot");
			}else if(snap!=null)
				dp.put("*snapshot",snap);
		}
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
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
}
