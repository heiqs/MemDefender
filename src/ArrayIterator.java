import org.uniHD.memory.util.LiveObjectDumpGenerator;

/**
 * A simple example that iterates through a String Array.
 */
public class ArrayIterator
{
    public static void main(String[] args) throws Exception
    {
	int size = 100;
	String[] Foo = new String[size];
        for (int i = 0; i < size; i++) {
		Foo[i] =  Integer.toString(i);
	}
        for (String str:Foo)
        {
            System.out.println("Index is " + str);
        }
	//hint GC
	//System.gc();
	//Thread.sleep(5000L);
	LiveObjectDumpGenerator.dumpToFile("ArrayIterator", "1");
    }
}
