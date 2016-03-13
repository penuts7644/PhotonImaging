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
import ij.gui.ImageWindow;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.RankFilters;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.Polygon;

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

    private final int photonOutlineSize = 20;
    private final int halfPhotonOutlineSize = this.photonOutlineSize / 2;

    private int photonCounter = 0;

    private MaximumFinder maxFind;

    /**
     * Setup method as initializer.
     *
     * Setup method is the initializer for this class and will always be ran first. Arguments necessary can be given
     * here. Setup method needs to be overridden.
     *
     * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
     * @return None if help shown, else plugin does gray 16-bit stacks and parallel.
     */
    @Override
    public int setup(String argument, ImagePlus imp) {
        if (argument.equals("about")) {
            this.showAbout();
            return PlugInFilter.DONE;
        } else if (argument.equals("final")) {
            this.createOutputImage();
            return PlugInFilter.DONE;
        }

//        boolean dialogCorrect = this.showDialog();
//        if (!dialogCorrect) {
//            return PlugInFilter.DONE;
//        }
        this.image = imp;
        this.photonCountMatrix = new int[imp.getWidth()][imp.getHeight()];
        this.maxFind = new MaximumFinder();

//        this.photonCountMatrix = new int[10][10];
//        this.photonCountMatrix[0][4] = 1;
//        this.photonCountMatrix[1][4] = 1;
//        this.photonCountMatrix[2][4] = 1;
//        this.photonCountMatrix[3][4] = 1;
//        this.photonCountMatrix[5][4] = 1;
//        this.photonCountMatrix[6][4] = 1;
//        this.photonCountMatrix[7][4] = 1;
//        this.photonCountMatrix[8][4] = 1;
//        this.photonCountMatrix[9][4] = 1;
//
//        this.photonCountMatrix[4][4] = 10;
//
//        this.photonCountMatrix[4][0] = 1;
//        this.photonCountMatrix[4][1] = 1;
//        this.photonCountMatrix[4][2] = 1;
//        this.photonCountMatrix[4][3] = 1;
//        this.photonCountMatrix[4][5] = 1;
//        this.photonCountMatrix[4][6] = 1;
//        this.photonCountMatrix[4][7] = 1;
//        this.photonCountMatrix[4][8] = 1;
//        this.photonCountMatrix[4][9] = 1;
        return PlugInFilter.DOES_STACKS
                | PlugInFilter.DOES_16
                | PlugInFilter.PARALLELIZE_STACKS
                | PlugInFilter.STACK_REQUIRED
                | PlugInFilter.FINAL_PROCESSING;
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

        Polygon coordinates;

        this.preprocessImage(ip);

        // find the photon coordinates
        coordinates = this.findPhotons(ip);

        // int avgThreshold = this.getAverageThreshold(ip);
        // loop through all found coordinates
        for (int i = 0; i < coordinates.npoints; i++) {
            int x = coordinates.xpoints[i];
            int y = coordinates.ypoints[i];
            int[] newCoordinates = this.findExactCoordinates(x, y, ip);

//            PolygonRoi polygonSelection = this.getRoiSelection(x, y, avgThreshold, ip);
//            coordinates.xpoints[i] = newCoordinates[0];
//            coordinates.ypoints[i] = newCoordinates[1];
            // Add the adjusted coordinates to the photon count matrix
            this.photonCountMatrix[newCoordinates[0]][newCoordinates[1]]++;
        }
        // Add the found photon coordinates to the total count grid
//        this.addToPhotonCount(coordinates);
    }

    /**
     * Preprocess the images. For instance: despeckling the images to prevent false positives.
     *
     */
    private void preprocessImage(ImageProcessor ip) {
        // Perform 'despeckle' using RankFilters
        RankFilters r = new RankFilters();
        r.rank(ip, 1, RankFilters.MEDIAN);
    }

    /**
     * Find the photons in the current image using MaximumFinder, and return their approximate coordinates.
     *
     */
    private Polygon findPhotons(ImageProcessor ip) {
        int[][] coordinates;

        // Find the maxima using MaximumFinder
        Polygon maxima = this.maxFind.getMaxima(ip, 30.0, false);

        coordinates = new int[2][maxima.npoints];
        coordinates[0] = maxima.xpoints; // x coordinates
        coordinates[1] = maxima.ypoints; // y coordinates

        return maxima;
    }

    private int getAverageThreshold(ImageProcessor ip) {
        return ip.getAutoThreshold();
    }

    private PolygonRoi getRoiSelection(float xCor, float yCor, int threshold, ImageProcessor ip) {
        Wand wd = new Wand(ip);
        wd.autoOutline((int) xCor, (int) yCor, threshold, 255, 1);
        System.out.println("N: " + wd.npoints + " X: " + wd.xpoints.length + " Y: " + wd.ypoints.length);
        PolygonRoi pr = new PolygonRoi(wd.xpoints, wd.ypoints, wd.npoints, 3);

        return pr;
    }

    /**
     * Calculate the exact positions of the given coordinates.
     *
     * @param xCor original x coordinate
     * @param yCor original y coordinate
     * @param ip the imageprocessor
     * @return the new calculated coordinates
     */
    private int[] findExactCoordinates(float xCor, float yCor, ImageProcessor ip) {
        int[] foundCoordinates = new int[2];
        int leftBoundary = (int) xCor - this.halfPhotonOutlineSize;
        int topBoundary = (int) yCor - this.halfPhotonOutlineSize;

        this.photonCounter++;

        // Create a new ROI (region of interest, or selected space)
        Roi photonOutline = new Roi(leftBoundary,
                topBoundary,
                this.photonOutlineSize,
                this.photonOutlineSize);

        // If the ROI is outside the frame (left and top), set the boundaries to 0
        if (leftBoundary < 0) {
            leftBoundary = 0;
        }
        if (topBoundary < 0) {
            topBoundary = 0;
        }

        // set the ROI
        ip.setRoi(photonOutline);
        ImagePlus photonImp = new ImagePlus("single photon " + this.photonCounter, ip.crop());
        photonImp.close();

        // reset the ROI
        ip.resetRoi();
        ip.crop();

//        old autothresholding method: (does not work)
//        photonIp.setAutoThreshold(AutoThresholder.Method.Triangle, false, ImageProcessor.BLACK_AND_WHITE_LUT);
//        photonImp.show();
//        photonImp.updateImage();
//        photonImp.updateAndDraw();
        // perform autothresholding
        ImageProcessor photonIp = photonImp.getProcessor();
        photonIp.autoThreshold();

        // find the new midpoints
        MaximumFinder m = new MaximumFinder();
        Polygon photonMaxima = m.getMaxima(photonIp, 10, true);

        // by default the 'new' coordinates are set to the original coordinates
        foundCoordinates[0] = (int) xCor;
        foundCoordinates[1] = (int) yCor;

        // If one of the found coordinatepairs is contains the original coordinates,
        // then they were right in the beginning, return the original coordinates
        for (int i = 0; i < photonMaxima.npoints; i++) {
            if (photonMaxima.xpoints[i] == this.halfPhotonOutlineSize
                    && photonMaxima.ypoints[i] == this.halfPhotonOutlineSize) {
                return foundCoordinates;
            }
        }

        // All resulting coordinates are different from the original coordinates:
        switch (photonMaxima.npoints) {
            case 0:
                // 1. if there were no coordinates found, return the original coordinates
//            System.out.println("slice " + ip.getSliceNumber() + "photon " + this.photonCounter + ": none found, coordinates: " + xCor + ", " + yCor);
                return foundCoordinates;
            case 1:
                // 2. if there was one coordinatepair found, return this pair
//            System.out.println("slice " + ip.getSliceNumber() + " photon " + this.photonCounter + ": different found, coordinates: " + xCor + ", " + yCor + ", now set to: " + foundCoordinates[0] + ", " + foundCoordinates[1] + " following: "+ (int)results.getValue("X", 0) + ", " + (int)results.getValue("Y", 0));
                foundCoordinates[0] = leftBoundary + photonMaxima.xpoints[0];
                foundCoordinates[1] = topBoundary + photonMaxima.ypoints[0];
                return foundCoordinates;
            default:
                // 3. there were multiple coordinatepairs found, return the one closest to the center,
                // this is most likely to be the correct one
                // set the first results as the 'new coordinates'
                foundCoordinates[0] = leftBoundary + photonMaxima.xpoints[0];
                foundCoordinates[1] = topBoundary + photonMaxima.ypoints[0];
                // calculate the distance of the first result to the center
                float distance = this.getEuclidianDistance(xCor, yCor, foundCoordinates[0], foundCoordinates[1]);
                // compare with all other results
                for (int i = 1; i < photonMaxima.npoints; i++) {
                    float newDistance = this.getEuclidianDistance(xCor, yCor,
                            (leftBoundary + photonMaxima.xpoints[i]),
                            (topBoundary + photonMaxima.ypoints[i]));
                    // if the newly found result is closer to the center, set it as the result
                    if (newDistance < distance) {
                        foundCoordinates[0] = leftBoundary + photonMaxima.xpoints[i];
                        foundCoordinates[1] = topBoundary + photonMaxima.ypoints[i];
                        distance = newDistance;
                    }
                }
                return foundCoordinates;
        }
    }

    /**
     * Calculate the euclidian distance between two points in two-dimensional space.
     *
     * @param firstX x coordinate of the first point
     * @param firstY y coordinate of the first point
     * @param secondX x coordinate of the second point
     * @param secondY y coordinate of the second point
     * @return euclidian distance between the two points
     */
    private float getEuclidianDistance(float firstX, float firstY, float secondX, float secondY) {
        return (float) Math.sqrt((firstX - secondX) * (firstX - secondX)
                + (firstY - secondY) * (firstY - secondY));
    }
//
//    /**
//     * Add the coordinate pairs to the photon count matrix.
//     *
//     * @param coordinates
//     */
//    private void addToPhotonCount(Polygon coordinates) {
//        for (int i = 0; i < coordinates.npoints; i++) {
//            int x = coordinates.xpoints[i];
//            int y = coordinates.ypoints[i];
//            this.photonCountMatrix[x][y]++;
//        }
//    }

    /**
     * This method generates and displays the final image from the photonCountMatrix.
     */
    private void createOutputImage() {

        // Create new ByteProcessor for output image with matrix data and it's width and height.
        ByteProcessor bp = new ByteProcessor(this.photonCountMatrix.length, this.photonCountMatrix[0].length);
        bp.setIntArray(this.photonCountMatrix);

        // Search for largest count value in matrix.
        int maxMatrixCount = 0;
        for (int[] photonCountMatrix1 : this.photonCountMatrix) {
            for (int photonCountMatrix2 : photonCountMatrix1) {
                if (photonCountMatrix2 > maxMatrixCount) {
                    maxMatrixCount = photonCountMatrix2;
                }
            }
        }

        // Use min and max values from matrix for for 0-255 grayscale pixel mapping.
        bp.setMinAndMax(0, maxMatrixCount);

        // Create new output image with title.
        ImagePlus outputImage = new ImagePlus("Photon Image Processor - Output", bp.createImage());

        // Make new image window in ImageJ and set the window visible.
        ImageWindow outputWindow = new ImageWindow(outputImage);
        outputWindow.setVisible(true);
    }

    /**
     * This method is not used yet.
     *
     * @return boolean for cancel or enter.
     */
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

    /**
     * This method displays the about information of the plugin.
     */
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
//        IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata");
//        IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata/zelfgemaakt");
        // paths Wout
        IJ.run("Image Sequence...", "open=/Volumes/NIFTY/GoogleDrive/Documenten/HanzeHogeschool/Thema11en12/Themaopdracht/SinglePhotonData");
        ImagePlus image = IJ.getImage();

        // Only if you use new ImagePlus(path) to open the file
        //image.show();
        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }

}
