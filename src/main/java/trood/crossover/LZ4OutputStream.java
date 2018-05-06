package trood.crossover;

import java.io.IOException;

import trood.crossover.lz4.LZ4Compressor;

public class LZ4OutputStream extends OutputStreamWithSync {
    final static byte[] MAGIC_WORD = {0x4C, 0x5A, 0x34, 0x42, 0x4C, 0x30, 0x4B};
    private final OutputStreamWithSync output;
    private LZ4Compressor compressor;
    private int blockSize;
    private final byte[] buffer;
    private int bufOff;
    private boolean closed = false;

    public LZ4OutputStream(final OutputStreamWithSync os) {
        output = os;
        compressor = new LZ4Compressor();
        buffer = new byte[1024 * 1024];
        System.arraycopy(MAGIC_WORD, 0, buffer, 0, MAGIC_WORD.length);
        bufOff = MAGIC_WORD.length;
        blockSize = bufOff;
    }

    @Override
    public void write(int b) throws IOException {
        throwIfClosed();
        write(new byte[]{(byte)b});
    }

    @Override
    public void write(byte[] b) throws IOException {
        throwIfClosed();
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        throwIfClosed();
        if(blockSize + len > 1024 * 1024) {
            compressor = new LZ4Compressor();
            System.arraycopy(MAGIC_WORD, 0, buffer, 0, MAGIC_WORD.length);
            bufOff = MAGIC_WORD.length;
            blockSize = 0;
        }
        final int result = compressor.continuingCompress(b, off, len, buffer, bufOff + 2, 1024 * 1024);
        if(result > 0) {
            buffer[bufOff++] = (byte)(result & 0xFF);
            buffer[bufOff++] = (byte)((result >>> 8) & 0xFF);
            bufOff += result;
            output.write(buffer, 0, bufOff);
            bufOff = 0;
            blockSize += len + 2;
        }
    }

    @Override
    public void flush() throws IOException {
        throwIfClosed();
        super.flush();
    }

    @Override
    public void sync() throws IOException {
        throwIfClosed();
        flush();
        output.sync();
    }

    @Override public void close() throws IOException {
        throwIfClosed();
        closed = true;
        output.close();
    }

    private void throwIfClosed() throws IOException {
        if(closed) {
            throw new IOException("Raw json stream is closed");
        }
    }

}

