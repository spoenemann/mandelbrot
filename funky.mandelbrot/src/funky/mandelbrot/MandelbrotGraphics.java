/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;

/**
 * A canvas that draws a Mandelbrot fractal.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotGraphics {
    
    /** factor by which to zoom the fractal for each mouse wheel unit. */
    public static final double ZOOM_PER_WHEEL_UNIT = 1.01;

    /** the color used for non-diverging regions. */
    private static final int NON_DIVERGE_COLOR = rgb(96, 32, 32);
    /** the number of iterations after which the color gradient repeats. */
    private static final int COLOR_PERIOD = 64;
    
    /**
     * Convert the specified color into the ARGB format.
     * 
     * @param r the red color value
     * @param g the green color value
     * @param b the blue color value
     * @return the corresponding ARGB value
     */
    private static int rgb(int r, int g, int b) {
        return 0xff000000 + (r << 16) + (g << 8) + b;
    }
    
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
                calculator.zoom(1 / event.getZoomFactor(), (int) event.getX(), (int) event.getY());
                repaint(canvas.getBoundsInLocal(), calculator.getBuffer());
            });
        canvas.addEventHandler(ScrollEvent.SCROLL,
            (ScrollEvent event) -> {
                double zoom = Math.pow(ZOOM_PER_WHEEL_UNIT, -event.getDeltaY());
                calculator.zoom(zoom, (int) event.getX(), (int) event.getY());
                repaint(canvas.getBoundsInLocal(), calculator.getBuffer());
            });
        ChangeListener<Number> sizeListener =
            (ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
                calculator.resize((int) canvas.getWidth(), (int) canvas.getHeight());
            };
        canvas.widthProperty().addListener(sizeListener);
        canvas.heightProperty().addListener(sizeListener);
        
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
    
    /** buffer used to draw colored pixels on the canvas. */
    private int[] argbBuffer = new int[0];
    
    /**
     * Repaint the given area using data from the given buffer.
     * 
     * @param area the area to repaint
     * @param valueBuffer the buffer from which fractal data are taken
     */
    public void repaint(Bounds area, int[][] valueBuffer) {
        Platform.runLater(new Runnable() {
            public void run() {
                int minx = (int) area.getMinX();
                int miny = (int) area.getMinY();
                int width = (int) area.getWidth();
                int height = (int) area.getHeight();

                // make sure the buffer is large enough
                if (argbBuffer.length < width * height) {
                    argbBuffer = new int[width * height];
                }
                
                for (int x = minx; x < minx + width; x++) {
                    for (int y = miny; y < miny + height; y++) {
                        if (x < valueBuffer.length && y < valueBuffer[x].length) {
                            int index = x - minx + width * (y - miny);
                            int value = valueBuffer[x][y];
                            if (value < 0) {
                                // the pixel belongs to the non-diverging set
                                argbBuffer[index] = NON_DIVERGE_COLOR;
                            } else {
                                // the pixel belongs to the diverging set
                                int remainder = value % COLOR_PERIOD;
                                int shade;
                                if (remainder < COLOR_PERIOD / 2) {
                                    shade = 256 * remainder / (COLOR_PERIOD / 2);
                                } else {
                                    shade = 256 * (COLOR_PERIOD - remainder - 1) / (COLOR_PERIOD / 2);
                                }
                                argbBuffer[index] = rgb(shade, shade, 224);
                            }
                        }
                    }
                }
                PixelWriter writer = canvas.getGraphicsContext2D().getPixelWriter();
                writer.setPixels(minx, miny, width, height, PixelFormat.getIntArgbInstance(),
                        argbBuffer, 0, width);
            }
        });
    }
    
}
