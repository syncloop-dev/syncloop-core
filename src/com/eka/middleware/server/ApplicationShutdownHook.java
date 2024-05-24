package com.eka.middleware.server;

import com.eka.lite.service.DataPipeline;
import com.eka.middleware.template.SystemException;
import org.graalvm.polyglot.io.ProcessHandler;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Optional;

public class ApplicationShutdownHook implements Runnable {

    private static Integer EXIT_CODE = 0;
    public static String[] arg;

    private static ProcessHandle lastProcess;
    private static long pid;

    @Override
    public void run() {
        if (EXIT_CODE == 1) {
        }
    }

    public static void restartServer(DataPipeline dataPipeline) throws Exception {

        if (!Boolean.parseBoolean(System.getProperty("CONTAINER_ON_PRIM_DEPLOYMENT"))) {
            throw new Exception("Restart is not allow");
        } else if (!dataPipeline.rp.getTenant().getName().equalsIgnoreCase("default")) {
            throw new Exception("Restart is not allow for other tenants");
        }

        EXIT_CODE = 1;

        if (System.getProperty("os.name").startsWith("Windows")) {
            Runtime.getRuntime().exec("taskkill /F /PID " + pid);
            Runtime.getRuntime().exec("windows-x64.bat");
        } else {
            Runtime.getRuntime().exec("sh unix-restart.sh");
        }

        //System.exit(EXIT_CODE);
    }

    public static void getCurrentProcess() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        String processName = runtimeMXBean.getName();
        pid = Long.parseLong(processName.split("@")[0]);

        Optional<ProcessHandle> optionalProcessHandle = ProcessHandle.of(pid);
        optionalProcessHandle.ifPresent(processHandle -> lastProcess = processHandle);
    }


    public static void prepareOutputFile() throws FileNotFoundException {
        PrintWriter out = null;
        if (Boolean.parseBoolean(System.getProperty("CONTAINER_DEPLOYMENT"))){
             out = new PrintWriter(new FileOutputStream("/unix-restart.sh"));

        }else{
             out = new PrintWriter(new FileOutputStream("./unix-restart.sh"));
        }
        out.println("kill -9 " + pid);
        //out.println("sh unix-x64.sh");

        out.flush();
        out.close();
    }

}
