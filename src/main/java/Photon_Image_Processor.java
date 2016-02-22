/*
 * Copyright (c) 2016 Lonneke Scheffer & Wout van Helvoirt
 * All rights reserved
 */

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Photon_Image_Processor
 *
 * Description comes here.
 *
 * @author Lonneke Scheffer & Wout van Helvoirt
 */
public class Photon_Image_Processor implements PlugInFilter {

    protected ImagePlus image;

    // image property members
    private int width;
    private int height;

    // plugin parameters
    public double value;
    public String name;

    /**
     * Setup method as initializer.
     *
     * Setup method is the initializer for this class and will always be ran first. Arguments necessary can be given
     * here. Setup method needs to be overridden.
     *
     * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
     * @return None if help shown, else plugin does gray 8, 16, 32 as well as color rgb.
     */
    @Override
    public int setup(String argument, ImagePlus imp) {
        if (argument.equals("about")) {
            showAbout();
            return PlugInFilter.DONE;
        }

        this.image = imp;
        return PlugInFilter.DOES_STACKS | PlugInFilter.DOES_8G | PlugInFilter.DOES_16 | PlugInFilter.DOES_32 | PlugInFilter.DOES_RGB;
    }

    /**
     * Executed method when selected.
     *
     * Run method gets executed when setup is finished and when the user selects this class via plugins in Fiji.
     * Run method needs to be overridden.
     *
     * @param ip image processor
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(ImageProcessor ip) {
        // get width and height
        this.width = ip.getWidth();
        this.height = ip.getHeight();
        
        this.preprocessImages();
        MaximumFinder maxFind = new MaximumFinder();
        this.findPhotons(ip, maxFind);
        
    }
    
    /**
     * Preprocess the images.
     * For instance: adjusting brightness/contrast/noise calibration
     * 
     */
    private void preprocessImages() {}
    
    /**
     * Find the photons in the current image, and return their approximate coordinates.
     * 
     */
    private void findPhotons(ImageProcessor ip, MaximumFinder maxFind) {
        maxFind.findMaxima(ip, 50.0, MaximumFinder.LIST, false);
        
        //Analyzer a = new Analyzer(ip);
        ResultsTable r = Analyzer.getResultsTable();
        float[] xCo = r.getColumn(0);
        float[] yCo = r.getColumn(1);
        
        System.out.println("slice number: " + ip.getSliceNumber());
        for (int i = 0; i < r.size(); i++){
            System.out.println(i + ": x = " + xCo[i] + ", y = " + yCo[i]);
           
        }
    }
    

    public void showAbout() {
        IJ.showMessage("About Photon Image Processor",
                "Test help message."
        );
    }

    /**
     * Main method for debugging.
     *
     * For debugging, it is convenient to have a method that starts ImageJ, loads an image and calls the plugin, e.g.
     * after setting breakpoints. Main method will get executed when running this file from IDE.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        // set the plugins.dir property to make the plugin appear in the Plugins menu
        Class<?> clazz = Photon_Image_Processor.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ImageJ();

        // Open the image sequence
        IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/kleinbeetjedata");
        ImagePlus image = IJ.getImage();
        
        // Only if you use new ImagePlus(path)
        //image.show();

        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }

    
}



//    private boolean showDialog() {
//        GenericDialog gd = new GenericDialog("Photon Image Processor");
//
//        // default value is 0.00, 2 digits right of the decimal point
//        gd.addNumericField("value", 0.00, 2);
//        gd.addStringField("name", "John");
//
//        gd.showDialog();
//        if (gd.wasCanceled()) {
//            return false;
//        }
//
//        // get entered values
//        value = gd.getNextNumber();
//        name = gd.getNextString();
//
//        return true;
//    }

    /**
     * Process an image.
     *
     * Please provide this method even if {@link ij.plugin.filter.PlugInFilter} does require it; the method
     * {@link ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)} can only handle 2-dimensional data.
     *
     * If your plugin does not change the pixels in-place, make this method return the results and change the
     * {@link #setup(java.lang.String, ij.ImagePlus)} method to return also the
     * <i>DOES_NOTHING</i> flag.
     *
     * @param image the image (possible multi-dimensional)
     */
//    public void process(ImagePlus image) {
//        // slice numbers start with 1 for historical reasons
//        for (int i = 1; i <= image.getStackSize(); i++) {
//            process(image.getStack().getProcessor(i));
//        }
//    }
//
//    // Select processing method depending on image type
//    public void process(ImageProcessor ip) {
//        int type = image.getType();
//        if (type == ImagePlus.GRAY8) {
//            process((byte[]) ip.getPixels());
//        } else if (type == ImagePlus.GRAY16) {
//            process((short[]) ip.getPixels());
//        } else if (type == ImagePlus.GRAY32) {
//            process((float[]) ip.getPixels());
//        } else if (type == ImagePlus.COLOR_RGB) {
//            process((int[]) ip.getPixels());
//        } else {
//            throw new RuntimeException("not supported");
//        }
//    }
//
//    // processing of GRAY8 images
//    public void process(byte[] pixels) {
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                // process each pixel of the line
//                // example: add 'number' to each pixel
//                pixels[x + y * width] += (byte) value;
//            }
//        }
//    }
//
//    // processing of GRAY16 images
//    public void process(short[] pixels) {
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                // process each pixel of the line
//                // example: add 'number' to each pixel
//                pixels[x + y * width] += (short) value;
//            }
//        }
//    }
//
//    // processing of GRAY32 images
//    public void process(float[] pixels) {
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                // process each pixel of the line
//                // example: add 'number' to each pixel
//                pixels[x + y * width] += (float) value;
//            }
//        }
//    }
//
//    // processing of COLOR_RGB images
//    public void process(int[] pixels) {
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                // process each pixel of the line
//                // example: add 'number' to each pixel
//                pixels[x + y * width] += (int) value;
//            }
//        }
//    }