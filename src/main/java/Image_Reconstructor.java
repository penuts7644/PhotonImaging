
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
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
    private ImagePlus imp;
    /** The matrix containing all original pixel values. */
    private int[][] originalMatrix;
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
        return this.flags;
    }

    @Override
    public void run(ImageProcessor ip) {
        int[][] bestMatrix;
        int[][] newMatrix;
        float bestMerit;
        float newMerit;
        
        this.originalMatrix = ip.getIntArray();
        
        bestMatrix = ip.getIntArray();
        bestMerit = calculateMerit(bestMatrix);
        
        while (true){
            newMatrix = this.changeMatrixRandomly(bestMatrix);
            newMerit = this.calculateMerit(bestMatrix);
            if (newMerit > bestMerit){
                bestMatrix = newMatrix;
                bestMerit = newMerit;
            }
        }
        
        // aan het eind: bestMatrix terugberekenen naar plaatje
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        
        if (this.originalMatrix.length != modifiedMatrix.length 
                || this.originalMatrix[0].length != modifiedMatrix[0].length){
            throw new IndexOutOfBoundsException("Your original matrix and modified matrix do not have the same size");
        }
        
        for (int i = 0; i < this.originalMatrix.length; i++){
            for (int j = 0; i < this.originalMatrix[0].length; i++){
                logLikelihood += (Math.log(modifiedMatrix[i][j] + this.darkCountRate) 
                        - (modifiedMatrix[i][j] + this.darkCountRate) 
                        - Math.log(CombinatoricsUtils.factorial(this.originalMatrix[i][j]))); // logfactorial? geeft dit dezelfde output?
                // NOG EVEN GOED UITZOEKEN HOE DEZE FORMULE ECHT WERKT!
            }
        }
        
        return logLikelihood;
        
    }
    
    private float calculateMatrixSparsity(int[][] matrix){
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private int[][] changeMatrixRandomly(int[][] matrix) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    

    
}
