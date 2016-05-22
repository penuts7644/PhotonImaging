/*
 * Copyright (c) 2016 Lonneke Scheffer and Wout van Helvoirt
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
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.AWTEvent;
import java.awt.Label;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Photon_Image_Processor
 *
 * This class is able to process a stack containing single photon events data and create a combined hi-res image.
 * Each light point within the image (based on user given tolerance value) is being processed as photon. Each photon
 * has a center that can be calculated in a fast or a more accurate way. There are two accurate calculations available.
 * One to create a higher resolution image with four times the amount of pixels (sub-pixel resolution) or one with
 * normal resolution. Photons are being counted and mapped to the correct pixel values to create a 16-bit image.
 *
 * @author Lonneke Scheffer and Wout van Helvoirt
 */
public final class Photon_Image_Processor implements ExtendedPlugInFilter, DialogListener {

    /** The ImagePlus given by the user. */
    protected ImagePlus image;
    /** A matrix for counting photons. */
    private int[][] photonCountMatrix;
    /** The 'silent' version of MaximumFinder, used to find photons. */
    private SilentMaximumFinder maxFind;
    /** The ProgressBar. */
    private ProgressBar pb;
    /** This boolean tells whether the 'previewing' window is open. */
    private boolean previewing = false;
    /** Noise tolerance, default is 100. */
    private double tolerance = 100;
    /** This boolean tells whether the user wants to perform preprocessing. */
    private boolean preprocessing = true;
    /** The output method (fast/accurate/sub-pixel resolution) is set to fast. */
    private String method = "Fast";
    /** This label is used to show the number of maxima found. */
    private Label messageArea;
    /** The number of passes for the progress bar, default is 0. */
    private int nPasses = 0;
    /** The current pass for the progress bar, default is 0. */
    private int cPass = 0;
    /** Set all requirements for plug-in to run. */
    private final int flags = PlugInFilter.DOES_STACKS
            | PlugInFilter.DOES_8G
            | PlugInFilter.DOES_16
            | PlugInFilter.PARALLELIZE_STACKS
            | PlugInFilter.STACK_REQUIRED
            | PlugInFilter.FINAL_PROCESSING;


    /**
     * Setup method as initializer.
     *
     * Setup method is the initializer for this class and will always be run first. Arguments can be given
     * here. Setup method needs to be overridden.
     *
     * @param arg String telling setup what to do.
     * @param imp ImagePlus containing the displayed stack/image.
     * @return flag 'DONE' if help shown, else the general plug in flags
     */
    @Override
    public int setup(final String arg, final ImagePlus imp) {

        // If arg is about, display help message. final for calling setup when run completed.
        if (arg.equals("about")) {
            this.showAbout();
            return PlugInFilter.DONE;
        } else if (arg.equals("final")) {
            this.createOutputImage();
            return PlugInFilter.DONE;
        }

        // Check if image open, else quit.
        if (imp != null) {
            this.image = imp;
            this.maxFind = new SilentMaximumFinder();
            this.setNPasses(this.image.getStackSize());
            this.pb = new ProgressBar(this.image.getCanvas().getWidth(), this.image.getCanvas().getHeight());
        }

        // Return options.
        return this.flags;
    }

    /**
     * The showDialog method will be run after the setup and creates the dialog window and shows it.
     *
     * Dialog window has support for noise tolerance value, preprocessing step and live preview (run is executed once).
     *
     * @param imp The ImagePlus.
     * @param command String containing the command.
     * @param pfr The PlugInFilterRunner necessary for live preview.
     * @return integer for cancel or oke.
     */
    @Override
    public int showDialog(final ImagePlus imp, final String command, final PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("Process Photon Images");

        // Add fields to dialog.
        gd.addNumericField("Noise tolerance", this.tolerance, 0);
        gd.addChoice("Method", new String[]{"Fast", "Accurate", "Subpixel resolution"}, "Fast");
        gd.addCheckbox("Automatic preprocessing", true);
        gd.addPreviewCheckbox(pfr, "Enable preview...");
        gd.addMessage("    "); //space for number of maxima
        this.messageArea = (Label) gd.getMessage();
        gd.addDialogListener(this);

        // Set previewing true and show the dialog.
        this.previewing = true;
        gd.showDialog();
        if (gd.wasCanceled()) {
            return PlugInFilter.DONE;
        }

        // Set previewing false when oke pressed.
        this.previewing = false;
        if (!this.dialogItemChanged(gd, null)) {
            return PlugInFilter.DONE;
        }

        // If subpixel resolution selected, make matrix twice the size.
        if (this.method.equals("Subpixel resolution")) {
            this.photonCountMatrix = new int[imp.getWidth() * 2][imp.getHeight() * 2];
        } else {
            this.photonCountMatrix = new int[imp.getWidth()][imp.getHeight()];
        }

        return this.flags;
    }

    /**
     * This method changes the preview if user has entered a new value.
     *
     * @param gd The dialog window.
     * @param e An AWTEvent.
     * @return boolean false if one or more field are not correct.
     */
    @Override
    public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
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

    /**
     * This method tells the the runner the amount of runs get executed.
     *
     * @param nPasses integer with the amount of runs to be called.
     */
    @Override
    public void setNPasses(final int nPasses) {
        this.nPasses = nPasses;
    }

    /**
     * Run method gets executed when setup is finished and when the user selects this class via plug-ins in Fiji.
     * This method does most of the work, calls all other methods in the right order.
     *
     * @param ip image processor
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(final ImageProcessor ip) {
        // Show status
        this.cPass++;
        IJ.showStatus("Processing...");

        Polygon rawCoordinates;

        // Preprocess the current slice.
        if (this.preprocessing) {
            this.preprocessImage(ip);
        }

        // Find the photon coordinates.
        rawCoordinates = this.findPhotons(ip);

        // If previewing enabled, show found maxima's on slice.
        if (this.previewing) {
            this.runPreview(rawCoordinates);
        } else if (this.method.equals("Fast")) {
            this.processPhotonsFast(rawCoordinates);
        } else {
            // Calculating the auto threshold takes relatively long so this function is only called once per image.
            //float autoThreshold = ip.getAutoThreshold();

            if (this.method.equals("Accurate")) {
                processPhotonsAccurate(ip, rawCoordinates);
            } else { // this.method equals "Subpixel resolution"
                processPhotonsSubPixel(ip, rawCoordinates);
            }
        }

        // Update the progressbar.
        this.pb.show(this.cPass, this.nPasses);

    }

    /**
     * This method is called while previewing, it shows the found coordinates with the current settings.
     *
     * @param rawCoordinates a polygon containing the coordinates as found by MaximumFinder
     */
    private void runPreview(final Polygon rawCoordinates) {
        // Save the coordinates in a ROI, set the ROI and change the messagearea.
        PointRoi p = new PointRoi(rawCoordinates.xpoints, rawCoordinates.ypoints, rawCoordinates.npoints);
        this.image.setRoi(p);
        this.messageArea.setText((rawCoordinates.xpoints == null ? 0 : rawCoordinates.npoints) + " photons found");
    }

    /**
     * This method is called when processing photons using the 'fast' method.
     * All photons are added to the photon count matrix, without altering.
     *
     * @param rawCoordinates a polygon containing the coordinates as found by MaximumFinder
     */
    private void processPhotonsFast(final Polygon rawCoordinates) {
        // Loop through all raw coordinates and add them to the count matrix.
        for (int i = 0; i < rawCoordinates.npoints; i++) {
            this.photonCountMatrix[rawCoordinates.xpoints[i]]
                                  [rawCoordinates.ypoints[i]]++;
        }
    }

    /**
     * This method is called when processing photons using the 'accurate' method.
     * The exact coordinates are calculated, and then floored and added to the count matrix.
     *
     * @param ip the ImageProcessor of the current image slice
     * @param rawCoordinates a polygon containing the coordinates as found by MaximumFinder
     */
    private void processPhotonsAccurate(final ImageProcessor ip, final Polygon rawCoordinates) {
        for (int i = 0; i < rawCoordinates.npoints; i++) {
            // Loop through all raw coordinates, calculate the exact coordinates,
            // floor the coordinates, and add them to the count matrix.
            double[] exactCoordinates = this.calculateExactCoordinates(rawCoordinates.xpoints[i],
                                                                       rawCoordinates.ypoints[i], ip);
            this.photonCountMatrix[(int) exactCoordinates[0]]
                                  [(int) exactCoordinates[1]]++;
        }
    }

    /**
     * This method is called when processing photons using the 'subpixel resolution' method.
     * The exact coordinates are calculated, and then multiplied by two and added to the count matrix.
     *
     * @param ip the ImageProcessor of the current image slice
     * @param rawCoordinates a polygon containing the coordinates as found by MaximumFinder
     */
    private void processPhotonsSubPixel(final ImageProcessor ip, final Polygon rawCoordinates) {
        for (int i = 0; i < rawCoordinates.npoints; i++) {
            // Loop through all raw coordinates, calculate the exact coordinates,
            // double the coordinates, and add them to the count matrix.
            double[] exactCoordinates = this.calculateExactCoordinates(rawCoordinates.xpoints[i],
                                                                       rawCoordinates.ypoints[i],
                                                                       ip);
            this.photonCountMatrix[(int) (exactCoordinates[0] * 2)]
                                  [(int) (exactCoordinates[1] * 2)]++;
        }
    }


    /**
     * Preprocess the images. For instance: despeckling the images to prevent false positives.
     *
     * @param ip Image processor.
     */
    private void preprocessImage(final ImageProcessor ip) {
        // Perform 'despeckle' using RankFilters.
        SilentRankFilters r = new SilentRankFilters();
        r.rank(ip, 1, RankFilters.MEDIAN);
    }

    /**
     * Find the photons in the current image using MaximumFinder, and return their approximate coordinates.
     *
     * @param ip Image processor.
     * @return Polygon with all maxima points found.
     */
    private Polygon findPhotons(final ImageProcessor ip) {
        int[][] coordinates;

        // Find the maxima using MaximumFinder
        Polygon maxima = this.maxFind.getMaxima(ip, this.tolerance, true);

        coordinates = new int[2][maxima.npoints];
        coordinates[0] = maxima.xpoints; // X coordinates
        coordinates[1] = maxima.ypoints; // y coordinates

        return maxima;
    }

    /**
     * Calculate the exact sub-pixel positions of the photon events at the given coordinates.
     *
     * @param xCor Original x coordinate as found by MaximumFinder.
     * @param yCor Original y coordinate as found by MaximumFinder.
     * @param ip Image processor.
     * @return The new calculated coordinates.
     */
    private double[] calculateExactCoordinates(final int xCor, final int yCor, final ImageProcessor ip) {
        // Wand MUST BE created here, otherwise wand object might be used for multiple photons at the same time.
        Wand wd = new Wand(ip);
        double[] subPixelCoordinates = new double[2];

        // Outline the center of the photon using the wand tool.
        //wd.autoOutline(xCor, yCor, autoThreshold, Wand.FOUR_CONNECTED);
        wd.autoOutline(xCor, yCor, this.tolerance, Wand.FOUR_CONNECTED);

        // Draw a rectangle around the outline.
        Rectangle rect = new PolygonRoi(wd.xpoints, wd.ypoints, wd.npoints, Roi.FREEROI).getBounds();

        // Check if the newly found coordinates are reasonable.
        // (If the original midpoint is too dark compared to the background,
        // the whole image might be selected by the wand tool, if the tolerance is too high.)
        if (rect.height == ip.getHeight() || rect.width > ip.getWidth()) {
            // If the width and heighth of the rectangle are too big, use the original coordinates.
            subPixelCoordinates[0] = xCor;
            subPixelCoordinates[1] = yCor;
        } else {
            // Otherwise, return the centers of the found rectangles as new coordinates.
            subPixelCoordinates[0] = rect.getCenterX();
            subPixelCoordinates[1] = rect.getCenterY();
        }

        return subPixelCoordinates;
    }

    /**
     * This method generates and displays the final image from the photonCountMatrix.
     */
    private void createOutputImage() {

        // Create new ShortProcessor for output image with matrix data and it's width and height.
        ShortProcessor sp = new ShortProcessor(this.photonCountMatrix.length, this.photonCountMatrix[0].length);
        sp.setIntArray(this.photonCountMatrix);

        // Add the amount of different values in array.
        List<Integer> diffMatrixCount = new ArrayList<>();
        for (int[] photonCountMatrix1 : this.photonCountMatrix) {
            for (int photonCountMatrix2 : photonCountMatrix1) {
                if (!diffMatrixCount.contains(photonCountMatrix2)) {
                    diffMatrixCount.add(photonCountMatrix2);
                }
            }
        }

        // Use 0 as min and largest value in the matrix as max for grayscale mapping.
        sp.setMinAndMax(0, (diffMatrixCount.size() - 2)); // Pixel mapping uses blocks.

        // Create new output image with title.
        ImagePlus outputImage = new ImagePlus("Photon Count Image", sp);

        // Make new image window in ImageJ and set the window visible.
        ImageWindow outputWindow = new ImageWindow(outputImage);
        outputWindow.setVisible(true);
    }

    /**
     * This method displays the about information of the plug-in.
     */
    public void showAbout() {
        IJ.showMessage("About Process Photon Images", "<html>"
            + "<b>This option is able to process a stack containing single photon events data and create a combined "
            + "high resolution image.</b><br>"
            + "Each light point within the image (based on user given tolerance value) is being processed as photon. "
            + "Each photon has a center that can be calculated in a fast or a more accurate way.<br><br>"
            + "The available calculations modes are:<br>"
            + "<ul>"
            + "<li><b>Fast</b> uses the lightest points found as coordinates for the output image."
            + "<li><b>Accurate</b> improves on fast by calculating the exact center from the light points before "
            + "creating an output image."
            + "<li><b>Sub-pixel resolution</b> uses the accurate method but outputs a higher resolution image with "
            + "four times the amount of pixels. This requires a larger amount of images than the other methods."
            + "</ul>"
            + "Photons are being counted and mapped to the correct pixel values to create a 16-bit output image. The "
            + "output image may be used for the option 'Threshold Photon Count' to remove noise.<br><br>"
            + "<font size=-2>Created by Lonneke Scheffer and Wout van Helvoirt."
        );
    }

    /**
     * Main method for debugging.
     *
     * For debugging, it is convenient to have a method that starts ImageJ, loads an image and calls the plug-in, e.g.
     * after setting breakpoints. Main method will get executed when running this file from IDE.
     *
     * @param args unused.
     */
    public static void main(final String[] args) {
        // set the plugins.dir property to make the plug-in appear in the Plugins menu
        Class<?> clazz = Photon_Image_Processor.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ImageJ();

        // Open the image sequence
        // IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/900Vdark");
        // IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata");
        // IJ.run("Image Sequence...", "open=/Volumes/Bioinf/SinglePhotonData");
        // IJ.run("Image Sequence...", "open=/Users/Wout/Desktop/100100");
        ImagePlus image = IJ.getImage();

        // run the plug-in
        IJ.runPlugIn(clazz.getName(), "");
    }
}
