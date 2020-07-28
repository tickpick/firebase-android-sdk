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
import com.google.firebase.encoders.annotations.Encodable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

class ReflectiveMethodSetter implements ReflectiveSetter {
  private Method method;

  @NonNull
  static ReflectiveMethodSetter of(@NonNull Method setter) {
    return new ReflectiveMethodSetter(setter);
  }

  private ReflectiveMethodSetter(Method method) {
    this.method = method;
  }

  @Override
  public void set(@NonNull Object obj, @Nullable Object val) {
    method.setAccessible(true);
    try {
      method.invoke(obj, val);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  @NonNull
  @Override
  public Class<?> getFieldRawType() {
    return method.getParameterTypes()[0];
  }

  @Nullable
  @Override
  public Type getFieldGenericType() {
    return method.getGenericParameterTypes()[0];
  }

  @Override
  public boolean isDecodedInline() {
    Encodable.Field annotation = method.getAnnotation(Encodable.Field.class);
    return annotation != null && annotation.inline();
  }
}
