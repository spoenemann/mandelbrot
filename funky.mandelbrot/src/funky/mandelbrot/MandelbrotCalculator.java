/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

/**
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCalculator {
    
    private double centerRe = -1.5;
    private double centerIm = 0.0;
    private double realWidth = 2.0;
    private int pixelWidth = 0;
    private int pixelHeight = 0;
    private int[][] buffer = new int[pixelWidth][pixelHeight];
    
    public void resize(final int width, final int height) {
        this.pixelWidth = width;
        this.pixelHeight = height;
        buffer = new int[pixelWidth][pixelHeight];
        recalculate();
    }
    
    private void recalculate() {
        
    }

}
