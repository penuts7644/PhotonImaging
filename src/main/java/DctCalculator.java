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

/**
 * DctCalculator
 * 
 * This class can be used to track the changing matrix sparsity for an image that is being modified.
 * With this DCT calculator, the total matrix sparsity only needs to be calculated once.
 * When modifying the image, the potential new value can be estimated by only looking at the
 * pixels in the block around the modified pixel. This is a lot faster than calculating the total
 * sparsity value for the whole image again
 *
 * @author Lonneke Scheffer
 */
public final class DctCalculator {

    /** The size for the DCT matrix to use. */
    private final int dctBlockSize;
    /** The matrix containing the pixel values of the image. */
    private int[][] matrix;
    /** The coefficients of the total image used to calculate the matrix sparsity. */
    private double[] totalCoefficients;
    /** The calculated sparsity of the total image. */
    private double totalSparsity;
    /** The coefficients of the modified image used to calculate the matrix sparsity. */
    private double[] temporaryCoefficients;
    /** The calculated sparsity of the total modified image. */
    private double temporarySparsity;

    /**
     * Create a new DCT calculator.
     *
     * @param dctBlockSize the size for the DCT blocks
     * @param matrix the matrix containing pixel values of the input image
     */
    public DctCalculator(final int dctBlockSize, final int[][] matrix) {
        // Check if the given block size is a power of 2
        if (!(((dctBlockSize & (dctBlockSize - 1)) == 0) && (dctBlockSize > 0))) {
            throw new ArithmeticException("Your given DCT block size (" + dctBlockSize + ") should be a power of 2!");
        }
        // Check if the DCT size does not exceed the matrix size
        if (matrix.length < dctBlockSize || matrix[0].length < dctBlockSize) {
            throw new IndexOutOfBoundsException("Your input matrix is too small, "
                    + "it should be bigger than your DCT block size (" + dctBlockSize + ")!");
        }

        this.dctBlockSize = dctBlockSize;
        this.matrix = matrix;
        this.totalCoefficients = this.calculateCoefficients(matrix);
        this.totalSparsity = this.calculateMatrixSparsity(this.totalCoefficients);
        this.temporaryCoefficients = new double[]{0, 0};
    }

    /**
     * Calculates the sparsity of the given matrix, using a Direct Cosine
     * Transform. The input matrix can be bigger than the DCT size, but if the
     * matrix size is not a multiple of the DCT size, the transformation will
     * only be performed on the part of the matrix that 'fits' inside a multiple
     * of the DCT size.
     *
     * For instance: an input matrix of size 25x25 will give the same outcome as
     * an input matrix of size 30x30 if the DCT size is 12, since they will both
     * be rounded to a 24x24 matrix.
     *
     * As stated in 'Imaging with a small number of photons', by P. A. Morris et
     * al.
     *
     * @param inputMatrix the input matrix
     * @return a measure of the sparsity of the matrix
     */
    private double[] calculateCoefficients(final int[][] inputMatrix) {
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
                        } catch (ArrayIndexOutOfBoundsException ex) {
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

        // Return the sum of the absolute coefficients and the sum of the squared coefficients.
        // These can be used to calculate a measure of sparsity.
        return new double[]{sumAbsoluteCoefficients, sumSquaredAbsoluteCoefficients};
    }


    /**
     * Calculate the matrix sparsity from the given sum of absolute coefficients and sum of squared coefficients.
     *
     * @param sumAbsoluteCoefficients the sum of the absolute coefficients
     * @param sumSquaredAbsoluteCoefficients the sum of the squared coefficients
     * @return a measure for the matrix sparsity
     */
    private double calculateMatrixSparsity(final double sumAbsoluteCoefficients,
                                           final double sumSquaredAbsoluteCoefficients) {
        return (Math.pow(sumAbsoluteCoefficients, 2) / sumSquaredAbsoluteCoefficients);
    }

    /**
     * Calculate the matrix sparsity from the given sum of absolute coefficients and sum of squared coefficients.
     *
     * @param coefficients contains the sum of the absolute coefficients and the sum of the squared coefficients
     * @return a measure for the matrix sparsity
     */
    private double calculateMatrixSparsity(final double[] coefficients) {
        return this.calculateMatrixSparsity(coefficients[0], coefficients[1]);
    }

    /**
     * This method is used to try out a modification of a pixel, to test what the new matrix sparsity would be.
     * 
     * @param xCoordinate the x coordinate in the pixel matrix
     * @param yCoordinate the y coordinate in the pixel matrix
     * @param newColorValue the new color value for pixel (x, y)
     * @return the estimated new sparsity with this modification
     */
    public double tryModification(final int xCoordinate, final int yCoordinate, final int newColorValue) {
        if (xCoordinate > this.matrix.length || yCoordinate > this.matrix[0].length) {
            throw new ArrayIndexOutOfBoundsException("Your given coordinates ("
                    + xCoordinate + "," + yCoordinate + ") are outside the matrix.");
        }

        int[][] originalMatrixPart;
        int[][] modifiedMatrixPart;
        double[] originalMatrixPartCoefficients;
        double[] modifiedMatrixPartCoefficients;

        // Cut out the parts of the total matrix where the modification is actually happening
        originalMatrixPart = this.getDctPart(xCoordinate, yCoordinate);
        modifiedMatrixPart = this.getDctPart(xCoordinate, yCoordinate);
        modifiedMatrixPart[xCoordinate % this.dctBlockSize][yCoordinate % this.dctBlockSize] = newColorValue;

        // Calculate the coefficients for the parts with and without the modification
        originalMatrixPartCoefficients = this.calculateCoefficients(originalMatrixPart);
        modifiedMatrixPartCoefficients = this.calculateCoefficients(modifiedMatrixPart);

        // Estimate the new coefficients and sparsity based on the outcomes for the small matrices
        // save those values.
        this.temporaryCoefficients[0] = this.totalCoefficients[0]
                                        - originalMatrixPartCoefficients[0] 
                                        + modifiedMatrixPartCoefficients[0];
        this.temporaryCoefficients[1] = this.totalCoefficients[1]
                                        - originalMatrixPartCoefficients[1] 
                                        + modifiedMatrixPartCoefficients[1];
        this.temporarySparsity = this.calculateMatrixSparsity(this.temporaryCoefficients);

        return this.temporarySparsity;
    }

    /**
     * Get the part of the matrix around the given coordinates.
     * When calculating the matrix sparsity, the total matrix is devided into dctBlockSize * dctBlockSize squares.
     * This method returns the square where the given coordinates belong to.
     *
     * @param xCoordinate x coordinate within the dct part
     * @param yCoordinate y coordinate within the dct part
     * @return the dct part around the x and y coordinates
     */
    private int[][] getDctPart(final int xCoordinate, final int yCoordinate) {
        int xStart;
        int yStart;
        int[][] matrixPart;
        int i;
        int j;

        // get the start values of the dct block within the complete matrix
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

    /**
     * This method will be called if the last tested modification was good enough, and should be saved.
     * This method updates the variables because the image has been updated.
     */
    public void performModification() {
        this.totalCoefficients[0] = this.temporaryCoefficients[0];
        this.totalCoefficients[1] = this.temporaryCoefficients[1];
        this.totalSparsity = this.temporarySparsity;
    }
    
    /**
     * This method test can be used to check if the matrix sparsity so far has been estimated well.
     * It calculates the total matrix sparsity of the image, and compares it to the estimated sparsity.
     */
    public void testEstimatedSparsitySoFar() {
        double calculatedSparsity;
        calculatedSparsity = this.calculateMatrixSparsity(this.calculateCoefficients(this.matrix));
        System.out.println("*** Matrix Sparsity ***");
        System.out.println("Estimated so far: " + this.totalSparsity);
        System.out.println("Calculated value: " + calculatedSparsity);
        System.out.println("Difference: " + Math.abs(this.totalSparsity - calculatedSparsity));
    }

    /**
     * Get the total sparsity calculated so far.
     * 
     * @return the total sparsity
     */
    public double getTotalSparsity() {
        return totalSparsity;
    }

}
