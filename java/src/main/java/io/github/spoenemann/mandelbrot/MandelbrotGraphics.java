/*********************************************************************
* Copyright (c) 2014 Miro Spönemann
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
**********************************************************************/
package io.github.spoenemann.mandelbrot;

import java.awt.Rectangle;

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
    private static final Pixel NON_DIVERGE_COLOR = new Pixel(96, 32, 32);
    /** the number of iterations after which the color gradient repeats. */
    private static final int COLOR_PERIOD = 64;
    /** the default colorizer. */
    private static final IColorizer DEFAULT_COLORIZER = x -> {
        if (x < 0) {
            // the pixel belongs to the non-diverging set
            return new Pixel(NON_DIVERGE_COLOR);
        } else {
            // the pixel belongs to the diverging set
            double remainder = (int) x % COLOR_PERIOD + x - Math.floor(x);
            int shade;
            if (remainder < COLOR_PERIOD / 2) {
                shade = (int) Math.round(255 * remainder / (COLOR_PERIOD / 2));
            } else {
                shade = (int) Math.round(255 * (COLOR_PERIOD - remainder) / (COLOR_PERIOD / 2));
            }
            return new Pixel(shade, shade, 224);
        }
    };
    
    /** the canvas on which we draw the fractal. */
    private final Canvas canvas;
    /** the buffer manager that controls all computations. */
    private final BufferManager bufferManager;

    /**
     * Create a Mandelbrot canvas.
     */
    public MandelbrotGraphics(Canvas canvas) {
        this.canvas = canvas;
        
        bufferManager = new BufferManager();
        bufferManager.addListener(this::repaint);
        bufferManager.setColorizer(DEFAULT_COLORIZER);
        bufferManager.resize((int) canvas.getWidth(), (int) canvas.getHeight());
        
        addEventHandlers();
    }
    
    /** the last point where the mouse was found while dragging. */
    private Point2D lastMouseLocation = new Point2D(0, 0);
    
    /**
     * Add handlers to mouse events and resize events on the canvas.
     */
    private void addEventHandlers() {
        canvas.addEventHandler(MouseEvent.ANY,
            (MouseEvent event) -> {
                if (event.getEventType().equals(MouseEvent.MOUSE_PRESSED)) {
                    lastMouseLocation = new Point2D((float) event.getX(), (float) event.getY());
                } else if (event.getEventType().equals(MouseEvent.MOUSE_DRAGGED)) {
                    bufferManager.translate((int) (event.getX() - lastMouseLocation.getX()),
                            (int) (event.getY() - lastMouseLocation.getY()));
                    repaint();
                    lastMouseLocation = new Point2D((float) event.getX(), (float) event.getY());
                }
            });
        canvas.addEventHandler(ZoomEvent.ZOOM,
            (ZoomEvent event) -> {
                bufferManager.zoom(1 / event.getZoomFactor(), (int) event.getX(), (int) event.getY());
                repaint();
            });
        canvas.addEventHandler(ScrollEvent.SCROLL,
            (ScrollEvent event) -> {
                double zoom = Math.pow(ZOOM_PER_WHEEL_UNIT, -event.getDeltaY());
                bufferManager.zoom(zoom, (int) event.getX(), (int) event.getY());
                repaint();
            });
        ChangeListener<Number> sizeListener =
            (ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
                bufferManager.resize((int) canvas.getWidth(), (int) canvas.getHeight());
            };
        canvas.widthProperty().addListener(sizeListener);
        canvas.heightProperty().addListener(sizeListener);
    }
    
    /**
     * Release any additional resources that are held by this canvas.
     */
    public void dispose() {
        bufferManager.shutdown();
    }
    
    /**
     * Repaint the whole canvas using the current calculator buffer.
     */
    private void repaint() {
        Bounds bounds = canvas.getBoundsInLocal();
        repaint(new Rectangle((int) bounds.getMinX(), (int) bounds.getMinY(), (int) bounds.getWidth(),
                (int) bounds.getHeight()), bufferManager.getBuffer(), (int) bounds.getWidth());
    }
    
    /**
     * Repaint the given area using data from the given buffer.
     * 
     * @param area the area to repaint
     * @param valueBuffer the buffer from which fractal data are taken
     * @param bufferWidth the total width of a row in the value buffer
     */
    public void repaint(Rectangle area, int[] valueBuffer, int bufferWidth) {
        Platform.runLater(new Runnable() {
            public void run() {
                PixelWriter writer = canvas.getGraphicsContext2D().getPixelWriter();
                int offset = area.x + area.y * bufferWidth;
                writer.setPixels(area.x, area.y, area.width, area.height,
                        PixelFormat.getIntArgbInstance(), valueBuffer, offset, bufferWidth);
            }
        });
    }
    
}
