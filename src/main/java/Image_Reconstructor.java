/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author lscheffer
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
import java.awt.Label;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.util.CombinatoricsUtils;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author lonneke
 */
public final class Image_Reconstructor implements ExtendedPlugInFilter, DialogListener {
    /** The ImagePlus given by the user. */
    protected ImagePlus imp;
    /** The number of passes for the progress bar, default is 0. */
    private int nPasses = 0;
    /** The size for the DCT matrix to use. */
    private int dctBlockSize = 12;
    /** The estimated dark count rate of the camera. */
    private float darkCountRate = (float)0.1;
    /** The regularization factor (lambda). Used to determine the importance of log likelihood versus image sparsity. */
    private float regularizationFactor = (float)0.5;
    /** The threshold ratio of passed changes : rejected changes in the new image. */
    private float thresholdRatioChanges = (float)0.09;
    /** This boolean tells whether the 'previewing' window is open. */
    private boolean previewing = false;
    /** The Random used to choose a (pseudo)random pixel and modify it (pseudo)randomly. */
    private Random randomGenerator;

    /** Set all requirements for plug-in to run. */
    private final int flags = PlugInFilter.DOES_8G
            | PlugInFilter.DOES_16; // DOES 32??? ?????????????????????????????????????????????????????????????

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

        this.imp = imp;
        this.randomGenerator = new Random();

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
        gd.addNumericField("Dark count rate", this.darkCountRate, 2);
        gd.addNumericField("Regularization factor", this.regularizationFactor, 5);
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
        this.darkCountRate = (float) gd.getNextNumber();
        this.regularizationFactor = (float) gd.getNextNumber();

        // Check if given arguments are correct.
        if (this.darkCountRate < 0) {
            this.darkCountRate = 0;
        }
        if (this.regularizationFactor < 0) {
            this.regularizationFactor = 0;
        } else if (this.regularizationFactor > 1){
            this.regularizationFactor = 1;
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
        boolean wasModified;
        float passedChanges;
        float rejectedChanges;

        // Duplicate the original image to create a new output image.
        ImageProcessor newImage = ip.duplicate();

        // The ratio between passed modifications and rejected modifications determines when to quit.
        // They are set to 1 instead of 0, because ratio 0 / 0 can not be calculated.
        passedChanges = 1;
        rejectedChanges = 1;
        
        // DIT IS ALLEMAAL VOOR DEBUGGEN MOET STRAKS ALLEMAAL WEGGGGGGGGGG
        long startTime = System.nanoTime();
        boolean img90 = true;
        boolean img80 = true;
        boolean img70 = true;
        boolean img60 = true;
        boolean img50 = true;
        boolean img40 = true;
        boolean img30 = true;
        boolean img20 = true;
        boolean img10 = true;
        
        // Keep making changes:
        //   - for at least 1000 iterations (because the ratio is so unstable in the beginning)
        //   - after that, keep making changes while the ratio passed changes : rejected changes is not below the threshold
        // (This way, the while loop is quit if most changes do not have an effect on the quality of the output image anymore)
        while (passedChanges + rejectedChanges < 1000 
                || (passedChanges + rejectedChanges >= 1000 && passedChanges/rejectedChanges > this.thresholdRatioChanges)) { 
            
            wasModified = this.tryToModifyImageGradientVersion(ip, newImage);
            
            if (wasModified) {
                passedChanges ++;
            } else{
                rejectedChanges ++;
            }
            
            
            // MISSCHIEN KAN HET STUK BINNEN DE WHILE IN EEN LOSSE FUNCTIE die dan alleen true/false returnt op basis van changepassed of changerejected
            // ALLEEN VOOR TESTEN, DAARNA VERWIJDEREN
            if (passedChanges/rejectedChanges < 0.9 && img90 && (passedChanges + rejectedChanges > 1000)){
                img90 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.9|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (passedChanges/rejectedChanges < 0.8 && img80 && (passedChanges + rejectedChanges > 1000)){
                img80 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.8|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (passedChanges/rejectedChanges < 0.7 && img70 && (passedChanges + rejectedChanges > 1000)){
                img70 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.7|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (passedChanges/rejectedChanges < 0.6 && img60 && (passedChanges + rejectedChanges > 1000)){
                img60 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.6|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (passedChanges/rejectedChanges < 0.5 && img50 && (passedChanges + rejectedChanges > 1000)){
                img50 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.5|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (passedChanges/rejectedChanges < 0.4 && img40 && (passedChanges + rejectedChanges > 1000)){
                img40 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.4|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (passedChanges/rejectedChanges < 0.3 && img30 && (passedChanges + rejectedChanges > 1000)){
                img30 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.3|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (passedChanges/rejectedChanges < 0.2 && img20 && (passedChanges + rejectedChanges > 1000)){
                img20 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.2|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (passedChanges/rejectedChanges < 0.1 && img10 && (passedChanges + rejectedChanges > 1000)){
                img10 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.1|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } 

        }
        
        // MOET CREATEOUTPUTIMAGE BLIJVEN BESTAAN ALS LOSSE FUNCTIE OF VERWIJDERD WORDEN?
//        // Create new output image with title.
//        ImagePlus outputImage = new ImagePlus("Reconstructed Image:" + this.darkCountRate + "," + this.regularizationFactor, newImage);
//
//        // Make new image window in ImageJ and set the window visible.
//        ImageWindow outputWindow = new ImageWindow(outputImage);
//        outputWindow.setVisible(true);

    }
    
    private boolean tryToModifyImage(ImageProcessor originalImage, ImageProcessor newImage){
        int randomX;
        int randomY;
        int randomColorValue;
        float randomColorMultiplier;
        int[][] originalMatrixPart;
        int[][] modifiedMatrixPart;
        
        // Choose a random pixel and a random modification for that pixel (pixelvalue + 1 * random value between 1 and 2)
        randomX = this.randomGenerator.nextInt(newImage.getWidth());
        randomY = this.randomGenerator.nextInt(newImage.getHeight());
        randomColorMultiplier = this.randomGenerator.nextFloat() + (float)1.0;
        randomColorValue = Math.round((float)(newImage.get(randomX, randomY) + 1) * randomColorMultiplier);
        
        // Create the matrices used to calculate the differences between original and modified image.
        originalMatrixPart = new int[this.dctBlockSize][this.dctBlockSize];
        modifiedMatrixPart = new int[this.dctBlockSize][this.dctBlockSize];
        int midpoint = this.dctBlockSize / 2 - 1;
        
        // Get the part of the original matrix around the randomly selected x and y,
        // from both the original and modified matrix.
        this.getMatrixPartValues(originalImage.getIntArray(), originalMatrixPart, randomX, randomY);
        this.getMatrixPartValues(newImage.getIntArray(), modifiedMatrixPart, randomX, randomY);

        // Modify the modifiedMatrixPart
        modifiedMatrixPart[midpoint][midpoint] = randomColorValue;
        
        // If the random change is better, the outcome of the merit function is higher, and the change should be made to the new image
        if (this.calculateMerit(originalMatrixPart, modifiedMatrixPart) > this.calculateMerit(originalMatrixPart, originalMatrixPart)){
            newImage.set(randomX, randomY, randomColorValue);
            return true; // true, because the image was modified
        } else{
            return false; // false, because the image was not modified
        }
        
    }
    
    
    private boolean tryToModifyImageGradientVersion(ImageProcessor originalImage, ImageProcessor newImage){
        int randomX;
        int randomY;
        int randomColorValue;
        float randomColorMultiplier;
        int[][] originalMatrixPart;
        int[][] modifiedMatrixPart;
        
        // Choose a random pixel and a random modification for that pixel (pixelvalue + 1 * random value between 1 and 2)
        randomX = this.randomGenerator.nextInt(newImage.getWidth());
        randomY = this.randomGenerator.nextInt(newImage.getHeight());
        randomColorMultiplier = this.randomGenerator.nextFloat() + (float)1.0;
        randomColorValue = Math.round((float)(newImage.get(randomX, randomY) + 1) * randomColorMultiplier);
        
        // Create the matrices used to calculate the differences between original and modified image.
        originalMatrixPart = new int[this.dctBlockSize][this.dctBlockSize];
        modifiedMatrixPart = new int[this.dctBlockSize][this.dctBlockSize];
        int midpoint = this.dctBlockSize / 2 - 1;
        
        // Get the part of the original matrix around the randomly selected x and y,
        // from both the original and modified matrix.
        this.getMatrixPartValues(originalImage.getIntArray(), originalMatrixPart, randomX, randomY);
        this.getMatrixPartValues(newImage.getIntArray(), modifiedMatrixPart, randomX, randomY);

        // Modify the modifiedMatrixPart
        modifiedMatrixPart[midpoint][midpoint] = randomColorValue;
        
        //////// VERANDER DE PUNTEN ER OMHEEN OOK
        try{
            modifiedMatrixPart[midpoint-1][midpoint] = Math.round((float)(newImage.get(randomX-1, randomY) + 1) * (float)((randomColorMultiplier - 1) * 0.7 + 1));
        } catch (IndexOutOfBoundsException ex) {}
        try{
            modifiedMatrixPart[midpoint+1][midpoint] = Math.round((float)(newImage.get(randomX+1, randomY) + 1) * (float)((randomColorMultiplier - 1) * 0.7 + 1));
        } catch (IndexOutOfBoundsException ex) {}
        try{
            modifiedMatrixPart[midpoint][midpoint-1] = Math.round((float)(newImage.get(randomX, randomY-1) + 1) * (float)((randomColorMultiplier - 1) * 0.7 + 1));
        } catch (IndexOutOfBoundsException ex) {}
        try{
            modifiedMatrixPart[midpoint][midpoint+1] = Math.round((float)(newImage.get(randomX, randomY+1) + 1) * (float)((randomColorMultiplier - 1) * 0.7 + 1));
        } catch (IndexOutOfBoundsException ex) {}
        try{
            modifiedMatrixPart[midpoint-1][midpoint-1] = Math.round((float)(newImage.get(randomX-1, randomY-1) + 1) * (float)((randomColorMultiplier - 1) * 0.3 + 1));
        } catch (IndexOutOfBoundsException ex) {}
        try{
            modifiedMatrixPart[midpoint+1][midpoint+1] = Math.round((float)(newImage.get(randomX+1, randomY+1) + 1) * (float)((randomColorMultiplier - 1) * 0.3 + 1));
        } catch (IndexOutOfBoundsException ex) {}
        try{
            modifiedMatrixPart[midpoint-1][midpoint+1] = Math.round((float)(newImage.get(randomX-1, randomY+1) + 1) * (float)((randomColorMultiplier - 1) * 0.3 + 1));
        } catch (IndexOutOfBoundsException ex) {}
        try{
            modifiedMatrixPart[midpoint+1][midpoint-1] = Math.round((float)(newImage.get(randomX+1, randomY-1) + 1) * (float)((randomColorMultiplier - 1) * 0.3 + 1));
        } catch (IndexOutOfBoundsException ex) {}

        // If the random change is better, the outcome of the merit function is higher, and the change should be made to the new image
        if (this.calculateMerit(originalMatrixPart, modifiedMatrixPart) > this.calculateMerit(originalMatrixPart, originalMatrixPart)){
            newImage.set(randomX, randomY, randomColorValue);
            
            try{
                newImage.set(randomX-1, randomY, Math.round((float)(newImage.get(randomX-1, randomY) + 1) * (float)((randomColorMultiplier - 1) * 0.7 + 1)));
            } catch (IndexOutOfBoundsException ex) {}
            try{
                newImage.set(randomX+1, randomY, Math.round((float)(newImage.get(randomX+1, randomY) + 1) * (float)((randomColorMultiplier - 1) * 0.7 + 1)));
            } catch (IndexOutOfBoundsException ex) {}
            try{
                newImage.set(randomX, randomY-1, Math.round((float)(newImage.get(randomX, randomY-1) + 1) * (float)((randomColorMultiplier - 1) * 0.7 + 1)));
            } catch (IndexOutOfBoundsException ex) {}
            try{
                newImage.set(randomX, randomY+1, Math.round((float)(newImage.get(randomX, randomY+1) + 1) * (float)((randomColorMultiplier - 1) * 0.7 + 1)));
            } catch (IndexOutOfBoundsException ex) {}
            try{
                newImage.set(randomX-1, randomY-1, Math.round((float)(newImage.get(randomX-1, randomY-1) + 1) * (float)((randomColorMultiplier - 1) * 0.3 + 1)));
            } catch (IndexOutOfBoundsException ex) {}
            try{
                newImage.set(randomX+1, randomY+1, Math.round((float)(newImage.get(randomX+1, randomY+1) + 1) * (float)((randomColorMultiplier - 1) * 0.3 + 1)));
            } catch (IndexOutOfBoundsException ex) {}
            try{
                newImage.set(randomX-1, randomY+1, Math.round((float)(newImage.get(randomX-1, randomY+1) + 1) * (float)((randomColorMultiplier - 1) * 0.3 + 1)));
            } catch (IndexOutOfBoundsException ex) {}
            try{
                newImage.set(randomX+1, randomY-1, Math.round((float)(newImage.get(randomX+1, randomY-1) + 1) * (float)((randomColorMultiplier - 1) * 0.3 + 1)));
            } catch (IndexOutOfBoundsException ex) {}
            
            
            
            
            return true; // true, because the image was modified
        } else{
            return false; // false, because the image was not modified
        }
        
    }
    
    private void createOutputImage(ImageProcessor ip, String name) {
        ImagePlus outputImage = new ImagePlus(name, ip);
        ImageWindow outputWindow = new ImageWindow(outputImage);
        outputWindow.setVisible(true);
    }

    /**
     * Copy a part of the source matrix, given a midpoint and new matrix size.
     * All coordinates outside the source matrix are filled in with zero.
     *
     * @param sourceMatrix the original matrix to copy from
     * @param matrixPart the partial matrix to be filled
     * @param midX the midpoint on the x axis of the new matrix in the original matrix
     * @param midY the midpoint on the y axis of the new matrix in the original matrix
     */
    private void getMatrixPartValues(final int[][] sourceMatrix, final int[][] matrixPart,
                                    final int midX, final int midY) {
        int startX = midX - (matrixPart.length / 2) + 1;
        int startY = midY - (matrixPart[0].length / 2) + 1;

        int i = 0;
        int j = 0;

        // Loop through the part of the original matrix that must be copied
        for (int x = startX; x < (startX + matrixPart.length); x++, i++) {
            for (int y = startY; y < (startY + matrixPart[0].length); y++, j++) {
                // try to copy the value, if the index is out of bounds, set the value to zero
                try {
                    matrixPart[i][j] = sourceMatrix[x][y];
                } catch (ArrayIndexOutOfBoundsException aiex) {
                    matrixPart[i][j] = 0;
                }
            }
            j = 0;
        }
    }

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



    /**
     * This method displays the about information of the plug-in.
     */
    public void showAbout() {
        IJ.showMessage("About Image Reconstructor", "<html>"
            + "<font size=-2>Created by Lonneke Scheffer and Wout van Helvoirt."
        );
    }

    /**
     * This merit function calculates the log likelihood and sparsity for a modified matrix.
     * The goal is to maximize the outcome of this merit function. The regularization factor
     * determines the importance of the log likelihood and sparsity compared to one another.
     * As stated in 'Imaging with a small number of photons', by P. A. Morris et al.
     * 
     * @param originalMatrix part of the original image
     * @param modifiedMatrix part of the modified image
     * @return a merit value for this modified image compared to the original
     */
    private float calculateMerit(final int[][] originalMatrix, final int[][] modifiedMatrix) {
        return this.calculateLogLikelihood(originalMatrix, modifiedMatrix) - this.regularizationFactor 
                * this.calculateMatrixSparsity(modifiedMatrix);
    }

    /**
     * Calculates the log likelihood for the modified matrix given the original matrix.
     * As stated in 'Imaging with a small number of photons', by P. A. Morris et al.
     * 
     * @param originalMatrix part of the original image
     * @param modifiedMatrix part of the modified image
     * @return the log likelihood
     */
    private float calculateLogLikelihood(final int[][] originalMatrix, final int[][] modifiedMatrix){
        float logLikelihood = 0;

        // The two matrices must be the same size
        if (originalMatrix.length != modifiedMatrix.length
                || originalMatrix[0].length != modifiedMatrix[0].length) {
            throw new IndexOutOfBoundsException("Your original matrix and modified matrix do not have the same size");
        }

        // The calculation for the log likelihood
        for (int i = 0; i < originalMatrix.length; i++) {
            for (int j = 0; i < originalMatrix[0].length; i++) {
                logLikelihood += (Math.log(modifiedMatrix[i][j] + this.darkCountRate)
                        - (modifiedMatrix[i][j] + this.darkCountRate)
                        - CombinatoricsUtils.factorialLog(originalMatrix[i][j]));
            }
        }

        return logLikelihood;

    }

    /**
     * Calculates the sparsity of the given matrix, using a Direct Cosine Transform.
     * The input matrix can be bigger than the DCT size, but if the matrix size is not 
     * a multiple of the DCT size, the transformation will only be performed on the part of 
     * the matrix that 'fits' inside a multiple of the DCT size.
     * 
     * For instance: an input matrix of size 25x25 will give the same outcome as an input
     * matrix of size 30x30 if the DCT size is 12, since they will both be rounded to a
     * 24x24 matrix.
     * 
     * As stated in 'Imaging with a small number of photons', by P. A. Morris et al.
     * 
     * @param matrix the input matrix
     * @return a measure of the sparsity of the matrix
     */
    private float calculateMatrixSparsity(final int[][] matrix){
        double sumAbsoluteCoefficients = 0;
        double sumSquaredAbsoluteCoefficients = 0;
        double[][] dctInputMatrix;
        double[][] dctOutputMatrix;

        dctInputMatrix = new double[this.dctBlockSize][this.dctBlockSize];
        DCT dct = new DCT(this.dctBlockSize);

        // Loop through the whole matrix, by steps of size DCT block size.
        for (int matrixWidth=0; matrixWidth < matrix.length; matrixWidth += this.dctBlockSize){
            for (int matrixHeigth=0; matrixHeigth < matrix[0].length; matrixHeigth += this.dctBlockSize){
                // Loop through a part of the matrix of size DCT size x DCT size,
                // and copy the values to a temporary matrix.
                for (int partWidth = matrixWidth; partWidth < (matrixWidth + this.dctBlockSize); partWidth++){
                    for (int partHeigth = matrixHeigth; partHeigth < (matrixHeigth + this.dctBlockSize); partHeigth ++){
                        dctInputMatrix[partWidth-matrixWidth][partHeigth-matrixHeigth] = (double) matrix[partWidth][partHeigth];

                    }
                }
                // Perform the direct cosine transform on the copied matrix part.
                dctOutputMatrix = dct.forwardDCT(dctInputMatrix);
                
                // Get the sum of the absolute coefficients (matrix values),
                // and the sum of the squared absolute coefficients (matrix values).
                // These are used in the calculation of P. A. Morris et al.
                for (int dtcWidth = 0; dtcWidth < dctOutputMatrix.length; dtcWidth ++){
                    for (int dtcHeigth = 0; dtcHeigth < dctOutputMatrix[0].length; dtcHeigth ++){
                        sumAbsoluteCoefficients += Math.abs(dctOutputMatrix[dtcWidth][dtcHeigth]);
                        sumSquaredAbsoluteCoefficients += Math.pow(Math.abs(dctOutputMatrix[dtcWidth][dtcHeigth]), 2);
                    }
                }

            }
        }

        // Square the sum of the absolute coefficients, and divide it by the sum of the squared absolute coefficients.
        // The outcome of this formula can be used as a measure of sparsity.
        return (float)(Math.pow(sumAbsoluteCoefficients, 2) / sumSquaredAbsoluteCoefficients);
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

}