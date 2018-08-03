package trood.crossover;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;


public class HdfsInputStream extends InputStream {
    private final FSDataInputStream input;
    private boolean closed = false;

    public HdfsInputStream(final String src) throws IOException {
        final Configuration conf = Streamers.getHDFSConfiguration();
        final FileSystem fs = FileSystem.get(conf);
        final Path path = new Path(src);
        System.out.print("HDFS working directory is:");
        System.out.print(fs.getWorkingDirectory());
        input = fs.open(path);
    }

    @Override
    public int available() throws IOException {
        throwIfClosed();
        return input.available();
    }

    @Override
    public void close() throws IOException {
        throwIfClosed();
        input.close();
        closed = true;
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
    public int read() throws IOException {
        throwIfClosed();
        return input.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        throwIfClosed();
        return input.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        throwIfClosed();
        return input.read(b, off, len);
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new UnsupportedOperationException("reset is unsupported");
    }

    @Override
    public long skip(long n) throws IOException {
        throwIfClosed();
        return input.skip(n);
    }

    private void throwIfClosed() throws IOException {
        if(closed) {
            throw new IOException("HDFS stream is closed");
        }
    }

}

