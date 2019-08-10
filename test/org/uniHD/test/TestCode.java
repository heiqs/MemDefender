package org.uniHD.test;

import java.util.Random;

public class TestCode {
	
	private final java.util.Collection<byte[]> leakingObjects = new java.util.LinkedList<byte[]>();
	
	@SuppressWarnings("unused")
	TestCode() throws InterruptedException  {
		
        String str = new String("hello world");
        
        // not captured
        String str2 = "hello2world";
        
        new String("wello3horld");
        
        // not captured
        int i = 0;
        
        new Object();
        
        char[] car = new char[10];
        
        int[] iar2 = new int[]{ 1, 2, 3 };
	}
    
	void localString() throws InterruptedException {
		
		new String ("hello world");
		Integer in = new Integer(42);
		Thread.sleep(3000);
		in/=7;
		in =null;

		String leakStrength = System.getProperty("java.leakStrength.1");
		leakStrength = leakStrength == null ? "1000" : leakStrength;

		int leakAmount = new java.util.Random().nextInt(Integer.parseInt(leakStrength));
		if (leakAmount > 1) {
			leakingObjects.add(new byte[leakAmount]);
		}
		
	}
	static Random rand = new Random();
    /**
     * @param args
     * @throws InterruptedException 
     */
	public static void main(String[] args) throws InterruptedException {

        TestCode tc = new TestCode();
        tc.localString();
        tc = null;
        
        // delay execution: Thread.sleep(10000);
		// Create 1e6 Strings
		int NUM_ITER = 1000000;
		Integer lastValue = 10;
		for (int i = 0; i < NUM_ITER; i++) {
			System.out.println("Last value is:" + lastValue );
			Integer value = rand.nextInt(lastValue+ i % 10000);
			String myStringy = String.format("This is my value: %d", value);
			lastValue = someResult(myStringy, lastValue );
		}
        
        System.out.println("Test done.");
    }

    public static Integer someResult(String myString, Integer myValue) {
		Integer result = Math.abs((myString.hashCode()*2 + myValue*myValue) % 10000);
		return result;
	}
}
