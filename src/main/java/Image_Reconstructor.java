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
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

import java.awt.AWTEvent;
import java.util.Random;

/**
 * Image_Reconstructor
 *
 * This class can be used to reconstruct the output images of
 * Photon_Image_Processor, if there was not enough input data available. Parts
 * of the algorithm were derived from the article 'Imaging with a small number
 * of photons'[1]. Random changes are made to the original file, and various
 * formulas from the article are used to tell whether the changes improved the
 * image.
 *
 * 1. Morris, P. A. et al. Imaging with a small number of photons. Nat. Commun.
 * 6:5913 doi: 10.1038/ncomms6913 (2015).
 *
 * @author Lonneke Scheffer and Wout van Helvoirt
 */
public final class Image_Reconstructor implements ExtendedPlugInFilter, DialogListener {

    // Variables used for the original algoritm of Morris et al.
    /** The estimated dark count rate of the camera. */
    private double darkCountRate = 0.1;
    /** The regularization factor (lambda).
     * Used to determine the importance of log likelihood versus image sparsity (DCT value). */
    private double regularizationFactor = 0.5;
    /** The size for the DCT matrix to use. */
    private final int dctBlockSize = 8;
    /** This class calculates the DCT value per image. */
    private DctCalculator dctCalc;
    /** This class calculates the log likelihood per image. */
    private LogLikelihoodCalculator logLikeCalc;

    // Variables used for preprocessing the input image
    /** This boolean tells whether the 'previewing' window is open. */
    private boolean previewing = false;
    /** The Gaussian Blurrer used for preprocessing the image. */
    private GaussianBlur blurrer;
    /** The blur radius used by the Gaussian Blurrer. */
    private double blurRadius = 2.0;
    /** This value is used to multiply the colors of the input image if it is too dark. */
    private double multiplyColorValue = 1.0;

    // Variables used to change the output image randomly
    /** The Random used to choose a (pseudo)random pixel and modify it (pseudo)randomly. */
    private Random randomGenerator;
    /** The random x coordinate in the output matrix. */
    private int randomX;
    /** The random y coordinate in the output matrix. */
    private int randomY;
    /** The new random color value of the pixel at (x, y) in the output matrix. */
    private int randomColorValue;
    /** The output matrix storing pixel values for the output image. */
    private int[][] outMatrix;
    /** The imageProcessor for the output image. */
    private ImageProcessor outIp;
    /** The imagePlus for the output image. */
    private ImagePlus outImp;

    // Variables used for iterating over the modifications
    /** The iteration counter keeps track of the amount of modifications that have been tried. */
    private int iterations = 0;
    /** After this many iterations, the scaling value is checked. */
    private final double iterationsPerCheck = 1000;
    /** The scaling value used to adjust random values to create new pixel colors. */
    private double scalingValue;
    /** If the scaling value comes below this cutoff, the plugin will end. */
    private double scalingValueCutoff;
    /** The number of accepted modifications in the last 1000 iterations. */
    private double acceptedModifications = 0.0;
    /** The minimal percentage of accepted modifications before the scalingvalue is decreased. */
    private double modificationThreshold = 25.0;

    /** The requirements for the plug-in to run. */
    private final int flags = PlugInFilter.DOES_8G | PlugInFilter.DOES_16;

    /**
     * Setup method as initializer.
     *
     * Setup method is the initializer for this class and will always be run
     * first. Arguments can be given here. Setup method needs to be overridden.
     *
     * @param arg String telling setup what to do.
     * @param imp ImagePlus containing the displayed stack/image.
     * @return int Flag 'DONE' if help shown, else the general plug in flags.
     */
    @Override
    public int setup(final String arg, final ImagePlus imp) {
        // If arg is about, display help message and quit.
        if (arg.equals("about")) {
            this.showAbout();
            return PlugInFilter.DONE;
        }

        if (imp != null) {
            this.randomGenerator = new Random();
            this.blurrer = new GaussianBlur();
        }

        return this.flags;
    }

    /**
     * The showDialog method will be run after the setup and creates the dialog
     * window and shows it.
     *
     * Dialog window has support for dark count rate and regularzation factor.
     * There is no preview possible.
     *
     * @param imp     The ImagePlus.
     * @param command String containing the command.
     * @param pfr     The PlugInFilterRunner necessary for live preview.
     * @return int For cancel or oke.
     */
    @Override
    public int showDialog(final ImagePlus imp, final String command, final PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("Reconstruct Image");

        // Add fields to dialog.
        gd.addNumericField("Dark count rate", this.darkCountRate, 5, 6, "per pixel");
        gd.addNumericField("Regularization factor", this.regularizationFactor, 5);
        gd.addNumericField("Modification threshold", this.modificationThreshold, 2, 6, "%");
        gd.addNumericField("Multiply image colors", this.multiplyColorValue, 2);
        gd.addNumericField("Blur radius", this.blurRadius, 2);
        gd.addPreviewCheckbox(pfr, "Preview blurred image...");
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
     * @return boolean false if one or more field are not correct.
     */
    @Override
    public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
        this.darkCountRate = gd.getNextNumber();
        this.regularizationFactor = gd.getNextNumber();
        double modificationThresholdPercentage = gd.getNextNumber();
        this.multiplyColorValue = gd.getNextNumber();
        this.blurRadius = gd.getNextNumber();

        // Check if given arguments are correct.
        if (this.darkCountRate < 0) {
            this.darkCountRate = 0;
        }
        if (this.regularizationFactor < 0) {
            this.regularizationFactor = 0;
        }
        // The modification threshold percentage should be between 1 and 40%
        // This percentage is used to check if the current scalingvalue needs to change
        if (modificationThresholdPercentage < 1.0) {
            modificationThresholdPercentage = 1.0;
        } else if (modificationThresholdPercentage > 40.0) {
            modificationThresholdPercentage = 40.0;
        }
        this.modificationThreshold = this.iterationsPerCheck / 100 * modificationThresholdPercentage;
        if (this.multiplyColorValue < 0.01) {
            this.multiplyColorValue = 0.01;
        }
        if (this.blurRadius < 0.1) {
            this.blurRadius = 0.1;
        }

        return (!gd.invalidNumber());
    }

    /**
     * Run method gets executed when setup is finished and when the user selects
     * this class via plug-ins in Fiji. This method does most of the work, calls
     * all other methods in the right order.
     *
     * @param originalIp Image processor
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(final ImageProcessor originalIp) {
        ImageProcessor multipliedIp;
        boolean continueLoop = true;
        double newMeritValue;
        double bestMeritValue;

        // If previewing is enabled, just perform preprocessing on the opened window.
        if (this.previewing) {
            originalIp.multiply(this.multiplyColorValue);
            this.blurrer.blurGaussian(originalIp, this.blurRadius);
            return;
        }

        // Prepare output image
        multipliedIp = originalIp.duplicate();
        multipliedIp.multiply(this.multiplyColorValue);
        this.createOutputImage(multipliedIp);

        // Prepare scaling value and cutoff
        this.scalingValue = this.getMaximumValue(this.outIp.getIntArray()) / 2;
        this.scalingValueCutoff = this.scalingValue / 20.0;
        //System.out.println("scalingvalue, elapsed time, total iterations");

        // With the output matrix, set up the DctCalculator and LogLikelihoodCalculator
        this.outMatrix = this.outIp.getIntArray();
        this.dctCalc = new DctCalculator(this.dctBlockSize, this.outMatrix);
        this.logLikeCalc = new LogLikelihoodCalculator(multipliedIp.getIntArray(), this.outMatrix, this.darkCountRate);

        // Try to maximize the bestMeritValue by changing the output randomly
        bestMeritValue = this.calculateMerit();
        while (continueLoop) {
            this.iterations++;

            this.selectNewModification();
            newMeritValue = calculateMeritWithModification();

            if (newMeritValue > bestMeritValue) {
                this.acceptedModifications++;
                bestMeritValue = newMeritValue;
                this.acceptModification();
            }

            continueLoop = this.testContinueLoop();
        }

    }

    /**
     * Creates an output image (opened in a new window) of an image processor.
     *
     * @param ip The image processor
     */
    private void createOutputImage(final ImageProcessor ip) {
        this.outIp = ip.duplicate();
        this.blurrer.blurGaussian(outIp, this.blurRadius);
        this.outImp = new ImagePlus("Reconstructed Image (reconstructing...)", this.outIp);
        ImageWindow outputWindow = new ImageWindow(this.outImp);
        outputWindow.setVisible(true);
    }

    /**
     * This method is used to get the maximum value from an integer matrix.
     *
     * @param pixelMatrix The input matrix containing pixel values
     * @return int The maximum value in the matrix
     */
    private int getMaximumValue(final int[][] pixelMatrix) {
        int maxValue = (int) Double.NEGATIVE_INFINITY;
        for (int[] row : pixelMatrix) {
            for (int pixel : row) {
                if (pixel > maxValue) {
                    maxValue = pixel;
                }
            }
        }
        return maxValue;
    }

    /**
     * Calculates the merit value, as defined by the original algorithm of Morris et al.
     * This calculates the merit value of the total image, without precalculated information.
     *
     * @return double The calculated merit value
     */
    private double calculateMerit() {
        return this.logLikeCalc.getTotalLogLikelihood()
                - this.regularizationFactor
                * this.dctCalc.getTotalSparsity();
    }

    /**
     * Calculates the merit value of a new image, with a randomly changed pixel.
     * This is a lot faster than the original algorithm, since the DCT value and log likelihood
     * have already been calculated for the whole image. Thus these values only have to be calculated
     * for the modified pixel.
     *
     * @return double The merit value of the modified image
     */
    private double calculateMeritWithModification() {
        return this.logLikeCalc.tryModification(this.randomX, this.randomY, this.randomColorValue)
                - this.regularizationFactor
                * this.dctCalc.tryModification(this.randomX, this.randomY, this.randomColorValue);
    }

    /**
     * Selects a new random pixel and a color for this pixel.
     */
    private void selectNewModification() {
        // Pick a random pixel
        this.randomX = this.randomGenerator.nextInt(this.outIp.getWidth());
        this.randomY = this.randomGenerator.nextInt(this.outIp.getHeight());
        // Pick a color (the same way as the original algorithm)
        this.randomColorValue = (int) (Math.abs((this.randomGenerator.nextDouble() - 0.5)
                                        * this.scalingValue + this.outIp.get(randomX, randomY)));
    }

    /**
     * If the merit value of the new image is better than the last image,
     * this method is called to actually save the information of the new image.
     * The output image is changed, and the information about the image
     * (total DCT value, total log likelihood) is overwritten with new information.
     */
    private void acceptModification() {
        this.outIp.set(this.randomX, this.randomY, this.randomColorValue);
        this.outMatrix[this.randomX][this.randomY] = this.randomColorValue;
        this.dctCalc.performModification();
        this.logLikeCalc.performModification();
        this.outImp.updateAndRepaintWindow();
    }

    /**
     * Test whether the while loop (of changing pixels in the image) should continue.
     * If the percentage of the accepted modifications of the last 'iterationsPerCheck' images 
     * comes below 'modificationthreshold', the scaling value is scaled down. If the scaling value
     * is too low, the algorithm should stop.
     * The scaling value is used to change the color of a randomly selected pixel.
     * This way, the image is changed a lot at first, and less later on.
     *
     * @return boolean Telling whether the loop should continue.
     */
    private boolean testContinueLoop() {

        if (this.iterations % this.iterationsPerCheck == 0) {
            // In the original algorithm, every 3000 iterations
            // there was checked whether acceptedModifications < 5% of the last 1000 iterations
            // if (this.iterations % 3000 && this.acceptedModifications < 50){...}
            // This has been simplified to the following because it fits better with our data.
            if (this.acceptedModifications < this.modificationThreshold) {
                this.scalingValue *= 0.9;
                if (this.scalingValue < this.scalingValueCutoff) {
                    this.outImp.getWindow().setTitle("Reconstructed Image (done)");
                    return false;
                }
            }
            this.acceptedModifications = 0.0;
        }
        return true;
    }


    /**
     * This method displays the about information of the plug-in.
     */
    public void showAbout() {
        IJ.showMessage("About Image Reconstructor", "<html>"
                + "<h1>Image Reconstructor</h1>"
                + "<b>This option can be used to reconstruct the output image created by 'Process Photon Images'."
                + "</b> The input image is reconstructed using the algorithm of the article "
                + "'Imaging with a small number of photons', by P. A. Morris et al.<br><br>"
                + "<h2>Algorithm</h2>"
                + "The original image is blurred and random changes are made. A check is performed to test whether "
                + "the random changes have improved the image. This check includes testing for the log likelihood "
                + "of the new image, and the sparsity of the new image. If there are very few possible modifications "
                + "left that could improve the image, the method for changing random pixels is altered, so the "
                + "modifications become less extreme and the image is finetuned.<br><br>"
                + "<h2>Parameter explanation and usage</h2>"
                + "<ul>"
                + "<li><b>Dark count rate</b>: The dark count rate per pixel of the camera used to record the data.<br>"
                + "<li><b>Regularization factor</b>: indicates how important the log likelihood and image sparsity are "
                + "compared to one another. A higher regularization factor results in a greater dependency of"
                + "image sparsity, and a lower regularization factor makes the log likelihood more important.<br>"
                + "<li><b>Modification threshold</b>: The lower boundary for percentage of modifications that improve the "
                + "image. In other words, how far the algorithm is proceeded. A higher percentage makes the algorithm "
                + "quit earlier and makes the output image less defined. The lower the percentage is, the more the "
                + "output image will eventually look like the input image.<br>"
                + "<li><b>Multiply image colors</b>: scaling value used to change the color of the input image, for instance "
                + "when the input image is too dark to be clear.<br>"
                + "<li><b>Blur radius</b>: The blur radius for the gaussian blur filter. A bigger blur radius removes more "
                + "detail from the original image, but also closes more gaps between pixels."
                + "</ul><br><br>"
                + "<font size=-2>Created by Lonneke Scheffer and Wout van Helvoirt."
        );
    }

    /**
     * Main method for debugging.
     *
     * For debugging, it is convenient to have a method that starts ImageJ.
     * Main method will get executed when running this file from IDE.
     *
     * @param args unused.
     */
    public static void main(final String[] args) {
        // set the plugins.dir property to make the plug-in appear in the Plugins menu
        Class<?> clazz = Image_Reconstructor.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ImageJ();

        // run the plug-in
        IJ.runPlugIn(clazz.getName(), "");
    }

    /**
     * This method tells the the runner the amount of runs get executed.
     * Not used.
     *
     * @param nPasses Integer with the amount of runs to be called.
     */
    @Override
    public void setNPasses(final int nPasses) {
    }

}
