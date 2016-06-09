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
 *
 * @author Lonneke Scheffer
 */
public class DctCalculator {

    private final int dctBlockSize;
    private int[][] matrix;
    private double[] totalCoefficients;
    private double totalSparsity;
    private double[] temporaryCoefficients;
    private double temporarySparsity;

    public DctCalculator(int dctBlockSize, int[][] matrix) {
        // check if power of 2
        if (!(((dctBlockSize & (dctBlockSize - 1)) == 0) && (dctBlockSize > 0))) {
            throw new ArithmeticException("Your given DCT block size (" + dctBlockSize + ") should be a power of 2!");
        }
        if (matrix.length < dctBlockSize || matrix[0].length < dctBlockSize) {
            throw new IndexOutOfBoundsException("Your input matrix is too small, it should be bigger than your DCT block size (" + dctBlockSize + ")!");
        }

        this.dctBlockSize = dctBlockSize;
        this.matrix = matrix;
        this.totalCoefficients = this.calculateCoefficients(matrix);
        this.totalSparsity = this.calculateMatrixSparsity(this.totalCoefficients);
        this.temporaryCoefficients = new double[]{0, 0};
    }

    public int getDctBlockSize() {
        return dctBlockSize;
    }

    public int[][] getMatrix() {
        return matrix;
    }

    public double getTotalSparsity() {
        return totalSparsity;
    }

    private double calculateMatrixSparsity(double sumAbsoluteCoefficients, double sumSquaredAbsoluteCoefficients) {
        return (Math.pow(sumAbsoluteCoefficients, 2) / sumSquaredAbsoluteCoefficients);
    }

    private double calculateMatrixSparsity(double[] coefficients) {
        return this.calculateMatrixSparsity(coefficients[0], coefficients[1]);
    }

    public void testEstimatedSparsitySoFar() {
        double calculatedSparsity;
        calculatedSparsity = this.calculateMatrixSparsity(this.calculateCoefficients(this.matrix));
        System.out.println("*** Matrix Sparsity ***");
        System.out.println("Estimated so far: " + this.totalSparsity);
        System.out.println("Calculated value: " + calculatedSparsity);
        System.out.println("Difference: " + Math.abs(this.totalSparsity - calculatedSparsity));
    }

    public double tryModification(int xCoordinate, int yCoordinate, int newColorValue) {
        if (xCoordinate > this.matrix.length || yCoordinate > this.matrix[0].length) {
            throw new ArrayIndexOutOfBoundsException("Your given coordinates (" + xCoordinate + "," + yCoordinate + ") are outside the matrix.");
        }

        int[][] originalMatrixPart;
        int[][] modifiedMatrixPart;
        double[] originalMatrixPartCoefficients;
        double[] modifiedMatrixPartCoefficients;

        originalMatrixPart = this.getDctPart(xCoordinate, yCoordinate);
        modifiedMatrixPart = this.getDctPart(xCoordinate, yCoordinate);
        modifiedMatrixPart[xCoordinate % this.dctBlockSize][yCoordinate % this.dctBlockSize] = newColorValue;

        originalMatrixPartCoefficients = this.calculateCoefficients(originalMatrixPart);
        modifiedMatrixPartCoefficients = this.calculateCoefficients(modifiedMatrixPart);

//        this.temporaryX = xCoordinate;
//        this.temporaryY = yCoordinate;
//        this.temporaryColor = newColorValue;
        this.temporaryCoefficients[0] = this.totalCoefficients[0] - originalMatrixPartCoefficients[0] + modifiedMatrixPartCoefficients[0];
        this.temporaryCoefficients[1] = this.totalCoefficients[1] - originalMatrixPartCoefficients[1] + modifiedMatrixPartCoefficients[1];
        this.temporarySparsity = this.calculateMatrixSparsity(this.temporaryCoefficients);

        return this.temporarySparsity;
    }

    public void performModification() {
//        this.matrix[this.temporaryX][this.temporaryY] = this.temporaryColor;
        this.totalCoefficients[0] = this.temporaryCoefficients[0];
        this.totalCoefficients[1] = this.temporaryCoefficients[1];
        this.totalSparsity = this.temporarySparsity;
    }

    private int[][] getDctPart(int xCoordinate, int yCoordinate) {
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
     * @param matrix the input matrix
     * @return a measure of the sparsity of the matrix
     */
    private double[] calculateCoefficients(int[][] inputMatrix) {
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

        // Square the sum of the absolute coefficients, and divide it by the sum of the squared absolute coefficients.
        // The outcome of this formula can be used as a measure of sparsity.
        return new double[]{sumAbsoluteCoefficients, sumSquaredAbsoluteCoefficients};
        //return (Math.pow(sumAbsoluteCoefficients, 2) / sumSquaredAbsoluteCoefficients);
    }

}
