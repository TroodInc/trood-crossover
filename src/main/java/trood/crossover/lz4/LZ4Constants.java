package trood.crossover.lz4;

enum LZ4Constants {
    ;

    static final int DEFAULT_COMPRESSION_LEVEL = 8+1;
    static final int MAX_COMPRESSION_LEVEL = 16+1;

    static final int MEMORY_USAGE = 14;
    static final int NOT_COMPRESSIBLE_DETECTION_LEVEL = 6;

    static final int MIN_MATCH = 4;

    static final int HASH_LOG = MEMORY_USAGE - 2;
    static final int HASH_TABLE_SIZE = 1 << HASH_LOG;

    static final int SKIP_STRENGTH = Math.max(NOT_COMPRESSIBLE_DETECTION_LEVEL, 2);
    static final int COPY_LENGTH = 8;
    static final int LAST_LITERALS = 5;
    static final int MF_LIMIT = COPY_LENGTH + MIN_MATCH;
    static final int MIN_LENGTH = MF_LIMIT + 1;

    static final int MAX_DISTANCE = 1 << 16;

    static final int ML_BITS = 4;
    static final int ML_MASK = (1 << ML_BITS) - 1;
    static final int RUN_BITS = 8 - ML_BITS;
    static final int RUN_MASK = (1 << RUN_BITS) - 1;

    static final int LZ4_64K_LIMIT = (1 << 16) + (MF_LIMIT - 1);
    static final int HASH_LOG_64K = HASH_LOG + 1;
    static final int HASH_TABLE_SIZE_64K = 1 << HASH_LOG_64K;

    static final int HASH_LOG_HC = 15;
    static final int HASH_TABLE_SIZE_HC = 1 << HASH_LOG_HC;
    static final int OPTIMAL_ML = ML_MASK - 1 + MIN_MATCH;

}
