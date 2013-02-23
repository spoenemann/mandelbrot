/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import javax.swing.JPanel;

/**
 * A canvas that draws a Mandelbrot fractal.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCanvas extends JPanel {
    
    /** factor by which to zoom the fractal for each mouse wheel unit. */
    public static final double ZOOM_PER_WHEEL_UNIT = 1.05;

    /** the serial version UID. */
    private static final long serialVersionUID = 7892870465027356734L;
    /** the color used for non-diverging regions. */
    private static final Color NON_DIVERGE_COLOR = new Color(96, 32, 32);
    /** the number of iterations after which the color gradient repeats. */
    private static final int COLOR_PERIOD = 64;
    
    /** the calculator class that performs all arithmetic stuff. */
    private final MandelbrotCalculator calculator;
    /** the last point where the mouse was found while dragging. */
    private Point lastMouseLocation = new Point();

    /**
     * Create a Mandelbrot canvas.
     */
    public MandelbrotCanvas() {
        // create the calculator class
        calculator = new MandelbrotCalculator();
        calculator.addListener(new MandelbrotCalculator.CalculationListener() {
            public void calculated(Rectangle area, int[][] buffer) {
                repaint(area);
            }
        });
        
        // register a mouse listener for reacting on user actions
        MouseAdapter mouseadapter = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                lastMouseLocation = e.getPoint();
            }
            public void mouseDragged(MouseEvent e) {
                Point location = e.getPoint();
                calculator.translate(location.x - lastMouseLocation.x, location.y - lastMouseLocation.y);
                repaint();
                lastMouseLocation = location;
            }
            public void mouseWheelMoved(MouseWheelEvent e) {
                double zoom = Math.pow(ZOOM_PER_WHEEL_UNIT, e.getWheelRotation());
                calculator.zoom(zoom, e.getX(), e.getY());
                repaint();
            }
        };
        this.addMouseListener(mouseadapter);
        this.addMouseMotionListener(mouseadapter);
        this.addMouseWheelListener(mouseadapter);
    }
    
    /**
     * Release any additional resources that are held by this canvas.
     */
    public void dispose() {
        calculator.shutdown();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void paint(Graphics g) {
        // paint the buffer given by the calculator
        int[][] buffer = calculator.getBuffer();
        Rectangle clip = g.getClipBounds();
        for (int x = clip.x; x < clip.x + clip.width; x++) {
            for (int y = clip.y; y < clip.y + clip.height; y++) {
                if (x < buffer.length && y < buffer[x].length) {
                    int value = buffer[x][y];
                    if (value < 0) {
                        // the pixel belongs to the non-diverging set
                        g.setColor(NON_DIVERGE_COLOR);
                    } else {
                        // the pixel belongs to the diverging set
                        int remainder = value % COLOR_PERIOD;
                        int shade;
                        if (remainder < COLOR_PERIOD / 2) {
                            shade = 256 * remainder / (COLOR_PERIOD / 2);
                        } else {
                            shade = 256 * (COLOR_PERIOD - remainder - 1) / (COLOR_PERIOD / 2);
                        }
                        g.setColor(new Color(shade, shade, 224));
                    }
                    g.drawLine(x, y, x, y);
                }
            }
        }
        
        // resume any calculations that were paused for painting the buffer
        calculator.resumeCalulations();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        // resize the calculator's internal buffer
        calculator.resize(width, height);
    }

}
