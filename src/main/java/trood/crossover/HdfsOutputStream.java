package trood.crossover;

import java.io.IOException;
import java.util.EnumSet;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CreateFlag;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Options.Rename;
import org.apache.hadoop.fs.Path;


public class HdfsOutputStream extends OutputStreamWithSync {
    private final FSDataOutputStream output;
    private boolean closed = false;
    private final boolean throughTemp;
    private final FileSystem fs;
    final private Path path;
    private Path tmpPath = null;

    public HdfsOutputStream(String dst, Boolean truncate) throws IOException {
        final Configuration conf = Streamers.getHDFSConfiguration();
        fs = FileSystem.get(conf);
        path = new Path(dst);
        if(fs.exists(path)) {
            if(truncate) {
                throughTemp = true;
                tmpPath = new Path(dst + "_tmp");
                output = fs.create(tmpPath, false);
            } else {
                throughTemp = false;
                output = fs.append(path);
            }
        } else {
            throughTemp = false;
            output = fs.create(path, false);
        }
    }

    public HdfsOutputStream(String dst) throws IOException {
        this(dst, false);
    }

    @Override
    public void write(int b) throws IOException {
        throwIfClosed();
        output.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        throwIfClosed();
        output.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        output.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        throwIfClosed();
        output.close();
        if(throughTemp) {
            fs.delete(path, false);
            if(!fs.rename(tmpPath, path)) {
                throw new IOException("Can not rename file [" + tmpPath.toUri() + "] to [" + path.toUri() + "].");
            }
        }
        closed = true;
    }

    @Override
    public void flush() throws IOException {
        throwIfClosed();
        output.flush();
    }

    @Override public void sync() throws IOException {
        throwIfClosed();
        output.hsync();
    }

    private void throwIfClosed() throws IOException {
        if(closed) {
            throw new IOException("HDFS stream is closed");
        }
    }
}
