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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

abstract class ReflectiveFieldSetter {

  abstract void set(@NonNull Object obj, @Nullable Object val);
  abstract Class<?> getFieldRawType();
  abstract Type getFieldGenericType();

  private ReflectiveFieldSetter() {}

  @NonNull
  static ReflectiveFieldSetter of(@NonNull Field field){
    return new FieldSetter(field);
  }

  @NonNull
  static ReflectiveFieldSetter of(@NonNull Method setter){
    return new MethodSetter(setter);
  }

  static class FieldSetter extends ReflectiveFieldSetter {
    private Field field;

    FieldSetter(Field field) {
      this.field = field;
    }

    @Override
    void set(@NonNull Object obj, @Nullable Object value) {
      field.setAccessible(true);
      try {
        field.set(obj, value);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    Class<?> getFieldRawType() {
      return field.getType();
    }

    @Override
    Type getFieldGenericType() {
      return field.getGenericType();
    }
  }

  static class MethodSetter extends ReflectiveFieldSetter {
    private Method method;

    MethodSetter(Method method) {
      this.method = method;
    }

    @Override
    void set(@NonNull Object obj, @Nullable Object val) {
      method.setAccessible(true);
      try {
        method.invoke(obj, val);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    Class<?> getFieldRawType() {
      return method.getParameterTypes()[0];
    }

    @Override
    Type getFieldGenericType() {
      return method.getGenericParameterTypes()[0];
    }
  }
}
