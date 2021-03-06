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
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

import java.awt.AWTEvent;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Image_Thresholder
 *
 * This class can be used to filter noise from the output image of
 * Photon_Image_Processor. All pixels will get a new value based on there
 * current value minus the given threshold value.
 *
 * @author Lonneke Scheffer and Wout van Helvoirt
 */
public final class Image_Thresholder implements ExtendedPlugInFilter, DialogListener {

    /** The matrix containing all pixel values. */
    private int[][] pixelValueMatrix;
    /** A sorted array containing all unique pixel values. */
    private Integer[] uniquePixelValues;
    /** The size of the slider (= amount of unique values). */
    private int sliderSize = 0;
    /** This boolean tells whether the 'previewing' window is open. */
    private boolean previewing = false;
    /** The threshold pixel value under which the pixel color is set to black. */
    private int threshold = 0;
    /** The number of passes for the progress bar, default is 0. */
    private int nPasses = 0;
    /** Set all requirements for plug-in to run. */
    private final int flags = PlugInFilter.DOES_8G
            | PlugInFilter.DOES_16;

    /**
     * Setup method as initializer.
     *
     * Setup method is the initializer for this class and will always be run
     * first. Arguments can be given here. Setup method needs to be overridden.
     *
     * @param arg String telling setup what to do.
     * @param imp ImagePlus containing the displayed stack/image.
     * @return int Flag 'DONE' if help shown, else the general plug in flags
     */
    @Override
    public int setup(final String arg, final ImagePlus imp) {

        // If arg is about, display help message and quit.
        if (arg.equals("about")) {
            this.showAbout();
            return PlugInFilter.DONE;
        }

        if (imp != null && imp.getNSlices() == 1) {
            // If there is one image open, get the pixel value information from the image.
            this.pixelValueMatrix = imp.getProcessor().getIntArray();
            this.getUniquePixelValues();
            this.sliderSize = this.uniquePixelValues.length - 1;
        } else {
            // If there are more or less than one image open, quit.
            IJ.showMessage("No Image", "There is no image open.");
            return PlugInFilter.DONE;
        }

        // Return options.
        return this.flags;
    }

    /**
     * The showDialog method will be run after the setup and creates the dialog
     * window and shows it.
     *
     * Dialog window has support for noise tolerance value, preprocessing step
     * and live preview (run is executed once).
     *
     * @param imp     The ImagePlus.
     * @param command String containing the command.
     * @param pfr     The PlugInFilterRunner necessary for live preview.
     * @return int For cancel or oke.
     */
    @Override
    public int showDialog(final ImagePlus imp, final String command, final PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("Threshold Photon Count");

        // Add fields to dialog.
        gd.addSlider("Threshold", 1, this.sliderSize, 1);
        gd.addPreviewCheckbox(pfr, "Enable preview...");
        gd.addDialogListener(this);

        // previewing is true while showing the dialog
        this.previewing = true;
        gd.showDialog();
        if (gd.wasCanceled()) {
            return PlugInFilter.DONE;
        }
        this.previewing = false;

        // check whether the user has changed the items
        if (!this.dialogItemChanged(gd, null)) {
            return PlugInFilter.DONE;
        }

        return this.flags;
    }

    /**
     * This method checks whether the user has changed the input fields, and
     * saves the new values.
     *
     * @param gd The dialog window.
     * @param e  An AWTEvent.
     * @return boolean False if one or more field are not correct.
     */
    @Override
    public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
        this.threshold = (int) gd.getNextNumber();

        // Check if given arguments are correct.
        if (this.threshold > this.sliderSize) {
            this.threshold = this.sliderSize;
        } else if (this.threshold < 0) {
            this.threshold = 0;
        }

        return (!gd.invalidNumber());
    }

    /**
     * This method tells the the runner the amount of runs get executed.
     *
     * @param nPasses Integer with the amount of runs to be called.
     */
    @Override
    public void setNPasses(final int nPasses) {
        this.nPasses = nPasses;
    }

    /**
     * This method creates a set of pixel values (excluding zero) that are in
     * the pixel value matrix.
     */
    private void getUniquePixelValues() {
        SortedSet pixelValueSet = new TreeSet();

        // Always add zero (otherwise if there is no zero in the image, the lowest value is skipped).
        pixelValueSet.add(0);

        // Add the all values of the matrix to the set.
        for (int[] row : this.pixelValueMatrix) {
            for (int value : row) {
                pixelValueSet.add(value);
            }
        }

        this.uniquePixelValues = (Integer[]) pixelValueSet.toArray(new Integer[0]);
    }

    /**
     * Run method gets executed when setup is finished and when the user selects
     * this class via plug-ins in Fiji. This method does most of the work, calls
     * all other methods in the right order.
     *
     * @param ip Image processor
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(final ImageProcessor ip) {
        // Show status.
        IJ.showStatus("Processing...");

        // Get threshold value.
        int thresholdValue = this.uniquePixelValues[this.threshold];

        // For each pixel, set the new pixel value.
        for (int width = 0; width < ip.getWidth(); width++) {
            for (int height = 0; height < ip.getHeight(); height++) {
                this.setPixelValue(width, height, ip, thresholdValue);
            }
        }
    }

    /**
     * This method gets the current pixel value and sets it to the current pixel
     * value minus the threshold value.
     *
     * @param xCor           X position of the pixel.
     * @param yCor           Y position of the pixel.
     * @param ip             Image processor.
     * @param thresholdValue Threshold value selected by user.
     */
    private void setPixelValue(final int xCor, final int yCor, final ImageProcessor ip, final int thresholdValue) {
        // Set the pixel value to a new value by taking ist's original value minus the the threshold value.
        ip.putPixelValue(xCor, yCor, (ip.getPixelValue(xCor, yCor) - thresholdValue));
    }

    /**
     * This method displays the about information of the plug-in.
     */
    public void showAbout() {
        IJ.showMessage("About Threshold Photon Count", "<html>"
                + "<h1>Threshold Photon Count</h1>"
                + "<b>This option can be used to filter noise from the output image created by 'Process Photon Images',"
                + " and optionally prepare for 'Reconstruct Image'. </b> All pixels below the 'threshold' value are "
                + "set to 0, and the remaining pixels are scaled to new values.<br><br>"
                + "<font size=-2>Created by Lonneke Scheffer and Wout van Helvoirt."
        );
    }

    
    
    /**
     * Main method for debugging.
     *
     * For debugging, it is convenient to have a method that starts ImageJ.
     * Main method will get executed when running this file from IDE.
     *
     * @param args unused
     */
    public static void main(final String[] args) {
        // set the plugins.dir property to make the plug-in appear in the Plugins menu
        Class<?> clazz = Image_Thresholder.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ImageJ();

        // run the plug-in
        IJ.runPlugIn(clazz.getName(), "");
    }
}
