/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.awt.Dimension;

import javax.swing.JFrame;

/**
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotApplication {

    /**
     * @param args
     */
    public static void main(String[] args) {
        JFrame frame = new JFrame("Funky Mandelbrot");
        frame.getContentPane().add(new MandelbrotCanvas());
        frame.setSize(new Dimension(600,400));
        frame.setMinimumSize(new Dimension(300, 200));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

}
