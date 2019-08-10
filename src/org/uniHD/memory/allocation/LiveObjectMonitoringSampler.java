package org.uniHD.memory.allocation;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LoggerConfig;
import com.google.monitoring.runtime.instrumentation.Sampler;
import com.sun.management.GarbageCollectionNotificationInfo;
import org.uniHD.memory.util.Configuration;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.uniHD.memory.LiveObjectMap.*;
import static sun.misc.Cleaner.create;

/**
 * This sampler relies on bytecode instrumentation provided by the java-allocation-instrumenter (Jeremy Manson) to
 * hook to java object allocation and object removal.
 * 
 * @author Felix Langner
 * @since 10/12/2012
 */

public class LiveObjectMonitoringSampler implements Sampler {
	private static final FluentLogger logger = FluentLogger.forEnclosingClass();

	static {
		logger.atFine().log("LiveObjectMonitoringSampler created");
	}

	static Random rand = new Random();
    private final Set<String> sourceCodeFiles;
	private final Configuration config;

    public LiveObjectMonitoringSampler(final String[] sourceFileRootFolders, Configuration configuration) {
    	sourceCodeFiles = SourceFileCollector.collectSourceFile(sourceFileRootFolders);
    	config = configuration;
		//add handler for garbage collection events
		addGcHandler();
		logger.atFine().log("LiveObjectMonitoringSampler constructor. Found srcCodeFiles =%s", sourceCodeFiles);
	}
    
    /*
     * (non-Javadoc)
     * @see com.google.monitoring.runtime.instrumentation.Sampler#
     * 				sampleAllocation(int, java.lang.String, java.lang.Object, long)
     */
    @Override
    public void sampleAllocation(final int count, final String desc, final Object newObj, final long size)  {

		// identify the source code line responsible for the instantiation of the object on the lowest available level
		String allocLocation = null;
		final StackTraceElement[] strace = new Exception().getStackTrace();
		int idx = 0;
		// todo: make the following filter for client-code-only faster, e.g. do not instrument any agent classes (contrary to now)
		// The following checks whether any part of the source code paths is contained in one of the stack trace row,
		// and if yes, this row is enhanced with corresp. source line number and considered as allocLocation
		// todo: fix REAL ERROR! The sourceCodeFiles seem to have only the file name (without pre-directories), but
		// strace[idx].getClassName() yields a fully-qualified name. For example, in the following run (see logs),
		// the sourceCodeFiles containts only "TestCode" (1 set element), but strace[idx].getClassName()=org.uniHD.test.TestCode
		// [FEIN|190810 20:01:17 855] LiveObjectMonitoringSampler constructor. Found srcCodeFiles =[TestCode] [org.uniHD.memory.allocation.LiveObjectMonitoringSampler <init>]
		// [FEIN|190810 20:03:27 861] Comparing srcCodeFiles vs. strace[idx].getClassName()=org.uniHD.test.TestCode [CONTEXT ratelimit_period="5000 MILLISECONDS [skipped: 1423743]" ] [org.uniHD.memory.allocation.LiveObjectMonitoringSampler sampleAllocation]
		do {
			if (sourceCodeFiles.contains(strace[idx].getClassName()))  {
				allocLocation = strace[idx].getClassName() + ":" + strace[idx].getLineNumber();
				break;
			} else {
				logger.atFine().atMostEvery(5000, TimeUnit.MILLISECONDS).log("Comparing srcCodeFiles vs. strace[idx].getClassName()=%s",
						strace[idx].getClassName());
			}
		} while (++idx < strace.length);

        // collect the measured allocation
        if (allocLocation != null) {
			logger.atFine().atMostEvery(50, TimeUnit.MILLISECONDS).log("**** Found target class: allocLocation=%s, objectID=%s, desc=%s, strack=%s",
					allocLocation, toIdentifierString(newObj), desc, strace);
			final String objectID = toIdentifierString(newObj);
            //System.out.println("objectID:" + objectID);
            //System.out.println("allocationSite:" + allocLocation);
            //System.out.println("size:" + size);
			allocated(objectID, newObj.getClass().getName(), allocLocation, size);
			// Following call creates a new PhantomReference (public class Cleaner extends PhantomReference<Object>)
			create(newObj, new CleanerRunnable(objectID, allocLocation));
			createLeaks(newObj, objectID, allocLocation);
		}

    }

    final static int LEAK_LIST_INIT_CAPACITY = 1000;
    static private List<Object> listOfLeaks = new ArrayList<Object>(LEAK_LIST_INIT_CAPACITY);

    private void createLeaks(final Object newObj, String objectID, String allocLocation) {
    	if (! config.injectorOn) return;
		if (rand.nextInt(100) > config.injectorLeakRatio) return;

    	if (config.injectorSelection) {
    		// Check whether current allocation site is in config.injectorSites
			String candidateAllocationSite = allocLocation.toLowerCase();
			if (! config.injectorSites.contains(candidateAllocationSite)) {
				return;
			}
		}
		// Ready to inject leak: add object to a static array
		listOfLeaks.add(newObj);
		logger.atFine().atMostEvery(200, TimeUnit.MILLISECONDS).log("Leak created for allocation site= %s and obj= %s, ", allocLocation, newObj);
	}



	/**
     * Method to build a java standard object identifier string from the object's native hash and class.
     * 
     * @param obj
     * @return a java standard object identifier.
     */
	private final static String toIdentifierString(final Object obj) {
		
		return obj.getClass().getName() + "@0x" + Integer.toHexString(System.identityHashCode(obj));
	}

	private static void addGcHandler() {
		List<GarbageCollectorMXBean> gcs = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
		for (GarbageCollectorMXBean gc : gcs) {
			NotificationEmitter emitter = (NotificationEmitter) gc;
			NotificationListener listener = new NotificationListener() {
				@Override
				public void handleNotification(Notification notification, Object handback) {

					//filter out other events; TODO: maybe increment currentGen only every x minor gcs
					//TODO: maybe implement filter instead of filtering here
					if(notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)){
						//update generation counter stored in LiveObjectMap
						incrementCurrentGen();
						GarbageCollectionNotificationInfo gcinfo = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
						//System.out.println("GC occured; generation " + getCurrentGen() );
						//System.out.println("Action executed: " +  gcinfo.getGcAction());


						//keep counts of full gcs, so know when to execute the detection algorithm
						if(gcinfo.getGcAction().equals("end of major GC")){
							System.out.println("major GC was executed");
							//increments the majorGC-counter and executes leak detection if needed
							handleMajorGC();

						}
					}
				}
			};
			emitter.addNotificationListener(listener, null, null);
		}
	}

	// Artur Andrzejak, Aug 2019:
	// The run() method is called automatically, when the corresponding obj becomes "Phantom reachable"
	// (i.e. ready to be finalized)
	private final static class CleanerRunnable implements Runnable {
		
		private final String objectId;
		private final String allocLocation;

		private CleanerRunnable(final String objectId, final String allocLocation) {
			this.objectId = objectId;
			this.allocLocation = allocLocation;
		}
		
		/*
		 * (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public final void run() {
			try {
				finalized(objectId);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}