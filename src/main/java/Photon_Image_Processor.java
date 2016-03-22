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
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.ProgressBar;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.filter.RankFilters;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.awt.Label;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Photon_Image_Processor
 *
 * Description comes here.
 *
 * @author Lonneke Scheffer & Wout van Helvoirt
 */
public class Photon_Image_Processor implements ExtendedPlugInFilter, DialogListener {

    protected ImagePlus image;
    private int[][] photonCountMatrix;

//    private final int photonOutlineSize = 20;
//    private final int halfPhotonOutlineSize = this.photonOutlineSize / 2;
//    private int photonCounter = 0;
    private SilentMaximumFinder maxFind;
    private ProgressBar pb;
    private Wand wd;
    private float autothreshold;

    private boolean previewing = false;
    private double tolerance = 100;
    private boolean preprocessing = true;
    private String method = "Fast";
    private Label messageArea;

    private int nPasses = 0;

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

        this.image = imp;

        this.maxFind = new SilentMaximumFinder();
        this.setNPasses(this.image.getStackSize());

        this.pb = new ProgressBar(this.image.getCanvas().getWidth(), this.image.getCanvas().getHeight());
       

        return PlugInFilter.DOES_STACKS
                | PlugInFilter.DOES_16
                | PlugInFilter.PARALLELIZE_STACKS
                | PlugInFilter.STACK_REQUIRED
                | PlugInFilter.FINAL_PROCESSING;
    }

    /**
     * The showDialog method will be ran after the setup and creates the dialog window and shows it.
     *
     * Dialog window has support for noise tolerance value, preprocessing step and live preview (run executed one time).
     *
     * @param imp The ImagePlus.
     * @param command String containing the command.
     * @param pfr The PlugInFilterRunner necessary for live preview.
     * @return int for cancel or ok.
     */
    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("Photon Image Processor");

        // Add fields to dialog.
        gd.addNumericField("Noise tolerance", this.tolerance, 0);
        gd.addChoice("Method", new String[] {"Fast", "Accurate", "Subpixel resolution"}, "Fast");
        gd.addCheckbox("Automatic preprocessing", true);
        gd.addPreviewCheckbox(pfr, "Enable preview...");
        gd.addMessage("    "); //space for number of maxima
        this.messageArea = (Label) gd.getMessage();
        gd.addDialogListener(this);
        this.previewing = true;
        gd.showDialog();
        if (gd.wasCanceled()) {
            return PlugInFilter.DONE;
        }
        this.previewing = false;
        if (!this.dialogItemChanged(gd, null)) {
            return PlugInFilter.DONE;
        }

        // Get entered values.
        this.tolerance = gd.getNextNumber();
        this.method = gd.getNextChoice();
        this.preprocessing = gd.getNextBoolean();

        if (this.method.equals("Subpixel resolution")) {
            this.photonCountMatrix = new int[imp.getWidth() * 2][imp.getHeight() * 2];
        } else {
            this.photonCountMatrix = new int[imp.getWidth()][imp.getHeight()];
        }

        return PlugInFilter.DONE;
    }

    /**
     * This method changes the preview if user has entered a new value.
     *
     * @param gd The dialog window.
     * @param e A AWTEvent.
     * @return boolean false if one or more field are not correct.
     */
    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        this.tolerance = gd.getNextNumber();
        this.method = gd.getNextChoice();
        this.preprocessing = gd.getNextBoolean();
        if (this.tolerance < 0) {
            this.tolerance = 0;
        }
        if (!gd.isPreviewActive()) {
            this.messageArea.setText("");
        }

        return (!gd.invalidNumber());
    }

    @Override
    public void setNPasses(int nPasses) {
        this.nPasses = nPasses;
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
        // Show status and set a progressbar.
        IJ.showStatus("Processing...");
        ip.setProgressBar(this.pb);

        Polygon coordinates;

        // Preprocess the current slice.
        if (this.preprocessing) {
            this.preprocessImage(ip);
        }

        // Find the photon coordinates.
        coordinates = this.findPhotons(ip);
        
        

        // If previewing enabled, show found maxima's on slice.
        if (this.previewing) {
            PointRoi p = new PointRoi(coordinates.xpoints, coordinates.ypoints, coordinates.npoints);
            image.setRoi(p);
            this.messageArea.setText((coordinates.xpoints == null ? 0 : coordinates.npoints) + " photons found");
        } else {
            this.wd = new Wand(ip);
            this.autothreshold = ip.getAutoThreshold();
            
            // Loop through all found coordinates.
            for (int i = 0; i < coordinates.npoints; i++) {
                int x = coordinates.xpoints[i];
                int y = coordinates.ypoints[i];

                if (true) {
                    int[] subPixelCoordinates = this.calculateSubPixelCoordinates(x, y, ip, autothreshold);
                    x = subPixelCoordinates[0];
                    y = subPixelCoordinates[1];
                }

                // Add the coordinates to the photon count matrix.
                this.photonCountMatrix[x][y]++;
            }
        }

        // Update the progressbar.
        this.pb.show(ip.getSliceNumber(), this.nPasses);
    }

    /**
     * Preprocess the images. For instance: despeckling the images to prevent false positives.
     *
     */
    private void preprocessImage(ImageProcessor ip) {
        // Perform 'despeckle' using RankFilters.
        SilentRankFilters r = new SilentRankFilters();
        r.rank(ip, 1, RankFilters.MEDIAN);
    }

    /**
     * Find the photons in the current image using MaximumFinder, and return their approximate coordinates.
     *
     */
    private Polygon findPhotons(ImageProcessor ip) {
        int[][] coordinates;

        // Find the maxima using MaximumFinder
        Polygon maxima = this.maxFind.getMaxima(ip, this.tolerance, true); // Multiple outputs on ext. drive

        coordinates = new int[2][maxima.npoints];
        coordinates[0] = maxima.xpoints; // x coordinates
        coordinates[1] = maxima.ypoints; // y coordinates

        return maxima;
    }

    /**
     * Calculate the exact subpixel positions of the photon events at the given coordinates.
     *
     * @param xCor original x coordinate as found by MaximumFinder
     * @param yCor original y coordinate as found by MaximumFinder
     * @param ip the imageprocessor
     * @return the new calculated coordinates
     */
    private int[] calculateSubPixelCoordinates(int xCor, int yCor, ImageProcessor ip, float autoThreshold){
        int[] subPixelCoordinates = new int[2];
        

        //wd.autoOutline(xCor, yCor, ip.getAutoThreshold(), ip.getMax(), Wand.EIGHT_CONNECTED);
        this.wd.autoOutline(xCor, yCor, autoThreshold, Wand.FOUR_CONNECTED);
        Rectangle rect = new PolygonRoi(this.wd.xpoints, this.wd.ypoints, this.wd.npoints, Roi.FREEROI).getBounds();
        
        
        subPixelCoordinates[0] = (int)rect.getCenterX() * 2;
        subPixelCoordinates[1] = (int)rect.getCenterY() * 2;

//        if (ip.getSliceNumber() == 1) {
//            //this.image.setRoi(pr, true);
//            this.image.setRoi(pr.getBounds());
//        }
//        System.out.println("image: "+ip.getSliceNumber());
//        System.out.println("* oldx: "+xCor+" oldy: "+yCor);
//        System.out.println("* width: "+pr.getBounds().width+" height: "+pr.getBounds().height);
//        System.out.println("* x: "+pr.getBounds().getCenterX()+" y: "+pr.getBounds().getCenterY());
        return subPixelCoordinates;
    }

//    /**
//     * Calculate the exact positions of the given coordinates.
//     *
//     * @param xCor original x coordinate
//     * @param yCor original y coordinate
//     * @param ip the imageprocessor
//     * @return the new calculated coordinates
//     */
//    private int[] findExactCoordinates(float xCor, float yCor, ImageProcessor ip) {
//        int[] foundCoordinates = new int[2];
//        int leftBoundary = (int) xCor - this.halfPhotonOutlineSize;
//        int topBoundary = (int) yCor - this.halfPhotonOutlineSize;
//
//        this.photonCounter++;
//
//        // Create a new ROI (region of interest, or selected space)
//        Roi photonOutline = new Roi(leftBoundary,
//                topBoundary,
//                this.photonOutlineSize,
//                this.photonOutlineSize);
//
//        // If the ROI is outside the frame (left and top), set the boundaries to 0
//        if (leftBoundary < 0) {
//            leftBoundary = 0;
//        }
//        if (topBoundary < 0) {
//            topBoundary = 0;
//        }
//
//        // set the ROI
//        ip.setRoi(photonOutline);
//        ImagePlus photonImp = new ImagePlus("single photon " + this.photonCounter, ip.crop());
//        photonImp.close();
//
//        // reset the ROI
//        ip.resetRoi();
//        ip.crop();
//
////        old autothresholding method: (does not work)
////        photonIp.setAutoThreshold(AutoThresholder.Method.Triangle, false, ImageProcessor.BLACK_AND_WHITE_LUT);
////        photonImp.show();
////        photonImp.updateImage();
////        photonImp.updateAndDraw();
//        // perform autothresholding
//        ImageProcessor photonIp = photonImp.getProcessor();
//        photonIp.autoThreshold();
//
//        // find the new midpoints
//        SilentMaximumFinder m = new SilentMaximumFinder();
//        Polygon photonMaxima = m.getMaxima(photonIp, 10, true);
//
//        // by default the 'new' coordinates are set to the original coordinates
//        foundCoordinates[0] = (int) xCor;
//        foundCoordinates[1] = (int) yCor;
//
//        // If one of the found coordinatepairs is contains the original coordinates,
//        // then they were right in the beginning, return the original coordinates
//        for (int i = 0; i < photonMaxima.npoints; i++) {
//            if (photonMaxima.xpoints[i] == this.halfPhotonOutlineSize
//                    && photonMaxima.ypoints[i] == this.halfPhotonOutlineSize) {
//                return foundCoordinates;
//            }
//        }
//
//        // All resulting coordinates are different from the original coordinates:
//        switch (photonMaxima.npoints) {
//            case 0:
//                // 1. if there were no coordinates found, return the original coordinates
////            System.out.println("slice " + ip.getSliceNumber() + "photon " + this.photonCounter + ": none found, coordinates: " + xCor + ", " + yCor);
//                return foundCoordinates;
//            case 1:
//                // 2. if there was one coordinatepair found, return this pair
////            System.out.println("slice " + ip.getSliceNumber() + " photon " + this.photonCounter + ": different found, coordinates: " + xCor + ", " + yCor + ", now set to: " + foundCoordinates[0] + ", " + foundCoordinates[1] + " following: "+ (int)results.getValue("X", 0) + ", " + (int)results.getValue("Y", 0));
//                foundCoordinates[0] = leftBoundary + photonMaxima.xpoints[0];
//                foundCoordinates[1] = topBoundary + photonMaxima.ypoints[0];
//                return foundCoordinates;
//            default:
//                // 3. there were multiple coordinatepairs found, return the one closest to the center,
//                // this is most likely to be the correct one
//                // set the first results as the 'new coordinates'
//                foundCoordinates[0] = leftBoundary + photonMaxima.xpoints[0];
//                foundCoordinates[1] = topBoundary + photonMaxima.ypoints[0];
//                // calculate the distance of the first result to the center
//                float distance = this.getEuclidianDistance(xCor, yCor, foundCoordinates[0], foundCoordinates[1]);
//                // compare with all other results
//                for (int i = 1; i < photonMaxima.npoints; i++) {
//                    float newDistance = this.getEuclidianDistance(xCor, yCor,
//                            (leftBoundary + photonMaxima.xpoints[i]),
//                            (topBoundary + photonMaxima.ypoints[i]));
//                    // if the newly found result is closer to the center, set it as the result
//                    if (newDistance < distance) {
//                        foundCoordinates[0] = leftBoundary + photonMaxima.xpoints[i];
//                        foundCoordinates[1] = topBoundary + photonMaxima.ypoints[i];
//                        distance = newDistance;
//                    }
//                }
//                return foundCoordinates;
//        }
//    }
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

    /**
     * This method generates and displays the final image from the photonCountMatrix.
     */
    private void createOutputImage() {

        // Create new ByteProcessor for output image with matrix data and it's width and height.
        ByteProcessor bp = new ByteProcessor(this.photonCountMatrix.length, this.photonCountMatrix[0].length);
        bp.setIntArray(this.photonCountMatrix);

        // Add the amount of different values in matrix.
        List<Integer> diffMatrixCount = new ArrayList<>();
        for (int[] photonCountMatrix1 : this.photonCountMatrix) {
            for (int photonCountMatrix2 : photonCountMatrix1) {
                if (!diffMatrixCount.contains(photonCountMatrix2)) {
                    diffMatrixCount.add(photonCountMatrix2);
                }
            }
        }

        // Use 0 as min and max value as size from matrix minus two outer values for for 0-255 grayscale pixel mapping.
        bp.setMinAndMax(0, (diffMatrixCount.size() - 2)); // Pixel mapping uses blocks.
        bp.applyLut();

        // Create new output image with title.
        ImagePlus outputImage = new ImagePlus("Photon Image Processor - Output", bp.duplicate());

        // Make new image window in ImageJ and set the window visible.
        ImageWindow outputWindow = new ImageWindow(outputImage);
        outputWindow.setVisible(true);
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
        IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/test_lonneke_kan_weg/100100");
//        IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata");
//        IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata/zelfgemaakt");
        // paths Wout
//        IJ.run("Image Sequence...", "open=/Volumes/Bioinf/SinglePhotonData");
//        IJ.run("Image Sequence...", "open=/Users/Wout/Desktop/100100");
        ImagePlus image = IJ.getImage();

        // Only if you use new ImagePlus(path) to open the file
        //image.show();
        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }
}
