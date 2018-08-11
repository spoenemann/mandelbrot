/*********************************************************************
* Copyright (c) 2014 Miro Spönemann
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
**********************************************************************/
package io.github.spoenemann.mandelbrot;

import java.util.LinkedList;
import java.util.function.BiFunction;

/**
 * Color information for one screen pixel.
 * 
 * @author msp@informatik.uni-kiel.de
 */
public class Pixel {
	
	/**
	 * Cast an unsigned byte to integer.
	 * 
	 * @param b an unsigned byte
	 * @return the corresponding int value
	 */
	private static int toInt(byte b) {
		if (b < 0) {
			return ((int) b) + 256;
		} else {
			return (int) b;
		}
	}
	
	/** The red color value. */
	private byte red;
	/** The green color value. */
	private byte green;
	/** The blue color value. */
	private byte blue;
	/** The subpixels for oversampling: top left, top right, bottom left, and bottom right. */
	private final Pixel[] subpixels = new Pixel[4];
	
	/**
	 * Copy the given pixel with all its subpixels.
	 * 
	 * @param other an existing pixel
	 */
	public Pixel(Pixel other) {
		this.red = other.red;
		this.green = other.green;
		this.blue = other.blue;
		for (int i = 0; i < 4; i++) {
			if (other.subpixels[i] != null) {
				this.subpixels[i] = new Pixel(other.subpixels[i]);
			}
		}
	}
	
	/**
	 * Create a pixel with given color values.
	 * 
	 * @param red the red color value
	 * @param green the green color value
	 * @param blue the blue color value
	 */
	public Pixel(byte red, byte green, byte blue) {
		this.red = red;
		this.green = green;
		this.blue = blue;
	}
	
	/**
	 * Create a pixel with given color values.
	 * 
	 * @param red the red color value
	 * @param green the green color value
	 * @param blue the blue color value
	 */
	public Pixel(int red, int green, int blue) {
		this.red = (byte) red;
		this.green = (byte) green;
		this.blue = (byte) blue;
	}

	/**
	 * Return the red color value.
	 * 
	 * @return the red color value
	 */
	public byte getRed() {
		return red;
	}

	/**
	 * Return the green color value.
	 * 
	 * @return the green color value
	 */
	public byte getGreen() {
		return green;
	}

	/**
	 * Return the blue color value.
	 * 
	 * @return the blue color value
	 */
	public byte getBlue() {
		return blue;
	}
    
    /**
     * Convert this pixel into the ARGB format used by {@link javafx.scene.image.PixelWriter}.
     * The color components are stored in order, from MSb to LSb: alpha, red, green, blue.
     * 
     * @return the ARGB value corresponding to this pixel
     */
    public int toARGB() {
        return 0xff000000 | (toInt(red) << 16) | (toInt(green) << 8) | toInt(blue);
    }
    
    /**
     * Compute the average color of this pixel with all its computed subpixels in the ARGB format
     * used by {@link javafx.scene.image.PixelWriter}.
     * 
     * @return the ARGB value corresponding to the average color of this pixel and its subpixels
     */
    public int computeAverageARGB() {
    	int redSum = 0;
    	int greenSum = 0;
    	int blueSum = 0;
    	int number = 0;
    	LinkedList<Pixel> pixelStack = new LinkedList<Pixel>();
    	pixelStack.add(this);
    	do {
    		Pixel pixel = pixelStack.pop();
    		redSum += toInt(pixel.red);
    		greenSum += toInt(pixel.green);
    		blueSum += toInt(pixel.blue);
    		number++;
    		for (int i = 0; i < 4; i++) {
    			if (pixel.subpixels[i] != null) {
    				pixelStack.push(pixel.subpixels[i]);
    			}
    		}
    	} while (!pixelStack.isEmpty());
    	return 0xff000000 | ((redSum / number) << 16) | ((greenSum / number) << 8) | (blueSum / number);
    }
    
    /**
     * Add subpixels on the specified level. This requires that all subpixels of lower levels have
     * already been generated.
     * 
     * @param level the subpixel level to generate
     * @param pixelGenerator the pixel generator used for subpixel creation
     */
    public void addSubpixels(int level, BiFunction<Double, Double, Pixel> pixelGenerator) {
    	addSubpixels(level, 0.25, 0, 0, pixelGenerator);
    }
    
    /**
     * Add subpixels on the specified sublevel.
     * 
     * @param level the subpixel sublevel to generate
     * @param scale the scale of the current level
     * @param offsetx the horizontal offset of the current level
     * @param offsety the vertical offset of the current level
     * @param pixelGenerator the pixel generator used for subpixel creation
     */
    private void addSubpixels(int level, double scale, double offsetx, double offsety,
    		BiFunction<Double, Double, Pixel> pixelGenerator) {
    	assert level >= 1;
    	if (level == 1) {
    		if (subpixels[0] == null)
    			subpixels[0] = pixelGenerator.apply(offsetx - scale, offsety - scale);
    		if (subpixels[1] == null)
    			subpixels[1] = pixelGenerator.apply(offsetx + scale, offsety - scale);
    		if (subpixels[2] == null)
    			subpixels[2] = pixelGenerator.apply(offsetx - scale, offsety + scale);
    		if (subpixels[3] == null)
    			subpixels[3] = pixelGenerator.apply(offsetx + scale, offsety + scale);
    	} else {
    		int nextLevel = level - 1;
    		double nextScale = scale / 2;
    		subpixels[0].addSubpixels(nextLevel, nextScale, offsetx - scale, offsety - scale, pixelGenerator);
    		subpixels[1].addSubpixels(nextLevel, nextScale, offsetx + scale, offsety - scale, pixelGenerator);
    		subpixels[2].addSubpixels(nextLevel, nextScale, offsetx - scale, offsety + scale, pixelGenerator);
    		subpixels[3].addSubpixels(nextLevel, nextScale, offsetx + scale, offsety + scale, pixelGenerator);
    	}
    }
	
}
