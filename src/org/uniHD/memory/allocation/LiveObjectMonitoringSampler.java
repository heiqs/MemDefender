package org.uniHD.memory.allocation;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LoggerConfig;
import com.google.monitoring.runtime.instrumentation.Sampler;
import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
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
    
    public LiveObjectMonitoringSampler(final String[] sourceFileRootFolders) {
    	sourceCodeFiles = SourceFileCollector.collectSourceFile(sourceFileRootFolders);
		//add handler for garbage collection events
		addGcHandler();
	}
    
    /*
     * (non-Javadoc)
     * @see com.google.monitoring.runtime.instrumentation.Sampler#
     * 				sampleAllocation(int, java.lang.String, java.lang.Object, long)
     */
    @Override
    public void sampleAllocation(final int count, final String desc, final Object newObj, final long size)  {

		logger.atFine().atMostEvery(500, TimeUnit.MILLISECONDS).log("sampleAllocation entered: count=%d, desc=%s, obj=%s, ", count, desc, size);
		// identify the source code line responsible for the instantiation of the object on the lowest available level
		String allocLocation = null;
		final StackTraceElement[] strace = new Exception().getStackTrace();
		int idx = 0;
		do {

			if (sourceCodeFiles.contains(strace[idx].getClassName()))  {
				allocLocation = strace[idx].getClassName() + ":" + strace[idx].getLineNumber();
				break;
			}

		} while (++idx < strace.length);

		// collect the measured allocation
		if (allocLocation != null) {

			final String objectID = toIdentifierString(newObj);
			//System.out.println("objectID:" + objectID);
			//System.out.println("allocationSite:" + allocLocation);
			//System.out.println("size:" + size);
			allocated(objectID, newObj.getClass().getName(), allocLocation, size);
			// Following call creates a new PhantomReference (public class Cleaner extends PhantomReference<Object>)
			create(newObj, new CleanerRunnable(objectID, allocLocation));

		}
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