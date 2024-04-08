package com.eka.middleware.scheduling;


import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.eka.middleware.heap.CacheManager;
import com.eka.middleware.heap.HashMap;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.RuntimePipeline;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.Tenant;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class EkaScheduler implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        //  System.out.println("App Scheduler......." + jobExecutionContext.getJobDetail().getKey());
        JobDataMap jobDataMap = jobExecutionContext.getMergedJobDataMap();
        String identificationName = jobDataMap.getString("identificationName");
        String cronExpression = jobDataMap.getString("cronExpression");
        String serviceFqn = jobDataMap.getString("serviceFqn");
        String identificationGroup = jobDataMap.getString("identificationGroup");
        String tenantName = jobDataMap.getString("tenantName");
        String job_name = jobDataMap.getString("job_name");
        try {
            executeScheduledJob(tenantName,cronExpression, serviceFqn,identificationName,job_name);
        } catch (SnippetException e) {
            throw new RuntimeException(e);
        }

    }

    private void executeScheduledJob(String tenantName,String cronExpression,String serviceFqn,String identificationName,String job_name) throws SnippetException {

        final String uuidThread = ServiceUtils.generateUUID("Job Scheduler thread - packages.ekaScheduler.cronJob.services.getSchedulerJobData.java" + System.nanoTime());
        final String uuid = ServiceUtils.generateUUID("Job Schedule - packages.ekaScheduler.cronJob.services.getSchedulerJobData.java.main" + System.nanoTime());

        final RuntimePipeline rpThread = RuntimePipeline.create(Tenant.getTenant(tenantName),uuidThread, null, null, "packages.ekaScheduler.cronJob.services.java.getSchedulerJobData.main", "");
        final DataPipeline dp=rpThread.dataPipeLine;
        Map<String, String> jobData =new HashMap<>();
        final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
        Map<String, Object> cache = CacheManager.getCacheAsMap(Tenant.getTenant("default"));

        ZonedDateTime startedAt = ZonedDateTime.now();
        jobData.put("id", identificationName.replaceAll(".*-(\\w+)-ID", "$1"));
        jobData.put("cronExpression", cronExpression);
        jobData.put("serviceFqn", serviceFqn);
        jobData.put("status", "running");
        jobData.put("internal_status", "running");
        jobData.put("start_time", dtf.format(startedAt));
        jobData.put("end_time", "");
        jobData.put("correlationId", uuid);
        jobData.put("sessionId", uuidThread);
        jobData.put("job_name", job_name);

        jobData.put("next_run", getNextInstant(cronExpression, startedAt) + "");

        dp.put("jobData", jobData);
        cache.put("scheduler:lastJobTime", startedAt.toString());
        cache.put("scheduler:lastJobFQN", serviceFqn);
        dp.log("Calling startJob for " + serviceFqn + " at time: " + startedAt);
        ServiceUtils.execute("packages.ekaScheduler.cronJob.handler.startJob.main", dp);
        ZonedDateTime endedAt = ZonedDateTime.now();
        dp.log(serviceFqn + " ended at time: " + dtf.format(endedAt));
        dp.log(serviceFqn + " took : " + (endedAt.toInstant().toEpochMilli() - startedAt.toInstant().toEpochMilli()) + "ms to finish the job.");
        if (dp.get("error") == null) {
            dp.log(serviceFqn + ": Service execution completed successfully");
        } else {
            dp.log(serviceFqn + ": Service execution failed. " + dp.get("error"));
        }
        jobData.put("status", "Completed");
        jobData.put("internal_status", "completed");
        jobData.put("end_time", dtf.format(endedAt));

    }
    private static Instant getNextInstant(String cronExpression,ZonedDateTime now) {
        if(cronExpression.equals("0"))
            return now.toInstant();
        String[] parts = cronExpression.split("\\s");
        if (parts.length == 7) {
            cronExpression = String.join(" ", Arrays.copyOf(parts, 5));
        }
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        CronParser parser = new CronParser(cronDefinition);
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(cronExpression));
        Optional<ZonedDateTime> zdt = executionTime.nextExecution(now);
        if (zdt.isPresent()) {
            ZonedDateTime next = zdt.get();
            return next.toInstant();
        } else {
            throw new IllegalArgumentException("No next execution time available for the given cron expression.");
        }
    }
}
