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

package x.y.z;

import org.apache.uima.jcas.cas.TOP_Type;
import org.apache.uima.jcas.impl.JCas;

/* comment 6 of 14 */
public class EndOfSentence extends TokenType {

  public final static int typeIndexID = JCas.getNextIndex();

  public final static int type = typeIndexID;

  public int getTypeIndexID() {
    return typeIndexID;
  }

  // Never called. Disable default constructor
  protected EndOfSentence() {
  }

  /** Internal - Constructor used by generator */
  public EndOfSentence(int addr, TOP_Type type) {
    super(addr, type);
  }

  public EndOfSentence(JCas jcas) {
    super(jcas);
  }

  /**
   * <!-- begin-user-doc --> Write your own initialization here <!-- end-user-doc -->
   * 
   * @generated modifiable
   */
  private void readObject() {
  }

}
