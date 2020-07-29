// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.encoders.reflective;

import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.FieldDescriptor;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.annotations.ExtraProperty;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ReflectiveDecoderHelper {
  private ReflectiveDecoderHelper() {}

  static FieldDescriptor buildFieldDescriptor(Method method) {
    String decodingKey = decodingKey(method);
    Annotation[] annotations = method.getDeclaredAnnotations();
    FieldDescriptor.Builder builder = FieldDescriptor.builder(decodingKey);
    for (Annotation annotation : annotations) {
      ExtraProperty extraProperty = annotation.annotationType().getAnnotation(ExtraProperty.class);
      if (extraProperty != null) {
        Set<Class<?>> allowedTypes = new HashSet<>(Arrays.asList(extraProperty.allowedTypes()));
        if (allowedTypes.size() == 0 || allowedTypes.contains(method.getParameterTypes()[0])) {
          builder.withProperty(annotation);
        }
      }
    }
    return builder.build();
  }

  static FieldDescriptor buildFieldDescriptor(Field field) {
    String decodingKey = decodingKey(field);
    Annotation[] annotations = field.getDeclaredAnnotations();
    FieldDescriptor.Builder builder = FieldDescriptor.builder(decodingKey);
    for (Annotation annotation : annotations) {
      ExtraProperty extraProperty = annotation.annotationType().getAnnotation(ExtraProperty.class);
      if (extraProperty != null) {
        Set<Class<?>> allowedTypes = new HashSet<>(Arrays.asList(extraProperty.allowedTypes()));
        if (allowedTypes.size() == 0 || allowedTypes.contains(field.getType())) {
          builder.withProperty(annotation);
        }
      }
    }
    return builder.build();
  }

  static String fieldName(Method method) {
    String methodName = method.getName();
    final String prefix = "set";
    if (!methodName.startsWith(prefix)) {
      throw new IllegalArgumentException("Unknown Bean prefix for method: " + methodName);
    }
    return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
  }

  static String decodingKey(AccessibleObject accessibleObject) {
    String key;
    if (accessibleObject instanceof Field) {
      key = ((Field) accessibleObject).getName();
    } else if (accessibleObject instanceof Method) {
      key = fieldName((Method) accessibleObject);
    } else {
      throw new EncodingException("Constructor shouldn't be used to get its decoding key");
    }
    if (accessibleObject.isAnnotationPresent(Encodable.Field.class)) {
      Encodable.Field annotation = accessibleObject.getAnnotation(Encodable.Field.class);
      if (annotation != null && annotation.name().length() > 0) {
        key = annotation.name();
      }
    }
    return key;
  }

  static boolean shouldIncludeSetter(Method method) {
    if (!method.getName().startsWith("set")) {
      return false;
    }
    if (method.getDeclaringClass().equals(Object.class)) {
      return false;
    }
    if (Modifier.isStatic(method.getModifiers())) {
      return false;
    }
    if (!method.getReturnType().equals(Void.TYPE)) {
      return false;
    }
    if (method.getParameterTypes().length != 1) {
      return false;
    }
    return !method.isAnnotationPresent(Encodable.Ignore.class);
  }

  static boolean shouldIncludeField(Field field) {
    if (field.getDeclaringClass().equals(Object.class)) {
      return false;
    }
    if (!Modifier.isPublic(field.getModifiers())) {
      return false;
    }
    if (Modifier.isStatic(field.getModifiers())) {
      return false;
    }
    if (Modifier.isTransient(field.getModifiers())) {
      return false;
    }
    return !field.isAnnotationPresent(Encodable.Ignore.class);
  }
}
