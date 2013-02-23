/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

/**
 * Main class for starting the Mandelbrot application.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotApplication {

    /**
     * Start the application.
     * 
     * @param args command line arguments (ignored by this program)
     */
    public static void main(String[] args) {
        JFrame frame = new JFrame("Funky Mandelbrot");
        final MandelbrotCanvas canvas = new MandelbrotCanvas();
        frame.getContentPane().add(canvas);
        frame.setSize(new Dimension(600,400));
        frame.setMinimumSize(new Dimension(300, 200));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                canvas.dispose();
            }
        });
    }

}
