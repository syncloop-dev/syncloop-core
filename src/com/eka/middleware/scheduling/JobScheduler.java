package com.eka.middleware.scheduling;


import com.eka.middleware.server.MiddlewareServer;
import com.eka.middleware.service.DataPipeline;
import org.quartz.*;

public class JobScheduler {

    private static String generateIdentification(String id,DataPipeline dataPipeline) {
        return String.format("%s-%s-ID", dataPipeline.rp.getTenant().getName(), id);
    }

    private static String generateGroup(String id,DataPipeline dataPipeline) {
        return String.format("%s-%s-GROUP", dataPipeline.rp.getTenant().getName(), id);
    }

    public static void addJob(String id,String serviceFqn,String cronExpression,String job_name,DataPipeline dataPipeline) throws SchedulerException {

        MiddlewareServer.appSchedulerFactory.scheduleJob(AppScheduler.class, generateIdentification(id,dataPipeline), generateGroup(id,dataPipeline),serviceFqn ,cronExpression,job_name,dataPipeline);
    }

    public static void updateJob(String id,String cronExpression, DataPipeline dataPipeline) throws SchedulerException {

        JobDetail jobDetail = MiddlewareServer.appSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id,dataPipeline), generateGroup(id,dataPipeline));
        MiddlewareServer.appSchedulerFactory.updateScheduler(jobDetail, cronExpression);
    }

    public static void deleteJob(String id, DataPipeline dataPipeline) throws SchedulerException {

        JobDetail jobDetail = MiddlewareServer.appSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id,dataPipeline), generateGroup(id,dataPipeline));
        MiddlewareServer.appSchedulerFactory.removeJob(jobDetail.getKey());
    }

    public static void enableOrDisableScheduler(String id,String enabled,DataPipeline dataPipeline) throws SchedulerException {
        if ("N".equals(enabled)) {
            JobDetail jobDetail = MiddlewareServer.appSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id, dataPipeline), generateGroup(id, dataPipeline));
            JobKey jobKey = MiddlewareServer.appSchedulerFactory.getKey(jobDetail);
            MiddlewareServer.appSchedulerFactory.removeJob(jobKey);
        } else if ("Y".equals(enabled)) {
            JobDetail jobDetail = MiddlewareServer.appSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id, dataPipeline), generateGroup(id, dataPipeline));
            JobKey jobKey = MiddlewareServer.appSchedulerFactory.getKey(jobDetail);
            MiddlewareServer.appSchedulerFactory.startJob(jobKey);

        }
    }

    public static void activateScheduler(DataPipeline dataPipeline) throws SchedulerException {
        Scheduler tenantScheduler =  MiddlewareServer.appSchedulerFactory.getSchedulerForTenant(dataPipeline.rp.getTenant().getName());
        if (tenantScheduler != null && !tenantScheduler.isStarted()) {
            tenantScheduler.start();
        }
    }

    public static void deactivateScheduler(DataPipeline dataPipeline) throws SchedulerException {
        Scheduler tenantScheduler =  MiddlewareServer.appSchedulerFactory.getSchedulerForTenant(dataPipeline.rp.getTenant().getName());
        if (tenantScheduler != null) {
            tenantScheduler.standby();
        }
    }

}
