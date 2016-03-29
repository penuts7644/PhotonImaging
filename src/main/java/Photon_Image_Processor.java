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
 * @author Lonneke Scheffer & Wout van Helvoirt
 */
public class Photon_Image_Processor implements ExtendedPlugInFilter, DialogListener {

    /** Set a ImagePlus. */
    protected ImagePlus image;
    /** Create a matrix for counting photons. */
    private int[][] photonCountMatrix;
    /** Set a SilentMaximumFinder. */
    private SilentMaximumFinder maxFind;
    /** Set a ProgressBar. */
    private ProgressBar pb;
    /** Set default previewing enabled value to false. */
    private boolean previewing = false;
    /** Set default tolerance to 100. */
    private double tolerance = 100;
    /** Set default preprocessing enabled value to true. */
    private boolean preprocessing = true;
    /** Set default accuracy method to fast. */
    private String method = "Fast";
    /** Set a Label used for found maxima in dialog. */
    private Label messageArea;
    /** Set default total amount of passes to 0. */
    private int nPasses = 0;
    /** Set default current pass to 0. */
    private int cPasses = 0;
    /** Set all requirements for plugin to run. */
    private final int flags = PlugInFilter.DOES_STACKS
            | PlugInFilter.DOES_8G
            | PlugInFilter.DOES_16
            | PlugInFilter.PARALLELIZE_STACKS
            | PlugInFilter.STACK_REQUIRED
            | PlugInFilter.FINAL_PROCESSING;

    /**
     * Setup method as initializer.
     *
     * Setup method is the initializer for this class and will always be ran first. Arguments necessary can be given
     * here. Setup method needs to be overridden.
     *
     * @param arg String telling setup what to do.
     * @param imp ImagePlus containing the displayed stack/image.
     * @return None if help shown, else plugin does gray 16-bit stacks and parallel.
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
     * The showDialog method will be ran after the setup and creates the dialog window and shows it.
     *
     * Dialog window has support for noise tolerance value, preprocessing step and live preview (run executed one time).
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

        // Check if given arguments are correct.
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
     * Executed method when selected.
     *
     * Run method gets executed when setup is finished and when the user selects this class via plugins in Fiji. Run
     * method needs to be overridden.
     *
     * @param ip Image processor.
     */
    @Override
    public void run(final ImageProcessor ip) {
        // Show status
        this.cPasses++;
        IJ.showStatus("Processing " + this.cPasses + "/" + this.nPasses);

        Polygon rawCoordinates;

        // Preprocess the current slice.
        if (this.preprocessing) {
            this.preprocessImage(ip);
        }

        // Find the photon coordinates.
        rawCoordinates = this.findPhotons(ip);

        // If previewing enabled, show found maxima's on slice.
        if (this.previewing) {
            PointRoi p = new PointRoi(rawCoordinates.xpoints, rawCoordinates.ypoints, rawCoordinates.npoints);
            image.setRoi(p);
            this.messageArea.setText((rawCoordinates.xpoints == null ? 0 : rawCoordinates.npoints) + " photons found");
        } else if (this.method.equals("Fast")) {
            for (int i = 0; i < rawCoordinates.npoints; i++) {

                // Loop through all raw coordinates and add them to the count matrix.
                this.photonCountMatrix[rawCoordinates.xpoints[i]][rawCoordinates.ypoints[i]]++;
            }
        } else {

            // Calculating the auto threshold takes relatively long so this function is only called once per image.
            float autoThreshold = ip.getAutoThreshold();
            double[] exactCoordinates;
            int x;
            int y;

            if (this.method.equals("Accurate")) {
                for (int i = 0; i < rawCoordinates.npoints; i++) {

                    // Loop through all raw coordinates, calculate the exact coordinates,
                    // floor the coordinates, and add them to the count matrix.
                    exactCoordinates = this.calculateExactCoordinates(rawCoordinates.xpoints[i], rawCoordinates.ypoints[i], autoThreshold, ip);
                    x = (int) exactCoordinates[0];
                    y = (int) exactCoordinates[1];
                    this.photonCountMatrix[x][y]++;
                }
            } else {

                // this.method equals "Subpixel resolution"
                for (int i = 0; i < rawCoordinates.npoints; i++) {

                    // Loop through all raw coordinates, calculate the exact coordinates,
                    // double the coordinates, and add them to the count matrix.
                    exactCoordinates = this.calculateExactCoordinates(rawCoordinates.xpoints[i], rawCoordinates.ypoints[i], autoThreshold, ip);
                    x = (int) (exactCoordinates[0] * 2);
                    y = (int) (exactCoordinates[1] * 2);
                    this.photonCountMatrix[x][y]++;
                }
            }
        }

        // Update the progressbar.
        this.pb.show(this.cPasses, this.nPasses);
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
     * @param autoThreshold Value for separating objects from the background.
     * @param ip Image processor.
     * @return The new calculated coordinates.
     */
    private double[] calculateExactCoordinates(final int xCor, final int yCor, final float autoThreshold, final ImageProcessor ip) {
        // Wand MUST BE created here, otherwise wand object might be used for multiple photons at the same time.
        Wand wd = new Wand(ip);
        double[] subPixelCoordinates = new double[2];

        // Outline the center of the photon using the wand tool.
        wd.autoOutline(xCor, yCor, autoThreshold, Wand.FOUR_CONNECTED);

        // Draw a rectangle around the outline.
        Rectangle rect = new PolygonRoi(wd.xpoints, wd.ypoints, wd.npoints, Roi.FREEROI).getBounds();

        // Get the x and y center of the rectangle.
        subPixelCoordinates[0] = rect.getCenterX();
        subPixelCoordinates[1] = rect.getCenterY();

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
     * This method displays the about information of the plugin.
     */
    public void showAbout() {
        IJ.showMessage("About Process Photon Images",
            "This class is able to process a stack containing single photon events data and create a combined hi-res image. "
            + "Each light point within the image (based on user given tolerance value) is being processed as photon. Each photon "
            + "has a center that can be calculated in a fast or a more accurate way. There are two accurate calculations available. "
            + "One to create a higher resolution image with four times the amount of pixels (sub-pixel resolution) or one with "
            + "normal resolution. Photons are being counted and mapped to the correct pixel values to create a 16-bit image."
        );
    }

    /**
     * Main method for debugging.
     *
     * For debugging, it is convenient to have a method that starts ImageJ, loads an image and calls the plugin, e.g.
     * after setting breakpoints. Main method will get executed when running this file from IDE.
     *
     * @param args unused.
     */
    public static void main(final String[] args) {
        // set the plugins.dir property to make the plugin appear in the Plugins menu
        Class<?> clazz = Photon_Image_Processor.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ImageJ();

        // Open the image sequence
        // IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/kleinbeetjedata");
        // IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata");
        // IJ.run("Image Sequence...", "open=/Volumes/Bioinf/SinglePhotonData");
        // IJ.run("Image Sequence...", "open=/Users/Wout/Desktop/100100");
        ImagePlus image = IJ.getImage();

        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }
}
