package trood.crossover.lz4;

import static trood.crossover.lz4.LZ4Constants.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;


public class LZ4Decompressor {

    private static class Dic implements Comparable<Dic> {
        final byte[] content;
        final int offset;
        int start;
        final int size;
        Dic(byte[] ct, int of, int st, int sz) {
            content = ct;
            offset = of;
            start = st;
            size = sz;
        }
        @Override public int compareTo(Dic o) {
            return  this.start - o.start;
        }
    }

    private List<Dic> dics = new ArrayList<Dic>();
    private int dicSize = 0;

    public int findDicByIndex(int idx) {
        final int i = Collections.binarySearch(dics, new Dic(null, 0, dicSize - idx, 0));
        if(i == -1) return i;
        return (i < 0)?-(i + 2):i;
    }

    public void dicCopyFrom(int backwardOff, int len, byte[] dest, int dOff) {
        int i = findDicByIndex(backwardOff);
        if(i == -1) throw new LZ4Exception("Offset not found: " + backwardOff);
        Dic d = dics.get(i);
        final int off = dicSize - backwardOff - d.start;
        int l = d.size - off;

        int copy = (l < len)?l:len;
        System.arraycopy(d.content, d.offset + off, dest, dOff, copy);
        len -= copy;
        dOff += copy;
        while(len > 0) {
            d = dics.get(++i);
            copy = (d.size < len)?d.size:len;
            System.arraycopy(d.content, d.offset + off, dest, dOff, copy);
            len -= copy;
            dOff += copy;
        }
    }


    public int continuingDecompress(byte[] src, final int srcOff, int srcLen, byte[] dest, final int destOff, int destLen) {
        final int result = genericDecompress(src, srcOff, srcLen, dest, destOff, destLen);
        if(dics.isEmpty()) {
            dics.add(new Dic(dest, destOff, 0, result));
        } else {
            final Dic d = dics.get(dics.size() - 1);
            dics.add(new Dic(dest, destOff, d.start + d.size, result));
        }
        dicSize += result;

        if(dicSize > 128 * 1024) {
            final ListIterator<Dic> itr = dics.listIterator();
            int delta = 0;
            while(itr.hasNext()) {
                Dic d = itr.next();
                if(d.start + d.size < 64 * 1024) {
                    break;
                } else {
                    itr.remove();
                    delta += d.size;
                }
            }
            if(delta > 0) {
                dicSize -= delta;
                for(Dic item : dics) {
                    item.start -= delta;
                }
            }
        }
        return result;
    }

    public int genericDecompress(byte[] src, final int srcOff, int srcLen, byte[] dest, final int destOff, int destLen) {
        if (destLen == 0) {
            return (srcLen == 1 && src[0] == 0)?0:-1;
        }

        final int srcEnd = srcOff + srcLen;
        final int destEnd = destOff + destLen;
        int sOff = srcOff;
        int dOff = destOff;

        while (true) {
            final int token = src[sOff++] & 0xFF;

            int literalLen = token >>> ML_BITS;
            if (literalLen == RUN_MASK) {
                byte len = (byte) 0xFF;
                while (sOff < srcEnd - literalLen && (len = src[sOff++]) == (byte) 0xFF) {
                    literalLen += 0xFF;
                }
                literalLen += len & 0xFF;
            }

            final int literalCopyEnd = dOff + literalLen;
            if((literalCopyEnd > destEnd - MF_LIMIT) || (sOff + literalLen > srcEnd - (2 + 1 + LAST_LITERALS))) {
                if (sOff + literalLen != srcEnd || literalCopyEnd > destEnd) {
                    throw new LZ4Exception("Malformed input at " + sOff);
                }
                System.arraycopy(src, sOff, dest, dOff, literalLen);
                sOff += literalLen;
                dOff = literalCopyEnd;
                break;
            }

            System.arraycopy(src, sOff, dest, dOff, literalLen);
            sOff += literalLen;
            dOff = literalCopyEnd;

            final int matchDec = (src[sOff++] & 0xFF) | ((src[sOff++] & 0xFF) << 8);
            if(matchDec > dicSize + dOff - destOff) {
                throw new LZ4Exception("Wrong match offset: matchDec=" + matchDec + ", dicSize=" + dicSize + ", dOff=" + dOff + ", destOff=" + destOff);
            }

            int matchLen = token & ML_MASK;
            if (matchLen == ML_MASK) {
                byte len = (byte) 0xFF;
                while ((len = src[sOff++]) == (byte) 0xFF) {
                    if(sOff > srcEnd - LAST_LITERALS) {
                        throw new LZ4Exception("Malformed input at " + sOff);
                    }
                    matchLen += 0xFF;
                }
                if(sOff > srcEnd - LAST_LITERALS) {
                    throw new LZ4Exception("Malformed input at " + sOff);
                }
                matchLen += len & 0xFF;
            }
            matchLen += MIN_MATCH;

            final int matchCopyEnd = dOff + matchLen;
            if(matchDec > dOff - destOff) {
                if (matchCopyEnd > destEnd - LAST_LITERALS) {
                    throw new LZ4Exception("Malformed input at " + sOff + "(destOff=" + destOff + ", destLen=" + destLen + ")");
                }
                if(matchLen <= matchDec - dOff + destOff) {
                    dicCopyFrom(matchDec - dOff + destOff, matchLen, dest, dOff); 
                } else {
                    final int copyLen = matchDec - dOff + destOff;
                    final int restLen = matchLen - copyLen;
                    dicCopyFrom(matchDec - dOff + destOff, copyLen, dest, dOff); 
                    System.arraycopy(dest, 0, dest, dOff + copyLen, restLen);
                }
            } else {
                if (matchCopyEnd > destEnd - LAST_LITERALS) {
                    throw new LZ4Exception("Malformed input at " + sOff);
                }
                System.arraycopy(dest, dOff - matchDec, dest, dOff, matchLen);
            }
            dOff = matchCopyEnd;
        }
        return dOff - destOff;
    }

}
