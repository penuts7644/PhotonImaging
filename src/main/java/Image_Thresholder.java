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
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.util.ArrayList;
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

    private int nPasses = 0;

    private int flags = PlugInFilter.DOES_8G
            | PlugInFilter.DOES_16;

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

        // Check if image open, else quit.
        if (imp != null && imp.getSlice() == 1 && imp.getStackSize() == 1) {
            this.image = imp;
            this.nPasses = this.image.getWidth() * this.image.getHeight();
            System.out.println(this.nPasses);
            this.setMatrixCounts(this.image.getProcessor());
            this.maxMatrixCount = this.photonCountMatrixSet.size();
        }

//        this.photonCountMatrix = new int[imp.getWidth() * 2][imp.getHeight() * 2];
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
     * @return int for cancel or ok.
     */
    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("Photon Image Processor");

        // Add fields to dialog.
        gd.addSlider("Threshold", 0, this.maxMatrixCount, 0);
        gd.addPreviewCheckbox(pfr, "Enable preview...");
        gd.addDialogListener(this);
        this.previewing = true;
        gd.showDialog();
        if (gd.wasCanceled()) {
            return PlugInFilter.DONE;
        }

        this.previewing = false;
        if (!this.dialogItemChanged(gd, null)) { // HOE KOM JE IN DEZE IF?
            return PlugInFilter.DONE;
        }

        return this.flags;
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
        this.threshold = (int) gd.getNextNumber();

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
        // Show status
        IJ.showStatus("Processing...");

        for (int w = 0; w < ip.getWidth(); w++) {
            for (int h = 0; h < ip.getHeight(); h++) {
                this.setPixelValue(w, h, ip);
            }
        }
    }

    /**
     * This method generates an 2D array of the image as well as a set of values.
     */
    private void setMatrixCounts(ImageProcessor ip) {

        this.photonCountMatrix = ip.getIntArray();

        // Add the amount of different values in matrix.
        List<Integer> diffMatrixCount = new ArrayList<>();
        for (int[] photonCountMatrix1 : this.photonCountMatrix) {
            for (int photonCountMatrix2 : photonCountMatrix1) {
                if (!diffMatrixCount.contains(photonCountMatrix2)) {
                    diffMatrixCount.add(photonCountMatrix2);
                }
            }
        }

        this.photonCountMatrixSet = diffMatrixCount;
    }

    /**
     * This method generates and displays the final image from the photonCountMatrix.
     */
    private void setPixelValue(int xCor, int yCor, ImageProcessor ip) {

        if (ip.getPixelValue(xCor, yCor) <= this.photonCountMatrixSet.get(this.threshold)) {
            ip.putPixelValue(xCor, yCor, 0);
        }
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