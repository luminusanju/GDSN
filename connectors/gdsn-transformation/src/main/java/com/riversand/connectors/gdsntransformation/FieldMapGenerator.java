/*
   FILE: FieldMapGenerator.java
   PURPOSE: Read the fieldmappings from config ojet and prepares a List
   COPYRIGHT: Copyright (c) 2017 Riversand Technologies, Inc. All rights reserved.
   HISTORY: 10 June 2020  Sravan Oddi  Created
*/
package com.riversand.connectors.gdsntransformation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;

import com.riversand.connectors.extension.helpers.TransformerHelper;
import com.riversand.rsconnect.common.config.FieldMapping;
import com.riversand.rsconnect.common.config.RSConnectContext;
import com.riversand.rsconnect.common.helpers.ConnectRuntimeException;
import com.riversand.rsconnect.common.transform.FieldMetadata;
import com.riversand.rsconnect.common.transform.IFieldMapGenerator;
import com.riversand.rsconnect.interfaces.clients.IServiceClient;
import com.riversand.rsconnect.interfaces.models.RdpStatusDetail;

public class FieldMapGenerator implements IFieldMapGenerator {
   private RSConnectContext connectContext;
   private String configId;

   /**
    * Constructor.
    *
    * @param sourceMetadata      Source field metadata.
    * @param destinationMetadata Destination field metadata.
    * @param connectContext      Mapping generation configuration.
    */
   public FieldMapGenerator(Map<String, Map<String, FieldMetadata>> sourceMetadata,
                            Map<String, Map<String, FieldMetadata>> destinationMetadata,
                            List<FieldMapping> overrides,
                            RSConnectContext connectContext,
                            IServiceClient iServiceClient) {
      this.connectContext = connectContext;
      this.configId = connectContext.getConnectProfile().getTransform().getSettings().getAdditionalSetting("mappingConfig");
   }

   @Override
   public List<FieldMapping> generate(RdpStatusDetail rdpStatusDetail) throws Exception {
      if (Strings.isNullOrEmpty(this.configId)) {
         throw new ConnectRuntimeException("RSC7820", "Mappings config id is missing.");
      }
      JsonObject dataObject = TransformerHelper.getConfigDataObject(connectContext.getExecutionContext().getTenantId(), this.configId,  "mappings", null);
      List<FieldMapping> fieldMappings = new ArrayList<>();
      fieldMappings.addAll(TransformerHelper.getFieldMappings(dataObject, "jsonData.mappings"));
      List<FieldMapping> relationshipMap = TransformerHelper.getFieldMappings(dataObject, "jsonData.relationshipMappings");
      if(CollectionUtils.isNotEmpty(relationshipMap)) {
         connectContext.getConnectProfile().getTransform().getRelationships().getFieldMap().addAll(relationshipMap);
      }

      return fieldMappings;
   }

   @Override
   public Map<String, Map<String, FieldMetadata>> getMetadata() {
      return null;
   }
}
