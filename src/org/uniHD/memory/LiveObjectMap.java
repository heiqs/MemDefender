package org.uniHD.memory;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LoggerConfig;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.uniHD.memory.LiveObjectMap.finalized;
import static org.uniHD.memory.util.Constants.COLUMN_SEPARATOR;

/**
 * Shadow registry for live objects of the JVM.
 * 
 * @author Felix Langner
 * @author Mohammad Ghanavati
 * @author Artur Andrzejak
 * @since 01/14/2013
 */
public final class LiveObjectMap implements Iterable<Entry<String, LiveObjectMap.AllocationSiteDetails>> {
	private static final FluentLogger logger = FluentLogger.forEnclosingClass();
	static {
		logger.atFine().log("LiveObjectMap created");
	}
		/**
         *	Number of old-generation gcs after which memory leak detection algorithm is executed
         */
	private final static int GCS_PER_DETECTION = 2;
	/**
	 * Keep track of the current generation of garbage collections and of the number of major GCS
	 */
	private static long currentGen = 0;
	private static long majorGCs = 0;

	/**
	 * Some tuning parameters for the hash maps.
	 */
	private final static int INITIAL_OBJECTS_CAPACITY = 100000;
	private final static int INITIAL_ALLOCATIONS_CAPACITY = 10000;
	//Determines initial size of HashMap which stores GenerationInformation per allocation site
	private final static int INITIAL_GENERATIONS_PER_OBJECT_CAPACITY = 10;
	//Determines if the number of objects that have been deallocated in one generation g should be tracked (per allocation site and generation)
	private final static boolean TRACK_DEALLOCATIONS = true;

	/**
	 * Static reference to access the collected data from.
	 */
	public final static LiveObjectMap INSTANCE = new LiveObjectMap();
	
	/**
	 * One shadow object for each object monitored in the JVM.
	 */
	private final static ConcurrentMap<String, SingleAllocationDetails> OBJECTS = 
			new ConcurrentHashMap<String, SingleAllocationDetails>(INITIAL_OBJECTS_CAPACITY);
	
	/**
	 * Summary of allocation information with source,class pairs as keys.
	 */
	private final static ConcurrentMap<String, AllocationSiteDetails> ALLOCATIONS =
			new ConcurrentHashMap<String, AllocationSiteDetails>(INITIAL_ALLOCATIONS_CAPACITY);
	
	private LiveObjectMap() { /* supports static referencing only */
	}

	/**
	 * Method to notify about the introduction of a new live object, its size and the source code location it was instantiated at.
	 * 
	 * @param allocatedObjectID
	 * @param clazz
	 * @param allocationSite
	 * @param objectSize
	 */
	public final static void allocated (final String allocatedObjectID, final String clazz, final String allocationSite,
																final long objectSize) {
		
		final String groupId = toGroupIdentifier(allocationSite, clazz);
		//System.out.println("@" + groupId + " @ " + System.currentTimeMillis());
		//store object generation temporarily so the generation is not different for ALLOCATIONS and OBJECTS
		final long objectGen = currentGen;

		// since there will be only one object with the same ID at any time and finalize() is only called once for it, no 
		// synchronisation is needed
		OBJECTS.put(allocatedObjectID, new SingleAllocationDetails(groupId, objectSize, objectGen));
				
		AllocationSiteDetails oldEntry;
		//add object is synchronized and putIfAbsent is executed atomically -> no race condition
		if ((oldEntry = ALLOCATIONS.putIfAbsent(groupId, new AllocationSiteDetails(objectSize, objectGen))) != null) {
			oldEntry.addObjectDetails(objectSize, objectGen);
		}
		// logger.atFine().atMostEvery(100, TimeUnit.MILLISECONDS).log("In allocated: %s and class %s", allocationSite, clazz);
	}
	
	/**
	 * Method to notify about the removal of a live object, given by its id.
	 * This is called automatically (via org.uniHD.memory.allocation.LiveObjectMonitoringSampler.CleanerRunnable#run() )
	 * when the corresp object becomes phantom-reachable
	 * todo: Make this code more efficient, e.g. getConfig must be really slow!
	 * @param freedObjectID
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public final static void finalized (final String freedObjectID) throws FileNotFoundException, IOException {
		
		// since there will be only one object with the same ID at any time and finalize() is only called once for it, no 
		// synchronisation is needed

		final SingleAllocationDetails entry;
		if ((entry = OBJECTS.remove(freedObjectID)) != null) {
    		//System.out.println("entry:" + entry);
			// conflicts because of concurrency may only occur during the manipulation of the allocation size
			//todo: understand comment above. remove and addObjectDetails are synchronized

			// Remove objects data from the statistics
			ALLOCATIONS.get(entry.groupIdentifier).removeObjectDetails(entry.objectSize, entry.generation);
		}
	}
	
	/**
	 * @return the number of Objects currently registered.
	 */
	public static int numObjects() {
		
		return OBJECTS.size();
	}
	
	/**
	 * @return the number of class-source location summary lines.
	 */
	public static int numSummaryLines() {
		
		return ALLOCATIONS.size();
	}



	/**
	 *	Increments the counter for majorGCs and checks if the detection algorithm should be executed
	 *  Synchronized keyword is probably not needed, since majorGCs are highly unlikely to be executed right after one another
	 */
	public synchronized static boolean handleMajorGC() {
		majorGCs++;
		if (majorGCs >= GCS_PER_DETECTION) {
			//System.out.println("execute memory leak detection algorithm");
			//execute memory leak detection algorithm concurrently
			//new DetectionRunner().start();

			//reset #majorGCs so next memleak-detection is only executed again in GCS_PER_DETECTION gcs
			majorGCs=0;
		}
		return false;
	}

	/**
	 * Execute memory leak detection algorithm in a new Thread

	private final static class DetectionRunner extends Thread {

		@Override public void run(){
			//sort after genCount TODO: set reasonable starting size
			ArrayList<GenPair> genPairList= new ArrayList<GenPair>();

			//objects with a very low genCount are not relevant, filter them out
			for (Entry<String, AllocationSiteDetails> allocation : ALLOCATIONS.entrySet()){
				if(allocation.getValue().genCount > POTENTIAL_LEAK_THRESHOLD) {
					genPairList.add(new GenPair(allocation.getKey(), allocation.getValue().genCount));
				}
			}
			Collections.sort(genPairList);
			System.out.println("#objects above minimum threshold: " + genPairList.size());
			//System.out.println(genPairList);
			//traverse in descending order and try to find threshold
			for(int i = genPairList.size()-1; i > 0; --i){
				if(((float) genPairList.get(i).genCount / genPairList.get(i-1).genCount)  >  GENCOUNT_GAP_THRESHOLD  ){
					System.out.println("#Memory Leaks detected: " + Integer.toString(genPairList.size()-i));
					//ok to copy the sublist, since our list of leaks will hopefully be relatively small
					List<GenPair> list = new ArrayList<GenPair>(genPairList.subList(i, genPairList.size()));

					//print ist of possible leaks to stdout
					System.out.println(list);
				}
			}
			System.out.println("Could not detect any memory leaks");
		}
	}
	 */

	public static long getCurrentGen() {
		return currentGen;
	}

	//increment currentGen and check for possible overflow
	public synchronized static void incrementCurrentGen() {
		if(++LiveObjectMap.currentGen == Long.MAX_VALUE){
			handleOverflow();
		}
	}

	private static void handleOverflow() {
		System.out.println("Integer overflow in currentGen.");
		System.exit(-1);
	}


	/**
	 * Pairs of groupID (source,class) and genCount, enables sorting by genCount
	 */
	private final static class GenPair implements Comparable<GenPair>{
		private final String groupID;
		private final Integer genCount;

		private GenPair(String groupID, int genCount) {
			this.groupID = groupID;
			this.genCount = genCount;
		}
		@Override
		public String toString(){
			return groupID + ", " + genCount;
		}
		@Override
		public int compareTo(GenPair pair) {
			return this.genCount.compareTo(pair.genCount);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<Entry<String, AllocationSiteDetails>> iterator() {
		
		return ALLOCATIONS.entrySet().iterator();
	}
	
	/**
	 * @param sourceLocation
	 * @param clazz
	 * @return an unique identifier created from the class and source location.
	 */
	private final static String toGroupIdentifier(String sourceLocation, String clazz) {
		
		return sourceLocation + COLUMN_SEPARATOR + clazz;
	}
	
	/**
	 * Details attached to an object allocation. Namely its size and the source code location of its instantiation.
	 * 
	 * @author Felix Langner
	 * @since 01/14/2013
	 */
	private final static class SingleAllocationDetails {
		
		private final String groupIdentifier;
		private final long objectSize;
		private final long generation;
		
		private SingleAllocationDetails(final String groupIdentifier, final long objectSize, long objectGen) {
			
			this.groupIdentifier = groupIdentifier;
			this.objectSize = objectSize;
			this.generation = objectGen;
		}
	}

	
	/**
	 * The summary of allocated bytes and number of occurrences  for a certain grouping criterion.
	 * 
	 * @author Felix Langner
	 * @since 01/14/2013
	 */
	public final static class AllocationSiteDetails {

		//General stats about the allocation site (no temporal information)
		private long allocatedBytes;
		private long numberOfFinalAllocations = 1L;
		private long numberOfAllocations = 1L;
		private long numberOfDeAllocations = 0L;

		//Key g is the generation, value stores Info about g
		// (e.g. #objects allocated in generation g,#deallocated objects allocated in generation g)
		//a concurrentMap is not needed, since all methods of AllocationSiteDetails are synchronized
		private Map<Long, GenerationInfo> generations;

		private AllocationSiteDetails(long initialSize, long objectGen) {
			
			this.allocatedBytes = initialSize;

			this.generations 	= new HashMap<Long, GenerationInfo>(INITIAL_GENERATIONS_PER_OBJECT_CAPACITY);
			this.generations.put(objectGen, new GenerationInfo(1,0,0));
		}
		
		private synchronized final void addObjectDetails(final long objectSize, long objectGen) {
			
			this.allocatedBytes += objectSize;
			this.numberOfAllocations++;
			this.numberOfFinalAllocations++;

			GenerationInfo generationInfo = this.generations.get(objectGen);
			if( generationInfo != null ){
				generationInfo.incrementNumAllocatedObjects();
			} else {
				//object is first obejcted allocated in generation objectGen
				//numAllocatedObjects is already 1, since at least one object in this generation has to exist for this constructor to be called
				this.generations.put(objectGen, new GenerationInfo(1,0,0));
			}

		}
		
		private synchronized final void removeObjectDetails(final long objectSize, long objectGen) {
			
			this.allocatedBytes -= objectSize;
			this.numberOfFinalAllocations--;
			this.numberOfDeAllocations++;

			//Change Info of object generation
			GenerationInfo generationInfo = this.generations.get(objectGen);
			//necessary to check for initialization first, since removeObjectDetails(..) could have been called before addObjectDetails(.)
			if( generationInfo != null ){
				generationInfo.incrementNumCollectedAllocatedObjects();
			} else {
				this.generations.put(objectGen, new GenerationInfo(0,0,1));
			}

			if (TRACK_DEALLOCATIONS){
				//Change Info of current generation
				generationInfo = this.generations.get(currentGen);
				//necessary to check for initialization first, since removeObjectDetails(objectGen) could have been called before addObjectDetails(currentGen)
				if( generationInfo != null ){
					generationInfo.incrementNumDeallocatedObjects();
				} else {
					this.generations.put(currentGen, new GenerationInfo(0,1,0));
				}
			}
		}
		
		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public synchronized final String toString() {
			StringBuffer generationInfoString = new StringBuffer();
			for (Entry e : generations.entrySet()){
				generationInfoString.append( e.getKey().toString() + "=" + e.getValue().toString()  + COLUMN_SEPARATOR);
			}
			//generationInfoString is shortened to remove trailing comma added above
			return "" + this.numberOfFinalAllocations + COLUMN_SEPARATOR + this.numberOfAllocations +
			COLUMN_SEPARATOR + this.numberOfDeAllocations + COLUMN_SEPARATOR + this.allocatedBytes + COLUMN_SEPARATOR +
					generationInfoString.deleteCharAt(generationInfoString.length()-1).toString();
		}
	}

	/**
	 * Class that stores information about a certain generation of an allocation site
	 */
	private static class GenerationInfo {
		//number of objects allocated in g
		private int numAllocatedObjects;
		//number of objects deallocated in g (regardless of their generation of allocation)
		private int numDeallocatedObjects;
		//number of objects  allocated in g that have already been garbage collected
		private int numCollectedAllocatedObjects;

		public GenerationInfo(int numAlloc, int numDealloc, int numCollectedAlloc) {
			numAllocatedObjects = numAlloc;
			if (TRACK_DEALLOCATIONS) {
				numDeallocatedObjects = numDealloc;
			}
			numCollectedAllocatedObjects = numCollectedAlloc;
		}

		public void incrementNumAllocatedObjects() {
			numAllocatedObjects++;
		}

		public void incrementNumDeallocatedObjects() {
			numDeallocatedObjects++;
		}

		public void incrementNumCollectedAllocatedObjects() {
			numCollectedAllocatedObjects++;
		}

		@Override
		public String toString(){
			return String.valueOf(numAllocatedObjects) + ":" + String.valueOf(numDeallocatedObjects) + ":" + String.valueOf(numCollectedAllocatedObjects);
		}


	}
}
