package com.riversand.connectors.gdsntransformation;

import com.google.common.base.Strings;

import com.riversand.rsconnect.common.transform.FieldMapMacro;

public class GDSNFieldMapMacro extends FieldMapMacro {

   public static boolean isPath(String field) {
      return !Strings.isNullOrEmpty(field) && field.startsWith("@path");
   }

   public static String getPath(String field) {
      if (!isPath(field)) {
         return field;
      }

      int endIndex = field.lastIndexOf(')');
      if (endIndex == -1) {
         return field;
      }

      return field.substring("@path".length() + 1, endIndex);
   }

   public static boolean isRelPath(String field) {
      return !Strings.isNullOrEmpty(field) && field.startsWith("@relPath");
   }

   public static String getRelPath(String field) {
      int endIndex = field.lastIndexOf(')');
      if (endIndex == -1) {
         return field;
      }
      String relToPath = field.substring("@relPath".length() + 1, endIndex);
      String[] relToPathSplit = relToPath.split(",");
      if (relToPathSplit.length == 2) {
         return String.format("%s.%s", relToPathSplit);
      } else {
         return relToPath;
      }
   }
}
