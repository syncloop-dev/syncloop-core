package com.eka.middleware.server;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.template.SystemException;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class ApplicationShutdownHook implements Runnable {

    private static Integer EXIT_CODE = 0;
    public static String[] arg;

    private static ProcessHandle lastProcess;

    @Override
    public void run() {
        if (EXIT_CODE == 1) {

            try {
                System.setProperty("jdk.httpclient.allowRestrictedHeaders", System.getProperty("jdk.httpclient.allowRestrictedHeaders"));
                System.setProperty("java.awt.headless", System.getProperty("java.awt.headless"));
                System.setProperty("CONTAINER_DEPLOYMENT", System.getProperty("CONTAINER_DEPLOYMENT"));
                System.setProperty("CONTAINER_ON_PRIM_DEPLOYMENT", System.getProperty("CONTAINER_ON_PRIM_DEPLOYMENT"));
                System.setProperty("COMMUNITY_DEPLOYMENT", System.getProperty("COMMUNITY_DEPLOYMENT"));
                System.setProperty("CORE_DEPLOYMENT", System.getProperty("CORE_DEPLOYMENT"));
                System.setProperty("com.sun.jndi.ldap.object.disableEndpointIdentification", System.getProperty("com.sun.jndi.ldap.object.disableEndpointIdentification"));

                while (lastProcess.isAlive()) {}

                MiddlewareServer.main(arg);
            } catch (SystemException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void restartServer(DataPipeline dataPipeline) throws Exception {

        if (!Boolean.parseBoolean(System.getProperty("CONTAINER_ON_PRIM_DEPLOYMENT"))) {
            throw new Exception("Restart is not allow");
        }

        EXIT_CODE = 1;
        System.exit(EXIT_CODE);
    }

    public static void getCurrentProcess() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        String processName = runtimeMXBean.getName();
        long pid = Long.parseLong(processName.split("@")[0]);
        ProcessHandle processHandle = ProcessHandle.of(pid).get();
        lastProcess = processHandle;
    }
}
