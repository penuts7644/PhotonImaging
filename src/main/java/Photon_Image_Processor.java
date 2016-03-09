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
import ij.process.AutoThresholder;
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
    private final int photonOutlineSize = 20;
    private final int halfPhotonOutlineSize = this.photonOutlineSize/2;
    
    private int photonCounter = 0;
    private int imageCounter = 0;

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
        this.imageCounter++;
        float[][] coordinates;
        String sameX;
        String sameY;
        
        this.preprocessImages(ip);
        
        MaximumFinder maxFind = new MaximumFinder();
        coordinates = this.findPhotons(ip, maxFind);
        System.out.println("+---------------------------------------------------+");
        System.out.println("|            Image" + this.imageCounter + ", " + coordinates[0].length + " photons found.             |");
        System.out.println("+---------------------------------------------------+");
        System.out.println("| Original x | Adjusted x | Original y | Adjusted y |");
        
        
        for (int i = 0; i < coordinates[0].length; i++){
            float x = coordinates[0][i];
            float y = coordinates[1][i];
            int[] newCoordinates = this.findExactCoordinates(x, y, ip);
            
            coordinates[0][i] = newCoordinates[0];
            coordinates[1][i] = newCoordinates[1];
            
            if (x == newCoordinates[0]){
                sameX = " ";
            }else {
                sameX = "*";
            }
            
            if (y == newCoordinates[1]){
                sameY = " ";
            }else {
                sameY = "*";
            }
            
            System.out.println("+------------+------------+------------+------------+");
            System.out.printf( "| %10s | %1s%9s | %10s | %1s%9s |%n", (int)x, sameX, newCoordinates[0], (int)y, sameY, newCoordinates[1]);
        }
        System.out.println("+---------------------------------------------------+\n");
        
        this.addToPhotonCount(coordinates);
  
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

    private int[] findExactCoordinates(float xCor, float yCor, ImageProcessor ip) {
        int[] foundCoordinates = new int[2];
        int leftBoundary = (int)xCor - this.halfPhotonOutlineSize;
        int topBoundary = (int)yCor - this.halfPhotonOutlineSize;
        
        this.photonCounter ++;
        
        Roi photonOutline = new Roi(leftBoundary,
                                    topBoundary,
                                    this.photonOutlineSize,
                                    this.photonOutlineSize);
        
        // If the roi is outside the frame (left and top), set the boundaries to 0
        if (leftBoundary < 0){
            leftBoundary = 0;
        }
        if (topBoundary < 0){
            topBoundary = 0;
        }
        
        // set the ROI
        ip.setRoi(photonOutline);
        ImagePlus photonImp = new ImagePlus("single photon " + this.photonCounter, ip.crop());
        photonImp.close();
        
        // reset the ROI
        ip.resetRoi();
        ip.crop();
        
//        photonIp.setAutoThreshold(AutoThresholder.Method.Triangle, false, ImageProcessor.BLACK_AND_WHITE_LUT);
//        photonImp.show();
//        photonImp.updateImage();
//        photonImp.updateAndDraw();
//        photonImp.getPixel(0, 0);
        
        // perform autothresholding
        ImageProcessor photonIp = photonImp.getProcessor();
        photonIp.autoThreshold();
        
        // find the new midpoints
        MaximumFinder m = new MaximumFinder();
        m.findMaxima(photonIp, 10, MaximumFinder.LIST, true);
        
        // get the results and close the window
        ResultsTable results = ResultsTable.getResultsTable();
        ResultsTable.getResultsWindow().close(false);
        
        // by default the 'new' coordinates are set to the original coordinates
        foundCoordinates[0] = (int)xCor;
        foundCoordinates[1] = (int)yCor;
        
        // If one of the found coordinatepairs is just the original coordinates,
        // then they were right in the beginning, return them
        for (int i = 0; i < results.getCounter(); i++){
            if (results.getValue("X", i) == this.halfPhotonOutlineSize &&
                results.getValue("Y", i) == this.halfPhotonOutlineSize){
                return foundCoordinates;
            }
        }
        
        // Coordinates are different from the original coordinates:
        if (results.getCounter() == 0){
            // 1. if there were no coordinates found, return the original coordinates
//            System.out.println("image " + this.imageCounter + "photon " + this.photonCounter + ": none found, coordinates: " + xCor + ", " + yCor);
            return foundCoordinates;
        } else if(results.getCounter() == 1){
            // 2. if there was one coordinatepair found, return this pair
//            System.out.println("image " + this.imageCounter + " photon " + this.photonCounter + ": different found, coordinates: " + xCor + ", " + yCor + ", now set to: " + foundCoordinates[0] + ", " + foundCoordinates[1] + " following: "+ (int)results.getValue("X", 0) + ", " + (int)results.getValue("Y", 0));
            foundCoordinates[0] = leftBoundary + (int)results.getValue("X", 0);
            foundCoordinates[1] = topBoundary + (int)results.getValue("Y", 0);
            return foundCoordinates;
        } else {
            // 3. there were multiple coordinatepairs found, return the one closest to the center,
            // this is most likely to be the correct one
            // set the first results as the 'new coordinates'
            foundCoordinates[0] = leftBoundary + (int)results.getValue("X", 0);
            foundCoordinates[1] = topBoundary + (int)results.getValue("Y", 0);
            // calculate the distance
            float distance = this.getEuclidianDistance(xCor, yCor, foundCoordinates[0], foundCoordinates[1]);
            // compare with all others
            for (int i = 1; i < results.getCounter(); i++){
                float newDistance = this.getEuclidianDistance(xCor, yCor, 
                                              (leftBoundary + (int)results.getValue("X", i)), 
                                              (topBoundary + (int)results.getValue("Y", i)));
                if (newDistance < distance){
                    foundCoordinates[0] = leftBoundary + (int)results.getValue("X", i);
                    foundCoordinates[1] = topBoundary + (int)results.getValue("Y", i);
                    distance = newDistance;
                }
            }
            return foundCoordinates;
        }
    }
    
    private float getEuclidianDistance(float originalX, float originalY, float newX,  float newY){
        return (float) Math.sqrt((originalX - newX) * (originalX - newX) 
                               + (originalY - newY) * (originalY - newY));
    }
 
    
    private void addToPhotonCount(float[][] coordinates){
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
        //this.photonOutlineSize = (int) gd.getNextNumber();

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
//        IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/kleinbeetjedata");
//        IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/meerdaneenkleinbeetje");
//        IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/SinglePhotonData");
        IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata");
//        IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata/zelfgemaakt");
        ImagePlus image = IJ.getImage();

        // Only if you use new ImagePlus(path)
        //image.show();
        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }

}