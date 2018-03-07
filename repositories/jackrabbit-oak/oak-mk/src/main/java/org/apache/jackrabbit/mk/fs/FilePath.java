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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.jackrabbit.mk.util.StringUtils;

/**
 * A path to a file. It similar to the Java 7 {@code java.nio.file.Path},
 * but simpler, and works with older versions of Java. It also implements the
 * relevant methods found in {@code java.nio.file.FileSystem} and
 * {@code FileSystems}
 */
public abstract class FilePath {

    private static final FilePath DEFAULT = new FilePathDisk();

    private static Map<String, FilePath> providers;

    /**
     * The prefix for temporary files.
     */
    private static String tempRandom;
    private static long tempSequence;

    /**
     * The complete path (which may be absolute or relative, depending on the
     * file system).
     */
    protected String name;

    /**
     * Get the file path object for the given path.
     * This method is similar to Java 7 {@code java.nio.file.FileSystem.getPath}.
     * Windows-style '\' is replaced with '/'.
     *
     * @param path the path
     * @return the file path object
     */
    public static FilePath get(String path) {
        path = path.replace('\\', '/');
        int index = path.indexOf(':');
        if (index < 2) {
            // use the default provider if no prefix or
            // only a single character (drive name)
            return DEFAULT.getPath(path);
        }
        String scheme = path.substring(0, index);
        registerDefaultProviders();
        FilePath p = providers.get(scheme);
        if (p == null) {
            // provider not found - use the default
            p = DEFAULT;
        }
        return p.getPath(path);
    }

    private static void registerDefaultProviders() {
        if (providers == null) {
            Map<String, FilePath> map = Collections.synchronizedMap(new HashMap<String, FilePath>());
            for (String c : new String[] {
                    "org.apache.jackrabbit.mk.fs.FilePathDisk",
                    "org.apache.jackrabbit.mk.fs.FilePathCache"
            }) {
                try {
                    FilePath p = (FilePath) Class.forName(c).newInstance();
                    map.put(p.getScheme(), p);
                } catch (Exception e) {
                    // ignore - the files may be excluded in purpose
                }
            }
            providers = map;
        }
    }

    /**
     * Register a file provider.
     *
     * @param provider the file provider
     */
    public static void register(FilePath provider) {
        registerDefaultProviders();
        providers.put(provider.getScheme(), provider);
    }

    /**
     * Unregister a file provider.
     *
     * @param provider the file provider
     */
    public static void unregister(FilePath provider) {
        registerDefaultProviders();
        providers.remove(provider.getScheme());
    }

    /**
     * Get the size of a file in bytes
     *
     * @return the size in bytes
     */
    public abstract long size();

    /**
     * Rename a file if this is allowed.
     *
     * @param newName the new fully qualified file name
     */
    public abstract void moveTo(FilePath newName) throws IOException;

    /**
     * Create a new file.
     *
     * @return true if creating was successful
     */
    public abstract boolean createFile();

    /**
     * Checks if a file exists.
     *
     * @return true if it exists
     */
    public abstract boolean exists();

    /**
     * Delete a file or directory if it exists.
     * Directories may only be deleted if they are empty.
     */
    public abstract void delete() throws IOException;

    /**
     * List the files and directories in the given directory.
     *
     * @return the list of fully qualified file names
     */
    public abstract List<FilePath> newDirectoryStream() throws IOException;

    /**
     * Normalize a file name.
     *
     * @return the normalized file name
     */
    public abstract FilePath toRealPath() throws IOException;

    /**
     * Get the parent directory of a file or directory.
     *
     * @return the parent directory name
     */
    public abstract FilePath getParent();

    /**
     * Check if it is a file or a directory.
     *
     * @return true if it is a directory
     */
    public abstract boolean isDirectory();

    /**
     * Check if the file name includes a path.
     *
     * @return if the file name is absolute
     */
    public abstract boolean isAbsolute();

    /**
     * Get the last modified date of a file
     *
     * @return the last modified date
     */
    public abstract long lastModified();

    /**
     * Check if the file is writable.
     *
     * @return if the file is writable
     */
    public abstract boolean canWrite();

    /**
     * Create a directory (all required parent directories already exist).
     */
    public abstract void createDirectory() throws IOException;

    /**
     * Get the file or directory name (the last element of the path).
     *
     * @return the last element of the path
     */
    public String getName() {
        int idx = Math.max(name.indexOf(':'), name.lastIndexOf('/'));
        return idx < 0 ? name : name.substring(idx + 1);
    }

    /**
     * Create an output stream to write into the file.
     *
     * @param append if true, the file will grow, if false, the file will be
     *            truncated first
     * @return the output stream
     */
    public abstract OutputStream newOutputStream(boolean append) throws IOException;

    /**
     * Open a random access file object.
     *
     * @param mode the access mode. Supported are r, rw, rws, rwd
     * @return the file object
     */
    public abstract FileChannel open(String mode) throws IOException;

    /**
     * Create an input stream to read from the file.
     *
     * @return the input stream
     */
    public abstract InputStream newInputStream() throws IOException;

    /**
     * Disable the ability to write.
     *
     * @return true if the call was successful
     */
    public abstract boolean setReadOnly();

    /**
     * Create a new temporary file.
     *
     * @param suffix the suffix
     * @param deleteOnExit if the file should be deleted when the virtual
     *            machine exists
     * @param inTempDir if the file should be stored in the temporary directory
     * @return the name of the created file
     */
    public FilePath createTempFile(String suffix, boolean deleteOnExit, boolean inTempDir) throws IOException {
        while (true) {
            FilePath p = getPath(name + getNextTempFileNamePart(false) + suffix);
            if (p.exists() || !p.createFile()) {
                // in theory, the random number could collide
                getNextTempFileNamePart(true);
                continue;
            }
            p.open("rw").close();
            return p;
        }
    }

    /**
     * Get the next temporary file name part (the part in the middle).
     *
     * @param newRandom if the random part of the filename should change
     * @return the file name part
     */
    protected static synchronized String getNextTempFileNamePart(boolean newRandom) {
        if (newRandom || tempRandom == null) {
            byte[] prefix = new byte[8];
            new Random().nextBytes(prefix);
            tempRandom = StringUtils.convertBytesToHex(prefix) + ".";
        }
        return tempRandom + tempSequence++;
    }

    /**
     * Get the string representation. The returned string can be used to
     * construct a new object.
     *
     * @return the path as a string
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Get the scheme (prefix) for this file provider.
     * This is similar to {@code java.nio.file.spi.FileSystemProvider.getScheme}.
     *
     * @return the scheme
     */
    public abstract String getScheme();

    /**
     * Convert a file to a path. This is similar to
     * {@code java.nio.file.spi.FileSystemProvider.getPath}, but may
     * return an object even if the scheme doesn't match in case of the the
     * default file provider.
     *
     * @param path the path
     * @return the file path object
     */
    public abstract FilePath getPath(String path);

    /**
     * Append an element to the path.
     * This is similar to {@code java.nio.file.spi.FileSystemProvider.resolve}.
     *
     * @param other the relative path (might be null)
     * @return the resolved path
     */
    public abstract FilePath resolve(String other);

}
