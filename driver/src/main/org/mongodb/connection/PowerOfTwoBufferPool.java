/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.connection;

import org.bson.ByteBuf;
import org.bson.ByteBufNIO;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class PowerOfTwoBufferPool implements BufferProvider {

    private final Map<Integer, SimplePool<ByteBuffer>> powerOfTwoToPoolMap = new HashMap<Integer, SimplePool<ByteBuffer>>();

    public PowerOfTwoBufferPool() {
        this(24);
    }

    public PowerOfTwoBufferPool(final int highestPowerOfTwo) {
        int x = 1;
        for (int i = 0; i <= highestPowerOfTwo; i++) {
            final int size = x;
            // TODO: Determine max size of each pool.
            powerOfTwoToPoolMap.put(size, new SimplePool<ByteBuffer>("ByteBufferPool-2^" + i, Integer.MAX_VALUE) {
                @Override
                protected ByteBuffer createNew() {
                    return PowerOfTwoBufferPool.this.createNew(size);
                }
            });
            x = x << 1;
        }
    }

    @Override
    public ByteBuf get(final int size) {
        final Pool<ByteBuffer> pool = powerOfTwoToPoolMap.get(roundUpToNextHighestPowerOfTwo(size));
        final ByteBuffer byteBuffer = (pool == null) ? createNew(size) : pool.get();

        byteBuffer.clear();
        byteBuffer.limit(size);
        return new PooledByteBufNIO(byteBuffer);
    }

    public void clear() {
        for (Pool<ByteBuffer> cur : powerOfTwoToPoolMap.values()) {
            cur.clear();
        }
    }

    private ByteBuffer createNew(final int size) {
        final ByteBuffer buf = ByteBuffer.allocate(size);  // TODO: configure whether this uses allocateDirect or allocate
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf;
    }

    private void release(final ByteBuffer buffer) {
        final SimplePool<ByteBuffer> pool = powerOfTwoToPoolMap.get(roundUpToNextHighestPowerOfTwo(buffer.capacity()));
        if (pool != null) {
            pool.release(buffer);
        }
    }

    static int roundUpToNextHighestPowerOfTwo(final int size) {
        int v = size;
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }

    private class PooledByteBufNIO extends ByteBufNIO {

        public PooledByteBufNIO(final ByteBuffer buf) {
            super(buf);
        }

        @Override
        public void close() {
            if (asNIO() != null) {
                release(asNIO());
                super.close();
            }
        }
    }
}