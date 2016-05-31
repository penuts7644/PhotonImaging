/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author lonneke
 */
public class DctCalculator {
    private final int dctBlockSize;
    private int[][] matrix;
    private double[] totalCoefficients;
    private double totalSparsity;
    private double[] temporaryCoefficients;
    private double temporarySparsity;
    private int temporaryX;
    private int temporaryY;
    private int temporaryColor;

    public DctCalculator(int dctBlockSize, int[][] matrix) {
        // check if power of 2
        if (!(((dctBlockSize & (dctBlockSize-1))==0) && (dctBlockSize>0))){
            throw new ArithmeticException("Your given DCT block size (" + dctBlockSize + ") should be a power of 2!");
        }
        if (matrix.length < dctBlockSize || matrix[0].length < dctBlockSize){
            throw new IndexOutOfBoundsException("Your input matrix is too small, it should be bigger than your DCT block size (" + dctBlockSize + ")!");
        }
        
        this.dctBlockSize = dctBlockSize;
        this.matrix = matrix;
        this.totalCoefficients = this.calculateCoefficients(matrix);
        this.totalSparsity = this.calculateMatrixSparsity(this.totalCoefficients);
    }
    
    
    private double calculateMatrixSparsity(double sumAbsoluteCoefficients, double sumSquaredAbsoluteCoefficients){
        return (Math.pow(sumAbsoluteCoefficients, 2) / sumSquaredAbsoluteCoefficients);
    }
    
    private double calculateMatrixSparsity(double[] coefficients){
        return this.calculateMatrixSparsity(coefficients[0], coefficients[1]);
    }
    
    public void testEstimatedSparsitySoFar(){
        System.out.println("Your estimated sparsity so far is: " +  this.totalSparsity);
        System.out.println("When calculating the new sparsity for the whole image, the outcome is: " + this.calculateMatrixSparsity(this.calculateCoefficients(this.matrix)));
    }
    
    public double tryModification(int xCoordinate, int yCoordinate, int newColorValue){
        if (xCoordinate > this.matrix.length || yCoordinate > this.matrix[0].length){
            throw new ArrayIndexOutOfBoundsException("Your given coordinates (" + xCoordinate + "," + yCoordinate + ") are outside the matrix.");
        }
        
        int[][] originalMatrixPart;
        int[][] modifiedMatrixPart;
        double[] originalMatrixPartCoefficients;
        double[] modifiedMatrixPartCoefficients;
        
        originalMatrixPart = this.getDctPart(xCoordinate, yCoordinate);
        modifiedMatrixPart = this.getDctPart(xCoordinate, yCoordinate);
        modifiedMatrixPart[xCoordinate%this.dctBlockSize][yCoordinate%this.dctBlockSize] = newColorValue;
        
        originalMatrixPartCoefficients = this.calculateCoefficients(originalMatrixPart);
        modifiedMatrixPartCoefficients = this.calculateCoefficients(modifiedMatrixPart);
        
        this.temporaryX = xCoordinate;
        this.temporaryY = yCoordinate;
        this.temporaryColor = newColorValue;
        this.temporaryCoefficients = new double[] {(this.totalCoefficients[0] - originalMatrixPartCoefficients[0] + modifiedMatrixPartCoefficients[0]), 
            (this.totalCoefficients[1] - originalMatrixPartCoefficients[1] + modifiedMatrixPartCoefficients[1])};
        this.temporarySparsity = this.calculateMatrixSparsity(this.temporaryCoefficients);
        
        return this.temporarySparsity;
    }
    
    public void performModification(){
        this.matrix[this.temporaryX][this.temporaryY] = this.temporaryColor;
        this.totalCoefficients[0] = this.temporaryCoefficients[0];
        this.totalCoefficients[1] = this.temporaryCoefficients[1];
        this.totalSparsity = this.temporarySparsity;
    }
    
    private int[][] getDctPart(int xCoordinate, int yCoordinate){
        int xStart;
        int yStart;
        int[][] matrixPart;
        int i;
        int j;
        
        xStart = xCoordinate - (xCoordinate % this.dctBlockSize);
        yStart = yCoordinate - (yCoordinate % this.dctBlockSize);
        
        matrixPart = new int[this.dctBlockSize][this.dctBlockSize];
        
        i = 0;
        j = 0;

        // Loop through the part of the original matrix that must be copied
        for (int x = xStart; x < (xStart + this.dctBlockSize); x++, i++) {
            for (int y = yStart; y < (yStart + this.dctBlockSize); y++, j++) {
                // try to copy the value, if the index is out of bounds, set the value to zero
                try {
                    matrixPart[i][j] = this.matrix[x][y];
                } catch (ArrayIndexOutOfBoundsException aiex) {
                    matrixPart[i][j] = 0;
                }
            }
            j = 0;
        }
        
        return matrixPart;
    }
    
    private double[] calculateCoefficients(int[][] inputMatrix){
        double sumAbsoluteCoefficients = 0;
        double sumSquaredAbsoluteCoefficients = 0;
        double[][] dctInputMatrix;
        double[][] dctOutputMatrix;

        dctInputMatrix = new double[this.dctBlockSize][this.dctBlockSize];
        DCT dct = new DCT(this.dctBlockSize);

        // Loop through the whole matrix, by steps of size DCT block size.
        for (int matrixWidth = 0; matrixWidth < inputMatrix.length; matrixWidth += this.dctBlockSize) {
            for (int matrixHeigth = 0; matrixHeigth < inputMatrix[0].length; matrixHeigth += this.dctBlockSize) {
                // Loop through a part of the matrix of size DCT size x DCT size,
                // and copy the values to a temporary matrix.
                for (int partWidth = matrixWidth; partWidth < (matrixWidth + this.dctBlockSize); partWidth++) {
                    for (int partHeigth = matrixHeigth; partHeigth < (matrixHeigth + this.dctBlockSize); partHeigth++) {
                        // If the matrix size is not a multiple of block size, an ArrayIndexOutOfBoundsException
                        // will occur at the edges of the image. Fill these positions in with zero.
                        try {
                            dctInputMatrix[partWidth - matrixWidth][partHeigth - matrixHeigth]
                                    = (double) inputMatrix[partWidth][partHeigth];
                        } catch (ArrayIndexOutOfBoundsException ex){
                            dctInputMatrix[partWidth - matrixWidth][partHeigth - matrixHeigth] = 0.0;
                        }
                    }
                }
                // Perform the direct cosine transform on the copied matrix part.
                dctOutputMatrix = dct.forwardDCT(dctInputMatrix);

                // Get the sum of the absolute coefficients (matrix values),
                // and the sum of the squared absolute coefficients (matrix values).
                // These are used in the calculation of P. A. Morris et al.
                for (double[] dctOutputMatrixRow : dctOutputMatrix) {
                    for (int dtcHeigth = 0; dtcHeigth < dctOutputMatrix[0].length; dtcHeigth++) {
                        sumAbsoluteCoefficients += Math.abs(dctOutputMatrixRow[dtcHeigth]);
                        sumSquaredAbsoluteCoefficients += Math.pow(Math.abs(dctOutputMatrixRow[dtcHeigth]), 2);
                    }
                }

            }
        }

        // Square the sum of the absolute coefficients, and divide it by the sum of the squared absolute coefficients.
        // The outcome of this formula can be used as a measure of sparsity.
        return new double[]{sumAbsoluteCoefficients, sumSquaredAbsoluteCoefficients};
        //return (Math.pow(sumAbsoluteCoefficients, 2) / sumSquaredAbsoluteCoefficients);
    }
    
    

}
