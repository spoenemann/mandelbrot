/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * Calculator of Mandelbrot fractals. Calculations are performed asynchronously and are
 * reported to listeners. The calculation results are transformed to color values.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCalculator implements Runnable {
    
    /**
     * The context data are shared among all instances of the calculator class.
     */
    public static class Context {
        /** list of calculation listeners. */
        final Collection<ICalculationListener> listeners = new ArrayList<ICalculationListener>();
        /** currently running calculation worker threads. */
        final Collection<MandelbrotCalculator> runningCalculators = new HashSet<MandelbrotCalculator>();
        /** the colorizer used to process calculation results. */
        IColorizer colorizer;
        /** real part of the complex start value for iterations. */
        double startRe = 0.0;
        /** imaginary part of the complex start value for iterations. */
        double startIm = 0.0;
        /** real part of the complex center point (horizontal). */
        double centerRe = -0.5;
        /** imaginary part of the complex center point (vertical). */
        double centerIm = 0.0;
        /** real part of the size of the viewed area (width). */
        double widthRe;
        /** imaginary part of the size of the viewed area (height). */
        double heightIm;
    }
    
    /** factor for the computed limit on the number of iterations. */
    private static final double ITERATION_FACTOR = 15.0;
    /** threshold for the absolute value beyond which diversion is detected. */
    private static final double DIVERGENCE_THRESHOLD = 2.0;
    /** minimal time in milliseconds between reports. */
    private static final long CALCULATION_REPORT_PERIOD = 100;
    /** initial sleep time before worker threads start their work. */
    private static final long INITIAL_SLEEP_TIME = 150;
    
    /** the context data used for calculations. */
    private final Context context;
    /** the buffer into which values are written. */
    private final int[] buffer;
    /** the width of the buffer in pixels. */
    private int bufferWidth;
    /** the height of the buffer in pixels. */
    private int bufferHeight;
    /** the area that shall be computed by this worker. */
    private final Rectangle area;
    /** if true, only buffer values that are zero are recalculated,
     *  otherwise all values are recalculated. */
    private final boolean onlyBlanks;
    /** whether the calculation process shall be aborted. */
    private boolean aborted = false;
    
    /**
     * Create a calculation worker that can be submitted to a thread pool.
     * 
     * @param context the context data
     * @param buffer the buffer into which values are written
     * @param bufferWidth the width of the buffer in pixels
     * @param bufferHeight the height of the buffer in pixels
     * @param area the area that shall be computed by this worker
     * @param onlyBlanks if true, only buffer values that are zero are recalculated,
     *      otherwise all values are recalculated
     */
    public MandelbrotCalculator(Context context, int[] buffer, int bufferWidth, int bufferHeight,
            Rectangle area, boolean onlyBlanks) {
        this.context = context;
        this.buffer = buffer;
        this.bufferWidth = bufferWidth;
        this.bufferHeight = bufferHeight;
        this.area = area;
        this.onlyBlanks = onlyBlanks;
    }
    
    /**
     * Abort this calculator.
     */
    public void abort() {
        aborted = true;
    }
    
    /**
     * Start the calculation.
     */
    public void run() {
        try {
            Thread.sleep(INITIAL_SLEEP_TIME);
            if (aborted) {
                return;
            }
            
            int iterationLimit = (int) (ITERATION_FACTOR * Math.log(bufferWidth / context.widthRe));
            
            int reportStart = area.y;
            long lastReport = System.currentTimeMillis();
            for (int y = area.y; y < area.y + area.height; y++) {
                for (int x = area.x; x < area.x + area.width; x++) {
                    int index = BufferManager.index(x, y, bufferWidth);
                    if (!onlyBlanks || isBlank(buffer[index])) {
                        
                        // calculate the current pixel and store it into the buffer
                        double value = calculate(x, y, iterationLimit);
                        buffer[index] = context.colorizer.color(value);
                    }
                    
                    if (aborted) {
                        return;
                    }
                }
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastReport >= CALCULATION_REPORT_PERIOD) {
                    // send a report to the viewer component so it can draw a portion of the fractal
                    Rectangle areaToReport = new Rectangle(area.x, reportStart,
                            area.width, y - reportStart + 1);
                    for (ICalculationListener listener : context.listeners) {
                        listener.calculated(areaToReport, buffer, bufferWidth);
                    }
                    
                    reportStart = y + 1;
                    lastReport = currentTime;
                }
            }
            
            if (reportStart < area.y + area.height) {
                Rectangle areaToReport = new Rectangle(area.x, reportStart,
                        area.width, area.y + area.height - reportStart);
                for (ICalculationListener listener : context.listeners) {
                    listener.calculated(areaToReport, buffer, bufferWidth);
                }
            }
        } catch (InterruptedException exception) {
            // terminate the worker thread
        } catch (RuntimeException exception) {
            exception.printStackTrace();
        } finally {
            synchronized (context.runningCalculators) {
                context.runningCalculators.remove(this);
            }
        }
    }
    
    /**
     * Determine whether the given pixel value is blank, that is it has probably not been
     * assigned a color yet.
     * 
     * @param value a pixel value
     * @return true if the value is interpreted as blank
     */
    private boolean isBlank(int value) {
        return (value & 0xffffff) == 0 || (value & 0xffffff) == 0xffffff;
    }
    
    private final double threshold = DIVERGENCE_THRESHOLD * DIVERGENCE_THRESHOLD;

    /**
     * Calculate the given pixel.
     * 
     * @param x horizontal coordinate in the viewed area
     * @param y vertical coordinate in the viewed area
     * @param iterationLimit limit on the number of iterations
     * @return the number of iterations after which the value exceeds the given threshold,
     *      or the negative iteration limit if the threshold was not reached
     */
    private double calculate(int x, int y, int iterationLimit) {
        double cre = context.centerRe + ((double) x / bufferWidth - 0.5) * context.widthRe;
        double cim = context.centerIm + ((double) y / bufferHeight - 0.5) * context.heightIm;
        
        double absolute;
        int i = 0;
        double re = context.startRe;
        double im = context.startIm;
        do {
            double nextre = re * re - im * im + cre;
            double nextim = 2 * re * im + cim;
            re = nextre;
            im = nextim;
            i++;
            absolute = re * re + im * im;
        } while (!aborted && absolute <= threshold && i < iterationLimit);
        
        if (aborted) {
            return 0;
        } else if (i < iterationLimit) {
            // the value is diverging
            return i + threshold / absolute;
        } else {
            // the value is not diverging
            return -absolute / threshold;
        }
    }
    
}
