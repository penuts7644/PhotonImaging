/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import org.apache.commons.math3.util.CombinatoricsUtils;

/**
 *
 * @author lonneke
 */
public class ImageReconstructor {
    private final int[][] photonCountMatrix;
    private final float darkCountRate;

    public ImageReconstructor(int[][] photonCountMatrix, float darkCountRate) {
        this.photonCountMatrix = photonCountMatrix;
        this.darkCountRate = darkCountRate;
    }
    
    public double getModifiedImageLogLikelihood(int[][] modifiedImage) {
        double logLikelihood = 0;
        
        // testen of modifiedimage en eigen photoncountmatrix even groot zijn
        // anders exceptie?

        for (int i = 0; i < this.photonCountMatrix.length; i++){
            for (int j = 0; i < this.photonCountMatrix[0].length; i++){
                logLikelihood += (Math.log(modifiedImage[i][j] + this.darkCountRate) 
                        - (modifiedImage[i][j] + this.darkCountRate) 
                        - Math.log(CombinatoricsUtils.factorial(this.photonCountMatrix[i][j]))); // logfactorial? geeft dit dezelfde output?
            }
        }
        
        return logLikelihood;
    }
    
    // exactere naam: getNumberOfParticipatingSpatialFrequencies
    // waar moet coefficientsOfSpatialFrequencies vandaan gehaald worden? wat is het? hoe wordt het gemaakt?
    // uit de formules lijkt het net alsof deze niet wordt meegegeven maar alleen Ij wordt meegegeven (waar komt a vandaan)
    
    
    
    // HANDIGE SITE: http://cs.stanford.edu/people/eroberts/courses/soco/projects/data-compression/lossy/jpeg/dct.htm
   // let op; ze zeggen zelf ook al dat dit efficienter is met matrix rekenen... maar laat het eerst maar eens werken (en uitzoeken of dit daadwerkelijk is wat ze bedoelen)
    public double getMeasureOfSparsity(double[] coefficientsOfSpatialFrequencies) {
        double sumAbsoluteCoefficients = 0;
        double sumSquaredAbsoluteCoefficients = 0;
        
        for (int i = 0; i < coefficientsOfSpatialFrequencies.length; i++){
            sumAbsoluteCoefficients += Math.abs(coefficientsOfSpatialFrequencies[i]);
            sumSquaredAbsoluteCoefficients += Math.pow(Math.abs(coefficientsOfSpatialFrequencies[i]), 2);
        }
        
        return Math.pow(sumAbsoluteCoefficients, 2) / sumSquaredAbsoluteCoefficients;
    }
    
    
    // bevat een dtc matrix floats of ints?
    // er zijn snellere manieren dan dit
    
    // NIET ZELF IMPLEMENTEREN ER IS VAST IETS VOOR OP INTERNET
    // misschien...? http://www.nyx.net/~smanley/dct/DCT.java
    // https://sourceware.org/svn/gcc/tags/libbid-last-merge/libjava/classpath/gnu/javax/imageio/jpeg/DCT.java
    // http://www.developer.com/java/data/article.php/3619081/Understanding-the-Discrete-Cosine-Transform-in-Java.htm
    public int[][] calculateDtcMatrix(int[][] image) {
        int[][] dtc = new int[image.length][image[0].length];
        
        for (int i = 0; i < image.length; i++){
            for(int j = 0; j < image[0].length; j++){
                int temp = 0;
                
                for (int ii = 0; ii < image.length; ii++){
                    for(int jj = 0; jj < image[0].length; jj++){
                        temp += 0;
                        // += Cosines[ii][i] * Cosines[jj][j] * Pixel[ii][jj];
                    }
                }
                
                // temp *= sqrt(2 * N) * Coefficient[i][j];
                dtc[i][j] = Math.round(temp);
            }
        }
        
        return dtc;
    }
    
    // andere naam bedenken?!
//    The merit function is calculated for this modified image, and repeated iterations are performed until the image corresponding to a maximization of this merit function is found.
    public double meritFunction(double regularzationFactor, int[][] modifiedImage, double[] coefficientsOfSpatialFrequencies){
        return this.getModifiedImageLogLikelihood(modifiedImage) - regularzationFactor * this.getMeasureOfSparsity(coefficientsOfSpatialFrequencies);
    }
}
