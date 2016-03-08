/*
 * Copyright (c) 2016 Lonneke Scheffer & Wout van Helvoirt
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.RankFilters;
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
    private int[][] photonCountMatrix;
    

    // image property members
//    private int width;
//    private int height;
    // plugin parameters
    public int photonOutlineSize = 20;

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

//        boolean dialogCorrect = this.showDialog();
//        if (!dialogCorrect) {
//            return PlugInFilter.DONE;
//        }
        
        this.image = imp;
        this.photonCountMatrix = new int[imp.getWidth()][imp.getHeight()];
        
        return PlugInFilter.DOES_STACKS | PlugInFilter.DOES_8G | PlugInFilter.DOES_16 | PlugInFilter.DOES_32;
    }

    /**
     * Executed method when selected.
     *
     * Run method gets executed when setup is finished and when the user selects this class via plugins in Fiji. Run
     * method needs to be overridden.
     *
     * @param ip image processor
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(ImageProcessor ip) {
        // get width and height
//        this.width = ip.getWidth();
//        this.height = ip.getHeight();
        float[][] rawCoordinates;
        
        this.preprocessImages(ip);
        
        MaximumFinder maxFind = new MaximumFinder();
        rawCoordinates = this.findPhotons(ip, maxFind);
        
        System.out.println("\n************\n" + rawCoordinates[0].length + " photons found\n************");
        for (int i = 0; i < rawCoordinates[0].length; i++){
            float x = rawCoordinates[0][i];
            float y = rawCoordinates[1][i];
            this.outlinePhoton(x, y);
        }
        
        this.addToPhotonCount(rawCoordinates);
  
// test print stukje van het count foton grid
//        for (int i=1000; i < 1100; i++){
//            for (int j=1000; j < 1100; j++){
//                System.out.printf(this.photonCountMatrix[i][j] + " ");
//            }
//            System.out.println("");
//        }

    }

    /**
     * Preprocess the images. For instance: adjusting brightness/contrast/noise calibration
     *
     */
    private void preprocessImages(ImageProcessor ip) {
        // Perform 'despeckle' using RankFilters
        RankFilters r = new RankFilters();
        r.rank(ip, 1, RankFilters.MEDIAN);
    }

    /**
     * Find the photons in the current image using MaximumFinder, and return their approximate coordinates.
     *
     */
    private float[][] findPhotons(ImageProcessor ip, MaximumFinder maxFind) {
        float[][] coordinates;

        // Find the maxima using MaximumFinder
        maxFind.findMaxima(ip, 30.0, MaximumFinder.LIST, false);
        
        // Retrieve the results
        ResultsTable results = ResultsTable.getResultsTable();
        coordinates = new float[2][results.size()];
        coordinates[0] = results.getColumn(0); // x coordinates
        coordinates[1] = results.getColumn(1); // y coordinates

        // Close the results table without showing the dialog for saving data
        ResultsTable.getResultsWindow().close(false);

        return coordinates;
    }

    private void outlinePhoton(float xCor, float yCor) {
        int halfPOS = this.photonOutlineSize / 2;
        Roi photonOutline = new Roi((xCor - halfPOS), (yCor - halfPOS), this.photonOutlineSize, this.photonOutlineSize);
        //System.out.println("x = " + photonOutline.getXBase() + ": y = " + photonOutline.getYBase());
    }
    
    private void addToPhotonCount(float[][] coordinates) {
        for (int i = 0; i < coordinates[0].length; i++){
            int x = (int) coordinates[0][i];
            int y = (int) coordinates[1][i];
            this.photonCountMatrix[x][y] ++;
        }
    }

    private boolean showDialog() {
        GenericDialog gd = new GenericDialog("Photon Image Processor");

        // default value is 20, 0 digits right of the decimal point
        gd.addSlider("Photon Grid Size", 1, 25, 1);

        gd.showDialog();
        if (gd.wasCanceled()) {
            return false;
        }

        // get entered values
        this.photonOutlineSize = (int) gd.getNextNumber();

        return true;
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
        //IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/SinglePhotonData");
        //IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata");
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
