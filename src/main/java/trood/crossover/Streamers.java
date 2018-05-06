package trood.crossover;

import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public final class Streamers {
    private static final Logger logger = LoggerFactory.getLogger("trood.crossover.streams");

    private Streamers() {
        throw new UnsupportedOperationException("");
    }

    public static Configuration getHDFSConfiguration() {
        final Configuration conf;
        final String paths = System.getProperty("hadoop.conf.paths");
        if (paths != null) {
            conf = new Configuration(false);
            for (String p : paths.split(",")) {
                try {
                    logger.info("Resource {} added to HDFS configuration", p);
                    conf.addResource(new URL(p));
                } catch (MalformedURLException e) {
                    logger.error("Resource {} can't be added to HDFS configuration", p, e.getMessage());
                    logger.error("Error", e);
                }
            }
        } else {
            conf = new Configuration(true);
        }
        return conf;
    }

    public static RawJsonOutputStream createJsonDownStream(String path, Boolean compress) throws IOException {
        final HdfsOutputStream hdfs = new HdfsOutputStream(path);
        try {
            if(compress) {
                return new RawJsonOutputStream(new LZ4OutputStream(hdfs));
            } else {
                return new RawJsonOutputStream(hdfs);
            }
        } catch (Throwable e) {
            try {
                hdfs.close();
            } catch (Throwable e2) {}
            throw e;
        }
    }

    public static RawJsonInputStream createJsonUpStream(String path, Boolean decompress) throws IOException {
        final HdfsInputStream hdfs = new HdfsInputStream(path);
        try {
            if(decompress) {
                return new RawJsonInputStream(new LZ4InputStream(hdfs));
            } else {
                return new RawJsonInputStream(hdfs);
            }
        } catch (Throwable e) {
            try {
                hdfs.close();
            } catch (Throwable e2) {}
            throw e;
        }
    }

}
