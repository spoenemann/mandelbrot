/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.paint.Color;

/**
 * A canvas that draws a Mandelbrot fractal.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotGraphics {
    
    /** factor by which to zoom the fractal for each mouse wheel unit. */
    public static final double ZOOM_PER_WHEEL_UNIT = 1.05;

    /** the color used for non-diverging regions. */
    private static final Color NON_DIVERGE_COLOR = new Color(0.38, 0.13, 0.13, 1);
    /** the number of iterations after which the color gradient repeats. */
    private static final int COLOR_PERIOD = 64;
    
    /** the canvas on which we draw the fractal. */
    private final Canvas canvas;
    /** the calculator class that performs all arithmetic stuff. */
    private final MandelbrotCalculator calculator;
    /** the last point where the mouse was found while dragging. */
    private Point2D lastMouseLocation = new Point2D(0, 0);

    /**
     * Create a Mandelbrot canvas.
     */
    public MandelbrotGraphics(Canvas canvas) {
        this.canvas = canvas;
        
        // register event handlers
        canvas.addEventHandler(MouseEvent.ANY,
            (MouseEvent event) -> {
                if (event.getEventType().equals(MouseEvent.MOUSE_PRESSED)) {
                    lastMouseLocation = new Point2D((float) event.getX(), (float) event.getY());
                } else if (event.getEventType().equals(MouseEvent.MOUSE_DRAGGED)) {
                    calculator.translate((int) (event.getX() - lastMouseLocation.getX()),
                            (int) (event.getY() - lastMouseLocation.getY()));
                    repaint(canvas.getBoundsInLocal(), calculator.getBuffer());
                    lastMouseLocation = new Point2D((float) event.getX(), (float) event.getY());
                }
            });
        canvas.addEventHandler(ZoomEvent.ZOOM,
            (ZoomEvent event) -> {
                calculator.zoom(event.getZoomFactor(), (int) event.getX(), (int) event.getY());
                repaint(canvas.getBoundsInLocal(), calculator.getBuffer());
            });
        canvas.addEventHandler(ScrollEvent.SCROLL,
                (ScrollEvent event) -> {
                    double zoom = Math.pow(ZOOM_PER_WHEEL_UNIT, event.getTextDeltaY());
                    calculator.zoom(zoom, (int) event.getX(), (int) event.getY());
                    repaint(canvas.getBoundsInLocal(), calculator.getBuffer());
                });
        
        // create the calculator class
        calculator = new MandelbrotCalculator();
        calculator.addListener(this::repaint);
        calculator.resize((int) canvas.getWidth(), (int) canvas.getHeight());
    }
    
    /**
     * Release any additional resources that are held by this canvas.
     */
    public void dispose() {
        calculator.shutdown();
    }
    
    /**
     * Repaint the given area using data from the given buffer.
     * 
     * @param area the area to repaint
     * @param buffer the buffer from which fractal data are taken
     */
    public void repaint(Bounds area, int[][] buffer) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        for (int x = (int) area.getMinX(); x < area.getMaxX(); x++) {
            for (int y = (int) area.getMinY(); y < area.getMaxY(); y++) {
                if (x < buffer.length && y < buffer[x].length) {
                    int value = buffer[x][y];
                    if (value < 0) {
                        // the pixel belongs to the non-diverging set
                        gc.setStroke(NON_DIVERGE_COLOR);
                    } else {
                        // the pixel belongs to the diverging set
                        int remainder = value % COLOR_PERIOD;
                        double shade;
                        if (remainder < COLOR_PERIOD / 2) {
                            shade = remainder / (COLOR_PERIOD / 2);
                        } else {
                            shade = (COLOR_PERIOD - remainder - 1) / (COLOR_PERIOD / 2);
                        }
                        gc.setStroke(new Color(shade, shade, 0.88, 1));
                    }
                    gc.strokeLine(x, y, x, y);
                }
            }
        }
        
        // resume any calculations that were paused for painting the buffer
        calculator.resumeCalulations();
    }
    
    /**
     * {@inheritDoc}
     */
//    @Override
    public void setBounds(int x, int y, int width, int height) {
        // TODO how to react on bounds changes?
        // resize the calculator's internal buffer
        calculator.resize(width, height);
    }

}
