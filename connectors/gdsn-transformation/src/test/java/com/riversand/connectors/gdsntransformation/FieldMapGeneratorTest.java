package com.riversand.connectors.gdsntransformation;

import java.io.InputStream;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.riversand.rsconnect.common.config.FieldMapping;
import com.riversand.rsconnect.common.config.RSConnectContext;
import com.riversand.rsconnect.common.helpers.RSExtensionConnectContextSerializer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FieldMapGeneratorTest {
   @Ignore
   @Test
   public void generate() throws Exception {
      InputStream contextStream = FieldMapGeneratorTest.class.getResourceAsStream("transformProfile.json");
      RSConnectContext connectContext = RSExtensionConnectContextSerializer.fromJson(null, contextStream);
      contextStream = FieldMapGeneratorTest.class.getResourceAsStream("transformProfile.json");
      RSConnectContext expectedContext = RSExtensionConnectContextSerializer.fromJson(null, contextStream);
      connectContext.getConnectProfile().getTransform().getFieldMap().removeAll(connectContext.getConnectProfile().getTransform().getFieldMap());
      FieldMapGenerator fieldMapGenerator = new FieldMapGenerator(null, null, null, connectContext, null);
      List<FieldMapping> fieldMappingList = fieldMapGenerator.generate(null);
      connectContext.getConnectProfile().getTransform().getFieldMap().addAll(fieldMappingList);
      assertNotNull(fieldMappingList);
      assertEquals(expectedContext.toJson(), connectContext.toJson());
   }
}
