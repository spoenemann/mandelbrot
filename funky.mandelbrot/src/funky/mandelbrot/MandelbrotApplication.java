/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.stage.Stage;

/**
 * Main class for starting the Mandelbrot application.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotApplication extends Application {
    
    /** the graphics object used to paint the fractal. */
    private MandelbrotGraphics graphics;

    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Funky Mandelbrot");
        Group root = new Group();
        Canvas canvas = new Canvas(600, 400);
        graphics = new MandelbrotGraphics(canvas);
        root.getChildren().add(canvas);
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }
    
    @Override
    public void stop() {
        graphics.dispose();
    }
    
}
