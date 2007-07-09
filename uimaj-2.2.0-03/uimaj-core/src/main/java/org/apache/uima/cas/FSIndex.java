/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.uima.cas;

/**
 * Feature structure index access interface.
 * 
 * <p>
 * Notice that each feature structure index uses its own ordering relation that will usually be
 * different from index to index. In particular, equality with respect to the index ordering will in
 * general not imply object identity for the feature structures.
 * 
 * <p>
 * We currently support three different kinds of indexes: sorted, set and bag indexes. The default
 * index is a sorted index. In a sorted index, all FSs that are commited are entered, even if they
 * are duplicates of already existing FSs. The index is sorted in the sense that iterators will
 * output FSs in sorted order according to the comparator for that index. The order of FSs that are
 * equal wrt the comparator is arbitrary but fixed. That is, if you iterate over the same index
 * several times, you will see the same relative order of FSs every time. We also guarantee that
 * reverse iterators will produce exactly the reverse sequence of forward iteration.
 * 
 * <p>
 * A set index will contain no duplicates of the same type, where a duplicate is defined by the
 * indexing comparator. That is, if you commit two feature structures of the same type that are
 * equal wrt the indexing comparator, only the first one will be entered into the index. Note that
 * you can still have duplicates wrt the indexing order if they are of a different type. A set index
 * is not guaranteed to be sorted.
 * 
 * <p>
 * A bag index finally simply stores everything, without any guaranteed order. Our current
 * implementation works such that for any two FSs of the same type, they will be returned in the
 * order in which they are committed (FIFO style). Note that any operation like find() or
 * FSIterator.moveTo() will not produce useful results on bag indexes, since bag indexes do not
 * honor comparators. Only use a bag index if you want very fast adding and will have to iterate
 * over the whole index anyway.
 * 
 * 
 */
public interface FSIndex {

  /**
   * Indexing strategy: sorted index. A sorted index contains all elements, including duplicates.
   * Iterators over sorted indexes will return elements in sorted order.
   */
  public static final int SORTED_INDEX = 0;

  /**
   * Indexing strategy: set index. A set index contains no duplicates of the same type, where a
   * duplicate is defined by the indexing comparator. A set index is not guaranteed to be sorted.
   */
  public static final int SET_INDEX = 1;

  /**
   * Indexing strategy: bag index. A bag index contains all elements, in no particular order.
   */
  public static final int BAG_INDEX = 2;
  
  /**
   * Special indexes used by the framework to implement
   * {@link FSIndexRepository#getAllIndexedFS(Type)}.  Not user-definable.
   */
  public static final int DEFAULT_BAG_INDEX = 3;
  
  /**
   * Return the number of feature strucures in this index.
   * 
   * @return The number of FSs in this index.
   */
  int size();

  /**
   * Return the type of feature structures this index contains.
   * 
   * @return The type of feature structures in this index.
   */
  Type getType();

  /**
   * Check if the index contains an element equal to the given feature structure according to the
   * ordering of the index. Note that this is in general not the same as feature structure identity.
   * 
   * @param fs
   *          The FS we're looking for.
   * @return <code>true</code> iff the index contains such an element.
   */
  boolean contains(FeatureStructure fs);

  /**
   * Find an entry in the index equal to the given feature structure according to the ordering of
   * the index. Note that this is in general not the same as feature structure identity.
   * 
   * @param fs
   *          The FS we're looking for.
   * @return A FS equal to <code>fs</code>, or <code>null</code> if no such FS exists.
   * @see FSIterator#moveTo(FeatureStructure)
   */
  FeatureStructure find(FeatureStructure fs);

  /**
   * Compare two feature structures according to the ordering relation of the index. If the input
   * feature structures are not of the type of the index, the result is undefined.
   * 
   * @return <code>-1</code> if <code>fs1 &lt; fs2</code>; <code>0</code> if
   *         <code>fs1 = fs2</code>; <code>1</code> else.
   */
  int compare(FeatureStructure fs1, FeatureStructure fs2);

  /**
   * Return an iterator over the index. The iterator will be set to the start position of the index.
   * 
   * @return An iterator over the index.
   */
  FSIterator iterator();

  /**
   * Return an iterator over the index. The position of the iterator will be set such that the
   * feature structure returned by a call to the iterator's {@link FSIterator#get() get()} method is
   * greater than or equal to <code>fs</code>, and any previous FS is less than <code>FS</code>.
   * If no such position exists, the iterator will be invalid.
   * 
   * @param fs
   *          The feature structure at which the iterator should be positioned.
   * @return An iterator positioned at <code>fs</code>, if it exists. An invalid iterator, else.
   */
  FSIterator iterator(FeatureStructure fs);

  /**
   * Return the indexing strategy.
   * 
   * @return One of <code>SORTED_INDEX</code>, <code>BAG_INDEX</code> or <code>SET_INDEX</code>.
   */
  int getIndexingStrategy();

}
