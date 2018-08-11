/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package io.github.spoenemann.mandelbrot;

import java.awt.Rectangle;

/**
 * Interface for listeners for reporting finished calculations.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public interface ICalculationListener {
    
    /**
     * The given area of the buffer has been calculated.
     * 
     * @param area area of the buffer that is ready to be painted
     * @param buffer buffer that contains calculations results
     * @param bufferWidth the total width of one line in the value buffer
     */
    void calculated(Rectangle area, int[] buffer, int bufferWidth);
}
