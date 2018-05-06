package trood.crossover;

import java.io.IOException;


public class RawJsonOutputStream extends OutputStreamWithSync {
    private final OutputStreamWithSync output;
    private boolean closed = false;

    public RawJsonOutputStream(OutputStreamWithSync os) {
        if(os == null) {
            throw new IllegalArgumentException("output stream is null");
        }
        output = os;
    }

    public void write(int b) throws IOException {
        throwIfClosed();
        output.write(b);
    }

    public void writeRawObject(String obj) throws IOException {
        throwIfClosed();
        output.write(obj.getBytes("UTF-8"));
    }

    private void throwIfClosed() throws IOException {
        if(closed) {
            throw new IOException("Raw json stream is closed");
        }
    }

    @Override public void flush() throws IOException {
        output.flush();
    }

    @Override public void close() throws IOException {
        throwIfClosed();
        closed = true;
        output.close();
    }

    @Override
    public void sync() throws IOException {
        throwIfClosed();
        output.sync();
    }

}
