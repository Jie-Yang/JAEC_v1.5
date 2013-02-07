/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package testmain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *
 * @author Jie Yang
 */
public class ShortAudioFrame {

    private byte[] frameBytes;
    private boolean isBigEndian;

    public ShortAudioFrame(byte[] frameBytes, boolean isBigEndian) {
        this.frameBytes = new byte[frameBytes.length];
        this.isBigEndian = isBigEndian;
        System.arraycopy(frameBytes, 0, this.frameBytes, 0, frameBytes.length);
    }

    public ShortAudioFrame(short signal, int frameBytesLengh, boolean isBigEndian) {
        this.frameBytes = new byte[frameBytesLengh];
        this.isBigEndian = isBigEndian;

        ByteBuffer bb = ByteBuffer.allocate(frameBytesLengh);
        byte[] twotwoB = new byte[frameBytesLengh];
        if (this.isBigEndian) {
            bb.order(ByteOrder.BIG_ENDIAN);
            ByteBuffer.wrap(twotwoB).order(ByteOrder.BIG_ENDIAN).asShortBuffer().put(signal);
            System.arraycopy(twotwoB, 0, this.frameBytes, 0, frameBytesLengh);
        } else {
            bb.order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer.wrap(twotwoB).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(signal);
            System.arraycopy(twotwoB, 0, this.frameBytes, 0, frameBytesLengh);
        }
    }

    public byte[] getBytes() {
        return this.frameBytes;
    }


    public short getShort() {
        ByteBuffer bb = ByteBuffer.allocate(2);
        if (this.isBigEndian) {
            bb.order(ByteOrder.BIG_ENDIAN);
            // add two 0 bytes to convert from two bytes endian to four bytes endian
            bb.put(this.frameBytes[0]);
            bb.put(this.frameBytes[1]);
        } else {
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.put(this.frameBytes[0]);
            bb.put(this.frameBytes[1]);
        }
        bb.rewind();
        short result = bb.getShort();
        return result;
    }
}
