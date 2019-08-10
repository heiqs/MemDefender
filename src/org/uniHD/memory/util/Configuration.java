package org.uniHD.memory.util;

import java.io.*;
import java.util.*;

import com.google.common.flogger.FluentLogger;

// A class holding configuration data
public class Configuration {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    // ==== General properties ====
    // List of paths to sources of the target application
    public String[] sourcePaths;
    private static String KEY_sourcePaths = "general.sourcePaths";
    // Name of the targeted application, used for report file
    public String appName = "memDefender-report";
    private static String KEY_appName = "general.appName";
    // Allocation-instrumenter arguments
    public String JAIArgs = "";

    // ==== injector properties ====
    public boolean injectorOn = false;
    private static String KEY_injectorOn = "injector.on";
    public boolean injectorSelection = false;
    private static String KEY_injectorSelection = "injector.selection";
    public int injectorLeakRatio = 100;
    private static String KEY_injectorLeakRatio = "injector.leakRatio";
    public Set<String> injectorSites;
    private static String KEY_injectorSites = "injector.sites";


    public void setConfigsFromPropertiesFile(String pathToPropertiesFile) {
        Properties props = readPropertiesFile(pathToPropertiesFile);
        logger.atFine().log("Read properties file as: %s", props);


        if (props.getProperty(KEY_sourcePaths).length() < 1) {
            throw new IllegalArgumentException("Configuration must contain a correct value for " + KEY_sourcePaths);
            // System.exit(1);
        } else {
            parseAndCheckSourcePaths(props.getProperty(KEY_sourcePaths));
        }
        appName = (String) props.getProperty(KEY_appName, appName);
        injectorOn = Boolean.parseBoolean((String) props.getProperty(KEY_injectorOn, "False"));
        injectorSelection = Boolean.parseBoolean((String) props.getProperty(KEY_injectorSelection, "False"));
        injectorLeakRatio = Integer.parseInt((String) props.getProperty(KEY_injectorLeakRatio, "100"));
        String[] commaSeparatedSitesArray = props.getProperty(KEY_injectorSites, "").toLowerCase().split(",");
        injectorSites = new HashSet<>();
        Collections.addAll(injectorSites, commaSeparatedSitesArray);

        logger.atFine().log("Parsed properties are: %s", this);
    }

    public void setConfigsFromCmdLine(List<String> args) {
        parseAndCheckSourcePaths(args.get(0));
        // get the optional application name
        if (args.size() > 1) {
            appName = args.get(1);
        }
        // parse the java-allocation-instrumenter args
        if (args.size() > 2 && args.get(2).equals("manualOnly")) {
            JAIArgs = "manualOnly";
        }
    }

    private void parseAndCheckSourcePaths(String colonSeparatedPaths) {
        sourcePaths = colonSeparatedPaths.split(":");
        for (final String sourcePath : sourcePaths) {
            checkPathExists(sourcePath);
        }
    }

    private void checkPathExists(String path) {
        if (!new File(path).exists()) {
            throw new IllegalArgumentException("Path '" + path + "' does not exist");
            // System.exit(1);
        }
    }

    private Properties readPropertiesFile(String propertiesPath) {
        Properties prop = new Properties();
        try (InputStream input = new FileInputStream(propertiesPath)) {
            prop.load(input);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Could not load properties from '" + propertiesPath + "'");
            // System.exit(1);
        }
        return prop;
    }


}
