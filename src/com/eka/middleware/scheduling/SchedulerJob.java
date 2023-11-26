package com.eka.middleware.scheduling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.eka.middleware.server.MiddlewareServer;

public class SchedulerJob implements Job {
	private static Logger LOGGER = LogManager.getLogger(SchedulerJob.class);
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    	LOGGER.debug("start schdeduler.......");

    }

}
