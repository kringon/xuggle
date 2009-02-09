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
#include <com/xuggle/ferry/Logger.h>
#include <com/xuggle/ferry/Buffer.h>
#include <com/xuggle/xuggler/Packet.h>

// for memset
#include <string.h>

VS_LOG_SETUP(VS_CPP_PACKAGE);

namespace com { namespace xuggle { namespace xuggler
  {

  using namespace com::xuggle::ferry;
  
  Packet :: Packet()
  {
    mPacket = (AVPacket*)av_malloc(sizeof(AVPacket));
    if (mPacket)
    {
      // initialize because ffmpeg doesn't
      av_init_packet(mPacket);
      mPacket->data = 0;
      mPacket->size = 0;
      mPacket->destruct = av_destruct_packet_nofree;
    }
    mIsComplete = false;
  }

  Packet :: ~Packet()
  {
    if (mPacket) {
      reset();
      av_free(mPacket);
    }
    mPacket = 0;
  }

  int64_t
  Packet :: getPts()
  {
    return (mPacket ? mPacket->pts : (int64_t)-1);
  }
  
  void
  Packet :: setPts(int64_t aPts)
  {
    if (mPacket) mPacket->pts = aPts;
  }
  
  int64_t
  Packet :: getDts()
  {
    return (mPacket ? mPacket->dts : (int64_t)-1);
  }
  
  void
  Packet :: setDts(int64_t aDts)
  {
    if (mPacket) mPacket->dts = aDts;
  }
  
  int32_t
  Packet :: getSize()
  {
    return (mPacket ? mPacket->size: (int32_t)-1);
  }
  int32_t
  Packet :: getMaxSize()
  {
    return (mBuffer ? mBuffer->getBufferSize() : -1);
  }
  int32_t
  Packet :: getStreamIndex()
  {
    return (mPacket ? mPacket->stream_index: (int32_t)-1);
  }
  int32_t
  Packet :: getFlags()
  {
    return (mPacket ? mPacket->flags: (int32_t)-1);
  }
  bool
  Packet :: isKeyPacket()
  {
    return (mPacket ? mPacket->flags & PKT_FLAG_KEY : false);
  }

  void
  Packet :: setKeyPacket(bool bKeyPacket)
  {
    if (mPacket)
    {
      if (bKeyPacket)
        mPacket->flags |= PKT_FLAG_KEY;
      else
        mPacket->flags = 0;
    }
  }

  void
  Packet :: setFlags(int32_t flags)
  {
    if (mPacket)
      mPacket->flags = flags;
  }

  void
  Packet :: setComplete(bool complete, int32_t size)
  {
    mIsComplete = complete;
    if (mIsComplete)
    {
      if (mPacket)
        mPacket->size = size;
    }
  }
  
  void
  Packet :: setStreamIndex(int32_t streamIndex)
  {
    if (mPacket)
      mPacket->stream_index = streamIndex;
  }
  int64_t
  Packet :: getDuration()
  {
    return (mPacket ? mPacket->duration: (int64_t)-1);
  }
  int64_t
  Packet :: getPosition()
  {
    return (mPacket ? mPacket->pos: (int64_t)-1);
  }
  com::xuggle::ferry::IBuffer *
  Packet :: getData()
  {
    return mBuffer.get();
  }
  void
  Packet :: wrapAVPacket(AVPacket* pkt)
  {
    // WE ALWAYS COPY the data; Let Ffmpeg do what it wants
    // to with it's own memory.
    VS_ASSERT(mPacket, "No packet?");
    VS_ASSERT(mPacket->destruct == av_destruct_packet_nofree,
        "Who's managing this?");
    
    // Make sure we have a buffer at least as large as this packet
    // This overwrites data, which we'll patch below.
    (void) this->allocateNewPayload(pkt->size);

    // Keep a copy of this, because we're going to nuke
    // it temorarily.
    uint8_t* data_buf = mPacket->data;
    
    // copy all data members, including data and size,
    // but we'll overwrite those next.
    *mPacket = *pkt;
    // Reset the data buf.
    mPacket->data = data_buf;
    mPacket->destruct = av_destruct_packet_nofree;
    mPacket->size = pkt->size;

    // Copy the contents of the new packet into data.
    memcpy(mPacket->data, pkt->data, pkt->size);
    
    // And assume we're now complete.
    setComplete(true, mPacket->size);
  }

  void
  Packet :: reset()
  {
    if (mPacket) {
      av_free_packet(mPacket);
      av_init_packet(mPacket);
      mPacket->destruct = av_destruct_packet_nofree;
    }
    setComplete(false, 0);
    // Don't reset the buffer though; we can potentially reuse it.
  }

  Packet*
  Packet :: make (int32_t payloadSize)
  {
    Packet *retval= 0;
    retval = Packet::make();
    if (retval)
    {
      if (retval->allocateNewPayload(payloadSize) >= 0)
      {
        // success
      } else {
        // we failed.
        VS_REF_RELEASE(retval);
      }
    }
    return retval;
  }
  Packet*
  Packet :: make (com::xuggle::ferry::IBuffer* buffer)
  {
    Packet *retval= 0;
    retval = Packet::make();
    if (retval)
    {
      retval->wrapBuffer(buffer);
    }
    return retval;
  }
  int32_t
  Packet :: allocateNewPayload(int32_t payloadSize)
  {
    int32_t retval = -1;
    reset();
    uint8_t* payload = 0;

    // Some FFMPEG encoders will read past the end of a
    // buffer, so you need to allocate extra; yuck.
    if (!mBuffer || mBuffer->getBufferSize() < payloadSize)
    {
      // buffer isn't big enough; we need to make a new one.
      payload = (uint8_t*) av_malloc(payloadSize+FF_INPUT_BUFFER_PADDING_SIZE);
      VS_ASSERT(payload, "Could not allocate memory");
      if (payload)
      {
        // we don't use the JVM for packets because Ffmpeg is REAL squirly about that
        mBuffer = Buffer::make(0, payload,
            payloadSize,
            Packet::freeAVBuffer, 0);
        // and memset the padding area.
        memset(payload + payloadSize,
            0,
            FF_INPUT_BUFFER_PADDING_SIZE);
      }
    } else {
      payload = (uint8_t*)mBuffer->getBytes(0, payloadSize);
    }
    VS_ASSERT(mPacket, "Should already have a packet");
    VS_ASSERT(mBuffer, "Should have allocated a buffer");
    VS_ASSERT(payload, "Should have allocated a payload");
    if (mBuffer && mPacket)
    {
      mPacket->destruct = av_destruct_packet_nofree;
      mPacket->data = payload;

      // And start out at zero.
      mPacket->size = 0;
      this->setComplete(false, 0);

      retval = 0;
    }
    return retval;
  }

  void
  Packet :: wrapBuffer(IBuffer *buffer)
  {
    if (buffer != mBuffer.value())
    {
      reset();
      // and acquire this buffer.
      mBuffer.reset(buffer, true);
    }
    if (mBuffer)
    {
      // and patch up our AVPacket
      VS_ASSERT(mPacket, "No AVPacket");
      if (mPacket)
      {
        mPacket->size = mBuffer->getBufferSize();
        mPacket->data = (uint8_t*)mBuffer->getBytes(0, mPacket->size);
        mPacket->destruct = av_destruct_packet_nofree;
        // And assume we're now complete.
        setComplete(true, mPacket->size);
      }
    }
  }
  bool
  Packet :: isComplete()
  {
    return mIsComplete && mPacket->data;
  }
  
  void
  Packet :: freeAVBuffer(void * buf, void * closure)
  {
    // We know that FFMPEG allocated this with av_malloc, but
    // that might change in future versions; so this is
    // inherently somewhat dangerous.
    (void) closure;
    av_free(buf);
  }

  }}}