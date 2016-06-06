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
import ij.gui.ProgressBar;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.AWTEvent;
import java.util.Random;
import org.apache.commons.math3.util.CombinatoricsUtils;


/**
 * Image_Reconstructor
 *
 * This class can be used to reconstruct the output images of Photon_Image_Processor, if there was not enough input
 * data available. Parts of the algorithm were derived from the article 'Imaging with a small number of photons'[1].
 * Random changes are made to the original file, and various formulas from the article are used to tell whether the
 * changes improved the image.
 *
 * 1. Morris, P. A. et al. Imaging with a small number of photons. Nat. Commun. 6:5913 doi: 10.1038/ncomms6913 (2015).
 *
 * @author Lonneke Scheffer and Wout van Helvoirt
 */

public final class Image_Reconstructor implements ExtendedPlugInFilter, DialogListener {
    /** The ImagePlus given by the user. */
    protected ImagePlus imp;
    /** The size for the DCT matrix to use. */
    private final int dctBlockSize = 8;
    /** The estimated dark count rate of the camera. */
    private double darkCountRate = 0.00017;
    /** The regularization factor (lambda). Used to determine the importance of log likelihood versus image sparsity. */
    private double regularizationFactor = 0.5;
    /** The blur radius used by the Gaussian Blurrer. */
    private double blurRadius = 1.5;
    /** The scaling value used to adjust random values to create new pixel colors. */
    private double scalingValue;
    /** This boolean tells whether the 'previewing' window is open. */
    private boolean previewing = false;
    /** The Random used to choose a (pseudo)random pixel and modify it (pseudo)randomly. */
    private Random randomGenerator;
    /** The Gaussian Blurrer used for preprocessing the image. */
    private GaussianBlur blurrer;
    
    private DctCalculator dctCalc;
    private LogLikelihoodCalculator logLikeCalc;
    private int randomX;
    private int randomY;
    private int randomColorValue;
    private ImageProcessor outIp;
    private ImagePlus outImp;
    private int[][] outMatrix;
    private double multiplyColorValue = 1.0;
    

    /** Set all requirements for plug-in to run. */
    private final int flags = PlugInFilter.DOES_8G
            | PlugInFilter.DOES_16;

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
    public int setup(String arg, ImagePlus imp) {
        // If arg is about, display help message and quit.
        if (arg.equals("about")) {
            this.showAbout();
            return PlugInFilter.DONE;
        }

        if (imp != null) {
            this.imp = imp;
            this.randomGenerator = new Random();
            this.blurrer = new GaussianBlur();
        }

        return this.flags;
    }

    /**
     * The showDialog method will be run after the setup and creates the dialog window and shows it.
     *
     * Dialog window has support for dark count rate and regularzation factor. There is no preview possible.
     *
     * @param imp The ImagePlus.
     * @param command String containing the command.
     * @param pfr The PlugInFilterRunner necessary for live preview.
     * @return integer for cancel or oke.
     */
    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("Reconstruct Image");

        // Add fields to dialog.
        gd.addNumericField("Dark count rate", this.darkCountRate, 5);
        gd.addNumericField("Regularization factor", this.regularizationFactor, 5);
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
     * This method checks whether the user has changed the input fields, and saves the new values.
     *
     * @param gd The dialog window.
     * @param e An AWTEvent.
     * @return boolean false if one or more field are not correct.
     */
    @Override
    public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
        this.darkCountRate = gd.getNextNumber();
        this.regularizationFactor = gd.getNextNumber();
        this.multiplyColorValue = gd.getNextNumber();
        this.blurRadius = gd.getNextNumber();

        // Check if given arguments are correct.
        if (this.darkCountRate < 0) {
            this.darkCountRate = 0;
        }
        if (this.regularizationFactor < 0) {
            this.regularizationFactor = 0;
        } else if (this.regularizationFactor > 1) {
            this.regularizationFactor = 1;
        }
        if (this.multiplyColorValue < 0.01){
            this.multiplyColorValue = 0.01;
        }
        
        if (this.blurRadius < 0.1){
            this.blurRadius = 0.1;
        }

        

        return (!gd.invalidNumber());
    }

    
    /**
     * Run method gets executed when setup is finished and when the user selects this class via plug-ins in Fiji.
     * This method does most of the work, calls all other methods in the right order.
     *
     * @param originalIp image processor
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(final ImageProcessor originalIp) {
        ImageProcessor multipliedIp;
        boolean continueLoop = true;
        double newMeritValue;
        double bestMeritValue;
        int acceptedModifications = 0;
        int totalModifications = 0;
        int iterations = 0;
        
        
        // Set N passes to the total number of necessary scaling adjustments until the scaling value cutoff is reached
        //this.setNPasses((int)(Math.log(this.scalingValueCutoff/this.scalingValue)/Math.log(0.9)));

        // If previewing is enabled, just perform preprocessing on the opened window.
        if (this.previewing) {
            originalIp.multiply(this.multiplyColorValue);
            this.blurrer.blurGaussian(originalIp, this.blurRadius);
            return;
        }
        
        multipliedIp = originalIp.duplicate();
        multipliedIp.multiply(this.multiplyColorValue);
        
        this.createOutputImage(multipliedIp);
        
        this.scalingValue = this.getMaximumValue(this.outIp.getIntArray()) / 2;
        System.out.println(this.scalingValue + "");
        
        
        // With the output matrix, set up the DctCalculator and LogLikelihoodCalculator
        this.outMatrix = this.outIp.getIntArray();
        this.dctCalc = new DctCalculator(this.dctBlockSize, this.outMatrix);
        this.logLikeCalc = new LogLikelihoodCalculator(multipliedIp.getIntArray(), this.outMatrix, this.darkCountRate);
        
        bestMeritValue = this.calculateMerit();
        
        while (continueLoop) {
            iterations++;
            totalModifications++;
            
            this.selectNewModification();
            newMeritValue = calculateMeritWithModification();
            
            if (newMeritValue > bestMeritValue){
                acceptedModifications++;
                bestMeritValue = newMeritValue;
                this.acceptModification();
            } 

            continueLoop = this.testContinueLoop(iterations, acceptedModifications, totalModifications);
        }

    }
    
    private int getMaximumValue(int[][] pixelMatrix) {
        int maxValue = (int) Double.NEGATIVE_INFINITY;
        for (int[] row : pixelMatrix){
            for (int pixel : row){
                if (pixel > maxValue) {
                    maxValue = pixel;
                }
            }
        }
        return maxValue;
    }

    /**
     * Creates an output image (opened in a new window) of an image processer.
     *
     * @param ip the image processor
     * @param name the name of the new window
     */
    private void createOutputImage(final ImageProcessor ip) {
        this.outIp = ip.duplicate();
        this.blurrer.blurGaussian(outIp, this.blurRadius);
        this.outImp = new ImagePlus("Reconstructed Image", this.outIp);
        ImageWindow outputWindow = new ImageWindow(this.outImp);
        outputWindow.setVisible(true);
    }
    
    private boolean testContinueLoop(int iterations, int acceptedModifications, int totalModifications){
        if (iterations % 10000 == 0){
            if (iterations % 30000 == 0 && acceptedModifications/totalModifications < 0.05){
                this.scalingValue *= 0.9;
                System.out.println("scaling down " + this.scalingValue);
                if (this.scalingValue < this.scalingValue/100){
                    System.out.println("done");    
                    return false;
                    }
            }
            acceptedModifications = 0;
            totalModifications = 0;
        }
        return true;
    }

    private void acceptModification(){
        this.outIp.set(this.randomX, this.randomY, this.randomColorValue);
        this.outMatrix[this.randomX][this.randomY] = this.randomColorValue;
        this.dctCalc.performModification();
        this.logLikeCalc.performModification();
        this.outImp.updateAndRepaintWindow();
    }
    
    private double calculateMerit(){
        return this.logLikeCalc.getTotalLogLikelihood() 
                - this.regularizationFactor 
                * this.dctCalc.getTotalSparsity();
    }
    
    private double calculateMeritWithModification(){
        return this.logLikeCalc.tryModification(this.randomX, this.randomY, this.randomColorValue) 
                - this.regularizationFactor 
                * this.dctCalc.tryModification(this.randomX, this.randomY, this.randomColorValue);
    }
    
    
    private void selectNewModification(){
        // Pick a random pixel and a random new color for that pixel
        this.randomX = this.randomGenerator.nextInt(this.outIp.getWidth());
        this.randomY = this.randomGenerator.nextInt(this.outIp.getHeight());
        // The original algorithm used -0.5 here, but it has been replaced by 0.4 to allow more increases in pixel color instead of decreases.
        this.randomColorValue = (int) (Math.abs((this.randomGenerator.nextDouble() - 0.4) * this.scalingValue + this.outIp.get(randomX, randomY)));
    }


    /**
     * This method displays the about information of the plug-in.
     */
    public void showAbout() {
        IJ.showMessage("About Image Reconstructor", "<html>"
            + "<b>This option can be used to reconstruct the output image created by the 'Process Photon Images' "
            + "option.</b><br>"
            + "The original image is changed randomly, and the modifications are evaluated. "
            + "Parts of the algorithm were derived from the article 'Imaging with a small number of photons', by P. A. Morris et al. <br><br>"
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
        Class<?> clazz = Image_Thresholder.class;
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

        // Only if you use new ImagePlus(path) to open the file
        // image.show();
        // run the plug-in
        IJ.runPlugIn(clazz.getName(), "");
    }

    @Override
    public void setNPasses(int nPasses) {}

}


//    /**
//     * Copy a part of the source matrix, given a midpoint and new matrix size.
//     * All coordinates outside the source matrix are filled in with zero.
//     *
//     * @param sourceMatrix the original matrix to copy from
//     * @param matrixPart the partial matrix to be filled
//     * @param midX the midpoint on the x axis of the new matrix in the original matrix
//     * @param midY the midpoint on the y axis of the new matrix in the original matrix
//     */
//    private void getMatrixPartValues(final int[][] sourceMatrix, final int[][] matrixPart,
//                                    final int midX, final int midY) {
//        int startX = midX - (matrixPart.length / 2) + 1;
//        int startY = midY - (matrixPart[0].length / 2) + 1;
//
//        int i = 0;
//        int j = 0;
//
//        // Loop through the part of the original matrix that must be copied
//        for (int x = startX; x < (startX + matrixPart.length); x++, i++) {
//            for (int y = startY; y < (startY + matrixPart[0].length); y++, j++) {
//                // try to copy the value, if the index is out of bounds, set the value to zero
//                try {
//                    matrixPart[i][j] = sourceMatrix[x][y];
//                } catch (ArrayIndexOutOfBoundsException aiex) {
//                    matrixPart[i][j] = 0;
//                }
//            }
//            j = 0;
//        }
//    }
//    
//    /**
//     * if the source matrix is bigger than the new matrix, only the part that fits inside the new matrix is copied
//     * if the source matrix is smaller, the unknown values are filled in with zero.
//     *
//     * @param sourceMatrix the matrix that the data is copied from
//     * @param newMatrix    the matrix that the data is copied to
//     */
//    private void copyMatrixValues(final int[][] sourceMatrix, int[][] newMatrix) { // WORDT DEZE NOG GEBRUIKT? KIJKEN VOORDAT JE HEM INLEVERT
//        for (int i = 0; i < newMatrix.length; i++) {
//            for (int j = 0; j < newMatrix[0].length; j++) {
//                try {
//                    newMatrix[i][j] = sourceMatrix[i][j];
//                } catch (ArrayIndexOutOfBoundsException aiex) {
//                    newMatrix[i][j] = 0;
//                }
//
//            }
//        }
//
//    }



//    /**
//     * This merit function calculates the log likelihood and sparsity for a modified matrix.
//     * The goal is to maximize the outcome of this merit function. The regularization factor
//     * determines the importance of the log likelihood and sparsity compared to one another.
//     * As stated in 'Imaging with a small number of photons', by P. A. Morris et al.
//     *
//     * @param originalMatrix part of the original image
//     * @param modifiedMatrix part of the modified image
//     * @return a merit value for this modified image compared to the original
//     */
//    private double calculateMerit(final int[][] originalMatrix, final int[][] modifiedMatrix, final double totalMatrixSparsity) {
//        return this.calculateLogLikelihood(originalMatrix, modifiedMatrix) - this.regularizationFactor
//                * totalMatrixSparsity;
//    }

//    /**
//     * Calculates the log likelihood for the modified matrix given the original matrix.
//     * As stated in 'Imaging with a small number of photons', by P. A. Morris et al.
//     *
//     * @param originalMatrix part of the original image
//     * @param modifiedMatrix part of the modified image
//     * @return the log likelihood
//     */
//    private double calculateLogLikelihood(final int[][] originalMatrix, final int[][] modifiedMatrix) {
//        double logLikelihood = 0;
//
//        // The two matrices must be the same size
//        if (originalMatrix.length != modifiedMatrix.length
//                || originalMatrix[0].length != modifiedMatrix[0].length) {
//            throw new IndexOutOfBoundsException("Your original matrix and modified matrix do not have the same size");
//        }
//
//        // The calculation for the log likelihood
//        for (int i = 0; i < originalMatrix.length; i++) {
//            for (int j = 0; j < originalMatrix[0].length; j++) {
//                logLikelihood += this.calculateLogLikelihood(originalMatrix[i][j], modifiedMatrix[i][j]);
//            }
//        }
//
//        return logLikelihood;
//    }
//    
//    private double calculateLogLikelihood(final int originalPixelValue, final int modifiedPixelValue){
//        return ((originalPixelValue * Math.log(modifiedPixelValue + this.darkCountRate))
//                        - (modifiedPixelValue + this.darkCountRate)
//                        - CombinatoricsUtils.factorialLog(originalPixelValue));
//    }
