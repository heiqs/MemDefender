package org.uniHD.memory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class LiveObjectMapPerformance {

	private final static String[] classes = new String[100];
	private final static String[] sources = new String[100];
	
	private final static AtomicInteger nextObjectToAllocate = new AtomicInteger(1);
	private final static AtomicInteger nextObjectToDeallocate = new AtomicInteger(1);
	
	// allocations/deallocations e.g. 1 means 50:50 and 3 means 75:25
	private final static float allocateToDeallocate = 3f;
	
	private final static Random random = new Random(1);
	
	/**
	 * @param args
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException {
		
		// init classes and sources
		byte[] string = new byte[30];
		for (int i = 0; i < 100; i++) {
			
			random.nextBytes(string);
			classes[i] = new String(string);
			
			random.nextBytes(string);
			sources[i] = new String(string);
		}

		Executor[] executors = new Executor[10];
		
		for (int iterations = 0; iterations < 20; iterations++) {
			
			for (int i = 0; i < executors.length; i++) {
				executors[i] = new Executor();
			}
			
			long time = System.currentTimeMillis();
			
			for (Executor executor : executors) {
				executor.start();
			}
			
			for (Executor executor : executors) {
				executor.join();
			}
			
			System.out.println("Objects: " + LiveObjectMap.numObjects() + 
										   "\nTime: " + (System.currentTimeMillis() - time) + "\n");
		}
	}

	private final static class Executor extends Thread {
		
		@Override
		public void run() {
			
			for (int i = 0; i < 100; i++) {
				final int deAlDecision = random.nextInt(4);
				if (deAlDecision < allocateToDeallocate) {
/*					LiveObjectMap.allocated(""+nextObjectToAllocate.getAndIncrement(),
																classes[random.nextInt(100)], 
																sources[random.nextInt(100)], 
																Math.abs(random.nextLong()));*/
				} else {
					try {
						LiveObjectMap.finalized(""+nextObjectToDeallocate.getAndIncrement());
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
	}
}
