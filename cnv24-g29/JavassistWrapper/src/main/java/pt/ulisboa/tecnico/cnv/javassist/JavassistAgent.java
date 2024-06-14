package pt.ulisboa.tecnico.cnv.javassist;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;

import pt.ulisboa.tecnico.cnv.javassist.tools.AbstractJavassistTool;

public class JavassistAgent {
	static String packageNames = "pt.ulisboa.tecnico.cnv.imageproc,"
								//+ "pt.ulisboa.tecnico.cnv.imageproc.EnhanceImageHandler.java,"
								+ "pt.ulisboa.tecnico.cnv.raytracer,"
								+ "pt.ulisboa.tecnico.cnv.raytracer.pigments,"
								+ "pt.ulisboa.tecnico.cnv.raytracer.shapes,"
								// Blur image
								+ "boofcv.alg.filter.blur.GBlurImageOps,"
								//+ "boofcv.io.image.ConvertBufferedImage,"
								+ "boofcv.io.image,"
								+ "boofcv.struct.image.GrayU8,"
								+ "boofcv.struct.image.ImageType,"
								+ "boofcv.struct.image.Planar,"

								// Enhance image.
	
								+ "boofcv.alg.enhance.EnhanceImageOps";
								
								
								//used previously
//								+ "boofcv.alg.enhance,"
//								+ "boofcv.alg.filter.blur,"
//								+ "boofcv.core.image,"
//								+ "boofcv.io.image,"
//								+ "boofcv.struct.image";
								
	
	static String writeDestination = "output";

    private static AbstractJavassistTool getTransformer(String toolName, List<String> packageNameList, String writeDestination) throws Exception {
        Class<?> transformerClass = Class.forName("pt.ulisboa.tecnico.cnv.javassist.tools." + toolName);
        return (AbstractJavassistTool) transformerClass.getDeclaredConstructor(List.class, String.class).newInstance(packageNameList, writeDestination);
    }

    /**
     * This method is invoked before the target 'main' method is invoked.
     * @param agentArgs
     * @param inst
     * @throws Exception
     */
    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        //String[] argSplits = agentArgs.split(":");
        //String toolName = agentArgs;
        //String packageNames = argSplits[1];
        //String writeDestination = argSplits[2];
        List<String> packageNameList = Arrays.asList(packageNames.split(","));
        String toolName = "ICount";
        //String toolName2 = "MethodExecutionTimer";
        //inst.addTransformer(getTransformer(toolName2, packageNameList, writeDestination), true);
        inst.addTransformer(getTransformer(toolName, packageNameList, writeDestination), true);
    }
}
