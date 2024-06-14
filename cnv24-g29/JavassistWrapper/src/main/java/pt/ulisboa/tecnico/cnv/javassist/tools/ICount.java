package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtClass;

public class ICount extends CodeDumper {
	
	private static Map<Integer, long[]> threadMetrics = new HashMap<>();

    /**
     * Number of executed basic blocks.
     */
    //private static long nblocks = 0;

    /**
     * Number of executed methods.
     */
   // private static long nmethods = 0;

    /**
     * Number of executed instructions.
     */
   // private static long ninsts = 0;

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static  void incBasicBlock(int position, int length) {
    	int threadId = (int)Thread.currentThread().getId();
    	long[] metrics = threadMetrics.getOrDefault(threadId, new long[4]);
    	//metrics[0]++; nmethods
    	metrics[1]++; // nblocks
    	metrics[2]+= length; // ninsts  	
    	threadMetrics.put(threadId, metrics);
    }

    public static  void incBehavior(String name) {
    	int threadId = (int)Thread.currentThread().getId();
    	long[] metrics = threadMetrics.getOrDefault(threadId, new long[4]);
    	metrics[0]++; //nmethods
    	threadMetrics.put(threadId, metrics);
    }

    public static long[] getMetrics() {
    	int threadId = (int)Thread.currentThread().getId();
    	long[] ans = new long[4];
    	ans[0] = threadMetrics.get(threadId)[0];
    	ans[1] = threadMetrics.get(threadId)[1];
    	ans[2] = threadMetrics.get(threadId)[2];
    	ans[3] = threadMetrics.get(threadId)[3];
    	threadMetrics.put(threadId, new long[4]);
    	return ans;
    	
    }
    
    public static void incMemory(long memoryUsed) {
    	int threadId = (int) Thread.currentThread().getId();
    	long[] metrics = threadMetrics.getOrDefault(threadId, new long[4]);
    	metrics[3] = memoryUsed;
    	threadMetrics.put(threadId, metrics);
    }
    


    @Override
    protected void transform(CtBehavior behavior) throws Exception {
    	super.transform(behavior); 
    	StringBuilder builder = new StringBuilder();
    	builder.append(String.format("%s.incBehavior(\"%s\");", ICount.class.getName(), behavior.getLongName()));
    	
    	if (behavior.getName().equals("handleRequest")) {
            behavior.addLocalVariable("memoryStart1", CtClass.longType);
            //behavior.insertBefore("memoryStart1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();");
            behavior.insertBefore("memoryStart1 =  Runtime.getRuntime().freeMemory();");
            behavior.addLocalVariable("memoryEnd1", CtClass.longType);
        	behavior.addLocalVariable("memoryUsed1", CtClass.longType);
           
        	//builder.append("memoryEnd1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();");		
        	builder.append("memoryEnd1 = Runtime.getRuntime().freeMemory();");
        	builder.append("memoryUsed1 = memoryStart1 - memoryEnd1; ");
        	//builder.append("System.out.println(\"------------------------------ memory used = \" + memoryUsed1);");
        	builder.append(String.format("%s.incMemory(memoryUsed1);", ICount.class.getName()));
        }	
        behavior.insertAfter(builder.toString());
        
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", ICount.class.getName(), block.getPosition(), block.getLength()));
    }

}
