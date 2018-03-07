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
package org.apache.accumulo.server.test.functional;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;
import org.apache.accumulo.core.util.Pair;
import org.apache.accumulo.server.tabletserver.NativeMap;
import org.apache.hadoop.io.Text;

public class NativeMapTest {
  
  // BEGIN JUnit methods
  // this used to be a unit test, but it would not
  // run on the build server... so made it a functional
  // test... in order to avoid a junit dependency
  // the junit methods used were implemented here
  
  private void assertTrue(boolean b) {
    if (!b) {
      throw new RuntimeException();
    }
    
  }
  
  private void assertEquals(Object o1, Object o2) {
    if (!o1.equals(o2)) {
      throw new RuntimeException(o1 + " != " + o2);
    }
  }
  
  private void assertFalse(boolean b) {
    if (b)
      throw new RuntimeException();
  }
  
  private void assertNotNull(Object o) {
    if (o == null)
      throw new RuntimeException();
  }
  
  private void assertNull(Object o) {
    if (o != null)
      throw new RuntimeException();
  }
  
  public static void main(String[] args) throws Exception {
    NativeMapTest nmt = new NativeMapTest();
    nmt.setUp();
    
    nmt.test1();
    nmt.test2();
    nmt.test4();
    nmt.test5();
    nmt.test7();
    nmt.test8();
    nmt.test9();
    nmt.test10();
    nmt.test11();
    nmt.testBinary();
    nmt.testEmpty();
    nmt.testConcurrentIter();
  }
  
  // END JUnit methods
  
  private Key nk(int r) {
    return new Key(new Text(String.format("r%09d", r)));
  }
  
  private Key nk(int r, int cf, int cq, int cv, int ts, boolean deleted) {
    Key k = new Key(new Text(String.format("r%09d", r)), new Text(String.format("cf%09d", cf)), new Text(String.format("cq%09d", cq)), new Text(String.format(
        "cv%09d", cv)), ts);
    
    k.setDeleted(deleted);
    
    return k;
  }
  
  private Value nv(int v) {
    return new Value(String.format("r%09d", v).getBytes());
  }
  
  public void setUp() {
    if (!NativeMap.loadedNativeLibraries()) {
      File f = new File(System.getProperty("user.dir"));
      
      while (f != null) {
        File nativeLib = new File(f, NativeMap.getNativeLibPath());
        if (nativeLib.exists()) {
          break;
        }
        f = f.getParentFile();
      }
      
      if (f != null) {
        File nativeLib = new File(f, NativeMap.getNativeLibPath());
        NativeMap.loadNativeLib(nativeLib.toString());
      }
    }
    
  }
  
  private void verifyIterator(int start, int end, int valueOffset, Iterator<Entry<Key,Value>> iter) {
    for (int i = start; i <= end; i++) {
      assertTrue(iter.hasNext());
      Entry<Key,Value> entry = iter.next();
      assertEquals(nk(i), entry.getKey());
      assertEquals(nv(i + valueOffset), entry.getValue());
    }
    
    assertFalse(iter.hasNext());
  }
  
  private void insertAndVerify(NativeMap nm, int start, int end, int valueOffset) {
    for (int i = start; i <= end; i++) {
      nm.put(nk(i), nv(i + valueOffset));
    }
    
    for (int i = start; i <= end; i++) {
      Value v = nm.get(nk(i));
      assertNotNull(v);
      assertEquals(nv(i + valueOffset), v);
      
      Iterator<Entry<Key,Value>> iter2 = nm.iterator(nk(i));
      assertTrue(iter2.hasNext());
      Entry<Key,Value> entry = iter2.next();
      assertEquals(nk(i), entry.getKey());
      assertEquals(nv(i + valueOffset), entry.getValue());
    }
    
    assertNull(nm.get(nk(start - 1)));
    
    assertNull(nm.get(nk(end + 1)));
    
    Iterator<Entry<Key,Value>> iter = nm.iterator();
    verifyIterator(start, end, valueOffset, iter);
    
    for (int i = start; i <= end; i++) {
      iter = nm.iterator(nk(i));
      verifyIterator(i, end, valueOffset, iter);
      
      // lookup nonexistant key that falls after existing key
      iter = nm.iterator(nk(i, 1, 1, 1, 1, false));
      verifyIterator(i + 1, end, valueOffset, iter);
    }
    
    assertEquals(end - start + 1, nm.size());
  }
  
  private void insertAndVerifyExhaustive(NativeMap nm, int num, int run) {
    for (int i = 0; i < num; i++) {
      for (int j = 0; j < num; j++) {
        for (int k = 0; k < num; k++) {
          for (int l = 0; l < num; l++) {
            for (int ts = 0; ts < num; ts++) {
              Key key = nk(i, j, k, l, ts, true);
              Value value = new Value((i + "_" + j + "_" + k + "_" + l + "_" + ts + "_" + true + "_" + run).getBytes());
              
              nm.put(key, value);
              
              key = nk(i, j, k, l, ts, false);
              value = new Value((i + "_" + j + "_" + k + "_" + l + "_" + ts + "_" + false + "_" + run).getBytes());
              
              nm.put(key, value);
            }
          }
        }
      }
    }
    
    Iterator<Entry<Key,Value>> iter = nm.iterator();
    
    for (int i = 0; i < num; i++) {
      for (int j = 0; j < num; j++) {
        for (int k = 0; k < num; k++) {
          for (int l = 0; l < num; l++) {
            for (int ts = num - 1; ts >= 0; ts--) {
              Key key = nk(i, j, k, l, ts, true);
              Value value = new Value((i + "_" + j + "_" + k + "_" + l + "_" + ts + "_" + true + "_" + run).getBytes());
              
              assertTrue(iter.hasNext());
              Entry<Key,Value> entry = iter.next();
              assertEquals(key, entry.getKey());
              assertEquals(value, entry.getValue());
              
              key = nk(i, j, k, l, ts, false);
              value = new Value((i + "_" + j + "_" + k + "_" + l + "_" + ts + "_" + false + "_" + run).getBytes());
              
              assertTrue(iter.hasNext());
              entry = iter.next();
              assertEquals(key, entry.getKey());
              assertEquals(value, entry.getValue());
            }
          }
        }
      }
    }
    
    assertFalse(iter.hasNext());
    
    for (int i = 0; i < num; i++) {
      for (int j = 0; j < num; j++) {
        for (int k = 0; k < num; k++) {
          for (int l = 0; l < num; l++) {
            for (int ts = 0; ts < num; ts++) {
              Key key = nk(i, j, k, l, ts, true);
              Value value = new Value((i + "_" + j + "_" + k + "_" + l + "_" + ts + "_" + true + "_" + run).getBytes());
              
              assertEquals(value, nm.get(key));
              
              Iterator<Entry<Key,Value>> iter2 = nm.iterator(key);
              assertTrue(iter2.hasNext());
              Entry<Key,Value> entry = iter2.next();
              assertEquals(key, entry.getKey());
              assertEquals(value, entry.getValue());
              
              key = nk(i, j, k, l, ts, false);
              value = new Value((i + "_" + j + "_" + k + "_" + l + "_" + ts + "_" + false + "_" + run).getBytes());
              
              assertEquals(value, nm.get(key));
              
              Iterator<Entry<Key,Value>> iter3 = nm.iterator(key);
              assertTrue(iter3.hasNext());
              Entry<Key,Value> entry2 = iter3.next();
              assertEquals(key, entry2.getKey());
              assertEquals(value, entry2.getValue());
            }
          }
        }
      }
    }
    
    assertEquals(num * num * num * num * num * 2, nm.size());
  }
  
  public void test1() {
    NativeMap nm = new NativeMap();
    Iterator<Entry<Key,Value>> iter = nm.iterator();
    assertFalse(iter.hasNext());
    nm.delete();
  }
  
  public void test2() {
    NativeMap nm = new NativeMap();
    
    insertAndVerify(nm, 1, 10, 0);
    insertAndVerify(nm, 1, 10, 1);
    insertAndVerify(nm, 1, 10, 2);
    
    nm.delete();
  }
  
  public void test4() {
    NativeMap nm = new NativeMap();
    
    insertAndVerifyExhaustive(nm, 3, 0);
    insertAndVerifyExhaustive(nm, 3, 1);
    
    nm.delete();
  }
  
  public void test5() {
    NativeMap nm = new NativeMap();
    
    insertAndVerify(nm, 1, 10, 0);
    
    Iterator<Entry<Key,Value>> iter = nm.iterator();
    iter.next();
    
    nm.delete();
    
    try {
      nm.put(nk(1), nv(1));
      assertTrue(false);
    } catch (IllegalStateException e) {
      
    }
    
    try {
      nm.get(nk(1));
      assertTrue(false);
    } catch (IllegalStateException e) {
      
    }
    
    try {
      nm.iterator();
      assertTrue(false);
    } catch (IllegalStateException e) {
      
    }
    
    try {
      nm.iterator(nk(1));
      assertTrue(false);
    } catch (IllegalStateException e) {
      
    }
    
    try {
      nm.size();
      assertTrue(false);
    } catch (IllegalStateException e) {
      
    }
    
    try {
      iter.next();
      assertTrue(false);
    } catch (IllegalStateException e) {
      
    }
    
  }
  
  public void test7() {
    NativeMap nm = new NativeMap();
    
    insertAndVerify(nm, 1, 10, 0);
    
    nm.delete();
    
    try {
      nm.delete();
      assertTrue(false);
    } catch (IllegalStateException e) {
      
    }
  }
  
  public void test8() {
    // test verifies that native map sorts keys sharing some common prefix properly
    
    NativeMap nm = new NativeMap();
    
    TreeMap<Key,Value> tm = new TreeMap<Key,Value>();
    
    tm.put(new Key(new Text("fo")), new Value("0".getBytes()));
    tm.put(new Key(new Text("foo")), new Value("1".getBytes()));
    tm.put(new Key(new Text("foo1")), new Value("2".getBytes()));
    tm.put(new Key(new Text("foo2")), new Value("3".getBytes()));
    
    for (Entry<Key,Value> entry : tm.entrySet()) {
      nm.put(entry.getKey(), entry.getValue());
    }
    
    Iterator<Entry<Key,Value>> iter = nm.iterator();
    
    for (Entry<Key,Value> entry : tm.entrySet()) {
      assertTrue(iter.hasNext());
      Entry<Key,Value> entry2 = iter.next();
      
      assertEquals(entry.getKey(), entry2.getKey());
      assertEquals(entry.getValue(), entry2.getValue());
    }
    
    assertFalse(iter.hasNext());
    
    nm.delete();
  }
  
  public void test9() {
    NativeMap nm = new NativeMap();
    
    Iterator<Entry<Key,Value>> iter = nm.iterator();
    
    try {
      iter.next();
      assertTrue(false);
    } catch (NoSuchElementException e) {
      
    }
    
    insertAndVerify(nm, 1, 1, 0);
    
    iter = nm.iterator();
    iter.next();
    
    try {
      iter.next();
      assertTrue(false);
    } catch (NoSuchElementException e) {
      
    }
    
    nm.delete();
  }
  
  public void test10() {
    int start = 1;
    int end = 10000;
    
    NativeMap nm = new NativeMap();
    for (int i = start; i <= end; i++) {
      nm.put(nk(i), nv(i));
    }
    
    long mem1 = nm.getMemoryUsed();
    
    for (int i = start; i <= end; i++) {
      nm.put(nk(i), nv(i));
    }
    
    long mem2 = nm.getMemoryUsed();
    
    if (mem1 != mem2) {
      throw new RuntimeException("Memory changed after inserting duplicate data " + mem1 + " " + mem2);
    }
    
    for (int i = start; i <= end; i++) {
      nm.put(nk(i), nv(i));
    }
    
    long mem3 = nm.getMemoryUsed();
    
    if (mem1 != mem3) {
      throw new RuntimeException("Memory changed after inserting duplicate data " + mem1 + " " + mem3);
    }
    
    byte bigrow[] = new byte[1000000];
    byte bigvalue[] = new byte[bigrow.length];
    
    for (int i = 0; i < bigrow.length; i++) {
      bigrow[i] = (byte) (0xff & (i % 256));
      bigvalue[i] = bigrow[i];
    }
    
    nm.put(new Key(new Text(bigrow)), new Value(bigvalue));
    
    long mem4 = nm.getMemoryUsed();
    
    Value val = nm.get(new Key(new Text(bigrow)));
    if (val == null || !val.equals(new Value(bigvalue))) {
      throw new RuntimeException("Did not get expected big value");
    }
    
    nm.put(new Key(new Text(bigrow)), new Value(bigvalue));
    
    long mem5 = nm.getMemoryUsed();
    
    if (mem4 != mem5) {
      throw new RuntimeException("Memory changed after inserting duplicate data " + mem4 + " " + mem5);
    }
    
    val = nm.get(new Key(new Text(bigrow)));
    if (val == null || !val.equals(new Value(bigvalue))) {
      throw new RuntimeException("Did not get expected big value");
    }
    
    nm.delete();
  }
  
  // random length random field
  private static byte[] rlrf(Random r, int maxLen) {
    int len = r.nextInt(maxLen);
    
    byte f[] = new byte[len];
    r.nextBytes(f);
    
    return f;
  }
  
  public void test11() {
    NativeMap nm = new NativeMap();
    
    // insert things with varying field sizes and value sizes
    
    // generate random data
    Random r = new Random(75);
    
    ArrayList<Pair<Key,Value>> testData = new ArrayList<Pair<Key,Value>>();
    
    for (int i = 0; i < 100000; i++) {
      
      Key k = new Key(rlrf(r, 97), rlrf(r, 13), rlrf(r, 31), rlrf(r, 11), Math.abs(r.nextLong()), false, false);
      Value v = new Value(rlrf(r, 511));
      
      testData.add(new Pair<Key,Value>(k, v));
    }
    
    // insert unsorted data
    for (Pair<Key,Value> pair : testData) {
      nm.put(pair.getFirst(), pair.getSecond());
    }
    
    for (int i = 0; i < 2; i++) {
      
      // sort data
      Collections.sort(testData, new Comparator<Pair<Key,Value>>() {
        @Override
        public int compare(Pair<Key,Value> o1, Pair<Key,Value> o2) {
          return o1.getFirst().compareTo(o2.getFirst());
        }
      });
      
      // verify
      Iterator<Entry<Key,Value>> iter1 = nm.iterator();
      Iterator<Pair<Key,Value>> iter2 = testData.iterator();
      
      while (iter1.hasNext() && iter2.hasNext()) {
        Entry<Key,Value> e = iter1.next();
        Pair<Key,Value> p = iter2.next();
        
        if (!e.getKey().equals(p.getFirst()))
          throw new RuntimeException("Keys not equal");
        
        if (!e.getValue().equals(p.getSecond()))
          throw new RuntimeException("Values not equal");
      }
      
      if (iter1.hasNext())
        throw new RuntimeException("Not all of native map consumed");
      
      if (iter2.hasNext())
        throw new RuntimeException("Not all of test data consumed");
      
      System.out.println("test 11 nm mem " + nm.getMemoryUsed());
      
      // insert data again w/ different value
      Collections.shuffle(testData, r);
      // insert unsorted data
      for (Pair<Key,Value> pair : testData) {
        pair.getSecond().set(rlrf(r, 511));
        nm.put(pair.getFirst(), pair.getSecond());
      }
    }
    
    nm.delete();
  }
  
  public void testBinary() {
    NativeMap nm = new NativeMap();
    
    byte emptyBytes[] = new byte[0];
    
    for (int i = 0; i < 256; i++) {
      for (int j = 0; j < 256; j++) {
        byte row[] = new byte[] {'r', (byte) (0xff & i), (byte) (0xff & j)};
        byte data[] = new byte[] {'v', (byte) (0xff & i), (byte) (0xff & j)};
        
        Key k = new Key(row, emptyBytes, emptyBytes, emptyBytes, 1);
        Value v = new Value(data);
        
        nm.put(k, v);
      }
    }
    
    Iterator<Entry<Key,Value>> iter = nm.iterator();
    for (int i = 0; i < 256; i++) {
      for (int j = 0; j < 256; j++) {
        byte row[] = new byte[] {'r', (byte) (0xff & i), (byte) (0xff & j)};
        byte data[] = new byte[] {'v', (byte) (0xff & i), (byte) (0xff & j)};
        
        Key k = new Key(row, emptyBytes, emptyBytes, emptyBytes, 1);
        Value v = new Value(data);
        
        assertTrue(iter.hasNext());
        Entry<Key,Value> entry = iter.next();
        
        assertEquals(k, entry.getKey());
        assertEquals(v, entry.getValue());
        
      }
    }
    
    assertFalse(iter.hasNext());
    
    for (int i = 0; i < 256; i++) {
      for (int j = 0; j < 256; j++) {
        byte row[] = new byte[] {'r', (byte) (0xff & i), (byte) (0xff & j)};
        byte data[] = new byte[] {'v', (byte) (0xff & i), (byte) (0xff & j)};
        
        Key k = new Key(row, emptyBytes, emptyBytes, emptyBytes, 1);
        Value v = new Value(data);
        
        Value v2 = nm.get(k);
        
        assertEquals(v, v2);
      }
    }
    
    nm.delete();
  }
  
  public void testEmpty() {
    NativeMap nm = new NativeMap();
    
    assertTrue(nm.size() == 0);
    assertTrue(nm.getMemoryUsed() == 0);
    
    nm.delete();
  }
  
  public void testConcurrentIter() throws IOException {
    NativeMap nm = new NativeMap();
    
    nm.put(nk(0), nv(0));
    nm.put(nk(1), nv(1));
    nm.put(nk(3), nv(3));
    
    SortedKeyValueIterator<Key,Value> iter = nm.skvIterator();
    
    // modify map after iter created
    nm.put(nk(2), nv(2));
    
    assertTrue(iter.hasTop());
    assertEquals(iter.getTopKey(), nk(0));
    iter.next();
    
    assertTrue(iter.hasTop());
    assertEquals(iter.getTopKey(), nk(1));
    iter.next();
    
    assertTrue(iter.hasTop());
    assertEquals(iter.getTopKey(), nk(2));
    iter.next();
    
    assertTrue(iter.hasTop());
    assertEquals(iter.getTopKey(), nk(3));
    iter.next();
    
    assertFalse(iter.hasTop());
    
    nm.delete();
  }
}
