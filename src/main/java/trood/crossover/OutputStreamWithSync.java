package trood.crossover;

import java.io.IOException;
import java.io.OutputStream;


public abstract class OutputStreamWithSync extends OutputStream {
    public abstract void sync() throws IOException;
}
