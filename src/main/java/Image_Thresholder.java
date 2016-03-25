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
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Image_Thresholder
 *
 * Description comes here.
 *
 * @author Lonneke Scheffer & Wout van Helvoirt
 */
public class Image_Thresholder implements ExtendedPlugInFilter, DialogListener {

    protected ImagePlus image;
    private int[][] photonCountMatrix;
    private List<Integer> photonCountMatrixSet;
    private int maxMatrixCount = 0;

    private boolean previewing = false;
    private int threshold = 0;
    private boolean inverted = false;

    private int nPasses = 0;

    private int flags = PlugInFilter.DOES_8G
            | PlugInFilter.DOES_16;

    /**
     * Setup method as initializer.
     *
     * Setup method is the initializer for this class and will always be ran first. Arguments necessary can be given
     * here. Setup method needs to be overridden.
     *
     * @param arg String telling setup what to do.
     * @param imp ImagePlus containing the displayed stack/image.
     * @return None if help shown, else plugin does gray 8-bit and 16-bit image.
     */
    @Override
    public int setup(String arg, ImagePlus imp) {

        // If arg is about, display help message.
        if (arg.equals("about")) {
            this.showAbout();
            return PlugInFilter.DONE;
        }

        // Check if image open, else quit.
        if (imp != null && imp.getNSlices() == 1) {
            this.image = imp;
            this.nPasses = this.image.getWidth() * this.image.getHeight();
            this.setMatrixCounts(this.image.getProcessor());
            this.maxMatrixCount = this.photonCountMatrixSet.size() - 1;
            //System.out.println(this.photonCountMatrixSet.toString());
        } else {
            return PlugInFilter.DONE;
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
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("Threshold Photon Count");

        // Add fields to dialog.
        gd.addSlider("Threshold", 1, this.maxMatrixCount, 1);
        gd.addCheckbox("Inverted", this.inverted);
        gd.addPreviewCheckbox(pfr, "Enable preview...");
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
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        this.threshold = (int) gd.getNextNumber();
        this.inverted = gd.getNextBoolean();

        // Check if given arguments are correct.
        if (this.threshold > this.maxMatrixCount) {
            this.threshold = this.maxMatrixCount;
        } else if (this.threshold < 0) {
            this.threshold = 0;
        }

        return (!gd.invalidNumber());
    }

    /**
     * This method tells the the runner the amount of runs get executed.
     *
     * @param nPasses integer with the amount of runs to be called.
     */
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
        // Show status
        IJ.showStatus("Processing...");

        // For each pixel, set the new pixel value.
        for (int w = 0; w < ip.getWidth(); w++) {
            for (int h = 0; h < ip.getHeight(); h++) {
                this.setPixelValue(w, h, ip);
            }
        }
    }

    /**
     * This method generates an 2D array of the image as well as a set of values.
     *
     * @param ip image processor
     */
    private void setMatrixCounts(ImageProcessor ip) {

        this.photonCountMatrix = ip.getIntArray();

        // Add the amount of different values in matrix.
        List<Integer> diffMatrixCount = new ArrayList();
        for (int[] photonCountMatrix1 : this.photonCountMatrix) {
            for (int photonCountMatrix2 : photonCountMatrix1) {
                if (!diffMatrixCount.contains(photonCountMatrix2) && photonCountMatrix2 != 0) {
                    diffMatrixCount.add(photonCountMatrix2);
                }
            }
        }

        Collections.sort(diffMatrixCount);
        this.photonCountMatrixSet = diffMatrixCount;
    }

    /**
     * This method sets the given pixel value to zero.
     *
     * @param xCor x position of the pixel.
     * @param yCor y position of the pixel.
     * @param ip image processor
     */
    private void setPixelValue(int xCor, int yCor, ImageProcessor ip) {

        // Check if pixel value higher or lower than the corresponding value in the list.
        boolean isPixelLower = ip.getPixelValue(xCor, yCor) <= this.photonCountMatrixSet.get(this.threshold);
        boolean isPixelUpper = ip.getPixelValue(xCor, yCor) >= this.photonCountMatrixSet.get(this.threshold);

        // If inverted is true, remove light value. If false, remove dark value.
        if (this.inverted && isPixelUpper) {
            ip.putPixelValue(xCor, yCor, 0);
        } else if (!this.inverted && isPixelLower) {
            ip.putPixelValue(xCor, yCor, 0);
        }
    }

    /**
     * This method displays the about information of the plugin.
     */
    public void showAbout() {
        IJ.showMessage("About Threshold Photon Count",
                "Test help message for Threshold Photon Count class."
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
        Class<?> clazz = Image_Thresholder.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ImageJ();

        // Open the image sequence
//        IJ.run("Image Sequence...", "open=/commons/student/2015-2016/Thema11/Thema11_LScheffer_WvanHelvoirt/kleinbeetjedata");
//        IJ.run("Image Sequence...", "open=/home/lonneke/imagephotondata");
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