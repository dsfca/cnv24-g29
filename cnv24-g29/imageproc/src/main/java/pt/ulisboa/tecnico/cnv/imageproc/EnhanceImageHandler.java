package pt.ulisboa.tecnico.cnv.imageproc;

import boofcv.alg.enhance.EnhanceImageOps;
import boofcv.concurrency.BoofConcurrency;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayU8;

import java.awt.image.BufferedImage;
import java.net.UnknownHostException;

public class EnhanceImageHandler extends ImageProcessingHandler {
	
	private int width;
	private int height;

    public BufferedImage process(BufferedImage bi) throws UnknownHostException {
    	this.width = bi.getWidth();
    	this.height = bi.getHeight();
    	BoofConcurrency.USE_CONCURRENT = false;
        GrayU8 gray = ConvertBufferedImage.convertFrom(bi, (GrayU8)null);
        GrayU8 adjusted = gray.createSameShape();
        EnhanceImageOps.equalizeLocal(gray, 50, adjusted, 256, null);
        return ConvertBufferedImage.convertTo(adjusted, null);
    }
    

    public static void main(String[] args) throws UnknownHostException {

        if (args.length != 2) {
            System.err.println("Syntax EnhanceImageHandler <input image path> <output image path>");
            return;
        }

        String inputImagePath = args[0];
        String outputImagePath = args[1];
        BufferedImage bufferedInput = UtilImageIO.loadImageNotNull(inputImagePath);
        BufferedImage bufferedOutput  = new EnhanceImageHandler().process(bufferedInput);
        UtilImageIO.saveImage(bufferedOutput, outputImagePath);
    }


	@Override
	String getEffect() {
		// TODO Auto-generated method stub
		return "EnhanceImage";
	}


	@Override
	int getWidth() {
		
		return width;
	}


	@Override
	int getHeight() {
		// TODO Auto-generated method stub
		return height;
	}
}
