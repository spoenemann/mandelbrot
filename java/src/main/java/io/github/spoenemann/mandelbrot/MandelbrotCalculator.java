/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package io.github.spoenemann.mandelbrot;

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
        /** factor for the computed limit on the number of iterations. */
        double iterationFactor = 15.0;
    }

    /** recursion limit for oversampling. */
    private static final int OVERSAMPLING_LIMIT = 2;
    /** threshold for the absolute value beyond which diversion is detected. */
    private static final double DIVERGENCE_THRESHOLD = 2.0;
    /** initial sleep time before worker threads start their work. */
    private static final long INITIAL_SLEEP_TIME = 100;
    
    /** the context data used for calculations. */
    private final Context context;
    /** the pixels with computed color values. */
    private final Pixel[][] pixels;
    /** the buffer into which ARGB values are written. */
    private final int[] buffer;
    /** the width of the buffer in pixels. */
    private int bufferWidth;
    /** the height of the buffer in pixels. */
    private int bufferHeight;
    /** the area that shall be computed by this worker. */
    private final Rectangle area;
    /** whether the calculation process shall be aborted. */
    private boolean aborted = false;
    /** minimal time in milliseconds between reports. */
    private final long reportPeriod;
    
    /**
     * Create a calculation worker that can be submitted to a thread pool.
     * 
     * @param context the context data
     * @param pixels the pixel array that is to be filled
     * @param buffer the buffer into which values are written
     * @param bufferWidth the width of the buffer in pixels
     * @param bufferHeight the height of the buffer in pixels
     * @param area the area that shall be computed by this worker
     * @param reportPeriod minimal time in milliseconds between reports sent to the listeners
     */
    public MandelbrotCalculator(Context context, Pixel[][] pixels, int[] buffer, int bufferWidth,
    		int bufferHeight, Rectangle area, long reportPeriod) {
        this.context = context;
        this.pixels = pixels;
        this.buffer = buffer;
        this.bufferWidth = bufferWidth;
        this.bufferHeight = bufferHeight;
        this.area = area;
        this.reportPeriod = reportPeriod;
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
            
            // the number of iterations is limited dynamically depending on the zoom factor
            int iterationLimit = (int) (context.iterationFactor
                    * Math.log(bufferWidth / context.widthRe));
            
            for (int level = 0; level < OVERSAMPLING_LIMIT; level++) {
	            int reportStart = area.y;
	            long lastReport = System.currentTimeMillis();
	            for (int y = area.y; y < area.y + area.height; y++) {
	                for (int x = area.x; x < area.x + area.width; x++) {
                        computePixel(x, y, level, iterationLimit);
	                    
	                    if (aborted) {
	                        return;
	                    }
	                }
	                
	                long currentTime = System.currentTimeMillis();
	                if (currentTime - lastReport >= reportPeriod) {
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
            }
        } catch (InterruptedException exception) {
            // terminate the worker thread
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        } finally {
            synchronized (context.runningCalculators) {
                context.runningCalculators.remove(this);
            }
        }
    }
    
    /**
     * Compute the color of the pixel with given coordinates and given oversampling level.
     * 
     * TODO check whether is's really necessary to compute the given oversampling level
     *      (only the border of the set is interesting)
     * 
     * @param x horizontal coordinate in the viewed area
     * @param y vertical coordinate in the viewed area
     * @param level the oversampling level
     * @param iterationLimit limit on the number of iterations
     * @return color result to be stored in a buffer
     */
    private void computePixel(int x, int y, int level, int iterationLimit) {
    	Pixel pixel = pixels[x][y];
        boolean[] colorChanged = new boolean[1];
        if (level == 0) {
        	if (pixel == null) {
        		colorChanged[0] = true;
        		double value = computeValue(x, y, iterationLimit);
        		pixel = context.colorizer.color(value);
        		pixels[x][y] = pixel;
        	}
        } else {
        	pixel.addSubpixels(level, (dx, dy) -> {
        		colorChanged[0] = true;
        		double value = computeValue(x + dx, y + dy, iterationLimit);
        		return context.colorizer.color(value);
        	});
        }
        if (colorChanged[0]) {
            int index = BufferManager.index(x, y, bufferWidth);
            buffer[index] = pixel.computeAverageARGB();
        }
    }
    
    private static final double THRESHOLD_SQR = DIVERGENCE_THRESHOLD * DIVERGENCE_THRESHOLD;

    /**
     * Compute a value at the complex position that corresponds to the given pixel coordinates.
     * 
     * @param x horizontal coordinate in the viewed area
     * @param y vertical coordinate in the viewed area
     * @param iterationLimit limit on the number of iterations
     * @return the number of iterations after which the value exceeds the given threshold,
     *      or the negative iteration limit if the threshold was not reached
     */
    private double computeValue(double x, double y, int iterationLimit) {
        double cre = context.centerRe + (x / bufferWidth - 0.5) * context.widthRe;
        double cim = context.centerIm + (y / bufferHeight - 0.5) * context.heightIm;
        
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
        } while (!aborted && absolute <= THRESHOLD_SQR && i < iterationLimit);
        
        if (aborted) {
            return 0;
        } else if (i < iterationLimit) {
            // the value is diverging
            return i + THRESHOLD_SQR / absolute;
        } else {
            // the value is not diverging
            return -absolute / THRESHOLD_SQR;
        }
    }
    
}
