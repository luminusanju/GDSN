package com.riversand.connectors.gdsntransformation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.junit.Test;

import com.google.gson.JsonObject;

import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManager;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManagerLogger;
import com.riversand.rsconnect.common.config.RSConnectContext;
import com.riversand.rsconnect.common.helpers.GsonBuilder;
import com.riversand.rsconnect.common.helpers.RSExtensionConnectContextSerializer;
import com.riversand.rsconnect.interfaces.models.IRecord;
import com.riversand.rsconnect.interfaces.models.JsonRecord;

import static com.riversand.connectors.extension.helpers.Constants.LogCodes.RSC_7273;
import static com.riversand.rsconnect.interfaces.constants.Constants.Services.RSCONNECT_SERVICE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GDSNTransformerTest {
   private static ProfilerManagerLogger pmLogger = ProfilerManager.getLogger(GDSNTransformerTest.class);

   private static JsonObject getObject(String name) {
      InputStream entityStream = GDSNTransformerTest.class.getResourceAsStream(name);
      return GsonBuilder.getGsonInstance().fromJson(new InputStreamReader(entityStream, Charset.defaultCharset()), JsonObject.class);
   }

   private static void validate(JsonRecord inboundRecord, JsonObject expectedTransformedEntity, String profileName) {
      InputStream contextStream = GDSNTransformerTest.class.getResourceAsStream(profileName);
      RSConnectContext connectContext = RSExtensionConnectContextSerializer.fromJson(null, contextStream);
      IRecord outboundRecord = null;
      try (GDSNTransformer gdsnTransformer = new GDSNTransformer(connectContext)) {
         outboundRecord = gdsnTransformer.transform(inboundRecord, null);
      } catch (Exception e) {
         pmLogger.error("", RSCONNECT_SERVICE, RSC_7273, e.getMessage(), e);
      }
      assertNotNull(outboundRecord);
      assertEquals(expectedTransformedEntity, ((JsonRecord) outboundRecord).getJsonObject());
   }

   @Test
   public void testTransform() {
      JsonObject entityObject = getObject("sourceEntity.json");
      JsonRecord inboundRecord = new JsonRecord(entityObject, null);
      JsonObject expectedTransformedEntity = getObject("expectedTransformedEntity.json");
      validate(inboundRecord, expectedTransformedEntity, "transformProfile.json");
   }

   @Test
   public void testNestedAttributeTransform() {
      JsonObject entityObject = getObject("sourceEntity.json");
      JsonRecord inboundRecord = new JsonRecord(entityObject, null);
      JsonObject expectedTransformedEntity = getObject("expectedNestedTransformedData.json");
      validate(inboundRecord, expectedTransformedEntity, "nestedAttributeProfile.json");
   }

   @Test
   public void testTransformRelationshipRecords() {
      JsonObject entityObject = getObject("sourceEntity.json");
      JsonRecord inboundRecord = new JsonRecord(entityObject, null);
      JsonObject expectedTransformedEntity = getObject("expectedTransformedRelationships.json");
      validate(inboundRecord, expectedTransformedEntity, "relationshipAttributeProfile.json");
   }
}
