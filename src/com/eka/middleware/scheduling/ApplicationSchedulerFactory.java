package com.eka.middleware.scheduling;

import com.beust.jcommander.internal.Lists;
import com.eka.middleware.service.DataPipeline;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;

import java.util.List;

public class ApplicationSchedulerFactory {

    private ApplicationSchedulerFactory() {
        super();
    }

    @Getter @Setter
    private Scheduler scheduler;

    /**
     * @param configFile
     * @return
     * @throws SchedulerException
     */
    public static ApplicationSchedulerFactory initScheduler(final String configFile) throws SchedulerException {
        ApplicationSchedulerFactory applicationSchedulerFactory = new ApplicationSchedulerFactory();
        SchedulerFactory schedFact = null;
        if (StringUtils.isBlank(configFile)) {
            schedFact = new org.quartz.impl.StdSchedulerFactory();
        } else {
            schedFact = new org.quartz.impl.StdSchedulerFactory(configFile);
        }
        applicationSchedulerFactory.setScheduler(schedFact.getScheduler());
        return applicationSchedulerFactory;
    }

    /**
     * @throws SchedulerException
     */
    public void startScheduler() throws SchedulerException {
        scheduler.start();
    }

    /**
     *
     * @throws SchedulerException
     */
    public void stopScheduler() throws SchedulerException {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.standby();
        }
    }

    /**
     *
     * @throws SchedulerException
     */
    public void deleteScheduler() throws SchedulerException {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    /**
     *
     * @return
     * @throws SchedulerException
     */
    public SchedulerMetaData getSchedulerMetaData() throws SchedulerException {
        return scheduler.getMetaData();
    }

    /**
     *
     * @return
     * @throws SchedulerException
     */
    public List<JobExecutionContext> getCurrentlyExecutingJobs(DataPipeline dataPipeline) throws SchedulerException {
        return Lists.newArrayList();
    }

    /**
     *
     * @param jobClass
     * @param jobName
     * @param jobGroup
     * @return
     */
    private JobDetail buildJobDetail(Class<? extends Job> jobClass, String jobName, String jobGroup) {
        return JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroup)
                .build();
    }

    /**
     *
     * @param triggerName
     * @param triggerGroup
     * @param cronExpression
     * @return
     */
    private Trigger buildCronTrigger(String triggerName, String triggerGroup, String cronExpression) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerName, triggerGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();
    }

    /**
     *
     * @param jobDetail
     * @return
     * @throws SchedulerException
     */
    public JobKey getKey(JobDetail jobDetail) throws SchedulerException {
        return jobDetail.getKey();
    }

    /**
     *
     * @param jobKey
     * @throws SchedulerException
     */
    public void removeJob(JobKey jobKey) throws SchedulerException {
        scheduler.unscheduleJob(TriggerKey.triggerKey(jobKey.getName(), jobKey.getGroup()));
        scheduler.deleteJob(jobKey);
    }

    /**
     *
     * @param jobDetail
     * @param newCronExpression
     */
    public void updateScheduler(JobDetail jobDetail, String newCronExpression) {
        try {
            JobKey jobKey = getKey(jobDetail);
            String jobName = jobKey.getName();
            String jobGroup = jobKey.getGroup();

            Trigger oldTrigger = scheduler.getTrigger(TriggerKey.triggerKey(jobName, jobGroup));

            if (oldTrigger != null) {
                Trigger newTrigger = buildCronTrigger(jobName, jobGroup, newCronExpression);
                scheduler.rescheduleJob(oldTrigger.getKey(), newTrigger);
            }
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param jobClass
     * @param identificationName
     * @param identificationGroup
     * @param cronExpression
     * @return
     * @throws SchedulerException
     */
    public <T extends SchedulerJob> JobKey scheduleJob(Class<T> jobClass , String identificationName , String identificationGroup , String cronExpression) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(jobClass)
                .withIdentity(identificationName , identificationGroup).build();

        Trigger trigger = TriggerBuilder
                .newTrigger()
                .withIdentity(identificationName , identificationGroup)
                .withSchedule(
                        CronScheduleBuilder.cronSchedule(cronExpression))
                .build();
        scheduler.scheduleJob(job, trigger);
        return job.getKey();
    }

    public static void main(String[] args) throws Exception {
        ApplicationSchedulerFactory applicationSchedulerFactory = initScheduler(null);
        applicationSchedulerFactory.startScheduler();

        applicationSchedulerFactory.scheduleJob(SchedulerJob.class, "A", "B", "* * * * * ? *");

        //applicationSchedulerFactory.removeJob(new JobKey("A", "B"));


        JobDetail job = applicationSchedulerFactory.buildJobDetail(SchedulerJob.class, "A", "B");

        applicationSchedulerFactory.updateScheduler(job, "*/3 * * * * ? *");
    }

}
