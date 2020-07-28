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

import static com.google.firebase.encoders.reflective.ReflectiveDecoderHelper.*;

import androidx.annotation.NonNull;
import com.google.firebase.decoders.CreationContext;
import com.google.firebase.decoders.FieldRef;
import com.google.firebase.decoders.ObjectDecoder;
import com.google.firebase.decoders.ObjectDecoderContext;
import com.google.firebase.decoders.TypeCreator;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.encoders.EncodingException;
import com.google.firebase.encoders.FieldDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

class ReflectiveObjectDecoderProvider implements ObjectDecoderProvider {

  static ReflectiveObjectDecoderProvider INSTANCE = new ReflectiveObjectDecoderProvider();

  private ReflectiveObjectDecoderProvider() {}

  @NonNull
  @Override
  public <T> ObjectDecoder<T> get(@NonNull Class<T> clazz) {
    return new ReflectiveObjectDecoderImpl<>();
  }

  private static class ReflectiveObjectDecoderImpl<T> implements ObjectDecoder<T> {

    private final Map<String, FieldRef<?>> refs = new HashMap<>();
    private final Map<String, FieldDescriptor> descriptors = new HashMap<>();
    private final Map<String, ReflectiveSetter> reflectiveSetters = new HashMap<>();

    private ReflectiveObjectDecoderImpl() {}

    @NonNull
    @Override
    public TypeCreator<T> decode(@NonNull ObjectDecoderContext<T> ctx) {
      Class<T> clazz = ctx.getTypeToken().getRawType();
      readMethods(clazz);
      readFields(clazz);
      decodeFields(ctx);
      return getTypeCreator(ctx.getTypeToken());
    }

    private void readMethods(Class<T> clazz) {
      Class<? super T> currentClass = clazz;
      while (currentClass != Object.class && currentClass != null) {
        Method[] methods = currentClass.getDeclaredMethods();
        for (Method method : methods) {
          if (!shouldIncludeSetter(method)) {
            continue;
          }

          String fieldName = fieldName(method);
          if (descriptors.get(fieldName) == null) {
            descriptors.put(
                fieldName,
                buildFieldDescriptor(decodingKey(method), method.getDeclaredAnnotations()));
          }
          if (reflectiveSetters.get(fieldName) == null) {
            reflectiveSetters.put(fieldName, ReflectiveMethodSetter.of(method));
          }
        }
        currentClass = currentClass.getSuperclass();
      }
    }

    private void readFields(Class<T> clazz) {
      Class<? super T> currentClass = clazz;
      while (currentClass != Object.class && currentClass != null) {
        for (Field field : currentClass.getDeclaredFields()) {
          if (!shouldIncludeField(field)) {
            continue;
          }
          String fieldName = field.getName();
          if (descriptors.get(fieldName) == null) {
            descriptors.put(
                fieldName,
                buildFieldDescriptor(decodingKey(field), field.getDeclaredAnnotations()));
          }
          if (reflectiveSetters.get(fieldName) == null) {
            reflectiveSetters.put(fieldName, ReflectiveFieldSetter.of(field));
          }
        }
        currentClass = currentClass.getSuperclass();
      }
    }

    private void decodeFields(ObjectDecoderContext<T> ctx) {
      for (Map.Entry<String, ReflectiveSetter> entry : reflectiveSetters.entrySet()) {
        String fieldName = entry.getKey();
        Class<?> fieldType = entry.getValue().getFieldRawType();
        FieldDescriptor fieldDescriptor = descriptors.get(fieldName);
        if (fieldDescriptor == null) {
          throw new RuntimeException(fieldName + " did not have a FieldDescriptor.");
        }
        FieldRef<?> ref;
        if (fieldType.equals(int.class)) {
          ref = ctx.decodeInteger(fieldDescriptor);
        } else if (fieldType.equals(long.class)) {
          ref = ctx.decodeLong(fieldDescriptor);
        } else if (fieldType.equals(short.class)) {
          ref = ctx.decodeShort(fieldDescriptor);
        } else if (fieldType.equals(double.class)) {
          ref = ctx.decodeDouble(fieldDescriptor);
        } else if (fieldType.equals(float.class)) {
          ref = ctx.decodeFloat(fieldDescriptor);
        } else if (fieldType.equals(boolean.class)) {
          ref = ctx.decodeBoolean(fieldDescriptor);
        } else {
          TypeToken<?> fieldTypeToken =
              getFieldTypeToken(entry.getValue().getFieldGenericType(), ctx);
          if (entry.getValue().isDecodedInline()) {
            if (fieldTypeToken instanceof TypeToken.ClassToken) {
              @SuppressWarnings("unchecked")
              TypeToken.ClassToken<Object> classToken =
                  (TypeToken.ClassToken<Object>) fieldTypeToken;
              ref = ctx.decodeInline(classToken, ReflectiveObjectDecoder.DEFAULT);
            } else {
              throw new IllegalArgumentException(
                  "Array types cannot be decoded inline, type:" + fieldTypeToken + " found.");
            }
          } else {
            ref = ctx.decode(fieldDescriptor, fieldTypeToken);
          }
        }
        refs.put(fieldName, ref);
      }
    }

    private TypeToken<?> getFieldTypeToken(Type type, ObjectDecoderContext<?> ctx) {
      if (type instanceof TypeVariable) {
        TypeVariable[] typeVariables = ctx.getTypeToken().getRawType().getTypeParameters();
        for (int i = 0; i < typeVariables.length; i++) {
          if (typeVariables[i].equals(type)) {
            return ctx.getTypeArgument(i);
          }
        }
      }
      return TypeToken.of(type);
    }

    private TypeCreator<T> getTypeCreator(TypeToken.ClassToken<T> classToken) {
      return (creationCtx -> {
        T instance = ReflectiveInitializer.newInstance(classToken);
        setFields(creationCtx, instance);
        return instance;
      });
    }

    @SuppressWarnings("unchecked")
    private void setFields(CreationContext creationCtx, Object instance) {
      for (Map.Entry<String, ReflectiveSetter> entry : reflectiveSetters.entrySet()) {
        String fieldName = entry.getKey();
        FieldRef<?> ref = refs.get(fieldName);
        Class<?> fieldType = entry.getValue().getFieldRawType();
        ReflectiveSetter fieldSetter = entry.getValue();
        if (fieldSetter == null) {
          throw new RuntimeException("FieldSetter for field:" + fieldName + " is null.");
        }
        if (ref == null) {
          throw new RuntimeException("FieldRef for field:" + fieldName + " is null.");
        }
        if (ref instanceof FieldRef.Boxed) {
          Object val = creationCtx.get((FieldRef.Boxed<?>) ref);
          fieldSetter.set(instance, val);
        } else if (fieldType.equals(int.class)) {
          int val = creationCtx.getInteger((FieldRef.Primitive<Integer>) ref);
          fieldSetter.set(instance, val);
        } else if (fieldType.equals(long.class)) {
          long val = creationCtx.getLong((FieldRef.Primitive<Long>) ref);
          fieldSetter.set(instance, val);
        } else if (fieldType.equals(short.class)) {
          short val = creationCtx.getShort((FieldRef.Primitive<Short>) ref);
          fieldSetter.set(instance, val);
        } else if (fieldType.equals(double.class)) {
          double val = creationCtx.getDouble((FieldRef.Primitive<Double>) ref);
          fieldSetter.set(instance, val);
        } else if (fieldType.equals(float.class)) {
          float val = creationCtx.getFloat((FieldRef.Primitive<Float>) ref);
          fieldSetter.set(instance, val);
        } else if (fieldType.equals(char.class)) {
          char val = creationCtx.getChar((FieldRef.Primitive<Character>) ref);
          fieldSetter.set(instance, val);
        } else if (fieldType.equals(boolean.class)) {
          boolean val = creationCtx.getBoolean((FieldRef.Primitive<Boolean>) ref);
          fieldSetter.set(instance, val);
        } else {
          throw new EncodingException(fieldType + " not supported.");
        }
      }
    }
  }
}
