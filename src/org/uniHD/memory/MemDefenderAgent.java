package org.uniHD.memory;

import static org.uniHD.memory.util.Constants.SERVER_PORT;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;

import org.uniHD.memory.allocation.LiveObjectMonitoringSampler;
import org.uniHD.memory.util.Configuration;
import org.uniHD.memory.util.LOMServer;

import com.google.monitoring.runtime.instrumentation.AllocationInstrumenter;
import com.google.monitoring.runtime.instrumentation.AllocationRecorder;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.flogger.FluentLogger;


public class MemDefenderAgent {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    // Setting logging properties for all files
    static {
        // Set logging format, see https://docs.oracle.com/javase/7/docs/api/java/util/logging/SimpleFormatter.html
        // System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$-5s | %1$tF %1$tT %1$tL | %2$-10s]  %5$s %n");
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$1.4s|%1$ty%1$tm%1$td %1$tT %1$tL] %5$s [%2$s] %n");

        // Set default logging format, applicable to all loggers (als flogger)
        setLevelForAllLoggers(Level.FINE);
        //LoggerConfig.of(logger).setLevel(Level.FINE);
        //logger.atInfo().log("[MD Agent] Current logging level is: %s. If you don't see expected  messages, read " +
        //		"http://bit.ly/2M9VhMH", LoggerConfig.of(logger).getLevel());
    }

    public static Configuration config;

    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        logger.atFine().log("[MD Agent] Entered premain, arguments: %s", agentArgs);

        final List<String> args = Arrays.asList(agentArgs == null ? new String[0] : agentArgs.split(","));
        config = new Configuration();
        if (args.size() > 1) {
            config.setConfigsFromCmdLine(args);
        } else {
            String pathToPropertiesFile = args.size() == 1 ? args.get(0) : "";
            config.setConfigsFromPropertiesFile(pathToPropertiesFile);
        }

        // delegate to the JAI
        AllocationInstrumenter.premain(config.JAIArgs, inst);
        logger.atFine().log("[MD Agent] Starting code instrumentation");
        instrument(config.sourcePaths);
        logger.atFine().log("[MD Agent] Instrumentation finished, starting Live Object Dump server");
        startServer(config.appName);
        logger.atFine().log("[MD Agent] Live Object Dump service started");

    }

    /**
     * Setup for the instrumentation of the application. Also a server is started to handle live object dump requests.
     *
     * @param sourcePaths
     * @return a {@link LOMServer} handle.
     */
    private final static void instrument(final String[] sourcePaths) {
        // initialize the object allocation sampler
        AllocationRecorder.addSampler(new LiveObjectMonitoringSampler(sourcePaths));
    }

    private final static void startServer(final String appName) throws IOException {
        // prepare the dump-request server
        final LOMServer server = new LOMServer(SERVER_PORT, appName);
        server.start();
    }


    public static void setLevelForAllLoggers(Level targetLevel) {
        Logger root = Logger.getLogger("");
        root.setLevel(targetLevel);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(targetLevel);
        }
        // System.out.println("Global logging level set to: " + targetLevel.getName());
    }


}
