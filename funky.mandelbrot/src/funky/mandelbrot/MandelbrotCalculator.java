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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCalculator {
    
    public static final double ITERATION_FACTOR = 15.0;
    public static final double DIVERGENCE_THRESHOLD = 2.0;
    
    private static final double START_WIDTH = 4.0;
    private static final double DIVERGE_FACTOR = 0.4;
    private static final double NON_DIVERGE_FACTOR = 3.0;
    private static final long CALCULATION_REPORT_PERIOD = 40;
    
    public interface CalculationListener {
        void calculated(Rectangle area, double[][] buffer);
    }
    
    private List<CalculationListener> listeners = new ArrayList<CalculationListener>();
    private Set<CalculationWorker> runningCalculators = new HashSet<CalculationWorker>();
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private double[][] valueBuffer = new double[0][0];
    private double[][] secondaryValueBuffer;
    private int pixelWidth;
    private int pixelHeight;
    private double centerRe;
    private double centerIm;
    private double widthRe;
    private double heightIm;
    
    public void shutdown() {
        executorService.shutdownNow();
    }
    
    public double[][] getBuffer() {
        return valueBuffer;
    }
    
    public void addListener(CalculationListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(CalculationListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    public void resize(final int newWidth, final int newHeight) {
        assert newWidth >= 0 && newHeight >= 0;
        int oldWidth = this.pixelWidth;
        int oldHeight = this.pixelHeight;
        if (newWidth == oldWidth && newHeight == oldHeight) {
            return;
        }
        abortCalculations();
        
        double[][] oldBuffer = valueBuffer;
        double[][] newBuffer = new double[newWidth][newHeight];
        
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
        
        for (int x = 0; x < copyWidth; x++) {
            for (int y = 0; y < copyHeight; y++) {
                newBuffer[newxStart + x][newyStart + y] = oldBuffer[oldxStart + x][oldyStart + y];
            }
        }

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
        
        recalculate(newBuffer, true);
    }
    
    public void translate(int deltax, int deltay) {
        if (deltax == 0 && deltay == 0) {
            return;
        }
        abortCalculations();
        
        double[][] oldBuffer = valueBuffer;
        double[][] newBuffer = secondaryValueBuffer;
        if (newBuffer == null) {
            newBuffer = new double[pixelWidth][pixelHeight];
        }
        
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
        
        for (int x = 0; x < pixelWidth; x++) {
            for (int y = 0; y < pixelHeight; y++) {
                if (x >= newxStart && x < newxStart + copyWidth
                        && y >= newyStart && y < newyStart + copyHeight) {
                    newBuffer[x][y] = oldBuffer[oldxStart + x - newxStart][oldyStart + y - newyStart];
                } else {
                    newBuffer[x][y] = 0;
                }
            }
        }

        centerRe -= deltax * widthRe / pixelWidth;
        centerIm -= deltay * heightIm / pixelHeight;
        valueBuffer = newBuffer;
        secondaryValueBuffer = oldBuffer;
        
        recalculate(newBuffer, true);
    }
    
    public void zoom(double factor, int focusx, int focusy) {
        assert factor > 0;
        if (factor == 1) {
            return;
        }
        abortCalculations();
        
        double[][] oldBuffer = valueBuffer;
        double[][] newBuffer = secondaryValueBuffer;
        if (newBuffer == null) {
            newBuffer = new double[pixelWidth][pixelHeight];
        }

        double inverseFactor = 1 / factor;
        for (int x = 0; x < pixelWidth; x++) {
            for (int y = 0; y < pixelHeight; y++) {
                int sourcex = (int) Math.round(focusx + inverseFactor * (x - focusx));
                int sourcey = (int) Math.round(focusy + inverseFactor * (y - focusy));
                if (sourcex >= 0 && sourcex < pixelWidth && sourcey >= 0 && sourcey < pixelHeight) {
                    newBuffer[x][y] = oldBuffer[sourcex][sourcey];
                } else {
                    newBuffer[x][y] = 0;
                }
            }
        }

        double oldCenterx = (double) pixelWidth / 2;
        centerRe += widthRe * (1 - inverseFactor) * (focusx - oldCenterx) / pixelWidth;
        double oldCentery = (double) pixelHeight / 2;
        centerIm += heightIm * (1 - inverseFactor) * (focusy - oldCentery) / pixelHeight;
        
        widthRe *= inverseFactor;
        heightIm *= inverseFactor;
        valueBuffer = newBuffer;
        secondaryValueBuffer = oldBuffer;
        
        recalculate(newBuffer, false);
    }
    
    public void resumeCalulations() {
        synchronized (runningCalculators) {
            for (CalculationWorker pointCalculator : runningCalculators) {
                synchronized (pointCalculator.forceWait) {
                    pointCalculator.forceWait.set(false);
                    pointCalculator.forceWait.notify();                    
                }
            }
        }
    }
    
    private void abortCalculations() {
        synchronized (runningCalculators) {
            for (CalculationWorker pointCalculator : runningCalculators) {
                pointCalculator.aborted = true;
                synchronized (pointCalculator.forceWait) {
                    pointCalculator.forceWait.set(false);
                    pointCalculator.forceWait.notify();                    
                }
            }
        }
    }
    
    private void recalculate(double[][] buffer, boolean onlyBlanks) {
        CalculationWorker pointCalculator = new CalculationWorker(buffer, onlyBlanks);
        synchronized (runningCalculators) {
            runningCalculators.add(pointCalculator);
        }
        executorService.submit(pointCalculator);
    }
    
    private class CalculationWorker implements Runnable {
        
        private double[][] buffer;
        private boolean onlyBlanks;
        private boolean aborted = false;
        private AtomicBoolean forceWait = new AtomicBoolean(true);
        
        CalculationWorker(double[][] buffer, boolean onlyBlanks) {
            this.buffer = buffer;
            this.onlyBlanks = onlyBlanks;
        }
        
        public void run() {
            try {
                synchronized (forceWait) {
                    while (forceWait.get()) {
                        forceWait.wait();
                        if (aborted) {
                            return;
                        }
                    }
                }
                
                int iterationLimit = (int) (ITERATION_FACTOR * Math.log(pixelWidth / widthRe));
                System.out.println(iterationLimit);
                double threshold = DIVERGENCE_THRESHOLD * DIVERGENCE_THRESHOLD;
                
                int reportStart = 0;
                long lastReport = System.currentTimeMillis();
                for (int x = 0; x < buffer.length; x++) {
                    for (int y = 0; y < buffer[x].length; y++) {
                        if (!onlyBlanks || buffer[x][y] == 0) {
                            double value = calculate(x, y, iterationLimit, threshold);
                            buffer[x][y] = value;
                        }
                        
                        if (aborted) {
                            return;
                        }
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastReport >= CALCULATION_REPORT_PERIOD) {
                        Rectangle areaToReport = new Rectangle(reportStart, 0,
                                x - reportStart + 1, buffer[x].length);
                        synchronized (listeners) {
                            for (CalculationListener listener : listeners) {
                                listener.calculated(areaToReport, buffer);
                            }
                        }
                        synchronized (forceWait) {
                            while (forceWait.get()) {
                                forceWait.wait();
                                if (aborted) {
                                    return;
                                }
                            }
                        }
                        
                        reportStart = x + 1;
                        lastReport = currentTime;
                    }
                }
                
                if (reportStart < buffer.length) {
                    Rectangle areaToReport = new Rectangle(reportStart, 0,
                            buffer.length - reportStart, buffer[buffer.length - 1].length);
                    synchronized (listeners) {
                        for (CalculationListener listener : listeners) {
                            listener.calculated(areaToReport, buffer);
                        }
                    }
                }
            } catch (InterruptedException exception) {
                // terminate the thread on interruption
            } finally {
                synchronized (runningCalculators) {
                    runningCalculators.remove(this);
                }
            }
        }
        
        private double calculate(int x, int y, int iterationLimit, double threshold) {
            double cre = centerRe + ((double) x / pixelWidth - 0.5) * widthRe;
            double cim = centerIm + ((double) y / pixelHeight - 0.5) * heightIm;
            
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
            } while (!aborted && absolute <= threshold && i < iterationLimit);
            
            if (aborted) {
                return 0;
            } else if (i < iterationLimit) {
                return 2 * Math.atan(DIVERGE_FACTOR * i) / Math.PI;
            } else {
                return -2 * Math.atan(NON_DIVERGE_FACTOR * absolute) / Math.PI;
            }
        }
    }

}
