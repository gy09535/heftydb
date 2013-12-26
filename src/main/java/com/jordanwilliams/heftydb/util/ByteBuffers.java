/*
 * Copyright (c) 2013. Jordan Williams
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jordanwilliams.heftydb.util;

import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class ByteBuffers {

    public static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private static Constructor directBufferConstructor;

    static {
        try {
            Constructor[] constructors = Class.forName("java.nio.DirectByteBuffer").getDeclaredConstructors();
            directBufferConstructor = constructors[0];
            directBufferConstructor.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ByteBuffer rawDirectBuffer(long address, int size) {
        try {
            return (ByteBuffer) directBufferConstructor.newInstance(address, size);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ByteBuffer fromString(String string) {
        return ByteBuffer.wrap(string.getBytes(Charset.defaultCharset()));
    }

    public static void free(ByteBuffer bb) {
        if (bb == null) {
            return;
        }

        Cleaner cleaner = ((DirectBuffer) bb).cleaner();

        if (cleaner != null) {
            cleaner.clean();
        }
    }
}
