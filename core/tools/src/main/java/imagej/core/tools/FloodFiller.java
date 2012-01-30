//
// FloodFiller.java
//

/*
ImageJ software for multidimensional image processing and analysis.

Copyright (c) 2010, ImageJDev.org.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the names of the ImageJDev.org developers nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/

package imagej.core.tools;

import imagej.data.Dataset;
import imagej.util.ColorRGB;
import imagej.util.RealRect;

import java.util.Arrays;

import net.imglib2.RandomAccess;
import net.imglib2.meta.Axes;
import net.imglib2.type.numeric.RealType;

/**
 * This class, which does flood filling, is used by the FloodFillTool. It was
 * adapted from IJ1's FloodFiller class. That class implements the flood filling
 * code used by IJ1's macro language and IJ1's particle analyzer. The Wikipedia
 * article at "http://en.wikipedia.org/wiki/Flood_fill" has a good description
 * of the algorithm used here as well as examples in C and Java.
 * 
 * @author Wayne Rasband
 * @author Barry DeZonia
 */
public class FloodFiller {

	private final DrawingTool tool;
	private final boolean isColor;
	private int colorAxis;
	private int uAxis;
	private int vAxis;
	private final StackOfLongs uStack;
	private final StackOfLongs vStack;

	/**
	 * Constructs a FloodFiller from a given DrawingTool. The FloodFiller uses the
	 * DrawingTool to fill a region of contiguous pixels in a plane of a Dataset.
	 */
	public FloodFiller(final DrawingTool tool) {
		this.tool = tool;
		this.isColor = tool.getDataset().isRGBMerged();
		if (isColor) this.colorAxis = tool.getDataset().getAxisIndex(Axes.CHANNEL);
		else this.colorAxis = -1;
		this.uAxis = -1;
		this.vAxis = -1;
		this.uStack = new StackOfLongs();
		this.vStack = new StackOfLongs();
	}

	/**
	 * Does a 4-connected flood fill using the current fill/draw value. Returns
	 * true if any pixels actually changed and false otherwise.
	 */
	public boolean fill4(final long u0, final long v0, final long[] position) {
		final Dataset ds = tool.getDataset();
		final RandomAccess<? extends RealType<?>> accessor =
			ds.getImgPlus().randomAccess();
		accessor.setPosition(position);
		uAxis = tool.getUAxis();
		vAxis = tool.getVAxis();
		// avoid degenerate case
		if (matches(accessor,u0,v0,tool.getColorValue(),tool.getGrayValue()))
			return false;
		final long maxU = ds.dimension(uAxis) - 1;
		final long maxV = ds.dimension(vAxis) - 1;
		final ColorRGB origColor = getColor(accessor,u0,v0);
		final double origValue = getValue(accessor,u0,v0);
		uStack.clear();
		vStack.clear();
		push(u0, v0);
		while (!uStack.isEmpty()) {
			final long u = popU();
			final long v = popV();
			if (!matches(accessor,u,v,origColor,origValue)) continue;
			long u1 = u;
			long u2 = u;
			// find start of scan-line
			while (u1>=0 && matches(accessor,u1,v,origColor,origValue)) u1--;
			u1++;
		  // find end of scan-line
			while (u2<=maxU && matches(accessor,u2,v,origColor,origValue)) u2++;                 
			u2--;
			// fill scan-line
			tool.drawLine(u1, v, u2, v);
			// find scan-lines above this one
			boolean inScanLine = false;
			for (long i=u1; i<=u2; i++) {
				if (!inScanLine && v>0 && matches(accessor,i,v-1,origColor,origValue))
					{push(i, v-1); inScanLine = true;}
				else if (inScanLine && v>0 &&
									!matches(accessor,i,v-1,origColor,origValue))
					inScanLine = false;
			}
			// find scan-lines below this one
			inScanLine = false;
			for (long i=u1; i<=u2; i++) {
				if (!inScanLine && v<maxV &&
							matches(accessor,i,v+1,origColor,origValue))
					{push(i, v+1); inScanLine = true;}
				else if (inScanLine && v<maxV &&
									!matches(accessor,i,v+1,origColor,origValue))
					inScanLine = false;
			}
		}
		// System.out.println("Stack allocated (but not necessarily used) "+uStack.stack.length);
		return true;
	}

	/**
	 * Does an 8-connected flood fill using the current fill/draw value. Returns
	 * true if any pixels actually changed and false otherwise.
	 */
	public boolean fill8(final long u0, final long v0, final long[] position) {
		final Dataset ds = tool.getDataset();
		final RandomAccess<? extends RealType<?>> accessor =
			ds.getImgPlus().randomAccess();
		accessor.setPosition(position);
		uAxis = tool.getUAxis();
		vAxis = tool.getVAxis();
		// avoid degenerate case
		if (matches(accessor,u0,v0,tool.getColorValue(),tool.getGrayValue()))
			return false;
		final long maxU = ds.dimension(uAxis) - 1;
		final long maxV = ds.dimension(vAxis) - 1;
		final ColorRGB origColor = getColor(accessor,u0,v0);
		final double origValue = getValue(accessor,u0,v0);
		uStack.clear();
		vStack.clear();
		push(u0, v0);
		while(!uStack.isEmpty()) {
			final long u = popU(); 
			final long v = popV();
			long u1 = u;
			long u2 = u;
			if (matches(accessor,u,v,origColor,origValue)) {
				// find start of scan-line
				while (u1>=0 && matches(accessor,u1,v,origColor,origValue)) u1--;
				u1++;
			  // find end of scan-line
				while (u2<=maxU && matches(accessor,u2,v,origColor,origValue)) u2++;
				u2--;
				tool.drawLine(u1, v, u2, v); // fill scan-line
			}
			if (v > 0) {
				if (u1 > 0) {
					if (matches(accessor,u1-1,v-1,origColor,origValue)) {
						push(u1-1, v-1);
					}
				}
				if (u2 < maxU) {
					if (matches(accessor,u2+1,v-1,origColor,origValue)) {
						push(u2+1, v-1);
					}
				}
			}
			if (v < maxV) {
				if (u1 > 0) {
					if (matches(accessor,u1-1,v+1,origColor,origValue)) {
						push(u1-1, v+1);
					}
				}
				if (u2 < maxU) {
					if (matches(accessor,u2+1,v+1,origColor,origValue)) {
						push(u2+1, v+1);
					}
				}
			}
			// find scan-lines above this one
			boolean inScanLine = false;
			for (long i=u1; i<=u2; i++) {
				if (!inScanLine && v>0 && matches(accessor,i,v-1,origColor,origValue))
					{push(i, v-1); inScanLine = true;}
				else if (inScanLine && v>0 &&
									!matches(accessor,i,v-1,origColor,origValue))
					inScanLine = false;
			}
			// find scan-lines below this one
			inScanLine = false;
			for (long i=u1; i<=u2; i++) {
				if (!inScanLine && v<maxV &&
							matches(accessor,i,v+1,origColor,origValue))
					{push(i, v+1); inScanLine = true;}
				else if (inScanLine && v<maxV &&
									!matches(accessor,i,v+1,origColor,origValue))
					inScanLine = false;
			}
		}
		// System.out.println("Stack allocated (but not necessarily used) "+uStack.stack.length);
		return true;
	}

	/**
	 * From IJ1; this method is used by the particle analyzer to remove interior
	 *  holes from particle masks.
	 */
	public void particleAnalyzerFill(
		long u0, long v0, long[] position, double level1, double level2,
		DrawingTool maskTool, RealRect bounds)
	{
		final Dataset ds = tool.getDataset();
		final RandomAccess<? extends RealType<?>> acc =
			ds.getImgPlus().randomAccess();
		acc.setPosition(position);
		uAxis = tool.getUAxis();
		vAxis = tool.getVAxis();
		long maxU = ds.dimension(uAxis) - 1;
		long maxV = ds.dimension(vAxis) - 1;
		maskTool.setGrayValue(0);
		maskTool.fill();
		maskTool.setGrayValue(255);
		uStack.clear();
		vStack.clear();
		push(u0, v0);
		while(!uStack.isEmpty()) {   
			long u = popU(); 
			long v = popV();
			if (!inParticle(acc,u,v,level1,level2)) continue;
			long u1 = u;
			long u2 = u;
			// find start of scan-line
			while (inParticle(acc,u1,v,level1,level2) && u1>=0) u1--;
			u1++;
		  // find end of scan-line
			while (inParticle(acc,u2,v,level1,level2) && u2<=maxU) u2++;                 
			u2--;
			// fill scan-line in mask
			maskTool.drawLine(
				(long) (u1-bounds.x), (long) (v-bounds.y),
				(long) (u2-bounds.x), (long) (v-bounds.y));
			// fill scan-line in image
			tool.drawLine(u1, v, u2, v);
			boolean inScanLine = false;
			if (u1>0) u1--;
			if (u2<maxU) u2++;
			for (long i=u1; i<=u2; i++) { // find scan-lines above this one
				if (!inScanLine && v>0 && inParticle(acc,i,v-1,level1,level2))
					{push(i, v-1); inScanLine = true;}
				else if (inScanLine && v>0 && !inParticle(acc,i,v-1,level1,level2))
					inScanLine = false;
			}
			inScanLine = false;
			for (long i=u1; i<=u2; i++) { // find scan-lines below this one
				if (!inScanLine && v<maxV && inParticle(acc,i,v+1,level1,level2))
					{push(i, v+1); inScanLine = true;}
				else if (inScanLine && v<maxV && !inParticle(acc,i,v+1,level1,level2))
					inScanLine = false;
			}
		}        
	}
	
	final boolean inParticle(
		RandomAccess<? extends RealType<?>> accessor, long u, long v,
		double level1, double level2)
	{
		double val = getValue(accessor, u, v);
		return val>=level1 && val<=level2;
	}
	
	// -- private helpers --

	/**
	 * Returns true if the current pixel located at the given (u,v) coordinates is
	 * the same as the specified color or gray values.
	 */
	private boolean	matches(
		final RandomAccess<? extends RealType<?>> accessor, final long u,
		final long v, final ColorRGB origColor, final double origValue)
	{
		accessor.setPosition(u, uAxis);
		accessor.setPosition(v, vAxis);

		// are we interested in values?
		if (!isColor) {
			final double val = accessor.get().getRealDouble();
			return val == origValue;
		}

		// else interested in colors
		double component;

		accessor.setPosition(0, colorAxis);
		component = accessor.get().getRealDouble();
		if (component != origColor.getRed()) return false;

		accessor.setPosition(1, colorAxis);
		component = accessor.get().getRealDouble();
		if (component != origColor.getGreen()) return false;

		accessor.setPosition(2, colorAxis);
		component = accessor.get().getRealDouble();
		if (component != origColor.getBlue()) return false;

		return true;
	}

	/**
	 * Gets the color of the pixel at the (u,v) coordinates of the UV plane of the
	 * current DrawingTool. If the underlying Dataset is not color returns null.
	 */
	private ColorRGB getColor(final RandomAccess<? extends RealType<?>> accessor,
		final long u, final long v)
	{
		if (!isColor) return null;
		accessor.setPosition(u, uAxis);
		accessor.setPosition(v, vAxis);
		accessor.setPosition(0, colorAxis);
		final int r = (int) accessor.get().getRealDouble();
		accessor.setPosition(1, colorAxis);
		final int g = (int) accessor.get().getRealDouble();
		accessor.setPosition(2, colorAxis);
		final int b = (int) accessor.get().getRealDouble();
		return new ColorRGB(r, g, b);
	}

	/**
	 * Gets the gray value of the pixel at the (u,v) coordinates of the UV plane
	 * of the current DrawingTool. If the underlying Dataset is not gray returns
	 * Double.NaN.
	 */
	private double getValue(final RandomAccess<? extends RealType<?>> accessor,
		final long u, final long v)
	{
		if (isColor) return Double.NaN;
		accessor.setPosition(u, uAxis);
		accessor.setPosition(v, vAxis);
		return accessor.get().getRealDouble();
	}

	/**
	 * Pushes the specified (u,v) point on the working stacks.
	 */
	private void push(final long u, final long v) {
		uStack.push(u);
		vStack.push(v);
	}

	/** Pops a U value of the working U stack. */
	private long popU() {
		return uStack.pop();
	}

	/** Pops a V value of the working V stack. */
	private long popV() {
		return vStack.pop();
	}

	/** To minimize object creations/deletions we want a stack of primitives. */
	private class StackOfLongs {

		private int top;
		private long[] stack;

		public StackOfLongs() {
			top = -1;
			stack = new long[400];
		}

		public boolean isEmpty() {
			return top < 0;
		}

		public void clear() {
			top = -1;
		}

		public void push(final long value) {
			if (top == stack.length - 1)
				stack =	Arrays.copyOf(stack, stack.length * 2);
			top++;
			stack[top] = value;
		}

		public long pop() {
			if (top < 0) throw new IllegalArgumentException("can't pop empty stack");
			final long value = stack[top];
			top--;
			return value;
		}
	}

}
