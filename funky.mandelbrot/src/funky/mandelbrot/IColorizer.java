/**
 * Funky Mandelbrot Application (c) 2014 by Miro
 */
package funky.mandelbrot;

/**
 * Interface for functions that map calculation results to colors.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public interface IColorizer {
    
    /**
     * Determine a color value for the given calculation result. Positive results indicate that
     * the corresponding complex sequence is unbounded, and are roughly proportional to the number
     * of iterations after which the predefined threshold is exceeded. Positive values are below
     * the (arbitrary) upper bound on the number of iterations. If the sequence is assumed to
     * be bounded, the result is between 0 and -1. In this case it is derived from the last
     * computed sequence value.
     * 
     * <p>Color values must conform to the ARGB format used by {@link javafx.scene.image.PixelWriter}.
     * The color components are stored in order, from MSb to LSb: alpha, red, green, blue.</p>
     * 
     * @param calcResult a calculation result
     * @return a corresponding color value
     */
    int color(double calcResult);

}