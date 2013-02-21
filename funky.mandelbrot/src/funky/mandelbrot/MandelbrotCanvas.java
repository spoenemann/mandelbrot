/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JPanel;

/**
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCanvas extends JPanel {

    private static final long serialVersionUID = 7892870465027356734L;
    
    private MandelbrotCalculator calculator;
    
    public MandelbrotCanvas() {
        calculator = new MandelbrotCalculator();
        calculator.addListener(new MandelbrotCalculator.CalculationListener() {
            public void calculated(Rectangle area, double[][] buffer) {
                repaint(area);
            }
        });
    }
    
    public void dispose() {
        calculator.shutdown();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void paint(Graphics g) {
        double[][] buffer = calculator.getBuffer();
        Rectangle clip = g.getClipBounds();
        for (int x = clip.x; x < clip.x + clip.width; x++) {
            for (int y = clip.y; y < clip.y + clip.height; y++) {
                if (x < buffer.length && y < buffer[x].length) {
                    double value = buffer[x][y];
                    if (value < 0) {
                        int shade = (int) (-224 * value);
                        g.setColor(new Color(shade, shade, shade));
                    } else {
                        int shade = (int) (256 * value);
                        g.setColor(new Color(shade, shade, 224));
                    }
                    g.drawLine(x, y, x, y);
                }
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        calculator.resize(width, height);
    }

}
