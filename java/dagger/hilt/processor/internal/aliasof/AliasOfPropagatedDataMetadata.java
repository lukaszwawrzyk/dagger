/*
 * Copyright (C) 2021 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.hilt.processor.internal.aliasof;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.AnnotationValues;
import dagger.hilt.processor.internal.BadInputException;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.root.ir.AliasOfPropagatedDataIr;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * A class that represents the values stored in an {@link
 * dagger.hilt.internal.aliasof.AliasOfPropagatedData} annotation.
 */
@AutoValue
public abstract class AliasOfPropagatedDataMetadata {

  /** Returns the aggregating element */
  public abstract TypeElement aggregatingElement();

  abstract ImmutableList<TypeElement> defineComponentScopeElements();

  abstract TypeElement aliasElement();

  /** Returns metadata for all aggregated elements in the aggregating package. */
  public static ImmutableSet<AliasOfPropagatedDataMetadata> from(XProcessingEnv env) {
    return from(
        AggregatedElements.from(
            ClassNames.ALIAS_OF_PROPAGATED_DATA_PACKAGE, ClassNames.ALIAS_OF_PROPAGATED_DATA, env),
        env);
  }

  /** Returns metadata for each aggregated element. */
  public static ImmutableSet<AliasOfPropagatedDataMetadata> from(
      ImmutableSet<TypeElement> aggregatedElements, Elements elements) {
    return aggregatedElements.stream()
        .map(aggregatedElement -> create(aggregatedElement, elements))
        .collect(toImmutableSet());
  }

  /** Returns metadata for each aggregated element. */
  public static ImmutableSet<AliasOfPropagatedDataMetadata> from(
      ImmutableSet<XTypeElement> aggregatedElements, XProcessingEnv env) {
    return from(
        Processors.mapTypeElementsToJavac(aggregatedElements), toJavac(env).getElementUtils());
  }

  public static AliasOfPropagatedDataIr toIr(AliasOfPropagatedDataMetadata metadata) {
    return new AliasOfPropagatedDataIr(
        ClassName.get(metadata.aggregatingElement()),
        metadata.defineComponentScopeElements().stream()
            .map(ClassName::get)
            .collect(toImmutableList()),
        ClassName.get(metadata.aliasElement()));
  }

  private static AliasOfPropagatedDataMetadata create(TypeElement element, Elements elements) {
    AnnotationMirror annotationMirror =
        Processors.getAnnotationMirror(element, ClassNames.ALIAS_OF_PROPAGATED_DATA);

    ImmutableMap<String, AnnotationValue> values =
        Processors.getAnnotationValues(elements, annotationMirror);

    ImmutableList<TypeElement> defineComponentScopes;
    if (values.containsKey("defineComponentScopes")) {
      defineComponentScopes =
          ImmutableList.copyOf(
              AnnotationValues.getTypeElements(values.get("defineComponentScopes")));
    } else if (values.containsKey("defineComponentScope")) {
      // Older version of AliasOfPropagatedData only passed a single defineComponentScope class
      // value. Fall back on reading the single value if we get old propagated data.
      defineComponentScopes =
          ImmutableList.of(AnnotationValues.getTypeElement(values.get("defineComponentScope")));
    } else {
      throw new BadInputException(
          "AliasOfPropagatedData is missing defineComponentScopes", element);
    }

    return new AutoValue_AliasOfPropagatedDataMetadata(
        element, defineComponentScopes, AnnotationValues.getTypeElement(values.get("alias")));
  }
}
