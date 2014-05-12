/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Calculator class that generates data for drawing Mandelbrot fractals. Calculations are
 * performed asynchronously and are reported to listeners. The calculation results are transformed
 * to color values
 * 
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCalculator {
    
    /** factor for the computed limit on the number of iterations. */
    private static final double ITERATION_FACTOR = 15.0;
    /** threshold for the absolute value beyond which diversion is detected. */
    private static final double DIVERGENCE_THRESHOLD = 2.0;
    /** initial width of the viewed fractal. */
    private static final double START_WIDTH = 4.0;
    /** minimal time in milliseconds between reports. */
    private static final long CALCULATION_REPORT_PERIOD = 40;
    
    /**
     * Interface for listeners for reporting finished calculations.
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
    
    /**
     * Determine an index into a buffer array using the given buffer width.
     * 
     * @param x the x coordinate
     * @param y the y coordinate
     * @param pixelWidth the total width of one line in the buffer
     * @return an index
     */
    private static int index(int x, int y, int pixelWidth) {
        return x + y * pixelWidth;
    }
    
    /**
     * Determine an index into a buffer array using the default buffer width.
     * 
     * @param x the x coordinate
     * @param y the y coordinate
     * @return an index
     */
    private int index(int x, int y) {
        return x + y * pixelWidth;
    }
    
    /** list of calculation listeners. */
    private List<ICalculationListener> listeners = new ArrayList<ICalculationListener>();
    /** currently running calculation worker threads. */
    private Set<CalculationWorker> runningCalculators = new HashSet<CalculationWorker>();
    /** executor service for running worker threads. */
    private ExecutorService executorService = Executors.newCachedThreadPool();
    /** the colorizer used to process calculation results (default is black & white). */
    private IColorizer colorizer = x -> x > 0 ? 0xffffffff : 0xff000000;
    /** the currently used buffer. */
    private int[] valueBuffer = new int[0];
    /** the secondary buffer used for copying values. */
    private int[] secondaryValueBuffer;
    /** the width of the buffers in pixels. */
    private int pixelWidth;
    /** the height of the buffers in pixels. */
    private int pixelHeight;
    /** real part of the complex start value for iterations. */
    private double startRe = 0.0;
    /** imaginary part of the complex start value for iterations. */
    private double startIm = 0.0;
    /** real part of the complex center point (horizontal). */
    private double centerRe = -0.5;
    /** imaginary part of the complex center point (vertical). */
    private double centerIm = 0.0;
    /** real part of the size of the viewed area (width). */
    private double widthRe;
    /** imaginary part of the size of the viewed area (height). */
    private double heightIm;

    /**
     * Shut down the calculator by terminating all worker threads.
     */
    public void shutdown() {
        abortCalculations();
        executorService.shutdownNow();
    }

    /**
     * Returns the currently active buffer for calculation results.
     * 
     * @return the active buffer
     */
    public int[] getBuffer() {
        return valueBuffer;
    }
    
    /**
     * Add a listener to calculation results.
     * 
     * @param listener the listener to add
     */
    public void addListener(ICalculationListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a listener to calculation results.
     * 
     * @param listener the listener to remove
     */
    public void removeListener(ICalculationListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    /**
     * Set the colorizer for processing calculation results.
     * 
     * @param colorizer a colorizer implementation
     */
    public void setColorizer(IColorizer colorizer) {
        this.colorizer = colorizer;
    }
    
    /**
     * Set the complex start value for iterations. The start value determines the first element
     * of the computed sequence of complex numbers.
     * 
     * @param startRe real part of the start value
     * @param startIm imaginary part of the start value
     */
    public void setStart(double startRe, double startIm) {
        this.startRe = startRe;
        this.startIm = startIm;
    }
    
    /**
     * Change the size of the viewed area and update buffers.
     * 
     * @param newWidth new width of the viewed area, in pixels
     * @param newHeight new height of the viewed area, in pixels
     */
    public void resize(int newWidth, int newHeight) {
        assert newWidth >= 0 && newHeight >= 0;
        int oldWidth = this.pixelWidth;
        int oldHeight = this.pixelHeight;
        if (newWidth == oldWidth && newHeight == oldHeight) {
            return;
        }
        abortCalculations();
        
        int[] oldBuffer = valueBuffer;
        int[] newBuffer = new int[newWidth * newHeight];
        
        // prepare values for copying the content that is still visible after resizing
        int copyWidth = Math.min(oldWidth, newWidth);
        int copyHeight = Math.min(oldHeight, newHeight);
        int oldxStart = 0;
        int newxStart = 0;
        if (newWidth > oldWidth) {
            newxStart = (newWidth - oldWidth) / 2;
        } else if (newWidth < oldWidth) {
            oldxStart = (oldWidth - newWidth) / 2;
        }
        int oldyStart = 0;
        int newyStart = 0;
        if (newHeight > oldHeight) {
            newyStart = (newHeight - oldHeight) / 2;
        } else if (newHeight < oldHeight) {
            oldyStart = (oldHeight - newHeight) / 2;
        }
        
        // copy content from the old to the new buffer
        for (int x = 0; x < copyWidth; x++) {
            for (int y = 0; y < copyHeight; y++) {
                newBuffer[index(newxStart + x, newyStart + y, newWidth)]
                        = oldBuffer[index(oldxStart + x, oldyStart + y, oldWidth)];
            }
        }

        // update parameters for fractal calculation
        if (oldWidth == 0) {
            widthRe = START_WIDTH;
        } else {
            widthRe = widthRe * newWidth / oldWidth;
        }
        if (oldHeight == 0) {
            heightIm = START_WIDTH * newHeight / newWidth;
        } else {
            heightIm = heightIm * newHeight / oldHeight;
        }
        pixelWidth = newWidth;
        pixelHeight = newHeight;
        secondaryValueBuffer = null;
        valueBuffer = newBuffer;
        
        // trigger a recalculation
        recalculate(new Rectangle(newWidth, newHeight), true);
    }
    
    /**
     * Translate the viewed area by the given amount.
     * 
     * @param deltax horizontal translation delta
     * @param deltay vertical translation delta
     */
    public void translate(int deltax, int deltay) {
        if (deltax == 0 && deltay == 0) {
            return;
        }
        abortCalculations();
        
        int[] oldBuffer = valueBuffer;
        int[] newBuffer = secondaryValueBuffer;
        if (newBuffer == null) {
            newBuffer = new int[pixelWidth * pixelHeight];
        }
        
        // prepare values for copying the content that is still visible after translation
        int copyWidth = Math.max(0, pixelWidth - Math.abs(deltax));
        int copyHeight = Math.max(0, pixelHeight - Math.abs(deltay));
        int oldxStart = 0;
        int newxStart = 0;
        if (deltax > 0) {
            newxStart = deltax;
        } else if (deltax < 0) {
            oldxStart = -deltax;
        }
        int oldyStart = 0;
        int newyStart = 0;
        if (deltay > 0) {
            newyStart = deltay;
        } else if (deltay < 0) {
            oldyStart = -deltay;
        }
        
        // copy content from the old to the new buffer
        for (int x = 0; x < pixelWidth; x++) {
            for (int y = 0; y < pixelHeight; y++) {
                if (x >= newxStart && x < newxStart + copyWidth
                        && y >= newyStart && y < newyStart + copyHeight) {
                    newBuffer[index(x, y)] = oldBuffer[index(oldxStart + x - newxStart,
                            oldyStart + y - newyStart)];
                } else {
                    newBuffer[index(x, y)] = 0xffffffff;
                }
            }
        }

        // update parameters for fractal calculation
        centerRe -= deltax * widthRe / pixelWidth;
        centerIm -= deltay * heightIm / pixelHeight;
        valueBuffer = newBuffer;
        secondaryValueBuffer = oldBuffer;

        // trigger a recalculation
        recalculate(new Rectangle(pixelWidth, pixelHeight), true);
    }
    
    /**
     * Determine a smooth color from double precision coordinates.
     * 
     * @param x the x coordinate
     * @param y the y coordinate
     * @return a smooth color
     */
    private int smoothColor(double x, double y) {
        double r = 0;
        double g = 0;
        double b = 0;
        int lox = (int) Math.floor(x);
        int hix = (int) Math.ceil(x);
        int loy = (int) Math.floor(y);
        int hiy = (int) Math.ceil(y);
        int contribs = 0;
        if (lox >= 0 && lox < pixelWidth) {
            double xfactor = 1 + lox - x;
            if (loy >= 0 && loy < pixelHeight) {
                double factor = xfactor * (1 + loy - y);
                int value = valueBuffer[index(lox, loy)];
                r += factor * ((value >> 16) & 0xff);
                g += factor * ((value >> 8) & 0xff);
                b += factor * (value & 0xff);
                contribs++;
            }
            if (hiy != loy && hiy >= 0 && hiy < pixelHeight) {
                double factor = xfactor * (y - loy);
                int value = valueBuffer[index(lox, hiy)];
                r += factor * ((value >> 16) & 0xff);
                g += factor * ((value >> 8) & 0xff);
                b += factor * (value & 0xff);
                contribs++;
            }
        }
        if (hix != lox && hix >= 0 && hix < pixelWidth) {
            double xfactor = x - lox;
            if (loy >= 0 && loy < pixelHeight) {
                double factor = xfactor * (1 + loy - y);
                int value = valueBuffer[index(hix, loy)];
                r += factor * ((value >> 16) & 0xff);
                g += factor * ((value >> 8) & 0xff);
                b += factor * (value & 0xff);
                contribs++;
            }
            if (hiy != loy && hiy >= 0 && hiy < pixelHeight) {
                double factor = xfactor * (y - loy);
                int value = valueBuffer[index(hix, hiy)];
                r += factor * ((value >> 16) & 0xff);
                g += factor * ((value >> 8) & 0xff);
                b += factor * (value & 0xff);
                contribs++;
            }
        }
        if (contribs == 0) {
            return 0xffffffff;
        }
        int required = (hix - lox + 1) * (hiy - loy + 1);
        if (contribs < required) {
            double factor = required / contribs;
            r *= factor;
            g *= factor;
            b *= factor;
        }
        return 0xff000000 | ((int) r << 16) | ((int) g << 8) | (int) b;
    }
    
    /**
     * Zoom the visible area to view a smaller or larger part.
     * 
     * @param factor zoom factor: < 1 for zooming in (shrink viewed area),
     *      > 1 for zooming out (enlarge viewed area)
     * @param focusx
     * @param focusy
     */
    public void zoom(double factor, int focusx, int focusy) {
        assert factor > 0;
        if (factor == 1) {
            return;
        }
        abortCalculations();
        
        int[] oldBuffer = valueBuffer;
        int[] newBuffer = secondaryValueBuffer;
        if (newBuffer == null) {
            newBuffer = new int[pixelWidth * pixelHeight];
        }

        // scale the view according to the zoom factor
        for (int x = 0; x < pixelWidth; x++) {
            for (int y = 0; y < pixelHeight; y++) {
                double sourcex = focusx + factor * (x - focusx);
                double sourcey = focusy + factor * (y - focusy);
                newBuffer[index(x, y)] = smoothColor(sourcex, sourcey);
            }
        }

        // update parameters for fractal calculation
        double oldCenterx = (double) pixelWidth / 2;
        centerRe += widthRe * (1 - factor) * (focusx - oldCenterx) / pixelWidth;
        double oldCentery = (double) pixelHeight / 2;
        centerIm += heightIm * (1 - factor) * (focusy - oldCentery) / pixelHeight;
        
        widthRe *= factor;
        heightIm *= factor;
        valueBuffer = newBuffer;
        secondaryValueBuffer = oldBuffer;

        // trigger a recalculation
        recalculate(new Rectangle(pixelWidth, pixelHeight), false);
    }
    
    /**
     * Abort all running calculations.
     */
    private void abortCalculations() {
        synchronized (runningCalculators) {
            for (CalculationWorker pointCalculator : runningCalculators) {
                pointCalculator.aborted = true;
            }
        }
    }
    
    /**
     * Perform a recalculation using a worker thread.
     * 
     * @param buffer the buffer to use for calculation
     * @param area the area that shall be computed
     * @param onlyBlanks if true, only buffer values that are zero are recalculated,
     *      otherwise all values are recalculated
     */
    private void recalculate(Rectangle area, boolean onlyBlanks) {
        CalculationWorker pointCalculator = new CalculationWorker(valueBuffer, pixelWidth,
                area, onlyBlanks);
        synchronized (runningCalculators) {
            runningCalculators.add(pointCalculator);
        }
        executorService.submit(pointCalculator);
    }
    
    /**
     * Calculation worker class.
     */
    private class CalculationWorker implements Runnable {
        
        /** the buffer into which values are written. */
        private int[] buffer;
        /** the total width of one line in the value buffer. */
        private int bufferWidth;
        /** the area that shall be computed by this worker. */
        private Rectangle area;
        /** if true, only buffer values that are zero are recalculated,
         *  otherwise all values are recalculated. */
        private boolean onlyBlanks;
        /** whether the calculation process shall be aborted. */
        private boolean aborted = false;
        
        /**
         * Create a calculation worker thread.
         * 
         * @param buffer the buffer into which values are written
         * @param bufferWidth the total width of one line in the value buffer
         * @param area the area that shall be computed by this worker
         * @param onlyBlanks if true, only buffer values that are zero are recalculated,
         *      otherwise all values are recalculated
         */
        CalculationWorker(int[] buffer, int bufferWidth, Rectangle area, boolean onlyBlanks) {
            this.buffer = buffer;
            this.bufferWidth = bufferWidth;
            this.area = area;
            this.onlyBlanks = onlyBlanks;
        }
        
        /**
         * Start the calculation.
         */
        public void run() {
            try {
                int iterationLimit = (int) (ITERATION_FACTOR * Math.log(pixelWidth / widthRe));
                double threshold = DIVERGENCE_THRESHOLD * DIVERGENCE_THRESHOLD;
                
                int reportStart = 0;
                long lastReport = System.currentTimeMillis();
                for (int x = area.x; x < area.x + area.width; x++) {
                    for (int y = area.y; y < area.y + area.height; y++) {
                        int index = index(x, y, bufferWidth);
                        if (!onlyBlanks || (buffer[index] & 0xffffff) == 0
                                || (buffer[index] & 0xffffff) == 0xffffff) {
                            
                            // calculate the current pixel and store it into the buffer
                            double value = calculate(x, y, iterationLimit, threshold);
                            buffer[index] = colorizer.color(value);
                        }
                        
                        if (aborted) {
                            return;
                        }
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastReport >= CALCULATION_REPORT_PERIOD) {
                        // send a report to the viewer component so it can draw a portion of the fractal
                        Rectangle areaToReport = new Rectangle(reportStart, area.y,
                                x - reportStart + 1, area.height);
                        synchronized (listeners) {
                            for (ICalculationListener listener : listeners) {
                                listener.calculated(areaToReport, buffer, bufferWidth);
                            }
                        }
                        
                        reportStart = x + 1;
                        lastReport = currentTime;
                    }
                }
                
                if (reportStart < area.x + area.width) {
                    Rectangle areaToReport = new Rectangle(reportStart, area.y,
                            area.x + area.width - reportStart, area.height);
                    synchronized (listeners) {
                        for (ICalculationListener listener : listeners) {
                            listener.calculated(areaToReport, buffer, bufferWidth);
                        }
                    }
                }
            } finally {
                synchronized (runningCalculators) {
                    runningCalculators.remove(this);
                }
            }
        }
        
        /**
         * Calculate the given pixel.
         * 
         * @param x horizontal coordinate in the viewed area
         * @param y vertical coordinate in the viewed area
         * @param iterationLimit limit on the number of iterations
         * @param threshold square of the absolute value above which points diverge
         * @return the number of iterations after which the value exceeds the given threshold,
         *      or the negative iteration limit if the threshold was not reached
         */
        private double calculate(int x, int y, int iterationLimit, double threshold) {
            double cre = centerRe + ((double) x / pixelWidth - 0.5) * widthRe;
            double cim = centerIm + ((double) y / pixelHeight - 0.5) * heightIm;
            
            double absolute;
            int i = 0;
            double re = startRe;
            double im = startIm;
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

}
