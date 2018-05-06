package trood.crossover.lz4;

import java.util.Collections;
import java.util.LinkedList;
import java.util.ListIterator;

import static trood.crossover.lz4.LZ4Constants.*;


public class LZ4Compressor {

    private static class Dic implements Comparable<Dic> {
        final byte[] content;
        int start;
        final int off;
        final int size;
        final byte overlay;
        Dic(byte[] ct, int st, int of, int sz, byte ov) {
            content = ct;
            start = st;
            off = of;
            size = sz;
            overlay = ov;
        }
        @Override public int compareTo(Dic o) {
            return this.start - o.start;
        }
    }

    private LinkedList<Dic> dics = new LinkedList<Dic>();

    private  Boolean dicCompareRawInt(int idx, byte[] src, int sOff) {
        final int i = Collections.binarySearch(dics, new Dic(null, idx, 0, 0, (byte)0x00));
        int j = (i < 0)?-(i + 2):i;
        Dic d = dics.get(j);
        int k = idx - d.start;
        if(idx + 3 - d.start < d.size) {
            return (d.content[d.off+k] == src[sOff]) && (d.content[d.off+k+1] == src[sOff+1]) && (d.content[d.off+k+2] == src[sOff+2]) && (d.content[d.off+k+3] == src[sOff+3]);
        } else {
            for(int o = 0; o < 4; o++) {
                if(k + o >= d.size) {
                    d = dics.get(++j);
                    k = idx - d.start;
                }
                if(d.content[d.off + k + o] != src[sOff + o])
                    return false;
            }
            return true;
        }
    }
    private  int findDicByIndex(int idx) {
        final int i = Collections.binarySearch(dics, new Dic(null, idx, 0, 0, (byte)0x00));
        if(i == -1) return i;
        int j = (i < 0)?-(i + 2):i;
        return j;
    }
    private  int dicCommonBytesBackward(byte[] src, int sOff, int ref, int anchor) {
        int count  = 0;
        int j = findDicByIndex(ref - 1);
        if(j == -1) return count;
        Dic d = dics.get(j);

        while(sOff > anchor && src[--sOff] == d.content[d.off + --ref - d.start]) {
            count++;
            if(ref < d.start) {
                if(j == 0 || sOff == anchor || d.overlay != src[sOff - 1]) return count;
                d = dics.get(--j);
            }
        }
        return count;
    }
    private  int dicCommonBytesForward(byte[] src, int sOff, int ref, int srcLimit, int refLimit) {
        int count = 0;
        int j = findDicByIndex(ref);
        if(j == -1) return count;
        Dic d = dics.get(j);
        if(ref >= d.start + d.size) return count;

        while(sOff < srcLimit && ref < refLimit && src[sOff++] == d.content[d.off + ref++ - d.start]) {
            count++;
            if(ref >= d.start + d.size) {
                if(j + 1 == dics.size()) return count;
                d = dics.get(++j);
            }
        }
        return count;
    }


    private final int[] hashTable = new int[HASH_TABLE_SIZE];
    private int currentOffset = 0;
    private int dicSize = 0;

    private void normalizeDic() {
        if(dicSize > 128 * 1024) {
            final int delta = currentOffset - 64 * 1024;
            final ListIterator<Dic> itr = dics.listIterator();
            while(itr.hasNext()) {
                Dic d = itr.next();
                if(d.start + d.size - 1 < delta) {
                    itr.remove();
                } else {
                    break;
                }
            }
            for(int i = 0; i < HASH_TABLE_SIZE; i ++) {
                if(hashTable[i] < delta) hashTable[i] = 0;
                else hashTable[i] -= delta;
            }
            currentOffset = 64 * 1024;
            dicSize = 0;
            for(Dic item : dics) {
                dicSize += item.size;
                item.start -= delta;
            }
        }
    }

    public int continuingCompress(byte[] src, int srcOff, int srcLen, byte[] dest, final int destOff, int maxDestLen) {
        normalizeDic();
        final int len = dics.size();
        if(len == 0) {
            dics.add(new Dic(src, currentOffset, srcOff, srcLen, (byte)0x00));
        } else {
            final Dic d = dics.get(len - 1);
            dics.add(new Dic(src, currentOffset, srcOff, srcLen, d.content[d.off + d.size - 1]));
        }

        final int result = genericCompress(src, srcOff, srcLen, dest, destOff, maxDestLen);
        currentOffset += srcLen;
        dicSize += srcLen;
        return result;
    }

    public int genericCompress(byte[] src, final int srcOff, int srcLen, byte[] dest, final int destOff, int maxDestLen) {
        final int destEnd = destOff + maxDestLen;
        final int srcEnd = srcOff + srcLen;
        final int srcLimit = srcEnd - LAST_LITERALS;
        final int mflimit = srcEnd - MF_LIMIT;

        int sOff = srcOff, dOff = destOff;
        int anchor = sOff++;

main:
        while (true) {
            int forwardOff = sOff;
            int ref;
            int step = 1;
            int searchMatchNb = 1 << SKIP_STRENGTH;
            int back;
            do {
                sOff = forwardOff;
                forwardOff += step;
                step = searchMatchNb++ >>> SKIP_STRENGTH;

                if (forwardOff > mflimit) {
                    break main;
                }

                final int h= hash((src[sOff] & 0xFF) | ((src[sOff+1] & 0xFF) << 8) | ((src[sOff+2] & 0xFF) << 16) | ((src[sOff+3] & 0xFF) << 24));
                ref = hashTable[h];
                back = sOff + currentOffset - ref;
                hashTable[h] = sOff + currentOffset;
            } while (back >= MAX_DISTANCE || !dicCompareRawInt(ref, src, sOff));


            final int excess = dicCommonBytesBackward(src, sOff, ref, anchor);
            sOff -= excess;
            ref -= excess;
            final int runLen = sOff - anchor;

            int tokenOff = dOff++;
            if (dOff + runLen + (2 + 1 + LAST_LITERALS) + (runLen >>> 8) > destEnd) {
                throw new LZ4Exception("maxDestLen is too small");
            }

            if (runLen >= RUN_MASK) {
                dest[tokenOff] = (byte)(RUN_MASK << ML_BITS);
                dOff = writeLen(runLen - RUN_MASK, dest, dOff);
            } else {
                dest[tokenOff] = (byte)(runLen << ML_BITS);
            }

            System.arraycopy(src, anchor, dest, dOff, runLen);
            dOff += runLen;

            while (true) {
                dest[dOff++] = (byte) back;
                dest[dOff++] = (byte) (back >>> 8);

                sOff += MIN_MATCH;
                final int matchLen = dicCommonBytesForward(src, sOff, ref + MIN_MATCH, srcLimit, currentOffset + anchor);
                if (dOff + (1 + LAST_LITERALS) + (matchLen >>> 8) > destEnd) {
                    throw new LZ4Exception("maxDestLen is too small");
                }
                sOff += matchLen;

                if (matchLen >= ML_MASK) {
                    dest[tokenOff] = (byte) (dest[tokenOff] | ML_MASK);
                    dOff = writeLen(matchLen - ML_MASK, dest, dOff);
                } else {
                    dest[tokenOff] = (byte)(dest[tokenOff] | matchLen);
                }

                if (sOff > mflimit) {
                    anchor = sOff;
                    break main;
                }

                hashTable[hash((src[sOff-2] & 0xFF) | 
                        ((src[sOff-1] & 0xFF) << 8) | 
                        ((src[sOff] & 0xFF) << 16) | 
                        ((src[sOff+1] & 0xFF) << 24))] = sOff - 2 + currentOffset;

                final int h = hash((src[sOff] & 0xFF) | 
                        ((src[sOff+1] & 0xFF) << 8) | 
                        ((src[sOff+2] & 0xFF) << 16) | 
                        ((src[sOff+3] & 0xFF) << 24));
                ref = hashTable[h];
                hashTable[h] = sOff + currentOffset;
                back = sOff + currentOffset - ref;

                if (back >= MAX_DISTANCE || !dicCompareRawInt(ref, src, sOff)) {
                    break;
                }

                tokenOff = dOff++;
                dest[tokenOff] = 0;
            }

            anchor = sOff++;
        }

        dOff = lastLiterals(src, anchor, srcEnd - anchor, dest, dOff, destEnd);
        return dOff - destOff;
    }

    private int lastLiterals(byte[] src, int sOff, int srcLen, byte[] dest, int dOff, int destEnd) {
        final int runLen = srcLen;

        if (dOff + runLen + 1 + (runLen + 255 - RUN_MASK) / 255 > destEnd) {
            throw new LZ4Exception();
        }

        if (runLen >= RUN_MASK) {
            dest[dOff++] = (byte) (RUN_MASK << ML_BITS);
            dOff = writeLen(runLen - RUN_MASK, dest, dOff);
        } else {
            dest[dOff++] = (byte) (runLen << ML_BITS);
        }
        System.arraycopy(src, sOff, dest, dOff, runLen);
        dOff += runLen;

        return dOff;
    }

    private int writeLen(int len, byte[] dest, int dOff) {
        while (len >= 0xFF) {
            dest[dOff++] = (byte) 0xFF;
            len -= 0xFF;
        }
        dest[dOff++] = (byte) len;
        return dOff;
    }

    private static int hash(int i) {
        return (i * -1640531535) >>> ((MIN_MATCH * 8) - HASH_LOG);
    }

}

