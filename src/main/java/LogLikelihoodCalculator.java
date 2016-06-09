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

import org.apache.commons.math3.util.CombinatoricsUtils;

/**
 *
 * @author Lonneke Scheffer
 */
public final class LogLikelihoodCalculator {

    private int[][] originalMatrix;
    private int[][] modifiedMatrix;
    private double darkCountRate;
    private double totalLogLikelihood;
    private double temporaryLogLikelihood;
//    private int temporaryX;
//    private int temporaryY;
//    private int temporaryColor;

    public LogLikelihoodCalculator(int[][] inputMatrix, int[][] outputMatrix, double darkCountRate) {
        if (inputMatrix.length == outputMatrix.length && inputMatrix[0].length == outputMatrix[0].length) {
            this.originalMatrix = inputMatrix;
            this.modifiedMatrix = outputMatrix;
        } else {
            throw new ArrayIndexOutOfBoundsException("Your input and output matrices should be of the same size!");
        }
        this.darkCountRate = darkCountRate;
        this.totalLogLikelihood = this.calculateLogLikelihood(inputMatrix, outputMatrix);
    }

    public double tryModification(int xCoordinate, int yCoordinate, int newColorValue) {
        if (xCoordinate > this.originalMatrix.length || yCoordinate > this.originalMatrix[0].length) {
            throw new ArrayIndexOutOfBoundsException("Your given coordinates (" + xCoordinate + "," + yCoordinate + ") are outside the matrix.");
        }

//        this.temporaryX = xCoordinate;
//        this.temporaryY = yCoordinate;
//        this.temporaryColor = newColorValue;
        this.temporaryLogLikelihood = this.totalLogLikelihood
                - this.calculateLogLikelihood(this.originalMatrix[xCoordinate][yCoordinate], this.modifiedMatrix[xCoordinate][yCoordinate])
                + this.calculateLogLikelihood(this.originalMatrix[xCoordinate][yCoordinate], newColorValue);

        return this.temporaryLogLikelihood;
    }

    public double getTotalLogLikelihood() {
        return totalLogLikelihood;
    }

    public void performModification() {
//        this.modifiedMatrix[this.temporaryX][this.temporaryY] = this.temporaryColor;
        this.totalLogLikelihood = this.temporaryLogLikelihood;
    }

    public void testEstimatedLogLikelihoodSoFar() {
        double calculatedLogLikelihood;
        calculatedLogLikelihood = this.calculateLogLikelihood(this.originalMatrix, this.modifiedMatrix);
        System.out.println("*** Log Likelihood ***");
        System.out.println("Estimated so far: " + this.totalLogLikelihood);
        System.out.println("Calculated value: " + calculatedLogLikelihood);
        System.out.println("Difference: " + Math.abs(this.totalLogLikelihood - calculatedLogLikelihood));
    }

    /**
     * Calculates the log likelihood for the modified matrix given the original
     * matrix. As stated in 'Imaging with a small number of photons', by P. A.
     * Morris et al.
     *
     * @param originalMatrix part of the original image
     * @param modifiedMatrix part of the modified image
     * @return the log likelihood
     */
    private double calculateLogLikelihood(final int[][] originalMatrix, final int[][] modifiedMatrix) {
        double logLikelihood = 0;

        // The two matrices must be the same size
        if (originalMatrix.length != modifiedMatrix.length
                || originalMatrix[0].length != modifiedMatrix[0].length) {
            throw new IndexOutOfBoundsException("Your original matrix and modified matrix do not have the same size");
        }

        // The calculation for the log likelihood
        for (int i = 0; i < originalMatrix.length; i++) {
            for (int j = 0; j < originalMatrix[0].length; j++) {
                logLikelihood += this.calculateLogLikelihood(originalMatrix[i][j], modifiedMatrix[i][j]);
            }
        }

        return logLikelihood;
    }

    private double calculateLogLikelihood(final int originalPixelValue, final int modifiedPixelValue) {
        return ((originalPixelValue * Math.log(modifiedPixelValue + this.darkCountRate))
                - (modifiedPixelValue + this.darkCountRate)
                - CombinatoricsUtils.factorialLog(originalPixelValue));
    }

}
