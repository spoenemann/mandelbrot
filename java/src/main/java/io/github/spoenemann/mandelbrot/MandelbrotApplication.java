/*********************************************************************
* Copyright (c) 2014 Miro Spönemann
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
**********************************************************************/
package io.github.spoenemann.mandelbrot;

import javafx.application.Application;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
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
        StackPane root = new StackPane();
        
        Canvas canvas = new Canvas(600, 400);
        graphics = new MandelbrotGraphics(canvas);
        root.getChildren().add(canvas);
        root.widthProperty().addListener(
            (ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
                canvas.setWidth(newValue.doubleValue());
            });
        root.heightProperty().addListener(
            (ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
                canvas.setHeight(newValue.doubleValue());
            });
        
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }
    
    @Override
    public void stop() {
        graphics.dispose();
    }
    
}
