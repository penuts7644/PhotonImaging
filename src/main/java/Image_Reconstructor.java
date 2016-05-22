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
public class Image_Reconstructor implements ExtendedPlugInFilter, DialogListener {
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
    private float thresholdRatioChanges = (float)0.09999;
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

    @Override
    public void run(ImageProcessor ip) {
        int[][] originalMatrixPart;
        int[][] modifiedMatrixPart;
        int randomX;
        int randomY;
        int randomColorValue;
        float randomColorMultiplier;

        ImageProcessor newImage = ip.duplicate();

        originalMatrixPart = new int[this.dctBlockSize][this.dctBlockSize];
        modifiedMatrixPart = new int[this.dctBlockSize][this.dctBlockSize];
        int midpoint = this.dctBlockSize / 2 - 1;
        
        float modified = 1;
        float unmodified = 1;
        
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
        
        
        while (modified + unmodified < 1000 || (modified/unmodified > this.thresholdRatioChanges && modified + unmodified >= 1000)) { // stop als er genoeg iteraties zijn gedaan (>1000) en de ratio modified:unmodified laag is
            // Choose a random pixel and a random color for that pixel
            randomX = this.randomGenerator.nextInt(newImage.getWidth());
            randomY = this.randomGenerator.nextInt(newImage.getHeight());
            randomColorMultiplier = this.randomGenerator.nextFloat() + (float)1.0;
            randomColorValue = Math.round((float)(newImage.get(randomX, randomY) + 1) * randomColorMultiplier);
            
            // Get the part of the original matrix around the randomly selected x and y,
            // from both the original and modified matrix.
//            this.getMatrixPartValues(newImage.getIntArray(), originalMatrixPart, randomX, randomY);
//            this.copyMatrixValues(originalMatrixPart, modifiedMatrixPart);
            // DEZE VERSIE MOGELIJK BETER WANT ORIGINAL MATRIX IS DAADWERKELIJK HET ORIGINEEL EN NIET DE STEEDS GEUPDATETE VARIANT
            this.getMatrixPartValues(ip.getIntArray(), originalMatrixPart, randomX, randomY);
            this.getMatrixPartValues(newImage.getIntArray(), modifiedMatrixPart, randomX, randomY);

            // Modify the modifiedMatrixPart
            modifiedMatrixPart[midpoint][midpoint] = randomColorValue;

            if (this.calculateMerit(originalMatrixPart, modifiedMatrixPart) > this.calculateMerit(originalMatrixPart, originalMatrixPart)){
                newImage.set(randomX, randomY, randomColorValue);
                modified ++;
            } else{
                unmodified ++;
            }
            
            if (modified/unmodified < 0.9 && img90 && (modified + unmodified > 1000)){
                img90 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.9|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (modified/unmodified < 0.8 && img80 && (modified + unmodified > 1000)){
                img80 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.8|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (modified/unmodified < 0.7 && img70 && (modified + unmodified > 1000)){
                img70 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.7|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (modified/unmodified < 0.6 && img60 && (modified + unmodified > 1000)){
                img60 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.6|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (modified/unmodified < 0.5 && img50 && (modified + unmodified > 1000)){
                img50 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.5|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (modified/unmodified < 0.4 && img40 && (modified + unmodified > 1000)){
                img40 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.4|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (modified/unmodified < 0.3 && img30 && (modified + unmodified > 1000)){
                img30 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.3|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (modified/unmodified < 0.2 && img20 && (modified + unmodified > 1000)){
                img20 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.2|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } else if (modified/unmodified < 0.1 && img10 && (modified + unmodified > 1000)){
                img10 = false;
                createOutputImage(newImage.duplicate(), "ratio:0.1|time:" + (float)(System.nanoTime() - startTime) + "|dct:" + this.dctBlockSize +"|darkcount:" + this.darkCountRate + "|lambda:" + this.regularizationFactor + "|colormethod:*2");
            } 
            
//            System.out.println(modified + unmodified);
//            System.out.println(modified/unmodified);
//            if (i % 1000 == 0){
//                System.out.println("Iteration " + i);
//                System.out.println("success = " + success);
//                System.out.println("fail = " + fail);
//                if (fail > 0){
//                    System.out.println("ratio success/fail= " + (float)success / (float)fail);
//                }
//                System.out.println("");
//            }

            //new ImagePlus(newImage);
        }

//        // Create new output image with title.
//        ImagePlus outputImage = new ImagePlus("Reconstructed Image:" + this.darkCountRate + "," + this.regularizationFactor, newImage);
//
//        // Make new image window in ImageJ and set the window visible.
//        ImageWindow outputWindow = new ImageWindow(outputImage);
//        outputWindow.setVisible(true);

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

    private float calculateMerit(final int[][] originalMatrix, final int[][] modifiedMatrix) {
        return this.calculateLogLikelihood(originalMatrix, modifiedMatrix) - this.regularizationFactor * this.calculateMatrixSparsity(modifiedMatrix);
    }


    private float calculateLogLikelihood(final int[][] originalMatrix, final int[][] modifiedMatrix){
        float logLikelihood = 0;

        // testen of modifiedimage en eigen photoncountmatrix even groot zijn
        // anders exceptie?
        // of ergens anders testen, je weet vrij zeker dat het goed is al..
        if (originalMatrix.length != modifiedMatrix.length
                || originalMatrix[0].length != modifiedMatrix[0].length){
            throw new IndexOutOfBoundsException("Your original matrix and modified matrix do not have the same size");
        }

        for (int i = 0; i < originalMatrix.length; i++){
            for (int j = 0; i < originalMatrix[0].length; i++){
                logLikelihood += (Math.log(modifiedMatrix[i][j] + this.darkCountRate)
                        - (modifiedMatrix[i][j] + this.darkCountRate)
                        - CombinatoricsUtils.factorialLog(originalMatrix[i][j]));
            }
        }

        return logLikelihood;

    }

    private float calculateMatrixSparsity(final int[][] matrix){
        double sumAbsoluteCoefficients = 0;
        double sumSquaredAbsoluteCoefficients = 0;
        double[][] dctInputMatrix;
        double[][] dctOutputMatrix;

        dctInputMatrix = new double[this.dctBlockSize][this.dctBlockSize];

//
        DCT dct = new DCT(this.dctBlockSize);
//        dct.forwardDCT((double[][])new int[8][8]);

        for (int matrixWidth=0; matrixWidth < matrix.length; matrixWidth += this.dctBlockSize){
            for (int matrixHeigth=0; matrixHeigth < matrix[0].length; matrixHeigth += this.dctBlockSize){
                // Create the DCT input matrix (N x N part cut out of the full matrix)
                for (int partWidth = matrixWidth; partWidth < (matrixWidth + this.dctBlockSize); partWidth++){
                    for (int partHeigth = matrixHeigth; partHeigth < (matrixHeigth + this.dctBlockSize); partHeigth ++){
                        dctInputMatrix[partWidth-matrixWidth][partHeigth-matrixHeigth] = (double) matrix[partWidth][partHeigth];

                    }
                }
                // perform the direct cosine transform
                dctOutputMatrix = dct.forwardDCT(dctInputMatrix);
                for (int dtcWidth = 0; dtcWidth < dctOutputMatrix.length; dtcWidth ++){
                    for (int dtcHeigth = 0; dtcHeigth < dctOutputMatrix[0].length; dtcHeigth ++){
                        sumAbsoluteCoefficients += Math.abs(dctOutputMatrix[dtcWidth][dtcHeigth]);
                        sumSquaredAbsoluteCoefficients += Math.pow(Math.abs(dctOutputMatrix[dtcWidth][dtcHeigth]), 2);
                    }
                }

            }
        }

        return (float)(Math.pow(sumAbsoluteCoefficients, 2) / sumSquaredAbsoluteCoefficients);
    }

    private void changeMatrixRandomly(int[][] matrix) {
        matrix[this.randomGenerator.nextInt(matrix.length)][this.randomGenerator.nextInt(matrix[0].length)] += 2;
    }



    private static void main(final String[] args) {
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

    @Override
    public void setNPasses(final int nPasses) {
        this.nPasses = nPasses;
    }

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




}