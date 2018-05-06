package trood.crossover;

import java.io.IOException;
import java.io.InputStream;

import trood.crossover.lz4.LZ4Decompressor;

public class LZ4InputStream extends InputStream {
    final static byte[] MAGIC_WORD = {0x4C, 0x5A, 0x34, 0x42, 0x4C, 0x30, 0x4B};
    private final InputStream input;
    private LZ4Decompressor decompressor;
    private byte[] buffer = new byte[1024 * 1024];
    private final int decSize = 1024 * 1024;
    private final byte[] decompressed = new byte[decSize];
    private int decPos = 0;
    private int readBufferPos = 0;
    private final byte[] single = new byte[1];
    private boolean closed = false;

    public LZ4InputStream(final InputStream is) {
        input = is;
        decompressor = new LZ4Decompressor();
    }

    @Override
    public int available() throws IOException {
        throwIfClosed();
        return input.available();
    }

    @Override
    public void close() throws IOException {
        throwIfClosed();
        decompressor = null;
        input.close();
    }

    @Override
    public int read() throws IOException {
        throwIfClosed();
        int result;
        while((result = read(single)) == 0);
        if(result == -1) return -1;
        else return single[0];
    }

    @Override
    public int read(byte[] b) throws IOException {
        throwIfClosed();
        if(b.length == 0)
            return 0;
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        throwIfClosed();
        if(len == 0)
            return 0;

        if(b.length - off < len) 
            throw new IllegalArgumentException("array is too small");

        if(readBufferPos == decPos) {
            if(readElement() < 0)
                return -1;
        }

        if(len > decPos - readBufferPos) {
            final int actualLen = decPos - readBufferPos;
            System.arraycopy(decompressed, readBufferPos, b, off, actualLen);
            readBufferPos = decPos;
            return actualLen;
        } else {
            System.arraycopy(decompressed, readBufferPos, b, off, len);
            readBufferPos += len;
            return len;
        }
    }

    private int readElement() throws IOException {
        int one = input.read();
        if (one == -1) { 
            return -1;
        }
        int two = input.read();
        if (two == -1) { 
            throw new IllegalStateException("Incorrect size value");
        }
        int bufOff = 0;

        if((byte)(one & 0xFF) == MAGIC_WORD[0] && (byte)(two & 0xFF) == MAGIC_WORD[1]) {
            for(int i = 0; i < MAGIC_WORD.length - 2;) {
                int res = input.read(buffer, i, MAGIC_WORD.length - 2 - i);
                if (res == -1) { 
                    throw new IllegalStateException("Unexpected EOF while magic word checking");
                }
                i += res;
            }
            boolean magic = true;
            for (int i = 0; i < MAGIC_WORD.length - 2; i++) {
                magic &= buffer[i] == MAGIC_WORD[i + 2];
            }
            if(magic) {
                one = input.read();
                if (one == -1) { 
                    throw new IllegalStateException("Incorrect size value");
                }
                two = input.read();
                if (two == -1) { 
                    throw new IllegalStateException("Incorrect size value");
                }
                decompressor = new LZ4Decompressor();
                decPos = 0;
                readBufferPos = 0;
            }
            else {
                bufOff = MAGIC_WORD.length - 2;
            }
        }

        final int size = (one & 0xFF) | ((two & 0xFF) << 8);
        for(int i = bufOff; i < size;) {
            int res = input.read(buffer, i, size- i);
            if (res == -1) { 
                throw new IllegalStateException("Unexpected EOF");
            }
            i += res;
        }
        
        int dec = decompressor.continuingDecompress(buffer, 0, size, decompressed, decPos, decSize - decPos);
        if(dec < 0)
            throw new IllegalStateException("Mailformed input data");
        decPos += dec;
        return decSize;
    }

    @Override
    public long skip(long n) throws IOException {
        throwIfClosed();
        if(n > decPos - readBufferPos) {
            final int result = decPos - readBufferPos;
            readBufferPos = decPos;
            return result;
            
        }
        readBufferPos += n;
        return n;
    }

    private void throwIfClosed() throws IOException {
        if(closed) {
            throw new IOException("HDFS stream is closed");
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

