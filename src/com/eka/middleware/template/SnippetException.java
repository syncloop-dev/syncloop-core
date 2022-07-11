package com.eka.middleware.template;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.ServiceUtils;

public class SnippetException extends Exception{
	public static Logger logger = LogManager.getLogger(SnippetException.class);
	public boolean propagate=true;
public SnippetException(DataPipeline dataPipeLine, String errMsg, Exception e) {
	super(e);
	propagate=!errMsg.equals(e.getMessage());
	if(propagate) {
		ServiceUtils.printException(dataPipeLine.getSessionId()+"    "+dataPipeLine.getCorrelationId()+"    "+errMsg, this);
	}
}
}
