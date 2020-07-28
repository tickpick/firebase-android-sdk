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
import java.lang.reflect.Field;
import java.lang.reflect.Type;

class ReflectiveFieldSetter implements ReflectiveSetter {
  private Field field;

  @NonNull
  static ReflectiveFieldSetter of(@NonNull Field field) {
    return new ReflectiveFieldSetter(field);
  }

  private ReflectiveFieldSetter(Field field) {
    this.field = field;
  }

  @Override
  public void set(@NonNull Object obj, @Nullable Object value) {
    field.setAccessible(true);
    try {
      field.set(obj, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @NonNull
  @Override
  public Class<?> getFieldRawType() {
    return field.getType();
  }

  @Nullable
  @Override
  public Type getFieldGenericType() {
    return field.getGenericType();
  }

  @Override
  public boolean isDecodedInline() {
    Encodable.Field annotation = field.getAnnotation(Encodable.Field.class);
    return annotation != null && annotation.inline();
  }
}
