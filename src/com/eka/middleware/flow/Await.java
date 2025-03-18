package com.eka.middleware.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.FlowBasicInfo;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;

import lombok.Getter;

public class Await implements FlowBasicInfo {
	private static Logger LOGGER = LogManager.getLogger(Await.class);
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
		snapshot = data.getString("snap", null);
		if (snapshot != null && snapshot.equals("disabled"))
			snapshot = null;
		snapCondition = data.getString("snapCondition", null);
		indexVar = data.getString("indexVar", "*index");
		String timeOut = data.getString("timeout_seconds_each_thread", "10");
		if (timeOut != null && timeOut.trim().length() > 0) {
			try {
				timeout_seconds_each_thread = Long.parseLong(timeOut);
				if (timeout_seconds_each_thread <= 0)
					timeout_seconds_each_thread = Long.MAX_VALUE;
			} catch (Exception e) {
				ServiceUtils.printException(
						"On Await step timeout seconds value is not set properly hence setting default value of '"
								+ timeout_seconds_each_thread + "'",
						e);
			}
		}
//		outArrayType = data.getString("outArrayType", "document");

		guid = data.getString("guid", null);
		name = await.getString("text", null);
		type = await.getString("type", null);
	}

	public void process(DataPipeline dp) throws SnippetException {
		if (dp.isDestroyed())
			throw new SnippetException(dp, "User aborted the service thread",
					new Exception("Service runtime pipeline destroyed manually"));
		if (disabled)
			return;

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
		canSnap = canSnap || dp.isRecordTrace();
		/*
		 * if(!canSnap) dp.drop("*snapshot");
		 */
		if (canSnap) {
			dp.snapBefore(comment, guid);
		}

		try {
			final List<List<Map<String, Object>>> list = dp.getFuture();
			if (list == null || list.size() <= 0)
				return;
			final AtomicBoolean continueLoop = new AtomicBoolean();
			continueLoop.set(true);
			final AtomicInteger listSize = new AtomicInteger(list.size());
			final Long timeout_ms = timeout_seconds_each_thread * 1000;
			final AtomicBoolean skip = new AtomicBoolean(true);
			final AtomicBoolean allDone = new AtomicBoolean(true);
			while (continueLoop.get() && !Thread.currentThread().isInterrupted()) {
				try {
					// final DataPipeline dp=this;
					final AtomicInteger index = new AtomicInteger(0);
					// dp.put(indexVar, index.get());
					final Map<String, String> seqGroupStatus=new HashMap<>();
					list.forEach(mapList -> {
						// futureList.add(map);
						if(listSize.get()>0) {
							int indexValue = index.getAndIncrement();
							allDone.set(true);
							skip.set(true);
							final StringBuilder allStatuses=new StringBuilder();
							final StringBuilder sequence=new StringBuilder();
							mapList.forEach(map -> {
								dp.clearServicePayload();

								Map<String, Object> asyncOutputDoc = map;
								final Map<String, Object> metaData = (Map<String, Object>) asyncOutputDoc.get("*metaData");
								String seq=(String) asyncOutputDoc.get("*sequence");
								dp.put(indexVar, seq);
								if(sequence.length()<=0)
									sequence.append(seq);
								final JsonArray transformers = (JsonArray) asyncOutputDoc.get("*futureTransformers");
								String batchID = (String) metaData.get("batchId");
								Boolean checkResponse= (Boolean) metaData.get("*enableResponse");
								if(checkResponse!=null && checkResponse==true) {
									dp.updateQueuedTaskStatus(batchID, transformers, asyncOutputDoc, metaData);
								}

								String status = (String) metaData.get("status");
								// metaData.put("*timeout_ms", timeout_ms);
								Long timeOut = metaData.get("*timeout_ms") == null ? null
										: metaData.get("*timeout_ms") instanceof Long ? (Long) metaData.get("*timeout_ms")
										: ((Integer) metaData.get("*timeout_ms")).longValue();// (Long)metaData.get("*timeout_ms");
								Long timedOut = 0l;
								Long startTime = metaData.get("*start_time_ms") == null ? null
										: metaData.get("*start_time_ms") instanceof Long
										? (Long) metaData.get("*start_time_ms")
										: ((Integer) metaData.get("*start_time_ms")).longValue();
								if (startTime == null)
									startTime = System.currentTimeMillis();

								Boolean closed = (Boolean) metaData.get("*Closed");
								if (timeOut == null)
									metaData.put("*timeout_ms", timeout_ms);
								if (startTime != null) {
									timedOut = (long) (((timeout_ms) + startTime) - System.currentTimeMillis());
								}
								if (startTime != null && timedOut <= 0 && metaData.get("*timedout") == null) {
									// listSize.decrementAndGet();
									//skip.set(true);
									allStatuses.append("true");
									metaData.put("*timedout", Boolean.TRUE);
								}//else
								//skip.set(false);
								if (!Boolean.TRUE.equals(closed)) {
									try {
										Thread.sleep(1);
										//System.out.println("Sequence: "+seq+", Batch ID : " + batchID + " " + status);
										if (!"Active".equals(status)) {
											// listSize.decrementAndGet();
											//skip.set(true);
											allStatuses.append("true");
											metaData.put("*Closed", Boolean.TRUE);
										}else
											allStatuses.append("false");
										if ("Completed".equals(status)) {
											allStatuses.append("true");
										} else
											allStatuses.append("false");
										if ("Failed".equals(status))
											dp.log("Batch ID : " + batchID + " " + status);
									} catch (Exception e) {
										LOGGER.debug("Value of time_out is " + metaData.get("*timeout_ms"));
										try {
											ServiceUtils.printException(ServiceUtils.toJson(asyncOutputDoc), e);
										} catch (Exception e2) {
											ServiceUtils.printException("Nested exception in await", e);
										}
										allStatuses.append("false");
									}
								}
							});
							if(allStatuses.toString().contains("false"))
								allDone.set(false);
							else {
								allDone.set(true);
								if(seqGroupStatus.get(sequence.toString())==null)
									seqGroupStatus.put(sequence.toString(),"Completed");
							}
							String currentType="Not started";
							try {
								if (allDone.get() && "Completed".equals(seqGroupStatus.get(sequence.toString()))) {
									listSize.decrementAndGet();
									seqGroupStatus.put(sequence.toString(),"Closed");
									//System.out.println("Sequence: "+sequence.toString()+" closed");
									mapList.forEach(map->{
										Map<String, Object> asyncOutputDoc = map;
										final JsonArray transformers = (JsonArray) asyncOutputDoc.get("*futureTransformers");
										asyncOutputDoc.forEach((k, v) -> {
											if (k != null & v != null)
												dp.getServicePayload().put(k, v);
										});
										dp.getServicePayload().put("asyncOutputDoc", asyncOutputDoc);
										if (transformers != null)
											try {
												FlowUtils.mapAfter(transformers, dp);
											} catch (SnippetException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}

									});
									JsonArray flows = await.getJsonArray("children");
									for (JsonValue jsonValue : flows) {
										final String type = jsonValue.asJsonObject().getString("type");
										currentType=type;
										JsonObject jov = jsonValue.asJsonObject().get("data").asJsonObject();
										String stepStatus = jov.getString("status", null);
										if (!"disabled".equals(stepStatus))
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
								}
							} catch (Exception e) {
								ServiceUtils.printException("Nested exception in await for: step "+currentType, e);
							} finally {
								// index.getAndIncrement();
								dp.clearServicePayload();
								allDone.set(true);
								skip.set(true);
							}
						}
					});
				} catch (Exception e) {
					ServiceUtils.printException("Internal error inside async service call", e);
				} finally {
					dp.drop("asyncOutputDoc");
					if (listSize.get() <= 0)
						continueLoop.set(false);
				}
			}
			dp.putGlobal("*hasError", false);
		} catch (Exception e) {
			dp.putGlobal("*error", e.getMessage());
			dp.putGlobal("*hasError", true);
			throw e;
		} finally {
			if(canSnap) {
				dp.snapAfter(comment, guid, new HashMap<String, Object>());
				if (null != snapshot || null != snapCondition) {
					dp.drop("*snapshot");
				}
			} else if (snap != null)
				dp.put("*snapshot", snap);
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
