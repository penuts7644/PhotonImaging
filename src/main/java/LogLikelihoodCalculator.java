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
 * LogLikelihoodCalculator
 *
 * This class can be used to track the log likelihood for an image that is being modified.
 * The total log likelihood is only calculated once, and when the image is modified, only
 * the log likelihood for the modified pixel needs to be recalculated. This makes the algorithm
 * a lot faster than calculating the whole log likelihood again.
 *
 * @author Lonneke Scheffer and Wout van Helvoirt
 */
public final class LogLikelihoodCalculator {

    /** The matrix containing the original pixel values. */
    private int[][] originalMatrix;
    /** The matrix containing the pixel values that is being modified. */
    private int[][] modifiedMatrix;
    /** The dark count rate per pixel for this camera. */
    private double darkCountRate;
    /** The total log likelihood for the image. */
    private double totalLogLikelihood;
    /** The temporary log likelihood for the modified image. */
    private double temporaryLogLikelihood;

    /**
     * Create a new LogLikelihoodCalculator.
     *
     * @param inputMatrix   The original image pixel values.
     * @param outputMatrix  The modified image pixel values.
     * @param darkCountRate The dark count rate for the used camera.
     */
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

    /**
     * Calculates the log likelihood for the modified matrix given the original
     * matrix. As stated in 'Imaging with a small number of photons', by P. A.
     * Morris et al.
     *
     * @param originalMatrix Part of the original image.
     * @param modifiedMatrix Part of the modified image.
     * @return double The log likelihood.
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

    /**
     * Calculates the log likelihood for a pixel,
     * given the original pixel color value and the modified pixel color value.
     *
     * @param originalPixelValue The original color value.
     * @param modifiedPixelValue The new color value.
     * @return double The log likelihood for this pixel.
     */
    private double calculateLogLikelihood(final int originalPixelValue, final int modifiedPixelValue) {
        return ((originalPixelValue * Math.log(modifiedPixelValue + this.darkCountRate))
                - (modifiedPixelValue + this.darkCountRate)
                - CombinatoricsUtils.factorialLog(originalPixelValue));
    }

    /**
     * This method is used to try out a modification of a pixel, to test what the log likelihood would be.
     *
     * @param xCoordinate   The x coordinate in the pixel matrix.
     * @param yCoordinate   The y coordinate in the pixel matrix.
     * @param newColorValue The new color value for pixel (x, y).
     * @return double The estimated new log likelihood with this modification.
     */
    public double tryModification(int xCoordinate, int yCoordinate, int newColorValue) {
        if (xCoordinate > this.originalMatrix.length || yCoordinate > this.originalMatrix[0].length) {
            throw new ArrayIndexOutOfBoundsException("Your given coordinates (" + xCoordinate + "," + yCoordinate
                    + ") are outside the matrix.");
        }

        // calculate the new log likelihood:
        // total log likelihood - log likelihood of unmodified pixel + log likelihood of modified pixel
        this.temporaryLogLikelihood = this.totalLogLikelihood
                - this.calculateLogLikelihood(this.originalMatrix[xCoordinate][yCoordinate],
                                              this.modifiedMatrix[xCoordinate][yCoordinate])
                + this.calculateLogLikelihood(this.originalMatrix[xCoordinate][yCoordinate],
                                              newColorValue);

        return this.temporaryLogLikelihood;
    }

    /**
     * This method will be called if the last tested modification was good enough, and should be saved.
     * This method updates the log likelihood because the image has been updated.
     */
    public void performModification() {
        this.totalLogLikelihood = this.temporaryLogLikelihood;
    }

     /**
     * This method test can be used to check if the log likelihood so far has been estimated well.
     * It calculates the total log likelihood of the image, and compares it to the estimated log likelihood.
     */
    public void testEstimatedLogLikelihoodSoFar() {
        double calculatedLogLikelihood;
        calculatedLogLikelihood = this.calculateLogLikelihood(this.originalMatrix, this.modifiedMatrix);
        System.out.println("*** Log Likelihood ***");
        System.out.println("Estimated so far: " + this.totalLogLikelihood);
        System.out.println("Calculated value: " + calculatedLogLikelihood);
        System.out.println("Difference: " + Math.abs(this.totalLogLikelihood - calculatedLogLikelihood));
    }

    /**
     * Get the total log likelihood calculated so far.
     *
     * @return double The total log likelihood.
     */
    public double getTotalLogLikelihood() {
        return totalLogLikelihood;
    }

}
