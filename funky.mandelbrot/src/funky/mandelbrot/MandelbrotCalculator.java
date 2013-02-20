/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCalculator {
    
    public static final int DEFAULT_ITERATION_LIMIT = 1000;
    public static final double DEFAULT_DIVERGENCE_THRESHOLD = 2.0;
    
    public interface CalculationListener {
        void pixelCalculated(int x, int y, int value);
    }
    
    private List<CalculationListener> listeners = new ArrayList<CalculationListener>();
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private int pixelWidth = 0;
    private int pixelHeight = 0;
    private int[][] buffer = new int[pixelWidth][pixelHeight];
    private int iterationLimit;
    private double threshold;
    private double centerRe = -1.5;
    private double centerIm = 0.0;
    private double realWidth = 2.0;
    private double imaginaryHeight = 2.0;
    
    public MandelbrotCalculator(int iterationLimit, double divergeceThreshold) {
        this.iterationLimit = iterationLimit;
        this.threshold = divergeceThreshold;
    }
    
    public MandelbrotCalculator() {
        this(DEFAULT_ITERATION_LIMIT, DEFAULT_DIVERGENCE_THRESHOLD);
    }
    
    public int[][] getBuffer() {
        return buffer;
    }
    
    public void addListener(CalculationListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(CalculationListener listener) {
        listeners.remove(listener);
    }
    
    public void resize(final int width, final int height) {
        this.pixelWidth = width;
        this.pixelHeight = height;
        buffer = new int[pixelWidth][pixelHeight];
        recalculate();
    }
    
    private void recalculate() {
        for (int x = 0; x < pixelWidth; x++) {
            for (int y = 0; y < pixelHeight; y++) {
                final int thex = x;
                final int they = y;
                executorService.submit(new Runnable() {
                    public void run() {
                        int value = calculate(thex, they);
                        synchronized (listeners) {
                            for (CalculationListener listener : listeners) {
                                listener.pixelCalculated(thex, they, value);
                            }
                        }
                    }
                });
            }
        }
    }
    
    private int calculate(int x, int y) {
        double maxAbsolute = threshold * threshold;
        double cre = ((double) x + 0.5) / pixelWidth * realWidth;
        double cim = ((double) y + 0.5) / pixelHeight * imaginaryHeight;
        
        double absolute;
        int i = 0;
        double re = 0;
        double im = 0;
        do {
            double nextre = re * re - im * im + cre;
            double nextim = 2 * re * im + cim;
            re = nextre;
            im = nextim;
            i++;
            absolute = re * re + im * im;
        } while (absolute <= maxAbsolute && i < iterationLimit);
        
        if (i < iterationLimit) {
            return i;
        } else {
            return Integer.MAX_VALUE;
        }
    }

}
