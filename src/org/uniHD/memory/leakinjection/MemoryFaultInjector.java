package org.uniHD.memory.leakinjection;

/*  Inserts memory leaks into running application.
Parameter leakStrength is retrieved from java system properties under name "java.leakStrength.<allocationSiteIndex>".
<allocationSiteIndex> is integer value of own choice identifying the location of the injection.

Behavior depending on leakStrength:
leakStrength < 0: 	there is no allocation site visible by LOM
leakStrength == 0: 	allocation site is visible by LOM (minimumArtificialAllocationSize is allocated but disposable) - no leak
leakStrength >= 0:	leak of size minimumArtificialAllocationSize..(leakStrength-1) is created
To use, you must
1. include this class in the java path of targeted application,
2. set leakStrength via java.leakStrength.<allocationSiteIndex> system property (todo: complete how)
3. include code at the desired leak position:

int leakAmount = MemoryFaultInjector.computeLeakAmount (<allocationSiteIndex>);
byte[] allocatedMemory = new byte[leakAmount]; 		// potential leak is shown by analysis here
addLeak(allocatedMemory, <allocationSiteIndex>);
*/


public class MemoryFaultInjector {

	// define leaking collection object as part of the long-living object
	private final static java.util.Collection<byte[]> leakingObjects = new java.util.LinkedList<byte[]>();
	
	// Get value of parameter leakStrength (encapsulate in a method for potential later mechanism changes
	public static int getLeakStrength(int allocationSiteIndex) {
		    int leakStrength = java.lang.Integer.parseInt(System.getProperty("java.leakStrength." + allocationSiteIndex));
		    return leakStrength;
	}
	
	
	// compute leak amount for each leak allocation site
	public static int computeLeakAmount(int allocationSiteIndex){
	
		// Minimum number of bytes an artificial allocation site allocates (if "visible" but possibly leakSize == 0)
		final int minimumArtificialAllocationSize = 1;
	  	int leakAmount = -1;
	  	int leakStrength = getLeakStrength(allocationSiteIndex);
	
		if (leakStrength >= 0) {
			// create leak amount of size in minimumArtificialAllocationSize..(leakStrength-1)
			leakAmount = new java.util.Random().nextInt(leakStrength+1);
			// for visible allocation site (with or w/out leak), leakAmount must be at least minimumArtificialAllocationSize
			leakAmount = Math.max(minimumArtificialAllocationSize, leakAmount);
	  	}
	  	// for negative leakStrength the leakAmount is 0 => no memory allocation at all
		System.out.println("leak:"+leakAmount);
		return leakAmount;
	}
	
	
	// add leakAmount to the collection of leakingObjects for leakStrength > 0
	public static void addLeak(byte[] allocatedMemory, int allocationSiteIndex){
		int leakStrength = getLeakStrength(allocationSiteIndex);
		if (leakStrength > 0) {
			leakingObjects.add(allocatedMemory);
		}
	}
}