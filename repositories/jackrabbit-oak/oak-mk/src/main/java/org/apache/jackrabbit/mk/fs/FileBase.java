/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.mk.fs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * The base class for file implementations.
 */
public abstract class FileBase extends FileChannel {

    @Override
    public void force(boolean metaData) throws IOException {
        // ignore
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public abstract long position() throws IOException;

    @Override
    public abstract FileChannel position(long newPosition) throws IOException;

    @Override
    public abstract int read(ByteBuffer dst) throws IOException;

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public abstract long size() throws IOException;

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public abstract FileChannel truncate(long size) throws IOException;

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public abstract int write(ByteBuffer src) throws IOException;

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        throw new UnsupportedOperationException();    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();    }

    @Override
    protected void implCloseChannel() throws IOException {
        // ignore
    }

}
