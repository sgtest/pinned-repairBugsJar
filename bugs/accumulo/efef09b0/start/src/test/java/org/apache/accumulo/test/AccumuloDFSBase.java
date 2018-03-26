/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.vfs2.CacheStrategy;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.cache.DefaultFilesCache;
import org.apache.commons.vfs2.cache.SoftRefFilesCache;
import org.apache.commons.vfs2.impl.DefaultFileReplicator;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.impl.FileContentInfoFilenameFactory;
import org.apache.commons.vfs2.provider.ReadOnlyHdfsFileProvider;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class AccumuloDFSBase {

  //Turn off the MiniDFSCluster logging
  static {
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
  }

  // Choose an IANA unassigned port
  // http://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xml
  protected static final Integer HDFS_PORT = 8620;
  
  protected static final String HDFS_URI = "hdfs://localhost:" + HDFS_PORT;
  
  protected static Configuration conf = null;
  protected static DefaultFileSystemManager vfs = null;
  protected static MiniDFSCluster cluster = null;
  static {
    Logger.getRootLogger().setLevel(Level.ERROR);
    
    //Put the MiniDFSCluster directory in the target directory
    System.setProperty("test.build.data", "target/build/test/data");
    
    //Setup HDFS
    conf = new Configuration();
    conf.set(FileSystem.FS_DEFAULT_NAME_KEY, HDFS_URI);
    conf.set("hadoop.security.token.service.use_ip", "true");
    
    // MiniDFSCluster will check the permissions on the data directories, but does not
    // do a good job of setting them properly. We need to get the users umask and set
    // the appropriate Hadoop property so that the data directories will be created
    // with the correct permissions.
    try {
	Process p = Runtime.getRuntime().exec("/bin/sh -c umask");
	BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
	String line = bri.readLine();
	p.waitFor();
//	System.out.println("umask response: " + line);
	Short umask = Short.parseShort(line.trim(), 8);
	//Need to set permission to 777 xor umask
	// leading zero makes java interpret as base 8
	int newPermission = 0777 ^ umask;
//	System.out.println("Umask is: " + String.format("%03o", umask));
//	System.out.println("Perm is: " + String.format("%03o", newPermission));
	conf.set("dfs.datanode.data.dir.perm", String.format("%03o", newPermission));
    } catch (Exception e) {
	throw new RuntimeException("Error getting umask from O/S", e);
    }
    
    conf.setLong(DFSConfigKeys.DFS_BLOCK_SIZE_KEY, 1024 * 100); //100K blocksize
    
    try {
      cluster = new MiniDFSCluster(HDFS_PORT, conf, 1, true, true, true, null, null, null, null);
      cluster.waitActive();
    } catch (IOException e) {
      throw new RuntimeException("Error setting up mini cluster", e);
    }
    
    //Set up the VFS
    vfs = new DefaultFileSystemManager();
    try {
      vfs.setFilesCache(new DefaultFilesCache());
      vfs.addProvider("res", new org.apache.commons.vfs2.provider.res.ResourceFileProvider());
      vfs.addProvider("zip", new org.apache.commons.vfs2.provider.zip.ZipFileProvider());
      vfs.addProvider("gz", new org.apache.commons.vfs2.provider.gzip.GzipFileProvider());
      vfs.addProvider("ram", new org.apache.commons.vfs2.provider.ram.RamFileProvider());
      vfs.addProvider("file", new org.apache.commons.vfs2.provider.local.DefaultLocalFileProvider());
      vfs.addProvider("jar", new org.apache.commons.vfs2.provider.jar.JarFileProvider());
      vfs.addProvider("http", new org.apache.commons.vfs2.provider.http.HttpFileProvider());
      vfs.addProvider("https", new org.apache.commons.vfs2.provider.https.HttpsFileProvider());
      vfs.addProvider("ftp", new org.apache.commons.vfs2.provider.ftp.FtpFileProvider());
      vfs.addProvider("ftps", new org.apache.commons.vfs2.provider.ftps.FtpsFileProvider());
      vfs.addProvider("war", new org.apache.commons.vfs2.provider.jar.JarFileProvider());
      vfs.addProvider("par", new org.apache.commons.vfs2.provider.jar.JarFileProvider());
      vfs.addProvider("ear", new org.apache.commons.vfs2.provider.jar.JarFileProvider());
      vfs.addProvider("sar", new org.apache.commons.vfs2.provider.jar.JarFileProvider());
      vfs.addProvider("ejb3", new org.apache.commons.vfs2.provider.jar.JarFileProvider());
      vfs.addProvider("tmp", new org.apache.commons.vfs2.provider.temp.TemporaryFileProvider());
      vfs.addProvider("tar", new org.apache.commons.vfs2.provider.tar.TarFileProvider());
      vfs.addProvider("tbz2", new org.apache.commons.vfs2.provider.tar.TarFileProvider());
      vfs.addProvider("tgz", new org.apache.commons.vfs2.provider.tar.TarFileProvider());
      vfs.addProvider("bz2", new org.apache.commons.vfs2.provider.bzip2.Bzip2FileProvider());
      vfs.addProvider("hdfs", new ReadOnlyHdfsFileProvider());
      vfs.addExtensionMap("jar", "jar");
      vfs.addExtensionMap("zip", "zip");
      vfs.addExtensionMap("gz", "gz");
      vfs.addExtensionMap("tar", "tar");
      vfs.addExtensionMap("tbz2", "tar");
      vfs.addExtensionMap("tgz", "tar");
      vfs.addExtensionMap("bz2", "bz2");
      vfs.addMimeTypeMap("application/x-tar", "tar");
      vfs.addMimeTypeMap("application/x-gzip", "gz");
      vfs.addMimeTypeMap("application/zip", "zip");
      vfs.setFileContentInfoFactory(new FileContentInfoFilenameFactory());
      vfs.setFilesCache(new SoftRefFilesCache());
      vfs.setReplicator(new DefaultFileReplicator());
      vfs.setCacheStrategy(CacheStrategy.ON_RESOLVE);
      vfs.init();
    } catch (FileSystemException e) {
      throw new RuntimeException("Error setting up VFS", e);
    }

  }
  
}
