/*
 * This file is part of Xuggler.
 * 
 * Xuggler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Xuggler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public
 * License along with Xuggler.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.xuggle.red5;

import com.xuggle.xuggler.IPacket;

/**
 * A base listener class that implements all methods as pass-through methods.
 * 
 * This can be helpful if you don't want to override every single method on the listener.
 */
public class PacketListener implements IPacketListener {

  public IPacket postEncode(IPacket aObject)
  {
    return aObject;
  }

  public IPacket preDecode(IPacket aObject)
  {
    return aObject;
  }
  
}
