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

package org.apache.uima.cas.impl;

import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Marker;

/**
 * A MarkerImpl holds a high-water "mark" in the CAS,
 * for all views.
 * Typically, one is obtained via the createMarker call
 * on a CAS.
 * 
 * Currently only one marker is used per CAS.
 * The Marker enables testing on each CAS update if the
 * update is "below" or "above" the marker - this is
 * used for implementing delta serialization, in which
 * only the changed data is sent.
 */
public class MarkerImpl implements Marker {
	
  protected int nextFSId;    //next FS addr
  protected boolean isValid;
  
  CASImpl cas;

  MarkerImpl(int nextFSAddr, CASImpl cas) {
    this.nextFSId = nextFSAddr;
    this.cas = cas;
    this.isValid = true;
  }

  
  
  public boolean isNew(FeatureStructure aFs) {
  	//check if same CAS instance
  	//TODO: define a CASRuntimeException
    FeatureStructureImplC fs = (FeatureStructureImplC) aFs;
  	if (!isValid || !cas.isInCAS(fs)) {
  		throw new CASRuntimeException(CASRuntimeException.CAS_MISMATCH, "FS and Marker are not from the same CAS.");
  	}
  	return isNew(fs.get_id());
  }

  public boolean isModified(FeatureStructure aFs) {
    FeatureStructureImplC fs = (FeatureStructureImplC) aFs;
	  if (!isValid || !cas.isInCAS(fs)) {
	   	throw new CASRuntimeException(CASRuntimeException.CAS_MISMATCH, "FS and Marker are not from the same CAS.");
	  }
  return isModified(fs);
  }
  
  boolean isNew(int id) {
	  return (id >= nextFSId);
  }
    
  public boolean isValid() {
    return isValid;
  }

  public int getNextFSId() {
    return nextFSId;
  }
  
}
