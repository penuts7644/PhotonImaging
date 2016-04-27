
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
public class ImageReconstructorBackup {
    private final int[][] photonCountMatrix;
    private final float darkCountRate;

    public ImageReconstructorBackup(int[][] photonCountMatrix, float darkCountRate) {
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
                // LET OP KLOPT NIET VOLGENS MIJ (moet je niet origineel vergelijken met nieuw???)
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

    
    public static void main(final String[] args) {
        System.out.println("hsello");
        
        
        
        ImageReconstructorBackup ir = new ImageReconstructorBackup(new int[5][5], 0);
        DCT dct = new DCT(8);
        //double[][] input = new double[][] {new double[] {1, 2, 3, 4, 5, 6, 7, 8}, new double[] {1, 2, 3, 4, 5, 6, 7, 8}, new double[] {1, 2, 3, 4, 5, 6, 7, 8}, new double[] {1, 2, 3, 4, 5, 6, 7, 8}, new double[] {1, 2, 3, 4, 5, 6, 7, 8}, new double[] {1, 2, 3, 4, 5, 6, 7, 8}, new double[] {1, 2, 3, 4, 5, 6, 7, 8}, new double[] {1, 2, 3, 4, 5, 6, 7, 8}};
        double[][] input = new double[][] {new double[] {140, 144, 147, 140, 140, 155, 179, 175}, 
                                           new double[] {144, 152, 140, 147, 140, 148, 167, 179}, 
                                           new double[] {152, 155, 136, 167, 163, 162, 152, 172}, 
                                           new double[] {168, 145, 156, 160, 152, 155, 136, 160}, 
                                           new double[] {162, 148, 156, 148, 140, 136, 147, 162}, 
                                           new double[] {147, 167, 140, 155, 155, 140, 136, 162}, 
                                           new double[] {136, 156, 123, 167, 162, 144, 140, 147}, 
                                           new double[] {148, 155, 136, 155, 152, 147, 147, 136}};
        double[][] output = dct.forwardDCT(input);
        
        for (int i = 0; i < output.length; i++){
            for (int j = 0; j < output[0].length; j++){
                System.out.printf(Math.round(output[i][j]) + " ");
            }
            System.out.printf("\n");
        }
        
        System.out.println(input.length);
        System.out.println(output.length);
        
        System.out.println("***");
        System.out.println(Math.floorDiv(8, 2));
    }
}
