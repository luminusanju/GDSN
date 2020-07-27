/*
   FILE: Transformer.java

   PURPOSE: Transform record fields based on the mapping.

   COPYRIGHT: Copyright (c) 2017 Riversand Technologies, Inc. All rights reserved.

   HISTORY: 19 June 2020  Sravan Oddi  Created
*/
package com.riversand.connectors.gdsntransformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.riversand.connectors.extension.helpers.TransformerHelper;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManager;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManagerLogger;
import com.riversand.rsconnect.common.config.AppConfig;
import com.riversand.rsconnect.common.config.FieldMapping;
import com.riversand.rsconnect.common.config.RSConnectContext;
import com.riversand.rsconnect.common.config.TransformConfig;
import com.riversand.rsconnect.common.helpers.ConnectIllegalArgumentException;
import com.riversand.rsconnect.common.helpers.ConnectRuntimeException;
import com.riversand.rsconnect.common.rsconnect.driver.Constants;
import com.riversand.rsconnect.common.transform.FieldMapMacro;
import com.riversand.rsconnect.interfaces.clients.IServiceClient;
import com.riversand.rsconnect.interfaces.models.ContextMapping;
import com.riversand.rsconnect.interfaces.models.IRecord;
import com.riversand.rsconnect.interfaces.models.JsonRecord;
import com.riversand.rsconnect.interfaces.models.RdpStatusDetail;
import com.riversand.rsconnect.interfaces.transformer.IRecordTransformer;

import static java.util.stream.Collectors.toList;

public class GDSNTransformer implements IRecordTransformer {

   private static final List<String> nestedTyes = Arrays.asList("oneToTwoLevel", "oneToThreeLevel");
   private ProfilerManagerLogger pmLogger = ProfilerManager.getLogger(GDSNTransformer.class);
   private RSConnectContext connectContext;
   private TransformConfig config;
   private String contextDelimiter;

   public GDSNTransformer(RSConnectContext connectContext, IServiceClient serviceClient) {
      this(connectContext);
   }

   /**
    * Class to transform the data from source format to destination format using with fieldMappings
    *
    * @param connectContext - Contains execution context and profile configuration
    */
   public GDSNTransformer(RSConnectContext connectContext) {
      this.connectContext = connectContext;
      this.config = connectContext.getConnectProfile().getTransform();
      if (CollectionUtils.isEmpty(this.config.getFieldMap()) && CollectionUtils.isEmpty(this.config.getRelationships().getFieldMap())) {
         throw new ConnectIllegalArgumentException("RSC7820", "fieldMaps are empty");
      }
      //Adding this condition for unit test handling
      if(!Strings.isNullOrEmpty(connectContext.getExecutionContext().getTenantId())) {
         this.contextDelimiter = AppConfig.getInstance().getContextDelimiter(connectContext.getExecutionContext().getTenantId());
      }
   }

   /**
    * Transform record data into destination format.
    *
    * @param record   input IRecord: Sample supports only JsonRecord
    * @param messages To Log messages when transform not happened with the field.
    * @return IRecord of output format
    */
   @Override
   public IRecord transform(IRecord record, RdpStatusDetail messages) {
      if (!(record instanceof JsonRecord)) {
         throw new ConnectRuntimeException("RSC7820", "Record doesn't support for transformation");
      }

      String entityType = record.getValue(Constants.TYPE);
      if (Strings.isNullOrEmpty(entityType)) {
         throw new ConnectRuntimeException("RSC7820", "Failed to get entityType from Object" + ((JsonRecord) record).getJsonObject());
      }

      IRecord outboundRecord = transformRecord(record, entityType);
      transformRelationshipRecords(entityType, record, outboundRecord);
      return outboundRecord;
   }

   /**
    * Method to transform record to JsonRecord
    *
    * @param inboundRecord - Input Record
    * @param entityType    - entity Type
    */
   private IRecord transformRecord(IRecord inboundRecord, String entityType) {
      //Copy to new List
      List<FieldMapping> fieldMap = config.getFieldMap().stream().map(FieldMapping::new).collect(toList());
      IRecord outboundRecord = new JsonRecord();
      JsonObject inboundObject = ((JsonRecord) inboundRecord).getJsonObject();
      List<ContextMapping> contexts = TransformerHelper.getContextMappings(inboundObject, connectContext.getConnectProfile().getCollect().getFormat().getType(), connectContext.getConnectProfile().getPublish().getFormat().getType(), contextDelimiter);

      if (!fieldMap.isEmpty()) {
         getAndSetRecordValues(inboundRecord, outboundRecord, entityType, null, fieldMap, null);
      }
      if (contexts != null) {
         for (ContextMapping contextMapping : contexts) {
            contextMapping.Initialize(contextDelimiter, connectContext.getConnectProfile().getCollect().getFormat().getType(), connectContext.getConnectProfile().getPublish().getFormat().getType());
            contextMapping.setISJson(true);
            String contextKey = contextMapping.getKeyFromSourceRecord(inboundRecord);
            // The context mapping may not be defined for this record.
            // Perhaps this record defines data for some other context.
            if (Strings.isNullOrEmpty(contextKey)) {
               continue;
            }

            // The context is defined, set all fields defined in this context.
            getAndSetRecordValues(inboundRecord, outboundRecord, entityType, contextKey, fieldMap, contextMapping);
         }
      }
      return outboundRecord;
   }

   private void transformRelationshipRecords(String entityType, IRecord inboundRecord, IRecord outboundRecord) {
      try {
         JsonObject inboundObject = ((JsonRecord) inboundRecord).getJsonObject();
         JsonElement relationships = JsonRecord.findObject(inboundObject, String.format("%s.%s", Constants.DATA, Constants.OPERATION_SEARCH_RELATIONSHIPS));
         processRelationships(entityType, outboundRecord, relationships);

         List<ContextMapping> contexts = TransformerHelper.getContextMappings(inboundObject, connectContext.getConnectProfile().getCollect().getFormat().getType(), connectContext.getConnectProfile().getPublish().getFormat().getType(), contextDelimiter);
         if (contexts != null) {
            for (ContextMapping contextMapping : contexts) {
               contextMapping.Initialize(contextDelimiter, connectContext.getConnectProfile().getCollect().getFormat().getType(), connectContext.getConnectProfile().getPublish().getFormat().getType());
               contextMapping.setISJson(true);
               String contextKey = contextMapping.getKeyFromSourceRecord(inboundRecord);
               if (Strings.isNullOrEmpty(contextKey)) {
                  continue;
               }

               relationships = JsonRecord.findObject(inboundObject, String.format("%s.%s[%s].%s", Constants.DATA, Constants.CONTEXTS, contextKey, Constants.OPERATION_SEARCH_RELATIONSHIPS));
               processRelationships(entityType, outboundRecord, relationships);
            }
         }
      } catch (Exception ex) {
         pmLogger.error(Constants.RSCONNECT_SERVICE, "RSC7273", ex.getMessage());
      }
   }

   private void processRelationships(String entityType, IRecord outboundRecord, JsonElement relationships) {
      if (relationships != null && relationships.isJsonObject()) {
         List<FieldMapping> mappings = config.getRelationships().getFieldMap();
         if (CollectionUtils.isNotEmpty(mappings)) {
            transformRelationships(entityType, outboundRecord, relationships, mappings);
         }
      }
   }

   private void transformRelationships(String entityType, IRecord outboundRecord, JsonElement relationships, List<FieldMapping> mappings) {
      for (Map.Entry<String, JsonElement> entry : relationships.getAsJsonObject().entrySet()) {
         if (entry.getValue() != null && entry.getValue().isJsonArray()) {
            int index = 0;
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
               if (element.isJsonObject()) {
                  Integer[] indices = new Integer[1];
                  indices[0] = index;
                  getAndSetRelationshipAttributeValues(entityType, new JsonRecord(element.getAsJsonObject(), null), outboundRecord, mappings, indices);
                  index++;
               }
            }
         }
      }
   }

   private void getAndSetRelationshipAttributeValues(String entityType, JsonRecord record, IRecord outboundRecord, List<FieldMapping> mappings, Integer... indicies) {
      Iterator<FieldMapping> iterator = mappings.iterator();
      while (iterator.hasNext()) {
         FieldMapping fieldMapping = iterator.next();
         if (fieldMapping.getEntityType().equals(entityType)) {
            String value = getRelationshipValue(record, fieldMapping, Constants.Mappings.VALUE);
            if (!Strings.isNullOrEmpty(value)) {
               setValue(outboundRecord, value, null, fieldMapping, null, record, indicies);
            }
         }
      }
   }

   private String getRelationshipValue(IRecord inbound, FieldMapping fieldMapping, String key) {
      String attributePath = "";
      String field = fieldMapping.getSource();
      if (Strings.isNullOrEmpty(field)) {
         throw new ConnectIllegalArgumentException("RSC7820", "sourve cannot be empty. Method: getrelationshipValue");
      }
      if (FieldMapMacro.isAttribute(field)) {
         String attributeName = FieldMapMacro.getAttribute(field);
         attributePath = String.format("attributes.%s.values[0].%s", attributeName, key);
      } else if (FieldMapMacro.isRelationshipAttribute(field)) {
         FieldMapMacro.RelAttrMacroValues macroValues = FieldMapMacro.getValuesFromRelationshipAttribute(null, field);
         attributePath = String.format("attributes.%s.values[0].%s", macroValues.attributeName, key);
      } else if (FieldMapMacro.isRelToAttribute(field)) {
         FieldMapMacro.RelToAttrMacroValues macroValues = FieldMapMacro.getValuesFromRelToAttribute(null, field);
         attributePath = String.format("relTo.data.attributes.%s.values[0].%s", macroValues.attributeName, key);
      }
      if (Strings.isNullOrEmpty(attributePath)) {
         attributePath = field;
      }
      return inbound.getValue(attributePath);
   }

   /**
    * Set all fields defined in this entity and context.
    */
   private void getAndSetRecordValues(IRecord inboundRecord, IRecord outboundRecord, String entityType, String contextKey, List<FieldMapping> fieldMap, ContextMapping contextMapping) {
      try {
         Iterator<FieldMapping> iterator = fieldMap.iterator();
         while (iterator.hasNext()) {
            FieldMapping fieldMapping = iterator.next();
            if (!fieldMapping.isEnabled()) {
               continue;
            }
            if (fieldMapping.getEntityType() == null || !fieldMapping.getEntityType().equals(entityType)) {
               continue;
            }
            if (isMultiLevelAttribute(fieldMapping)) {
               setValueForLevels(inboundRecord, outboundRecord, fieldMapping, contextKey);
            } else {
               int index = 0;
               String value = getValue(inboundRecord, contextKey, fieldMapping, index);
               if (!Strings.isNullOrEmpty(value)) {
                  String uomValue = null;
                  if (fieldMapping.hasUOM()) {
                     uomValue = TransformerHelper.getValueFromUOMField(inboundRecord, contextKey, config.getSettings().getCollectionSeparator(), fieldMapping, index, value);
                  }
                  setValue(outboundRecord, value, contextKey, fieldMapping, uomValue, inboundRecord);
               } else {
                  pmLogger.debug("", Constants.RSCONNECT_SERVICE, "RSC7273", fieldMapping.getSource());
               }
            }
         }
      } catch (Exception ex) {
         pmLogger.error("", Constants.RSCONNECT_SERVICE, "RSC7273", ex);
      }
   }

   /**
    * Get the value of this field.
    */
   private String getValue(IRecord record, String sourceContextKey, FieldMapping fieldMapping, int index, Integer... parentIndices) {
      if (fieldMapping.isCollectionType() || fieldMapping.isLocalizable()) {
         StringJoiner joiner = new StringJoiner(config.getSettings().getCollectionSeparator());
         index = 0;
         while (true) {
            String value = getValueInContext(record, fieldMapping, sourceContextKey, index, parentIndices);
            // When null, we have reached the end of the array.
            if (Strings.isNullOrEmpty(value)) {
               break;
            }
            joiner.add(value);
            index++;
         }
         return joiner.toString();
      } else {
         return getValueInContext(record, fieldMapping, sourceContextKey, index, parentIndices);
      }
   }

   private boolean isMultiLevelAttribute(FieldMapping fieldMapping) {
      return nestedTyes.contains(fieldMapping.getType());
   }

   /**
    * Get value from record. If value not found, check in self context. To support flat hierarchy RSJSON format.
    */
   private String getValueInContext(IRecord record, FieldMapping fieldMapping, String sourceContextKey, int index, Integer... parentIndices) {
      String attributePath = TransformerHelper.getSourceFieldInContext(fieldMapping.getSource(), sourceContextKey, Constants.Mappings.VALUE, 0, parentIndices);
      index = getIndex(record, fieldMapping, index, attributePath);

      return TransformerHelper.getSourceFieldValue(record, fieldMapping, sourceContextKey, Constants.Mappings.VALUE, index, parentIndices);
   }

   private int getIndex(IRecord record, FieldMapping fieldMapping, int index, String attributePath) {
      attributePath = attributePath.replaceAll(Pattern.quote("[0].value"), "").replaceAll(Pattern.quote("[0].src"), "");
      JsonArray values = JsonRecord.findArray(((JsonRecord) record).getJsonObject(), attributePath);
      int matchCount = 0;
      if (values != null && values.size() > 0) {
         int loopIndex = 0;
         for (JsonElement valueElement : values) {
            ++matchCount;
            if (!fieldMapping.isCollectionType() && !fieldMapping.isLocalizable()) {
               index = loopIndex;
               break;
            } else {
               if (index == matchCount - 1) {
                  index = loopIndex;
                  break;
               }
            }
            ++loopIndex;
         }
         if (loopIndex == values.size()) {
            index = loopIndex;
         }
      }
      if (matchCount == 0) {
         index = 10000;
      }
      return index;
   }

   /**
    * Set the value of this field in specified context.
    */
   private void setValue(IRecord record, String value, String contextKey, FieldMapping fieldMapping, String uom, IRecord inboundRecord, Integer... nestedIndices) {
      int index = 0;
      if (fieldMapping.isCollectionType() || fieldMapping.isLocalizable()) {
         String[] uoms = null;
         if (fieldMapping.hasUOM() && !Strings.isNullOrEmpty(uom)) {
            uoms = uom.split(Pattern.quote(config.getSettings().getCollectionSeparator()));
         }
         for (String subValue : value.split(Pattern.quote(config.getSettings().getCollectionSeparator()))) {
            String uomValue = null;
            if (fieldMapping.hasUOM() && uoms != null) {
               if ((index < uoms.length)) {
                  uomValue = uoms[index];
               }
            }
            setFieldValue(inboundRecord, record, contextKey, fieldMapping, index, subValue, uomValue, nestedIndices);
            index++;
         }
      } else {
         setFieldValue(inboundRecord, record, contextKey, fieldMapping, index, value, uom, nestedIndices);
      }
   }

   private void setFieldValue(IRecord inboundRecord, IRecord record, String contextKey, FieldMapping fieldMapping, int index, String value, String uomValue, Integer... nestedIndices) {
      String field;
      if (fieldMapping.getType().equalsIgnoreCase("referenceTypeData")) {
         field = getDestiantionPath(fieldMapping.getDestination());
         String[] fields = field.split("#@#");
         if (fields.length == 2) {
            setRecordValue(record, fieldMapping, fields[0], index, value, nestedIndices);
            value = TransformerHelper.getSourceFieldValue(inboundRecord, fieldMapping, contextKey, "properties.referenceDataIdentifier", index, nestedIndices);
            setRecordValue(record, fieldMapping, fields[1], index, value, nestedIndices);
         } else {
            setRecordValue(record, fieldMapping, field, index, value, nestedIndices);
         }
      } else if (fieldMapping.isLocalizable()) {
         field = getDestinationField(fieldMapping.getDestination(), index, fieldMapping, nestedIndices);
         String localeString = TransformerHelper.getSourceFieldValue(inboundRecord, fieldMapping, contextKey, "locale", index, nestedIndices);
         Locale locale = Locale.forLanguageTag(localeString);
         setValue(record, fieldMapping, locale.getLanguage(), "%s.@languageCode", field);
         setValue(record, fieldMapping, value, "%s.__value__", field);
      } else if (fieldMapping.hasUOM()) {
         field = getDestiantionPath(fieldMapping.getDestination());
         String[] fields = field.split("#@#");
         if (fields.length == 2) {
            setRecordValue(record, fieldMapping, fields[0], index, uomValue, nestedIndices);
            setRecordValue(record, fieldMapping, fields[1], index, value, nestedIndices);
         } else {
            setRecordValue(record, fieldMapping, field, index, value, nestedIndices);
         }
      } else {
         setRecordValue(record, fieldMapping, fieldMapping.getDestination(), index, value, nestedIndices);
      }
   }

   private void setRecordValue(IRecord record, FieldMapping fieldMapping, String field, int index, String value, Integer[] nestedIndices) {
      field = getDestinationField(field, index, fieldMapping, nestedIndices);
      setValue(record, fieldMapping, value, field);
   }

   private void setValue(IRecord record, FieldMapping fieldMapping, String value, String path, Object... params) {
      String destinationField = String.format(path, params);
      record.setValue(destinationField, value, fieldMapping.getType(), true);
   }

   /**
    * Get the final destination field which can be understood by the record set/get value.
    */
   private String getDestinationField(String field, int index, FieldMapping fieldMapping, Integer... parentIndices) {
      if (Strings.isNullOrEmpty(field)) {
         throw new ConnectIllegalArgumentException("RSC7820", "field cannot be null");
      }
      List<Integer> indexArray = getIndexArray(index, fieldMapping, parentIndices);
      if(GDSNFieldMapMacro.isAttribute(field)) {
         return GDSNFieldMapMacro.getAttribute(field);
         //This is required for internal attribute transformation
      }
      else if (GDSNFieldMapMacro.isPath(field)) {
         String path = GDSNFieldMapMacro.getPath(field);
         if (fieldMapping.getType().equalsIgnoreCase("referenceTypeData")) {
            return path;
         }
         return String.format(path, indexArray.toArray());
      } else if (GDSNFieldMapMacro.isRelPath(field)) {
         String path = GDSNFieldMapMacro.getRelPath(field);
         if (fieldMapping.getType().equalsIgnoreCase("referenceTypeData")) {
            return path;
         }
         return String.format(path, indexArray.toArray());
      } else if (field.contains("[%d]")) {
         return String.format(field, indexArray.toArray());
      }
      return field;
   }

   private String getDestiantionPath(String field) {
      if (Strings.isNullOrEmpty(field)) {
         throw new ConnectIllegalArgumentException("RSC7820", "field cannot be null");
      }
      if (GDSNFieldMapMacro.isPath(field)) {
         return GDSNFieldMapMacro.getPath(field);
      } else if (GDSNFieldMapMacro.isRelPath(field)) {
         return GDSNFieldMapMacro.getRelPath(field);
      }
      return field;
   }

   private List<Integer> getIndexArray(int index, FieldMapping fieldMapping, Integer[] parentIndices) {
      List<Integer> indexArray = new ArrayList<>();
      if (parentIndices != null && parentIndices.length > 0) {
         for (int i = 0; i < parentIndices.length; i++) {
            indexArray.add(parentIndices[i]);
         }
         if (fieldMapping.isCollectionType() || fieldMapping.isLocalizable()) {
            indexArray.add(index);
         }
      } else {
         indexArray.add(index);
      }
      return indexArray;
   }

   @Override
   public String toString() {
      return "GDSNTransformer: inboundFormat = " + connectContext.getConnectProfile().getCollect().getFormat().getType() +
            ", outboundFormat = " + connectContext.getConnectProfile().getPublish().getFormat().getType();
   }

   private void setValueForLevels(IRecord inboundRecord, IRecord outboundRecord, FieldMapping fieldMapping, String contextKey) {
      for (FieldMapping childFieldMapping :
            fieldMapping.getChildFieldMappings()) {
         String attributeName = FieldMapMacro.getAttribute(childFieldMapping.getSource());
         Integer firstIndexOfParent = attributeName.indexOf(".group[");
         String firstParentName = attributeName.substring(0, firstIndexOfParent);
         Integer groupCount = getGroupCount(childFieldMapping.getSource());
         String firstParentPath;
         if (Strings.isNullOrEmpty(contextKey)) {
            firstParentPath = String.format(Constants.Mapping.ENTITY_ATTRIBUTE_IN_SELF, firstParentName);
         } else {
            firstParentPath = String.format(Constants.Mapping.ENTITY_ATTRIBUTE_IN_CONTEXT,
                  contextKey, firstParentName);
         }
         JsonObject attributeParentObject = JsonRecord.findObject(((JsonRecord) inboundRecord).getJsonObject(), firstParentPath);
         if (attributeParentObject != null) {
            ArrayList<Integer> parentIndicesList = new ArrayList<>();
            getAndSetValuesForChild(inboundRecord, outboundRecord, contextKey, attributeParentObject, childFieldMapping, groupCount, parentIndicesList, 0);
         }
      }

   }

   private void getAndSetValuesForChild(IRecord inboundRecord, IRecord outboundRecord, String sourceContextKey,
                                        JsonObject attributeParentObject, FieldMapping fieldMapping, Integer groupCount,
                                        ArrayList<Integer> parentIndices, Integer currentParentIndex) {
      if (attributeParentObject != null && attributeParentObject.has(Constants.NESTED_ATTRIBUTES_GROUP)) {
         JsonArray groups = attributeParentObject.get(Constants.NESTED_ATTRIBUTES_GROUP).getAsJsonArray();
         if (groups != null && groups.size() > 0) {
            for (int index = 0; index < groups.size(); ++index) {
               if (parentIndices.size() > currentParentIndex) {
                  parentIndices.set(currentParentIndex, index);
               } else {
                  parentIndices.add(currentParentIndex, index);
               }
               JsonObject childAttributes = groups.get(index).getAsJsonObject();
               for (Map.Entry<String, JsonElement> childAttributeMap : childAttributes.entrySet()) {
                  if (childAttributeMap.getValue() instanceof JsonObject) {
                     if (fieldMapping.getSource().endsWith("." + childAttributeMap.getKey() + ")")
                           && parentIndices.size() == groupCount) {
                        int count = 0;
                        String value = getValue(inboundRecord, sourceContextKey, fieldMapping, count, parentIndices.toArray(new Integer[parentIndices.size()]));
                        if (!Strings.isNullOrEmpty(value)) {
                           String uomValue = null;
                           if (fieldMapping.hasUOM()) {
                              uomValue = TransformerHelper.getValueFromUOMField(inboundRecord, sourceContextKey, config.getSettings().getCollectionSeparator(), fieldMapping, count, value, parentIndices.toArray(new Integer[parentIndices.size()]));
                           }
                           setValue(outboundRecord, value, sourceContextKey, fieldMapping, uomValue, inboundRecord, parentIndices.toArray(new Integer[parentIndices.size()]));

                        }
                        count++;
                     } else if (childAttributeMap.getValue().getAsJsonObject().has(Constants.NESTED_ATTRIBUTES_GROUP) && parentIndices.size() < groupCount) {
                        getAndSetValuesForChild(inboundRecord, outboundRecord, sourceContextKey, childAttributeMap.getValue().getAsJsonObject(), fieldMapping, groupCount, parentIndices, currentParentIndex + 1);
                        parentIndices.remove(currentParentIndex + 1);
                     }
                  }
               }
            }
         }
      }
   }

   private Integer getGroupCount(String source) {
      Integer groupCount = 0;
      Integer groupIndex = source.indexOf("group[", 0);
      while (groupIndex != -1) {
         ++groupCount;
         groupIndex = source.indexOf("group[", groupIndex + 1);
      }
      return groupCount;

   }

   @Override
   public void close() throws Exception {

   }
}