package com.eka.middleware.template;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.RuntimePipeline;

public class MultiPart {
	// final Map<String, Object> payload;
	final public File file;
	final public InputStream is;
	final public Map<String, String> headers = new HashMap<String, String>();
	final public Map<String, Object> formData;
	final public byte[] body;
	final public String type;

	private String getCurrentResource() {
		StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		// Since its a private method it will be called
		// inside it's own class function so we get the
		// stack that called the last public function that
		// called this method
		return ste[3].getClassName() + "." + ste[3].getMethodName();
	}

	public MultiPart(DataPipeline dataPipeLine, File file) throws Exception {
		this.file = file;
		final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());	
		type = "file";
		is = null;
		body = null;
		formData = null;
		rp.payload.put("*multiPart", this);
	}

	public MultiPart(DataPipeline dataPipeLine, InputStream is, boolean setOctetStream) throws Exception {
		// TODO Auto-generated constructor stub
		// this.payload=payload;
		final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());
		type = "body";
		this.is = is;
		file = null;
		body = null;
		formData = null;
		//final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());
		rp.payload.put("*multiPart", this);
	}
	
	public MultiPart(DataPipeline dataPipeLine, InputStream is, String fileName, boolean setOctetStream) throws Exception {
		// TODO Auto-generated constructor stub
		// this.payload=payload;
		final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());
		type = "body";
		this.is = is;
		file = null;
		body = null;
		formData = null;
		//final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());
		rp.payload.put("*multiPart", this);
	}

	public MultiPart(DataPipeline dataPipeLine, byte[] body) throws Exception{
		final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());	
		type = "body";
		is = null;
		file = null;
		formData = null;
		this.body = body;
		rp.payload.put("*multiPart", this);
	}
	
	public MultiPart(DataPipeline dataPipeLine, byte[] body, String fileName) throws Exception{
		final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId()); 
		type = "body";
		is = null;
		file = null;
		formData = null;
		this.body = body;
		//final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());
		rp.payload.put("*multiPart", this);
	}

	public MultiPart(Map<String, Object> formData) {
		type = "body";
		is = null;
		file = null;
		this.formData = formData;
		this.body = null;
	}

	public MultiPart(DataPipeline dataPipeLine, String body) throws Exception{
		final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());
		type = "body";
		is = null;
		file = null;
		formData = null;
		this.body = body.getBytes();
		//final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());
		rp.payload.put("*multiPart", this);
	}

	public void putHeader(String key, String value) {
		headers.put(key, value);
	}

}
