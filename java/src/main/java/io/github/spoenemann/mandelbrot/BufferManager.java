/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package io.github.spoenemann.mandelbrot;

import java.awt.Rectangle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The buffer manager computes immediate reactions to navigation in the complex area and delegates
 * further calculations to the {@link MandelbrotCalculator}.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public class BufferManager {
    
    /** initial width of the viewed fractal. */
    private static final double START_WIDTH = 4.0;
    /** minimal number of calculation worker threads. */
    private static final int MIN_WORKER_THREADS = 2;
    /** approximate number of graphics updates per second. */
    private static final int FRAMES_PER_SEC = 20;
    
    /**
     * Determine an index into a buffer array using the given buffer width.
     * 
     * @param x the x coordinate
     * @param y the y coordinate
     * @param bufferWidth the total width of one line in the buffer
     * @return an index
     */
    public static int index(int x, int y, int bufferWidth) {
        return x + y * bufferWidth;
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
    
    /** the shared context data. */
    private final MandelbrotCalculator.Context context = new MandelbrotCalculator.Context();
    /** executor service for running worker threads. */
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    /** the currently used pixel buffer. */
    private Pixel[][] pixels;
    /** the secondary pixel buffer used for copying. */
    private Pixel[][] secondaryPixels;
    /** the currently used ARGB value buffer. */
    private int[] valueBuffer = new int[0];
    /** the secondary ARGB value buffer used for copying. */
    private int[] secondaryValueBuffer;
    /** the width of the buffers in pixels. */
    private int pixelWidth;
    /** the height of the buffers in pixels. */
    private int pixelHeight;

    /**
     * Shut down the calculator by terminating all worker threads.
     */
    public void shutdown() {
        abortCalculations();
        executorService.shutdownNow();
    }

    /**
     * Returns the currently active ARGB value buffer for calculation results.
     * 
     * @return the active ARGB value buffer
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
        synchronized (context.listeners) {
            context.listeners.add(listener);
        }
    }
    
    /**
     * Remove a listener to calculation results.
     * 
     * @param listener the listener to remove
     */
    public void removeListener(ICalculationListener listener) {
        synchronized (context.listeners) {
            context.listeners.remove(listener);
        }
    }
    
    /**
     * Set the colorizer for processing calculation results.
     * 
     * @param colorizer a colorizer implementation
     */
    public void setColorizer(IColorizer colorizer) {
        context.colorizer = colorizer;
    }
    
    /**
     * Set the complex start value for iterations. The start value determines the first element
     * of the computed sequence of complex numbers. The default start value (0, 0) generates
     * the Mandelbrot set.
     * 
     * @param startRe real part of the start value
     * @param startIm imaginary part of the start value
     */
    public void setStart(double startRe, double startIm) {
        context.startRe = startRe;
        context.startIm = startIm;
    }
    
    /**
     * Set the factor used to determine the limit on the number of iterations. This limit is
     * determined dynamically depending on the current zoom level. The higher the factor,
     * the more iterations are done for non-diverging pixels, hence calculations take more time,
     * but the resulting images are more accurate.
     * 
     * @param iterationFactor factor for the computed limit on the number of iterations
     */
    public void setIterationFactor(double iterationFactor) {
        context.iterationFactor = iterationFactor;
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
        Pixel[][] oldPixels = pixels;
        Pixel[][] newPixels = new Pixel[newWidth][newHeight];
        
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
        for (int y = 0; y < copyHeight; y++) {
            for (int x = 0; x < copyWidth; x++) {
                newBuffer[index(newxStart + x, newyStart + y, newWidth)]
                        = oldBuffer[index(oldxStart + x, oldyStart + y, oldWidth)];
                newPixels[newxStart + x][newyStart + y] = oldPixels[oldxStart + x][oldyStart + y];
            }
        }

        // update parameters for fractal calculation
        if (oldWidth == 0) {
            context.widthRe = START_WIDTH;
        } else {
            context.widthRe = context.widthRe * newWidth / oldWidth;
        }
        if (oldHeight == 0) {
            context.heightIm = START_WIDTH * newHeight / newWidth;
        } else {
            context.heightIm = context.heightIm * newHeight / oldHeight;
        }
        pixelWidth = newWidth;
        pixelHeight = newHeight;
        secondaryValueBuffer = null;
        valueBuffer = newBuffer;
        secondaryPixels = null;
        pixels = newPixels;
        
        // trigger a recalculation
        recalculate(new Rectangle(newWidth, newHeight));
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
        Pixel[][] oldPixels = pixels;
        Pixel[][] newPixels = secondaryPixels;
        if (newPixels == null) {
        	newPixels = new Pixel[pixelWidth][pixelHeight];
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
        for (int y = 0; y < pixelHeight; y++) {
            for (int x = 0; x < pixelWidth; x++) {
                if (x >= newxStart && x < newxStart + copyWidth
                        && y >= newyStart && y < newyStart + copyHeight) {
                    int oldx = oldxStart + x - newxStart;
                    int oldy = oldyStart + y - newyStart; 
                    newBuffer[index(x, y)] = oldBuffer[index(oldx, oldy)];
                    newPixels[x][y] = oldPixels[oldx][oldy];
                } else {
                    newBuffer[index(x, y)] = 0xffffffff;
                    newPixels[x][y] = null;
                }
            }
        }

        // update parameters for fractal calculation
        context.centerRe -= deltax * context.widthRe / pixelWidth;
        context.centerIm -= deltay * context.heightIm / pixelHeight;
        valueBuffer = newBuffer;
        secondaryValueBuffer = oldBuffer;
        pixels = newPixels;
        secondaryPixels = oldPixels;

        // trigger a recalculation
        recalculate(new Rectangle(pixelWidth, pixelHeight));
    }
    
    /**
     * Determine a smooth color from double precision coordinates.
     * 
     * TODO use {@link Pixel} and its subpixels to get a more precise color value
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
            double factor = (double) required / contribs;
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
        for (int y = 0; y < pixelHeight; y++) {
            for (int x = 0; x < pixelWidth; x++) {
                double sourcex = focusx + factor * (x - focusx);
                double sourcey = focusy + factor * (y - focusy);
                newBuffer[index(x, y)] = smoothColor(sourcex, sourcey);
                pixels[x][y] = null;
            }
        }

        // update parameters for fractal calculation
        double oldCenterx = (double) pixelWidth / 2;
        context.centerRe += context.widthRe * (1 - factor) * (focusx - oldCenterx) / pixelWidth;
        double oldCentery = (double) pixelHeight / 2;
        context.centerIm += context.heightIm * (1 - factor) * (focusy - oldCentery) / pixelHeight;
        
        context.widthRe *= factor;
        context.heightIm *= factor;
        valueBuffer = newBuffer;
        secondaryValueBuffer = oldBuffer;

        // trigger a recalculation
        recalculate(new Rectangle(pixelWidth, pixelHeight));
    }
    
    /**
     * Abort all running calculations.
     */
    private void abortCalculations() {
        synchronized (context.runningCalculators) {
            for (MandelbrotCalculator calculator : context.runningCalculators) {
                calculator.abort();
            }
        }
    }
    
    /**
     * Perform a recalculation using a number of worker threads.
     * 
     * @param area the area that shall be computed
     */
    private void recalculate(Rectangle area) {
        int cores = Runtime.getRuntime().availableProcessors();
        int workerThreads = Math.max(cores, MIN_WORKER_THREADS);
        long reportPeriod = workerThreads * (1000 / FRAMES_PER_SEC);
        synchronized (context.runningCalculators) {
            int height = area.height / workerThreads;
            int y = area.y;
            for (int i = 0; i < workerThreads; i++) {
                if (i == workerThreads - 1) {
                    height = area.height - (y - area.y);
                }
                MandelbrotCalculator calculator = new MandelbrotCalculator(context, pixels, valueBuffer,
                        pixelWidth, pixelHeight, new Rectangle(area.x, y, area.width, height),
                        reportPeriod);
                y += height;
                context.runningCalculators.add(calculator);
                executorService.submit(calculator);
            }
        }
    }

}
