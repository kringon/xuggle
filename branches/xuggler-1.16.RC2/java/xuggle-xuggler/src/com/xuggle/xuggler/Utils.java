/*
 * Copyright (c) 2008-2009 by Xuggle Inc. All rights reserved.
 *
 * It is REQUESTED BUT NOT REQUIRED if you use this library, that you let 
 * us know by sending e-mail to info@xuggle.com telling us briefly how you're
 * using the library and what you like or don't like about it.
 *
 * This library is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any later
 * version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with this library; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package com.xuggle.xuggler;

import java.util.concurrent.TimeUnit;

import com.xuggle.ferry.IBuffer;
import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.ITimeValue;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBuffer;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.awt.image.Raster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A collection of useful utilities for creating blank {@link IVideoPicture} objects
 * and managing audio time stamp to sample conversions.
 * 
 * @author aclarke
 *
 */
public class Utils
{
  /**
   * Get a new blank frame object encoded in {@link IPixelFormat.Type#YUV420P} format.
   * 
   * @param w width of object
   * @param h height of object
   * @param y Y component of background color.
   * @param u U component of background color.
   * @param v V component of background color.
   * @param pts The time stamp, in microseconds, you want this frame to have, or {@link Global#NO_PTS} for none.
   * @return A new frame, or null if we can't create it.
   */
  public static IVideoPicture getBlankFrame(int w, int h, int y, int u, int v, long pts)
  {
    IVideoPicture frame = IVideoPicture.make(IPixelFormat.Type.YUV420P, w, h);
    if (frame != null)
    {
      IBuffer data = frame.getData();
      int bufSize = frame.getSize();
      java.nio.ByteBuffer buffer = data.getByteBuffer(0, bufSize);
      if (buffer != null)
      {
        // we have the raw data; now we set it to the specified YUV value.
        int lineLength = 0;
        int offset = 0;
        
        // first let's check the L
        offset = 0;
        lineLength = frame.getDataLineSize(0);
        for(int i = offset ; i < offset + lineLength * h; i++)
        {
          buffer.put(i, (byte)y);
        }

        // now, check the U value
        offset = (frame.getDataLineSize(0)*h);
        lineLength = frame.getDataLineSize(1);
        for(int i = offset ; i < offset + lineLength * ((h+1) / 2); i++)
        {
          buffer.put(i, (byte)u);
        }

        // and finally the V
        offset = (frame.getDataLineSize(0)*h) + (frame.getDataLineSize(1)*((h+1)/2));
        lineLength = frame.getDataLineSize(2);
        for(int i = offset; i < offset + lineLength * ((h+1) / 2); i++)
        {
          buffer.put(i, (byte)v);
        }
      }
      // set it complete
      frame.setComplete(true, IPixelFormat.Type.YUV420P, w, h, pts);
    }
    
    return frame;
  }
  
  /**
   * Returns a blank frame with a green-screen background.
   * 
   * @see #getBlankFrame(int, int, int, int, int, long)
   * 
   * @param w width in pixels
   * @param h height in pixels 
   * @param pts presentation timestamp (in {@link TimeUnit#MICROSECONDS} you want set
   * @return a new blank frame
   */
  public static IVideoPicture getBlankFrame(int w, int h, int pts)
  {
    return getBlankFrame(w, h, 0, 0, 0, pts);
  }

  /**
   * For a given sample rate, returns how long it would take to play a number of samples.
   * @param numSamples The number of samples you want to find the duration for
   * @param sampleRate The sample rate in Hz
   * @return The duration it would take to play numSamples of sampleRate audio
   */
  public static ITimeValue samplesToTimeValue(long numSamples, int sampleRate)
  {
    if (sampleRate <= 0)
      throw new IllegalArgumentException("sampleRate must be greater than zero");
    
    return ITimeValue.make(
        IAudioSamples.samplesToDefaultPts(numSamples, sampleRate),
        ITimeValue.Unit.MICROSECONDS);
  }
  
  /**
   * For a given time duration and sample rate, return the number of samples it would take to fill.
   * @param duration duration
   * @param sampleRate sample rate of audio
   * @return number of samples required to fill that duration
   */
  public static long timeValueToSamples(ITimeValue duration, int sampleRate)
  {
    if (duration == null)
      throw new IllegalArgumentException("must pass in a valid duration");
    if (sampleRate <= 0)
      throw new IllegalArgumentException("sampleRate must be greater than zero");
    return IAudioSamples.defaultPtsToSamples(duration.get(ITimeValue.Unit.MICROSECONDS), sampleRate); 
  }

    /** A tables used to roll bits for byte/int translation. */

    private static final int[] mBigEndianRollTable    = {24, 16, 8, 0};
    private static final int[] mLittleEndianRollTable = {0, 8, 16, 24};
    private static final int[] mRollTable;

    /** Select a the correct roll table based on endian. */

    static
    {
      mRollTable = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN 
        ? mBigEndianRollTable
        : mLittleEndianRollTable;
    }

    /**
     * Inefficiently convert a byte[] into an int[].  Byte array length
     * must be a multiple of 4.
     *
     * @param bytes the source byte array
     *
     * @return the newly created integer array.
     *
     * @throws ArrayIndexOutOfBoundsException if byte array not a
     * muitlpe of 4.
     */

    public static int[] byteArrayToIntArray(byte[] bytes)
    {
      final int[] ints = new int[bytes.length / 4];
      int offset = 0;
      int roll = 0;

      for (byte b: bytes)
      {
        ints[offset] += (b & 0x000000ff) << mRollTable[roll];
        roll = (++roll) % mRollTable.length;
        if (roll == 0)
          ++offset;
      }

      return ints;
    }
    
    /**
     * Inefficiently convert a int[] into an byte[].
     *
     * @param ints the source int array
     *
     * @return the newly created byte array.
     */

    public static byte[] intArrayToByteArray(int[] ints)
    {
      final byte[] bytes = new byte[ints.length * 4];
      int offset = 0;
      for (int i: ints)
        for (int roll: mRollTable)
          bytes[offset++] = (byte)(i >> roll);

      return bytes;
    }

    /**
     * Convert an {@link IVideoPicture} to a {@link BufferedImage}.  This
     * input picture must be of type {@link IPixelFormat.Type#RGB32}.
     * This method makes several copies of the raw picture bytes, which
     * is by no means the fastest way to do this.  
     *
     * The image data ultimatly resides in java memory space, which
     * means the caller does not need to concern themselfs with memory
     * management issues.
     *
     * @param aPicture The {@link IVideoPicture} to be converted.
     * 
     * @return the resultant {@link BufferedImage} which will contain
     * the video frame.
     * 
     * @throws IllegalArgumentException if the passed {@link
     * IVideoPicture} is not of type {@link IPixelFormat.Type#RGB32}.
     */

    public static BufferedImage videoPictureToImage(IVideoPicture aPicture)
    {
      // if the picutre is not in RGB24, attempt to resample it

      if (aPicture.getPixelType() != IPixelFormat.Type.RGB32)
        throw new IllegalArgumentException(
          "The video picture is of type " + aPicture.getPixelType() +
          " but is required to be of type " + IPixelFormat.Type.RGB32);

      // get picutre parameters

      final int w = aPicture.getWidth();
      final int h = aPicture.getHeight();
      final int pixelLength = 4;
      final int lineLength = w * pixelLength; 

      // make a copy of the raw bytes in the picture and convert those
      // to integers

      final byte[] bytes = aPicture.getData().getByteArray(
        0, aPicture.getSize());
      final int[] ints = byteArrayToIntArray(bytes);

      // recreate the alpha chanel in the integer array, not quite sure
      // why this works but is required to get proper alpha channel
      // effects

      for (int i = 0; i < ints.length; ++i)
        ints[i] |= 0xff000000;
      
      // create the data buffer from the ints
      
      final DataBufferInt db = new DataBufferInt(ints, ints.length);

      // create an a sample model which matches the byte layout of the
      // image data and raster which contains the data which now can be
      // properly interpreted

      final int[] bitMasks = {0xff0000, 0xff00, 0xff, 0xff000000};
      final SampleModel sm = new SinglePixelPackedSampleModel(
        db.getDataType(), w, h, bitMasks);
      final WritableRaster wr = Raster.createWritableRaster(sm, db, null);
      
      // create a color model

      final ColorModel cm = new DirectColorModel(
        32, 0xff0000, 0xff00, 0xff, 0xff000000);

      // return a new image created from the color model and raster

      return new BufferedImage(cm, wr, false, null);
    }

   /**
     * Convert a {@link BufferedImage} to an {@link IVideoPicture} of
     * type {@link IPixelFormat.Type#RGB32}.  This is NOT the most
     * efficient way to do this conversion and is thus ripe for
     * optimization.  The {@link BufferedImage} must be a 32 RGBA type.
     * Further more the underlying data buffer of the {@link
     * BufferedImage} must be composed of types bytes or integers (which
     * is the most typical case).
     *
     * @param aImage The source {@link BufferedImage}.
     * @param aPts The presentation time stamp of the picture.
     *
     * @return An {@link IVideoPicture} in {@link
     * IPixelFormat.Type#RGB32} format.
     *
     * @throws IllegalArgumentException if the underlying data buffer of
     * the {@link BufferedImage} is composed of types other bytes or
     * integers.
     */

    public static IVideoPicture imageToVideoPicture(
      BufferedImage aImage, long aPts)
    {
      // get the image byte buffer buffer

      DataBuffer imageBuffer = aImage.getRaster().getDataBuffer();
      byte[] imageBytes;

      // handel byte buffer case

      if (imageBuffer instanceof DataBufferByte)
      {
        imageBytes = ((DataBufferByte)imageBuffer).getData();
      }

      // handel integer buffer case

      else if (imageBuffer instanceof DataBufferInt)
      {
        int[] imageInts = ((DataBufferInt)imageBuffer).getData();
        imageBytes = intArrayToByteArray(imageInts);
      }

      // if it's some other type, throw 

      else
      {
        throw new IllegalArgumentException(
          "Unsupported BufferedImage data buffer type: " + 
          imageBuffer.getDataType());
      }

      // create the video picture and get it's underling buffer

      IVideoPicture picture = IVideoPicture.make(
        IPixelFormat.Type.RGB32, aImage.getWidth(), aImage.getHeight());
      IBuffer pictureBuffer = picture.getData();
      ByteBuffer pictureByteBuffer = pictureBuffer.getByteBuffer(
        0, pictureBuffer.getBufferSize());

      // crame the image bytes into the picture
      
      pictureByteBuffer.put(imageBytes);
      pictureByteBuffer = null;
      picture.setComplete(
        true, IPixelFormat.Type.RGB32, 
        aImage.getWidth(), aImage.getHeight(), aPts);

      // return the RGB32 picture

      return picture;
    }
}