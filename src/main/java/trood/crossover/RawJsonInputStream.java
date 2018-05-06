package trood.crossover;

import java.io.IOException;
import java.io.InputStream;

public class RawJsonInputStream extends InputStream {
    private final static int MAX_OBJ_LEN = 1024 * 1024;
    private final static int MAX_BUF_LEN = 1024 * 1024;
    private final InputStream input;
    private final byte[] buffer = new byte[MAX_BUF_LEN]; 
    private int endBufPos = 0;
    private int readBufPos = 0;
    private final byte[] obj = new byte[MAX_OBJ_LEN]; 
    private int objPos = 0;
    private int readObjPos = 0;
    private final byte[] single = new byte[1];


    public RawJsonInputStream(final InputStream is) {
        input = is;
    }

    @Override
    public int available() throws IOException {
        return input.available();
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    @Override
    public int read() throws IOException {
        int len;
        while((len = read(single)) == 0);
        if(len == -1) return -1;
        else return single[0];
    }

    @Override
    public int read(byte[] b) throws IOException {
        if(b.length == 0) return 0;
        else return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if(len == 0)
            return 0;

        if(b.length - off < len) 
            throw new IllegalArgumentException("array is too small");

        if(readObjPos == objPos) {
            if(readObj() < 0)
                return -1;
        }

        if(len > objPos - readObjPos) {
            final int actualLen = objPos - readObjPos;
            System.arraycopy(obj, readObjPos, b, off, actualLen);
            readObjPos = objPos;
            return actualLen;
        } else {
            System.arraycopy(obj, readObjPos, b, off, len);
            readObjPos += len;
            return len;
        }
    }

    public String readRawObject() throws IOException {
        if(readObjPos == objPos) {
            if(readObj() < 0)
                return null;
        }
        final String result = new String(obj, readObjPos, objPos - readObjPos);
        readObjPos = objPos;
        return result;
    }

    private int readObj() throws IOException {
        int len;
        if(endBufPos == 0 || readBufPos == endBufPos) {
            while((len = input.read(buffer)) == 0);
            if(len == -1) return -1;
            endBufPos = len;
            readBufPos = 0;
        }

        readObjPos = 0;
        objPos = 0;

        if(buffer[readBufPos] != 0x7B) 
            throw new IllegalStateException("start bracket of object not found(readBufPos=" + readBufPos + ", endBufPos=" + endBufPos +")");
        int brackets = 1;
        boolean insideString = false;
        byte previous = 0x7B;
        
        int off = readBufPos + 1;
        while(brackets != 0) {
            if(off == endBufPos) {
                if(endBufPos - readObjPos > MAX_OBJ_LEN - objPos)
                    throw new IllegalStateException("object is too big");
                final int copyLen = endBufPos - readBufPos;
                System.arraycopy(buffer, readBufPos, obj, objPos, copyLen);
                objPos += copyLen;

                while((len = input.read(buffer)) == 0) ;
                if(len == -1) return -1;
                endBufPos = len;
                readBufPos = 0;
                off = 0;
            }

            final byte b = buffer[off++];
            if(insideString) {
                if(b == 0x22 && previous != 0x5C) {
                    insideString = false;
                }
            } else {
                if(b == 0x22) {
                    insideString = true;
                } else if(b == 0x7B) {
                    brackets++;
                } else if(b == 0x7D) {
                    brackets--;
                }
            }
            previous = b;
        }

        final int copyLen = off - readBufPos;
        System.arraycopy(buffer, readObjPos, obj, objPos, copyLen);
        objPos += copyLen;
        readBufPos += copyLen;
        return objPos;
    }

    @Override
    public long skip(long n) throws IOException {
        if(n <= objPos - readObjPos) {
            readObjPos += n;
            return n;
        } else {
            final int result = objPos - readObjPos;
            readObjPos = objPos;
            return result;
        }
    }


    @Override
    public synchronized void mark(int readlimit) {
        throw new UnsupportedOperationException("mark is unsupported");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new UnsupportedOperationException("reset is unsupported");
    }
}

