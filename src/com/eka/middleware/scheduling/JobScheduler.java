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

    public static void addJob(String id,String serviceFqn,String cronExpression, DataPipeline dataPipeline) throws SchedulerException {

        MiddlewareServer.appSchedulerFactory.scheduleJob(AppScheduler.class, generateIdentification(id,dataPipeline), generateGroup(id,dataPipeline),serviceFqn ,cronExpression,dataPipeline);
    }

    public static void updateJob(String id,String cronExpression, DataPipeline dataPipeline) throws SchedulerException {

        JobDetail jobDetail = MiddlewareServer.appSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id,dataPipeline), generateGroup(id,dataPipeline));
        MiddlewareServer.appSchedulerFactory.updateScheduler(jobDetail, cronExpression);
    }

    public static void deleteJob(String id, DataPipeline dataPipeline) throws SchedulerException {

        JobDetail jobDetail = MiddlewareServer.appSchedulerFactory.buildJobDetail(AppScheduler.class, generateIdentification(id,dataPipeline), generateGroup(id,dataPipeline));
        MiddlewareServer.appSchedulerFactory.removeJob(jobDetail.getKey());
    }

}
