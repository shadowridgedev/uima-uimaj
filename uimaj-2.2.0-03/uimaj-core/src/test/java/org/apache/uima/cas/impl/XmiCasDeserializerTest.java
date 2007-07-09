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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.ArrayFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.IntArrayFS;
import org.apache.uima.cas.StringArrayFS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.impl.XmiSerializationSharedData.OotsElementData;
import org.apache.uima.cas.impl.XmiSerializationSharedData.XmiArrayElement;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas_data.impl.CasComparer;
import org.apache.uima.internal.util.XmlAttribute;
import org.apache.uima.internal.util.XmlElementNameAndContents;
import org.apache.uima.resource.metadata.FsIndexDescription;
import org.apache.uima.resource.metadata.TypeDescription;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.resource.metadata.impl.TypePriorities_impl;
import org.apache.uima.resource.metadata.impl.TypeSystemDescription_impl;
import org.apache.uima.test.junit_extension.JUnitExtension;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.FileUtils;
import org.apache.uima.util.XMLInputSource;
import org.apache.uima.util.XMLSerializer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;


public class XmiCasDeserializerTest extends TestCase {

  private FsIndexDescription[] indexes;

  private TypeSystemDescription typeSystem;

  /**
   * Constructor for XCASDeserializerTest.
   * 
   * @param arg0
   */
  public XmiCasDeserializerTest(String arg0) throws IOException {
    super(arg0);
  }

  protected void setUp() throws Exception {
    File typeSystemFile = JUnitExtension.getFile("ExampleCas/testTypeSystem.xml");
    File indexesFile = JUnitExtension.getFile("ExampleCas/testIndexes.xml");

    typeSystem = UIMAFramework.getXMLParser().parseTypeSystemDescription(
            new XMLInputSource(typeSystemFile));
    indexes = UIMAFramework.getXMLParser().parseFsIndexCollection(new XMLInputSource(indexesFile))
            .getFsIndexes();
  }

  public void testDeserializeAndReserialize() throws Exception {
    try {
      File tsWithNoMultiRefs = JUnitExtension.getFile("ExampleCas/testTypeSystem.xml");
      doTestDeserializeAndReserialize(tsWithNoMultiRefs);
      File tsWithMultiRefs = JUnitExtension.getFile("ExampleCas/testTypeSystem_withMultiRefs.xml");
      doTestDeserializeAndReserialize(tsWithMultiRefs);
    } catch (Exception e) {
      JUnitExtension.handleException(e);
    }
  }

  private void doTestDeserializeAndReserialize(File typeSystemDescriptor) throws Exception {
    // deserialize a complex CAS from XCAS
    CAS cas = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(), indexes);

    InputStream serCasStream = new FileInputStream(JUnitExtension.getFile("ExampleCas/cas.xml"));
    XCASDeserializer deser = new XCASDeserializer(cas.getTypeSystem());
    ContentHandler deserHandler = deser.getXCASHandler(cas);
    SAXParserFactory fact = SAXParserFactory.newInstance();
    SAXParser parser = fact.newSAXParser();
    XMLReader xmlReader = parser.getXMLReader();
    xmlReader.setContentHandler(deserHandler);
    xmlReader.parse(new InputSource(serCasStream));
    serCasStream.close();

    // reserialize as XMI
    String xml = serialize(cas, null);
    
    // deserialize into another CAS
    CAS cas2 = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(), indexes);
    XmiCasDeserializer deser2 = new XmiCasDeserializer(cas2.getTypeSystem());
    ContentHandler deserHandler2 = deser2.getXmiCasHandler(cas2);
    xmlReader.setContentHandler(deserHandler2);
    xmlReader.parse(new InputSource(new StringReader(xml)));
    
    // compare
    assertEquals(cas.getAnnotationIndex().size(), cas2.getAnnotationIndex().size());
    assertEquals(cas.getDocumentText(), cas2.getDocumentText());
    CasComparer.assertEquals(cas,cas2);

    // check that array refs are not null
    Type entityType = cas2.getTypeSystem().getType("org.apache.uima.testTypeSystem.Entity");
    Feature classesFeat = entityType.getFeatureByBaseName("classes");
    Iterator iter = cas2.getIndexRepository().getIndex("testEntityIndex").iterator();
    assertTrue(iter.hasNext());
    while (iter.hasNext()) {
      FeatureStructure fs = (FeatureStructure) iter.next();
      StringArrayFS arrayFS = (StringArrayFS) fs.getFeatureValue(classesFeat);
      assertNotNull(arrayFS);
      for (int i = 0; i < arrayFS.size(); i++) {
        assertNotNull(arrayFS.get(i));
      }
    }

    // test that lenient mode does not report errors
    CAS cas3 = CasCreationUtils.createCas(new TypeSystemDescription_impl(),
            new TypePriorities_impl(), new FsIndexDescription[0]);
    XmiCasDeserializer deser3 = new XmiCasDeserializer(cas3.getTypeSystem());
    ContentHandler deserHandler3 = deser3.getXmiCasHandler(cas3, true);
    xmlReader.setContentHandler(deserHandler3);
    xmlReader.parse(new InputSource(new StringReader(xml)));
  }

  public void testMultipleSofas() throws Exception {
    try {
      CAS cas = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(),
              new FsIndexDescription[0]);
      // set document text for the initial view
      cas.setDocumentText("This is a test");
      // create a new view and set its document text
      CAS cas2 = cas.createView("OtherSofa");
      cas2.setDocumentText("This is only a test");

      // create an annotation and add to index of both views
      AnnotationFS anAnnot = cas.createAnnotation(cas.getAnnotationType(), 0, 5);
      cas.getIndexRepository().addFS(anAnnot);
      cas2.getIndexRepository().addFS(anAnnot);
      FSIndex tIndex = cas.getAnnotationIndex();
      FSIndex t2Index = cas2.getAnnotationIndex();
      assertTrue(tIndex.size() == 2); // document annot and this one
      assertTrue(t2Index.size() == 2); // ditto

      // serialize
      StringWriter sw = new StringWriter();
      XMLSerializer xmlSer = new XMLSerializer(sw, false);
      XmiCasSerializer xmiSer = new XmiCasSerializer(cas.getTypeSystem());
      xmiSer.serialize(cas, xmlSer.getContentHandler());
      String xml = sw.getBuffer().toString();

      // deserialize into another CAS (repeat twice to check it still works after reset)
      CAS newCas = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(),
              new FsIndexDescription[0]);
      for (int i = 0; i < 2; i++) {
        XmiCasDeserializer newDeser = new XmiCasDeserializer(newCas.getTypeSystem());
        ContentHandler newDeserHandler = newDeser.getXmiCasHandler(newCas);
        SAXParserFactory fact = SAXParserFactory.newInstance();
        SAXParser parser = fact.newSAXParser();
        XMLReader xmlReader = parser.getXMLReader();
        xmlReader.setContentHandler(newDeserHandler);
        xmlReader.parse(new InputSource(new StringReader(xml)));

        // check sofas
        assertEquals("This is a test", newCas.getDocumentText());
        CAS newCas2 = newCas.getView("OtherSofa");
        assertEquals("This is only a test", newCas2.getDocumentText());

        // check that annotation is still indexed in both views
        assertTrue(tIndex.size() == 2); // document annot and this one
        assertTrue(t2Index.size() == 2); // ditto

        newCas.reset();
      }
    } catch (Exception e) {
      JUnitExtension.handleException(e);
    }
  }

  public void testTypeSystemFiltering() throws Exception {
    try {
      // deserialize a complex CAS from XCAS
      CAS cas = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(), indexes);

      InputStream serCasStream = new FileInputStream(JUnitExtension.getFile("ExampleCas/cas.xml"));
      XCASDeserializer deser = new XCASDeserializer(cas.getTypeSystem());
      ContentHandler deserHandler = deser.getXCASHandler(cas);
      SAXParserFactory fact = SAXParserFactory.newInstance();
      SAXParser parser = fact.newSAXParser();
      XMLReader xmlReader = parser.getXMLReader();
      xmlReader.setContentHandler(deserHandler);
      xmlReader.parse(new InputSource(serCasStream));
      serCasStream.close();

      // now read in a TypeSystem that's a subset of those types
      TypeSystemDescription partialTypeSystemDesc = UIMAFramework.getXMLParser()
              .parseTypeSystemDescription(
                      new XMLInputSource(JUnitExtension
                              .getFile("ExampleCas/partialTestTypeSystem.xml")));
      TypeSystem partialTypeSystem = CasCreationUtils.createCas(partialTypeSystemDesc, null, null)
              .getTypeSystem();

      // reserialize as XMI, filtering out anything that doesn't fit in the
      // partialTypeSystem
      StringWriter sw = new StringWriter();
      XMLSerializer xmlSer = new XMLSerializer(sw, false);
      XmiCasSerializer xmiSer = new XmiCasSerializer(partialTypeSystem);
      xmiSer.serialize(cas, xmlSer.getContentHandler());
      String xml = sw.getBuffer().toString();
      // System.out.println(xml);

      // deserialize into another CAS (which has the whole type system)
      CAS cas2 = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(), indexes);
      XmiCasDeserializer deser2 = new XmiCasDeserializer(cas2.getTypeSystem());
      ContentHandler deserHandler2 = deser2.getXmiCasHandler(cas2);
      xmlReader.setContentHandler(deserHandler2);
      xmlReader.parse(new InputSource(new StringReader(xml)));

      // check that types have been filtered out
      Type orgType = cas2.getTypeSystem().getType("org.apache.uima.testTypeSystem.Organization");
      assertNotNull(orgType);
      assertTrue(cas2.getAnnotationIndex(orgType).size() == 0);
      assertTrue(cas.getAnnotationIndex(orgType).size() > 0);

      // but that some types are still there
      Type personType = cas2.getTypeSystem().getType("org.apache.uima.testTypeSystem.Person");
      FSIndex personIndex = cas2.getAnnotationIndex(personType);
      assertTrue(personIndex.size() > 0);

      // check that mentionType has been filtered out (set to null)
      FeatureStructure somePlace = personIndex.iterator().get();
      Feature mentionTypeFeat = personType.getFeatureByBaseName("mentionType");
      assertNotNull(mentionTypeFeat);
      assertNull(somePlace.getStringValue(mentionTypeFeat));
    } catch (Exception e) {
      JUnitExtension.handleException(e);
    }
  }

  public void testNoInitialSofa() throws Exception {
    CAS cas = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(),
            new FsIndexDescription[0]);
    // create non-annotation type so as not to create the _InitialView Sofa
    IntArrayFS intArrayFS = cas.createIntArrayFS(5);
    intArrayFS.set(0, 1);
    intArrayFS.set(1, 2);
    intArrayFS.set(2, 3);
    intArrayFS.set(3, 4);
    intArrayFS.set(4, 5);
    cas.getIndexRepository().addFS(intArrayFS);

    // serialize the CAS
    StringWriter sw = new StringWriter();
    XMLSerializer xmlSer = new XMLSerializer(sw, false);
    XmiCasSerializer xmiSer = new XmiCasSerializer(cas.getTypeSystem());
    xmiSer.serialize(cas, xmlSer.getContentHandler());
    String xml = sw.getBuffer().toString();

    // deserialize into another CAS
    CAS cas2 = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(),
            new FsIndexDescription[0]);

    XmiCasDeserializer deser2 = new XmiCasDeserializer(cas2.getTypeSystem());
    ContentHandler deserHandler2 = deser2.getXmiCasHandler(cas2);
    SAXParserFactory fact = SAXParserFactory.newInstance();
    SAXParser parser = fact.newSAXParser();
    XMLReader xmlReader = parser.getXMLReader();
    xmlReader.setContentHandler(deserHandler2);
    xmlReader.parse(new InputSource(new StringReader(xml)));

    //test that index is correctly populated
    Type intArrayType = cas2.getTypeSystem().getType(CAS.TYPE_NAME_INTEGER_ARRAY);
    Iterator iter = cas2.getIndexRepository().getAllIndexedFS(intArrayType);
    assertTrue(iter.hasNext());
    IntArrayFS intArrayFS2 = (IntArrayFS)iter.next();
    assertFalse(iter.hasNext());
    assertEquals(5, intArrayFS2.size());
    assertEquals(1, intArrayFS2.get(0));
    assertEquals(2, intArrayFS2.get(1));
    assertEquals(3, intArrayFS2.get(2));
    assertEquals(4, intArrayFS2.get(3));
    assertEquals(5, intArrayFS2.get(4));

    // test that serializing the new CAS produces the same XML
    sw = new StringWriter();
    xmlSer = new XMLSerializer(sw, false);
    xmiSer = new XmiCasSerializer(cas2.getTypeSystem());
    xmiSer.serialize(cas2, xmlSer.getContentHandler());
    String xml2 = sw.getBuffer().toString();    
    assertTrue(xml2.equals(xml));
  }

  public void testv1FormatXcas() throws Exception {
    CAS cas = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(),
            new FsIndexDescription[0]);
    CAS v1cas = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(),
            new FsIndexDescription[0]);

    // get a complex CAS
    InputStream serCasStream = new FileInputStream(JUnitExtension.getFile("ExampleCas/cas.xml"));
    XCASDeserializer deser = new XCASDeserializer(cas.getTypeSystem());
    ContentHandler deserHandler = deser.getXCASHandler(cas);
    SAXParserFactory fact = SAXParserFactory.newInstance();
    SAXParser parser = fact.newSAXParser();
    XMLReader xmlReader = parser.getXMLReader();
    xmlReader.setContentHandler(deserHandler);
    xmlReader.parse(new InputSource(serCasStream));
    serCasStream.close();

    // test it
    assertTrue(CAS.NAME_DEFAULT_SOFA.equals(cas.getSofa().getSofaID()));

    // get a v1 XMI version of the same CAS
    serCasStream = new FileInputStream(JUnitExtension.getFile("ExampleCas/v1xmiCas.xml"));
    XmiCasDeserializer deser2 = new XmiCasDeserializer(v1cas.getTypeSystem());
    ContentHandler deserHandler2 = deser2.getXmiCasHandler(v1cas);
    xmlReader.setContentHandler(deserHandler2);
    xmlReader.parse(new InputSource(serCasStream));
    serCasStream.close();

    // compare
    assertEquals(cas.getAnnotationIndex().size(), v1cas.getAnnotationIndex().size());
    assertTrue(CAS.NAME_DEFAULT_SOFA.equals(v1cas.getSofa().getSofaID()));

    // now a v1 XMI version of a multiple Sofa CAS
    v1cas.reset();
    serCasStream = new FileInputStream(JUnitExtension.getFile("ExampleCas/xmiMsCasV1.xml"));
    deser2 = new XmiCasDeserializer(v1cas.getTypeSystem());
    deserHandler2 = deser2.getXmiCasHandler(v1cas);
    xmlReader.setContentHandler(deserHandler2);
    xmlReader.parse(new InputSource(serCasStream));
    serCasStream.close();

    // test it
    CAS engView = v1cas.getView("EnglishDocument");
    assertTrue(engView.getDocumentText().equals("this beer is good"));
    assertTrue(engView.getAnnotationIndex().size() == 5); // 4 annots plus documentAnnotation
    CAS gerView = v1cas.getView("GermanDocument");
    assertTrue(gerView.getDocumentText().equals("das bier ist gut"));
    assertTrue(gerView.getAnnotationIndex().size() == 5); // 4 annots plus documentAnnotation
    assertTrue(CAS.NAME_DEFAULT_SOFA.equals(v1cas.getSofa().getSofaID()));
    assertTrue(v1cas.getDocumentText().equals("some text for the default text sofa."));

    // reserialize as XMI
    StringWriter sw = new StringWriter();
    XMLSerializer xmlSer = new XMLSerializer(sw, false);
    XmiCasSerializer xmiSer = new XmiCasSerializer(v1cas.getTypeSystem());
    xmiSer.serialize(v1cas, xmlSer.getContentHandler());
    String xml = sw.getBuffer().toString();

    cas.reset();

    // deserialize into another CAS
    deser2 = new XmiCasDeserializer(cas.getTypeSystem());
    deserHandler2 = deser2.getXmiCasHandler(cas);
    xmlReader.setContentHandler(deserHandler2);
    xmlReader.parse(new InputSource(new StringReader(xml)));

    // test it
    engView = cas.getView("EnglishDocument");
    assertTrue(engView.getDocumentText().equals("this beer is good"));
    assertTrue(engView.getAnnotationIndex().size() == 5); // 4 annots plus documentAnnotation
    gerView = cas.getView("GermanDocument");
    assertTrue(gerView.getDocumentText().equals("das bier ist gut"));
    assertTrue(gerView.getAnnotationIndex().size() == 5); // 4 annots plus documentAnnotation
    assertTrue(CAS.NAME_DEFAULT_SOFA.equals(v1cas.getSofa().getSofaID()));
    assertTrue(v1cas.getDocumentText().equals("some text for the default text sofa."));
  }
  
  public void testDuplicateNsPrefixes() throws Exception {
    TypeSystemDescription ts = new TypeSystemDescription_impl();
    ts.addType("org.bar.foo.Foo", "", "uima.tcas.Annotation");
    ts.addType("org.baz.foo.Foo", "", "uima.tcas.Annotation");
    CAS cas = CasCreationUtils.createCas(ts, null, null);
    cas.setDocumentText("Foo");
    Type t1 = cas.getTypeSystem().getType("org.bar.foo.Foo");
    Type t2 = cas.getTypeSystem().getType("org.baz.foo.Foo");
    AnnotationFS a1 = cas.createAnnotation(t1,0,3);
    cas.addFsToIndexes(a1);
    AnnotationFS a2 = cas.createAnnotation(t2,0,3);
    cas.addFsToIndexes(a2);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    XmiCasSerializer.serialize(cas, baos);
    baos.close();
    byte[] bytes = baos.toByteArray();
    
    CAS cas2 = CasCreationUtils.createCas(ts, null, null);
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    XmiCasDeserializer.deserialize(bais, cas2);
    bais.close();
    
    CasComparer.assertEquals(cas, cas2);
  }
  
  public void testMerging() throws Exception {
    // deserialize a complex CAS from XCAS
    CAS cas = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(), indexes);
    InputStream serCasStream = new FileInputStream(JUnitExtension.getFile("ExampleCas/cas.xml"));
    XCASDeserializer.deserialize(serCasStream, cas);
    serCasStream.close();
    int numAnnotations = cas.getAnnotationIndex().size(); //for comparison later
    String docText = cas.getDocumentText(); //for comparison later
    //add a new Sofa to test that multiple Sofas in original CAS work
    CAS preexistingView = cas.createView("preexistingView");
    String preexistingViewText = "John Smith blah blah blah";
    preexistingView.setDocumentText(preexistingViewText);
    createPersonAnnot(preexistingView, 0, 10);
    
    // do XMI serialization to a string, using XmiSerializationSharedData
    // to keep track of maximum ID generated
    XmiSerializationSharedData serSharedData = new XmiSerializationSharedData();
    String xmiStr = serialize(cas, serSharedData);
    int maxOutgoingXmiId = serSharedData.getMaxXmiId();
    
    //deserialize into two new CASes, again using XmiSerializationSharedData so
    //we can get consistent IDs later.  
    CAS newCas1 = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(), indexes);
    XmiSerializationSharedData deserSharedData1 = new XmiSerializationSharedData();
    deserialize(xmiStr, newCas1, deserSharedData1, false, -1);
    
    CAS newCas2 = CasCreationUtils.createCas(typeSystem, new TypePriorities_impl(), indexes);
    XmiSerializationSharedData deserSharedData2 = new XmiSerializationSharedData();
    deserialize(xmiStr, newCas2, deserSharedData2, false, -1);
    
    //add new FS to each new CAS
    createPersonAnnot(newCas1, 0, 10);
    createPersonAnnot(newCas1, 20, 30);
    createPersonAnnot(newCas2, 40, 50);
    AnnotationFS person = createPersonAnnot(newCas2, 60, 70);
    
    //add an Owner relation that points to an organization in the original CAS,
    //to test links across merge boundary
    Type orgType = newCas2.getTypeSystem().getType(
            "org.apache.uima.testTypeSystem.Organization");
    AnnotationFS org = (AnnotationFS)newCas2.getAnnotationIndex(orgType).iterator().next();
    Type ownerType = newCas2.getTypeSystem().getType(
            "org.apache.uima.testTypeSystem.Owner");
    Feature argsFeat = ownerType.getFeatureByBaseName("relationArgs");
    Feature componentIdFeat = ownerType.getFeatureByBaseName("componentId");
    Type relArgsType = newCas2.getTypeSystem().getType(
            "org.apache.uima.testTypeSystem.BinaryRelationArgs");
    Feature domainFeat = relArgsType.getFeatureByBaseName("domainValue");
    Feature rangeFeat = relArgsType.getFeatureByBaseName("rangeValue");
    AnnotationFS ownerAnnot = newCas2.createAnnotation(ownerType, 0, 70);
    FeatureStructure relArgs = newCas2.createFS(relArgsType);
    relArgs.setFeatureValue(domainFeat, person);
    relArgs.setFeatureValue(rangeFeat, org);
    ownerAnnot.setFeatureValue(argsFeat, relArgs);
    ownerAnnot.setStringValue(componentIdFeat, "XCasDeserializerTest");
    newCas2.addFsToIndexes(ownerAnnot);
    int orgBegin = org.getBegin();
    int orgEnd = org.getEnd();
    
    //add Sofas
    CAS newView1 = newCas1.createView("newSofa1");
    final String sofaText1 = "This is a new Sofa, created in CAS 1.";
    newView1.setDocumentText(sofaText1);
    final String annotText = "Sofa";
    int annotStart1 = sofaText1.indexOf(annotText);
    AnnotationFS annot1 = newView1.createAnnotation(orgType, annotStart1, annotStart1 + annotText.length());
    newView1.addFsToIndexes(annot1);
    CAS newView2 = newCas1.createView("newSofa2");
    final String sofaText2 = "This is another new Sofa, created in CAS 2.";
    newView2.setDocumentText(sofaText2);
    int annotStart2 = sofaText2.indexOf(annotText);
    AnnotationFS annot2 = newView2.createAnnotation(orgType, annotStart2, annotStart2 + annotText.length());
    newView2.addFsToIndexes(annot2);

    //re-serialize each new CAS back to XMI, keeping consistent ids
    String newSerCas1 = serialize(newCas1, deserSharedData1);
    String newSerCas2 = serialize(newCas2, deserSharedData2);
    
    //merge the two XMI CASes back into the original CAS
    XmiSerializationSharedData deserSharedData3 = new XmiSerializationSharedData();
    deserialize(newSerCas1, cas, deserSharedData3, false, -1);
    
    assertEquals(numAnnotations +2, cas.getAnnotationIndex().size());
    
    deserialize(newSerCas2, cas, deserSharedData3, false, maxOutgoingXmiId);
    
    assertEquals(numAnnotations + 5, cas.getAnnotationIndex().size());
    
    assertEquals(docText, cas.getDocumentText());
    
    //check covered text of annotations
    FSIterator iter = cas.getAnnotationIndex().iterator();
    while (iter.hasNext()) {
      AnnotationFS annot = (AnnotationFS)iter.next();
      assertEquals(cas.getDocumentText().substring(
              annot.getBegin(), annot.getEnd()), annot.getCoveredText());
    }
    //check Owner annotation we created to test link across merge boundary
    iter = cas.getAnnotationIndex(ownerType).iterator();
    while (iter.hasNext()) {
      AnnotationFS annot = (AnnotationFS)iter.next();
      String componentId = annot.getStringValue(componentIdFeat);
      if ("XCasDeserializerTest".equals(componentId)) {
        FeatureStructure targetRelArgs = annot.getFeatureValue(argsFeat);
        AnnotationFS targetDomain = (AnnotationFS)targetRelArgs.getFeatureValue(domainFeat);
        assertEquals(60, targetDomain.getBegin());
        assertEquals(70, targetDomain.getEnd());
        AnnotationFS targetRange = (AnnotationFS)targetRelArgs.getFeatureValue(rangeFeat);
        assertEquals(orgBegin, targetRange.getBegin());
        assertEquals(orgEnd, targetRange.getEnd());
      }     
    } 
    //check Sofas
    CAS targetView1 = cas.getView("newSofa1");
    assertEquals(sofaText1, targetView1.getDocumentText());
    CAS targetView2 = cas.getView("newSofa2");
    assertEquals(sofaText2, targetView2.getDocumentText());
    AnnotationFS targetAnnot1 = (AnnotationFS) 
      targetView1.getAnnotationIndex(orgType).iterator().get();
    assertEquals(annotText, targetAnnot1.getCoveredText());
    AnnotationFS targetAnnot2 = (AnnotationFS) 
    targetView2.getAnnotationIndex(orgType).iterator().get();
    assertEquals(annotText, targetAnnot2.getCoveredText());
    assertTrue(targetView1.getSofa().getSofaRef() != 
            targetView2.getSofa().getSofaRef());
    
    CAS checkPreexistingView = cas.getView("preexistingView");
    assertEquals(preexistingViewText, checkPreexistingView.getDocumentText());
    Type personType = cas.getTypeSystem().getType("org.apache.uima.testTypeSystem.Person");    
    AnnotationFS targetAnnot3 = (AnnotationFS)
            checkPreexistingView.getAnnotationIndex(personType).iterator().get();
    assertEquals("John Smith", targetAnnot3.getCoveredText());
    
    //try an initial CAS that contains multiple Sofas
    
  }
  
  public void testOutOfTypeSystemData() throws Exception {
    // deserialize a simple XMI into a CAS with no TypeSystem    
    CAS cas = CasCreationUtils.createCas(new TypeSystemDescription_impl(),
            new TypePriorities_impl(), new FsIndexDescription[0]);
    File xmiFile = JUnitExtension.getFile("ExampleCas/simpleCas.xmi");
    String xmiStr = FileUtils.file2String(xmiFile, "UTF-8");
    
    XmiSerializationSharedData sharedData = new XmiSerializationSharedData();
    deserialize(xmiStr, cas, sharedData, true, -1);
    
    //do some checks on the out-of-type system data
    List ootsElems = sharedData.getOutOfTypeSystemElements();
    assertEquals(9, ootsElems.size());
    List ootsViewMembers = sharedData.getOutOfTypeSystemViewMembers("1");
    assertEquals(7, ootsViewMembers.size());
    
    // now reserialize including OutOfTypeSystem data
    String xmiStr2 = serialize(cas, sharedData);
    
    //deserialize both original and new XMI into CASes that do have the full typesystem
    CAS newCas1 = CasCreationUtils.createCas(typeSystem, null, indexes);
    deserialize(xmiStr, newCas1, null, false, -1);
    CAS newCas2 = CasCreationUtils.createCas(typeSystem, null, indexes);
    deserialize(xmiStr2, newCas2, null, false, -1);
    CasComparer.assertEquals(newCas1, newCas2);  
    
    //Test a partial type system with a missing some missing features and
    //missing "Organization" type
    File partialTypeSystemFile = JUnitExtension.getFile("ExampleCas/partialTestTypeSystem.xml");
    TypeSystemDescription partialTypeSystem = UIMAFramework.getXMLParser().parseTypeSystemDescription(
            new XMLInputSource(partialTypeSystemFile));
    CAS partialTsCas = CasCreationUtils.createCas(partialTypeSystem, null, indexes);
    XmiSerializationSharedData sharedData2 = new XmiSerializationSharedData();
    deserialize(xmiStr, partialTsCas, sharedData2, true, -1);
    
    assertEquals(1,sharedData2.getOutOfTypeSystemElements().size());
    OotsElementData ootsFeats3 = sharedData2.getOutOfTypeSystemFeatures(sharedData2.getFsAddrForXmiId(3));
    assertEquals(1, ootsFeats3.attributes.size());
    XmlAttribute ootsAttr = (XmlAttribute)ootsFeats3.attributes.get(0);
    assertEquals("mentionType", ootsAttr.name);
    assertEquals("NAME", ootsAttr.value);
    OotsElementData ootsFeats5 = sharedData2.getOutOfTypeSystemFeatures(sharedData2.getFsAddrForXmiId(5));
    assertEquals(0, ootsFeats5.attributes.size());
    assertEquals(1, ootsFeats5.childElements.size());
    XmlElementNameAndContents ootsChildElem = (XmlElementNameAndContents)
            ootsFeats5.childElements.get(0);
    assertEquals("mentionType", ootsChildElem.name.qName);
    assertEquals("NAME", ootsChildElem.contents);
    
    OotsElementData ootsFeats8 = sharedData2.getOutOfTypeSystemFeatures(sharedData2.getFsAddrForXmiId(8));
    assertEquals(1, ootsFeats8.attributes.size());
    OotsElementData ootsFeats10 = sharedData2.getOutOfTypeSystemFeatures(sharedData2.getFsAddrForXmiId(10));
    assertEquals(1, ootsFeats10.attributes.size());
    OotsElementData ootsFeats11 = sharedData2.getOutOfTypeSystemFeatures(sharedData2.getFsAddrForXmiId(11));
    assertEquals(4, ootsFeats11.childElements.size());
    
    String xmiStr3 = serialize(partialTsCas, sharedData2);
    newCas2.reset();
    deserialize(xmiStr3, newCas2, null, false, -1);
    CasComparer.assertEquals(newCas1, newCas2);    
 }
  
  public void testOutOfTypeSystemArrayElement() throws Exception {
    //add to type system an annotation type that has an FSArray feature
    TypeDescription testAnnotTypeDesc = typeSystem.addType("org.apache.uima.testTypeSystem.TestAnnotation", "", "uima.tcas.Annotation");
    testAnnotTypeDesc.addFeature("arrayFeat", "", "uima.cas.FSArray");
    //populate a CAS with such an array
    CAS cas = CasCreationUtils.createCas(typeSystem, null, null);
    Type testAnnotType = cas.getTypeSystem().getType("org.apache.uima.testTypeSystem.TestAnnotation");
    Type orgType = cas.getTypeSystem().getType(
      "org.apache.uima.testTypeSystem.Organization");
    AnnotationFS orgAnnot1 = cas.createAnnotation(orgType, 0, 10);
    cas.addFsToIndexes(orgAnnot1);
    AnnotationFS orgAnnot2 = cas.createAnnotation(orgType, 10, 20);
    cas.addFsToIndexes(orgAnnot2);
    AnnotationFS testAnnot = cas.createAnnotation(testAnnotType, 0, 20);
    cas.addFsToIndexes(testAnnot);
    ArrayFS arrayFs = cas.createArrayFS(2);
    arrayFs.set(0, orgAnnot1);
    arrayFs.set(1, orgAnnot2);
    Feature arrayFeat = testAnnotType.getFeatureByBaseName("arrayFeat");
    testAnnot.setFeatureValue(arrayFeat, arrayFs);
    
    //serialize to XMI
    String xmiStr = serialize(cas, null);
    
    //deserialize into a CAS that's missing the Organization type
    File partialTypeSystemFile = JUnitExtension.getFile("ExampleCas/partialTestTypeSystem.xml");
    TypeSystemDescription partialTypeSystem = UIMAFramework.getXMLParser().parseTypeSystemDescription(
            new XMLInputSource(partialTypeSystemFile));
    testAnnotTypeDesc = partialTypeSystem.addType("org.apache.uima.testTypeSystem.TestAnnotation", "", "uima.tcas.Annotation");
    testAnnotTypeDesc.addFeature("arrayFeat", "", "uima.cas.FSArray");
    CAS partialTsCas = CasCreationUtils.createCas(partialTypeSystem, null, null);
    XmiSerializationSharedData sharedData = new XmiSerializationSharedData();
    deserialize(xmiStr, partialTsCas, sharedData, true, -1);
    
    //check out of type system data
    Type testAnnotType2 = partialTsCas.getTypeSystem().getType("org.apache.uima.testTypeSystem.TestAnnotation");
    FeatureStructure testAnnot2 = partialTsCas.getAnnotationIndex(testAnnotType2).iterator().get(); 
    Feature arrayFeat2 = testAnnotType2.getFeatureByBaseName("arrayFeat");
    FeatureStructure arrayFs2 = testAnnot2.getFeatureValue(arrayFeat2);
    List ootsElems = sharedData.getOutOfTypeSystemElements();
    assertEquals(2, ootsElems.size());
    List ootsArrayElems = sharedData.getOutOfTypeSystemArrayElements(arrayFs2.hashCode());
    assertEquals(2, ootsArrayElems.size());
    for (int i = 0; i < 2; i++) {
      OotsElementData oed = (OotsElementData)ootsElems.get(i);
      XmiArrayElement arel = (XmiArrayElement)ootsArrayElems.get(i);
      assertEquals(oed.xmiId, arel.xmiId);      
    }
    
    //reserialize along with out of type system data
    String xmiStr2 = serialize(partialTsCas, sharedData);
    
    //deserialize into a new CAS and compare
    CAS cas2 = CasCreationUtils.createCas(typeSystem, null, null);
    deserialize(xmiStr2, cas2, null, false, -1);
    
    CasComparer.assertEquals(cas, cas2);    
  }
  
  public void testOutOfTypeSystemListElement() throws Exception {
    //add to type system an annotation type that has an FSList feature
    TypeDescription testAnnotTypeDesc = typeSystem.addType("org.apache.uima.testTypeSystem.TestAnnotation", "", "uima.tcas.Annotation");
    testAnnotTypeDesc.addFeature("listFeat", "", "uima.cas.FSList");
    //populate a CAS with such an list
    CAS cas = CasCreationUtils.createCas(typeSystem, null, null);
    Type testAnnotType = cas.getTypeSystem().getType("org.apache.uima.testTypeSystem.TestAnnotation");
    Type orgType = cas.getTypeSystem().getType(
      "org.apache.uima.testTypeSystem.Organization");
    AnnotationFS orgAnnot1 = cas.createAnnotation(orgType, 0, 10);
    cas.addFsToIndexes(orgAnnot1);
    AnnotationFS orgAnnot2 = cas.createAnnotation(orgType, 10, 20);
    cas.addFsToIndexes(orgAnnot2);
    AnnotationFS testAnnot = cas.createAnnotation(testAnnotType, 0, 20);
    cas.addFsToIndexes(testAnnot);
    Type nonEmptyFsListType = cas.getTypeSystem().getType(CAS.TYPE_NAME_NON_EMPTY_FS_LIST);
    Type emptyFsListType = cas.getTypeSystem().getType(CAS.TYPE_NAME_EMPTY_FS_LIST);
    Feature headFeat = nonEmptyFsListType.getFeatureByBaseName("head");
    Feature tailFeat = nonEmptyFsListType.getFeatureByBaseName("tail");
    FeatureStructure emptyNode = cas.createFS(emptyFsListType);
    FeatureStructure secondNode = cas.createFS(nonEmptyFsListType);
    secondNode.setFeatureValue(headFeat, orgAnnot2);
    secondNode.setFeatureValue(tailFeat, emptyNode);
    FeatureStructure firstNode = cas.createFS(nonEmptyFsListType);
    firstNode.setFeatureValue(headFeat, orgAnnot1);
    firstNode.setFeatureValue(tailFeat, secondNode);
    
    Feature listFeat = testAnnotType.getFeatureByBaseName("listFeat");
    testAnnot.setFeatureValue(listFeat, firstNode);
    
    //serialize to XMI
    String xmiStr = serialize(cas, null);
    System.out.println(xmiStr);
    
    //deserialize into a CAS that's missing the Organization type
    File partialTypeSystemFile = JUnitExtension.getFile("ExampleCas/partialTestTypeSystem.xml");
    TypeSystemDescription partialTypeSystem = UIMAFramework.getXMLParser().parseTypeSystemDescription(
            new XMLInputSource(partialTypeSystemFile));
    testAnnotTypeDesc = partialTypeSystem.addType("org.apache.uima.testTypeSystem.TestAnnotation", "", "uima.tcas.Annotation");
    testAnnotTypeDesc.addFeature("listFeat", "", "uima.cas.FSList");
    CAS partialTsCas = CasCreationUtils.createCas(partialTypeSystem, null, null);
    XmiSerializationSharedData sharedData = new XmiSerializationSharedData();
    deserialize(xmiStr, partialTsCas, sharedData, true, -1);
    
    //check out of type system data
    Type testAnnotType2 = partialTsCas.getTypeSystem().getType("org.apache.uima.testTypeSystem.TestAnnotation");
    FeatureStructure testAnnot2 = partialTsCas.getAnnotationIndex(testAnnotType2).iterator().get(); 
    Feature listFeat2 = testAnnotType2.getFeatureByBaseName("listFeat");
    FeatureStructure listFs = testAnnot2.getFeatureValue(listFeat2);
    List ootsElems = sharedData.getOutOfTypeSystemElements();
    assertEquals(2, ootsElems.size());
    OotsElementData oed = sharedData.getOutOfTypeSystemFeatures(listFs.hashCode());
    XmlAttribute attr = (XmlAttribute)oed.attributes.get(0);
    assertNotNull(attr);
    assertEquals(CAS.FEATURE_BASE_NAME_HEAD, attr.name);
    assertEquals(attr.value, ((OotsElementData)ootsElems.get(0)).xmiId);
    
    //reserialize along with out of type system data
    String xmiStr2 = serialize(partialTsCas, sharedData);
    System.out.println(xmiStr2);
    
    //deserialize into a new CAS and compare
    CAS cas2 = CasCreationUtils.createCas(typeSystem, null, null);
    deserialize(xmiStr2, cas2, null, false, -1);
    
    CasComparer.assertEquals(cas, cas2);    
  }
  
  public void testOutOfTypeSystemDataComplexCas() throws Exception {
    // deserialize a complex XCAS
    CAS originalCas = CasCreationUtils.createCas(typeSystem, null, indexes);
    InputStream serCasStream = new FileInputStream(JUnitExtension.getFile("ExampleCas/cas.xml"));
    XCASDeserializer.deserialize(serCasStream, originalCas);
    serCasStream.close();
    
    //serialize to XMI
    String xmiStr = serialize(originalCas, null);
    
    //deserialize into a CAS with no type system
    CAS casWithNoTs = CasCreationUtils.createCas(new TypeSystemDescription_impl(),
            new TypePriorities_impl(), new FsIndexDescription[0]);
    XmiSerializationSharedData sharedData = new XmiSerializationSharedData();
    deserialize(xmiStr, casWithNoTs, sharedData, true, -1);
        
    // now reserialize including OutOfTypeSystem data
    String xmiStr2 = serialize(casWithNoTs, sharedData);
    
    //deserialize into a new CAS that has the full type system
    CAS newCas = CasCreationUtils.createCas(typeSystem, null, indexes);
    deserialize(xmiStr2, newCas, null, false, -1);
    
    //compare
    CasComparer.assertEquals(originalCas, newCas);
    
    //Test a partial type system with a missing some missing features and
    //missing "Organization" type
    File partialTypeSystemFile = JUnitExtension.getFile("ExampleCas/partialTestTypeSystem.xml");
    TypeSystemDescription partialTypeSystem = UIMAFramework.getXMLParser().parseTypeSystemDescription(
            new XMLInputSource(partialTypeSystemFile));
    CAS partialTsCas = CasCreationUtils.createCas(partialTypeSystem, null, indexes);
    XmiSerializationSharedData sharedData2 = new XmiSerializationSharedData();
    deserialize(xmiStr, partialTsCas, sharedData2, true, -1);
        
    String xmiStr3 = serialize(partialTsCas, sharedData2);
    newCas.reset();
    deserialize(xmiStr3, newCas, null, false, -1);
    CasComparer.assertEquals(originalCas, newCas);    
 }
  
  public void testGetNumChildren() throws Exception {
    // deserialize a complex XCAS
    CAS cas = CasCreationUtils.createCas(typeSystem, null, indexes);
//    InputStream serCasStream = new FileInputStream(JUnitExtension.getFile("ExampleCas/cas.xml"));
//    XCASDeserializer.deserialize(serCasStream, cas);
//    serCasStream.close();
  InputStream serCasStream = new FileInputStream(JUnitExtension.getFile("ExampleCas/simpleCas.xmi"));
  XmiCasDeserializer.deserialize(serCasStream, cas);
  serCasStream.close();
    
    // call serializer with a ContentHandler that checks numChildren
    XmiCasSerializer xmiSer = new XmiCasSerializer(cas.getTypeSystem());
    GetNumChildrenTestHandler handler = new GetNumChildrenTestHandler(xmiSer);
    xmiSer.serialize(cas, handler);
  }

  /** Utility method for serializing a CAS to an XMI String 
   * */
  private String serialize(CAS cas, XmiSerializationSharedData serSharedData) throws IOException, SAXException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();    
    XmiCasSerializer.serialize(cas, null, baos, false, serSharedData);
    baos.close();
    String xmiStr = new String(baos.toByteArray(), "UTF-8");   //note by default XmiCasSerializer generates UTF-8
    
    //workaround for newline serialization problem in Sun Java 1.4.2
    //this test file should contain CRLF line endings, but Sun Java loses them
    //when it serializes XML.
    if(!builtInXmlSerializationSupportsCRs()) {
      xmiStr = xmiStr.replaceAll("&#10;", "&#13;&#10;");
    }        
    return xmiStr;
  }


  /** Utility method for deserializing a CAS from an XMI String */
  private void deserialize(String xmlStr, CAS cas, XmiSerializationSharedData sharedData, boolean lenient, int mergePoint) throws FactoryConfigurationError, ParserConfigurationException, SAXException, IOException {
    byte[] bytes = xmlStr.getBytes("UTF-8"); //this assumes the encoding is UTF-8, which is the default output encoding of the XmiCasSerializer
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    XmiCasDeserializer.deserialize(bais, cas, lenient, sharedData, mergePoint);
    bais.close();
  }  
  
  private AnnotationFS createPersonAnnot(CAS cas, int begin, int end) {
    Type personType = cas.getTypeSystem().getType("org.apache.uima.testTypeSystem.Person");
    AnnotationFS person = cas.createAnnotation(personType, begin, end);
    cas.addFsToIndexes(person);
    return person;
  }
  
  /**
   * Checks the Java vendor and version and returns true if running a version
   * of Java whose built-in XSLT support can properly serialize carriage return
   * characters, and false if not.  It seems to be the case that Sun JVMs prior
   * to 1.5 do not properly serialize carriage return characters.  We have to
   * modify our test case to account for this.
   * @return true if XML serialization of CRs behave properly in the current JRE
   */
  private boolean builtInXmlSerializationSupportsCRs() {
    String javaVendor = System.getProperty("java.vendor");
    if( javaVendor.startsWith("Sun") ) {
        String javaVersion = System.getProperty("java.version");
        if( javaVersion.startsWith("1.3") || javaVersion.startsWith("1.4") )
            return false;
    }
    return true;
  }  
  
  static class GetNumChildrenTestHandler extends DefaultHandler {
    XmiCasSerializer xmiSer;
    Stack childCountStack = new Stack();
    
    GetNumChildrenTestHandler(XmiCasSerializer xmiSer) {
      this.xmiSer = xmiSer;
      childCountStack.push(new Integer(1));
    }

    /* (non-Javadoc)
     * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      // TODO Auto-generated method stub
      super.startElement(uri, localName, qName, attributes);
      childCountStack.push(new Integer(xmiSer.getNumChildren()));
    }

    /* (non-Javadoc)
     * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
     */
    public void endElement(String uri, String localName, String qName) throws SAXException {
      // TODO Auto-generated method stub
      super.endElement(uri, localName, qName);
      //check that we've seen the expected number of child elements
      //(count on top of stack should be 0)
      Integer count = (Integer)childCountStack.pop();
      assertEquals(0, count.intValue());
      
      //decremenet child count of our parent
      count = (Integer)childCountStack.pop();
      childCountStack.push(new Integer(count.intValue() - 1));
    }

    /* (non-Javadoc)
     * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
     */
    public void characters(char[] ch, int start, int length) throws SAXException {
      // text node is considered a child
      if (length > 0) {
        Integer count = (Integer)childCountStack.pop();
        childCountStack.push(new Integer(count.intValue() - 1));
      }
    }
    
    
  }
}
