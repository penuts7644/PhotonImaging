
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.util.Random;
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
public class Image_Reconstructor implements PlugInFilter {
    /** */
    private int dctBlockSize = 8;
    private float darkCountRate = 0;
    private float regularizationFactor = (float)0.5;
    /* breedte = breedte - (breedte % dctBlockSize)*/ 
    private int outputWidth;
    private int outputHeigth;
    private ImagePlus imp;
    /** The matrix containing all original pixel values. */
    private int[][] originalMatrix;
    
    private Random randomNumber = new Random(); // DEZE RANDOM MOET ERGENS ANDERS AANGEMAAKT WORDEN LATER UITZOEKEN
    /** Set all requirements for plug-in to run. */
    private final int flags = PlugInFilter.DOES_8G
            | PlugInFilter.DOES_16; // DOES 32??? ?????????????????????????????????????????????????????????????
    
    
    @Override
    public int setup(String arg, ImagePlus imp) {
        // If arg is about, display help message and quit.
        if (arg.equals("about")) {
            this.showAbout();
            return PlugInFilter.DONE;
        }

        this.imp = imp;
        this.outputHeigth = this.imp.getHeight() - (this.imp.getHeight() % this.dctBlockSize);
        this.outputWidth = this.imp.getWidth()- (this.imp.getWidth()% this.dctBlockSize);
        this.originalMatrix = new int[this.outputWidth][this.outputHeigth];
        
        return this.flags;
    }

    @Override
    public void run(ImageProcessor ip) {
        int[][] bestMatrix;
        int[][] alteredMatrix;
        float bestMerit;
        float newMerit;
        
        bestMatrix = new int[this.outputWidth][this.outputHeigth];
        alteredMatrix = new int[this.outputWidth][this.outputHeigth];
        
        // Copy all values from the imageprocessor to the matrix
        // This is not the same as originalMatrix = ip.getIntArray(), because the originalMatrix might be smaller (see setup)
        // Also create a 'best matrix', which is identical to the original matrix in the beginning
        this.copyMatrixValues(this.originalMatrix, ip.getIntArray());
        this.copyMatrixValues(bestMatrix, this.originalMatrix);
        
//        for (int i=0; i < this.originalMatrix.length; i++){
//            for (int j=0; j < this.originalMatrix[0].length; j++){
//                this.originalMatrix[i][j] = ip.getPixel(i, j);
//                bestMatrix[i][j] = this.originalMatrix[i][j];
//            }
//        }
        
        
        this.calculateMatrixSparsity(bestMatrix);

        bestMerit = calculateMerit(bestMatrix);
        
        for (int i = 0; i < 1000; i++) {
            this.copyMatrixValues(alteredMatrix, bestMatrix);
            this.changeMatrixRandomly(alteredMatrix);
            newMerit = this.calculateMerit(alteredMatrix);
            if (newMerit > bestMerit){
                this.copyMatrixValues(bestMatrix, alteredMatrix);
                bestMerit = newMerit;
            } 
        }
        
        System.out.println("klaar");
        
        ShortProcessor sp = new ShortProcessor(bestMatrix.length, bestMatrix[0].length);
        sp.setIntArray(bestMatrix);
        
        // Create new output image with title.
        ImagePlus outputImage = new ImagePlus("Photon Count Image", sp);

        // Make new image window in ImageJ and set the window visible.
        ImageWindow outputWindow = new ImageWindow(outputImage);
        outputWindow.setVisible(true);
        
        // aan het eind: bestMatrix terugberekenen naar plaatje
    }
    
    /**
     * if the source matrix is bigger than the new matrix, only the part that fits inside the new matrix is copied
     * if the source matrix is smaller, the unknown values are filled in with zero
     * 
     * @param newMatrix
     * @param sourceMatrix 
     */
    public void copyMatrixValues(int[][] newMatrix, int[][] sourceMatrix){
        for (int i=0; i<newMatrix.length; i++){
            for (int j=0; j<newMatrix[0].length; j++){
                try{
                    newMatrix[i][j] = sourceMatrix[i][j];
                } catch (ArrayIndexOutOfBoundsException aiex){
                    newMatrix[i][j] = 0;
                }
                
            }
        }
        
    }
    
    
    
    /**
     * This method displays the about information of the plug-in.
     */
    public void showAbout() {
        IJ.showMessage("About Image Reconstructor", "<html>"
            + "<font size=-2>Created by Lonneke Scheffer and Wout van Helvoirt."
        );
    }

    private float calculateMerit(int[][] modifiedMatrix) {
        return this.calculateLogLikelihood(modifiedMatrix) - this.regularizationFactor * this.calculateMatrixSparsity(modifiedMatrix);
    }
    
    
//            double logLikelihood = 0;
//        
//        // testen of modifiedimage en eigen photoncountmatrix even groot zijn
//        // anders exceptie?
//
//        for (int i = 0; i < this.photonCountMatrix.length; i++){
//            for (int j = 0; i < this.photonCountMatrix[0].length; i++){
//                logLikelihood += (Math.log(modifiedImage[i][j] + this.darkCountRate) 
//                        - (modifiedImage[i][j] + this.darkCountRate) 
//                        - Math.log(CombinatoricsUtils.factorial(this.photonCountMatrix[i][j]))); // logfactorial? geeft dit dezelfde output?
//                // LET OP KLOPT NIET VOLGENS MIJ (moet je niet origineel vergelijken met nieuw???)
//            }
//        }
//        
//        return logLikelihood;
    
    private float calculateLogLikelihood(int[][] modifiedMatrix){
        float logLikelihood = 0;
        
        // testen of modifiedimage en eigen photoncountmatrix even groot zijn
        // anders exceptie?
        // of ergens anders testen, je weet vrij zeker dat het goed is al..
        if (this.originalMatrix.length != modifiedMatrix.length 
                || this.originalMatrix[0].length != modifiedMatrix[0].length){
            throw new IndexOutOfBoundsException("Your original matrix and modified matrix do not have the same size");
        }
        
        
        
        for (int i = 0; i < this.originalMatrix.length; i++){
            for (int j = 0; i < this.originalMatrix[0].length; i++){
                try{
                    logLikelihood += (Math.log(modifiedMatrix[i][j] + this.darkCountRate) 
                        - (modifiedMatrix[i][j] + this.darkCountRate)
                        - CombinatoricsUtils.factorialLog(this.originalMatrix[i][j]));
                        //- Math.log(CombinatoricsUtils.factorial(this.originalMatrix[i][j]))); // logfactorial? geeft dit dezelfde output?
                    // NOG EVEN GOED UITZOEKEN HOE DEZE FORMULE ECHT WERKT!
                } catch (MathArithmeticException ex){
                    System.out.println("hoi, waarde te hoog: ");
                }
                
            }
        }
        
        return logLikelihood;
        
    }
    
    private float calculateMatrixSparsity(int[][] matrix){
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


//        for (int i=0; i < matrix.length; i += this.dctBlockSize){
//            for (int j=0; j < matrix[0].length; j += this.dctBlockSize){
//                System.out.printf("\nbreedte " + i + "tot" + (i + this.dctBlockSize) + "\n");
//                System.out.printf("\nhoogte " + j + "tot" + (j + this.dctBlockSize) + "\n");
//                for (int x = i; x < (i + this.dctBlockSize); x++){
//                    for (int y = j; y < (j + this.dctBlockSize); y ++){
//                        dctInputMatrix[x-i][y-j] = (double) matrix[x][y];
//                        System.out.printf(dctInputMatrix[x-i][y-j] + " ");
////                            System.out.printf(x + "," + y + " ");
//                    }
//                    System.out.printf("\n");
//                }
//                
//            }
//        }
        
        
        
//        for (int i = 0; i < matrix.length; i++){
//            sumAbsoluteCoefficients += Math.abs(coefficientsOfSpatialFrequencies[i]);
//            sumSquaredAbsoluteCoefficients += Math.pow(Math.abs(coefficientsOfSpatialFrequencies[i]), 2);
//        }
        
        return (float)(Math.pow(sumAbsoluteCoefficients, 2) / sumSquaredAbsoluteCoefficients);
    }

    private void changeMatrixRandomly(int[][] matrix) {
        matrix[this.randomNumber.nextInt(matrix.length)][this.randomNumber.nextInt(matrix[0].length)] += 2;
        
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    

    public static void main(final String[] args) {
        // set the plugins.dir property to make the plug-in appear in the Plugins menu
        Class<?> clazz = Image_Thresholder.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);
        
        //System.out.println(CombinatoricsUtils.factorial(100));
        

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
