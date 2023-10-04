package com.eka.middleware.quartz;

import com.eka.middleware.heap.CacheManager;
import com.eka.middleware.service.DataPipeline;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;

import java.util.List;
import java.util.Map;

public class QuartzCronSchedulerFactory {

    private QuartzCronSchedulerFactory() {
        super();
    }

    public static Scheduler initScheduler(final String configFile, DataPipeline dataPipeline) throws SchedulerException {
        SchedulerFactory schedFact = null;
        if (StringUtils.isBlank(configFile)) {
            schedFact = new org.quartz.impl.StdSchedulerFactory();
        } else {
            schedFact = new org.quartz.impl.StdSchedulerFactory(configFile);
        }
        Scheduler scheduler = schedFact.getScheduler();

        Map<String, Object> cache = CacheManager.getCacheAsMap(dataPipeline.rp.getTenant());

        cache.put("configFile", configFile);
        cache.put("tenant", dataPipeline.rp.getTenant());
        cache.put("schedulerInstance", scheduler);
        cache.put("schedulerID", scheduler.getSchedulerInstanceId());
        cache.put("schedulerName", scheduler.getSchedulerName());

        return scheduler;
    }
    public static void startScheduler(String configfile,DataPipeline dataPipeline) throws SchedulerException {
        Scheduler scheduler=initScheduler(configfile,dataPipeline);
        scheduler.start();
    }
    public static void stopScheduler(Scheduler scheduler,DataPipeline dataPipeline) throws SchedulerException {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.standby();
        }
    }
    public static void deleteScheduler(Scheduler scheduler,DataPipeline dataPipeline) throws SchedulerException {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
    public static SchedulerMetaData getSchedulerMetaData(Scheduler scheduler) throws SchedulerException {
        return scheduler.getMetaData();
    }
    private static void scheduleJob(Scheduler scheduler, JobDetail jobDetail, Trigger trigger) throws SchedulerException {
        scheduler.scheduleJob(jobDetail, trigger);
    }
    public static List<JobExecutionContext> getCurrentlyExecutingJobs(Scheduler scheduler) throws SchedulerException {
        return scheduler.getCurrentlyExecutingJobs();
    }
    private static JobDetail buildJobDetail(Class<? extends Job> jobClass, String jobName, String jobGroup) {
        return JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroup)
                .build();
    }
    private static Trigger buildCronTrigger(String triggerName, String triggerGroup, String cronExpression) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerName, triggerGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();
    }
    public static JobKey getKey(JobDetail jobDetail) throws SchedulerException {
        return jobDetail.getKey();
    }

    public static void removeJob(Scheduler scheduler, JobKey jobKey) throws SchedulerException {
        scheduler.unscheduleJob(TriggerKey.triggerKey(jobKey.getName(), jobKey.getGroup()));
        scheduler.deleteJob(jobKey);
    }

    public static void updateScheduler(Scheduler scheduler, JobDetail jobDetail, String newCronExpression) {
        try {
            JobKey jobKey = getKey(jobDetail);
            String jobName = jobKey.getName();
            String jobGroup = jobKey.getGroup();

            Trigger oldTrigger = scheduler.getTrigger(TriggerKey.triggerKey(jobName, jobGroup));

            if (oldTrigger != null) {
                scheduler.unscheduleJob(oldTrigger.getKey());
            }

            Trigger newTrigger=  buildCronTrigger(jobName,jobGroup,newCronExpression);
            scheduler.scheduleJob(newTrigger);
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    public static Scheduler getScheduler(String configFile) throws SchedulerException {
        SchedulerFactory schedFact;
        if (StringUtils.isBlank(configFile)) {
            schedFact = new org.quartz.impl.StdSchedulerFactory();
        } else {
            schedFact = new org.quartz.impl.StdSchedulerFactory(configFile);
        }
        return schedFact.getScheduler();
    }
    public static void stopScheduler1(Scheduler scheduler, DataPipeline dataPipeline) throws SchedulerException {
        if (scheduler != null && !scheduler.isShutdown()) {
            String configFile = (String) CacheManager.getCacheAsMap(dataPipeline.rp.getTenant()).get("configFile");
            SchedulerFactory schedFact = StringUtils.isBlank(configFile)
                    ? new org.quartz.impl.StdSchedulerFactory()
                    : new org.quartz.impl.StdSchedulerFactory(configFile);
            Scheduler shutdownScheduler = schedFact.getScheduler();
            shutdownScheduler.standby();
        }
    }
    public static void deleteScheduler1(Scheduler scheduler, DataPipeline dataPipeline) throws SchedulerException {
        if (scheduler != null && !scheduler.isShutdown()) {
            String configFile = (String) CacheManager.getCacheAsMap(dataPipeline.rp.getTenant()).get("configFile");
            SchedulerFactory schedFact = StringUtils.isBlank(configFile)
                    ? new org.quartz.impl.StdSchedulerFactory()
                    : new org.quartz.impl.StdSchedulerFactory(configFile);
            Scheduler deleteScheduler = schedFact.getScheduler();
            deleteScheduler.shutdown();
        }
    }

}
