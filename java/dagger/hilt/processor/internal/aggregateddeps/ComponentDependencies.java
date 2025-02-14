/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.aggregateddeps;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.earlyentrypoint.AggregatedEarlyEntryPointMetadata;
import dagger.hilt.processor.internal.uninstallmodules.AggregatedUninstallModulesMetadata;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Represents information needed to create a component (i.e. modules, entry points, etc) */
@AutoValue
public abstract class ComponentDependencies {
  private static Builder builder() {
    return new AutoValue_ComponentDependencies.Builder();
  }

  /** Returns the modules for a component, without any filtering. */
  public abstract ImmutableSetMultimap<ClassName, TypeElement> modules();

  /** Returns the modules for a component, without any filtering. */
  public ImmutableSetMultimap<ClassName, XTypeElement> modules(XProcessingEnv env) {
    ImmutableSetMultimap.Builder<ClassName, XTypeElement> builder = ImmutableSetMultimap.builder();
    for (Entry<ClassName, TypeElement> entry : modules().entries()) {
      builder.put(entry.getKey(), toXProcessing(entry.getValue(), env));
    }
    return builder.build();
  }

  /** Returns the entry points associated with the given a component. */
  public abstract ImmutableSetMultimap<ClassName, TypeElement> entryPoints();

  /** Returns the component entry point associated with the given a component. */
  public abstract ImmutableSetMultimap<ClassName, TypeElement> componentEntryPoints();

  /** Returns the component entry point associated with the given a component. */
  public ImmutableSetMultimap<ClassName, XTypeElement> componentEntryPoints(XProcessingEnv env) {
    ImmutableSetMultimap.Builder<ClassName, XTypeElement> builder = ImmutableSetMultimap.builder();
    for (Map.Entry<ClassName, TypeElement> entry : componentEntryPoints().entries()) {
      builder.put(entry.getKey(), toXProcessing(entry.getValue(), env));
    }
    return builder.build();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract ImmutableSetMultimap.Builder<ClassName, TypeElement> modulesBuilder();

    abstract ImmutableSetMultimap.Builder<ClassName, TypeElement> entryPointsBuilder();

    abstract ImmutableSetMultimap.Builder<ClassName, TypeElement> componentEntryPointsBuilder();

    abstract ComponentDependencies build();
  }

  /** Returns the component dependencies for the given metadata. */
  public static ComponentDependencies from(
      ImmutableSet<ComponentDescriptor> descriptors,
      ImmutableSet<AggregatedDepsMetadata> aggregatedDepsMetadata,
      ImmutableSet<AggregatedUninstallModulesMetadata> aggregatedUninstallModulesMetadata,
      ImmutableSet<AggregatedEarlyEntryPointMetadata> aggregatedEarlyEntryPointMetadata,
      Elements elements) {
    ImmutableSet<TypeElement> uninstalledModules =
        ImmutableSet.<TypeElement>builder()
            .addAll(
                aggregatedUninstallModulesMetadata.stream()
                    .flatMap(metadata -> metadata.uninstallModuleElements().stream())
                    // @AggregatedUninstallModules always references the user module, so convert to
                    // the generated public wrapper if needed.
                    // TODO(bcorso): Consider converting this to the public module in the processor.
                    .map(module -> PkgPrivateMetadata.publicModule(module, elements))
                    .collect(toImmutableSet()))
            .addAll(
                aggregatedDepsMetadata.stream()
                    .flatMap(metadata -> metadata.replacedDependencies().stream())
                    .collect(toImmutableSet()))
            .build();

    ComponentDependencies.Builder componentDependencies = ComponentDependencies.builder();
    ImmutableSet<ClassName> componentNames =
        descriptors.stream().map(ComponentDescriptor::component).collect(toImmutableSet());
    for (AggregatedDepsMetadata metadata : aggregatedDepsMetadata) {
      for (TypeElement componentElement : metadata.componentElements()) {
        ClassName componentName = ClassName.get(componentElement);
        checkState(
            componentNames.contains(componentName), "%s is not a valid Component.", componentName);
        switch (metadata.dependencyType()) {
          case MODULE:
            if (!uninstalledModules.contains(metadata.dependency())) {
              componentDependencies.modulesBuilder().put(componentName, metadata.dependency());
            }
            break;
          case ENTRY_POINT:
            componentDependencies.entryPointsBuilder().put(componentName, metadata.dependency());
            break;
          case COMPONENT_ENTRY_POINT:
            componentDependencies
                .componentEntryPointsBuilder()
                .put(componentName, metadata.dependency());
            break;
        }
      }
    }

    componentDependencies
        .entryPointsBuilder()
        .putAll(
            ClassNames.SINGLETON_COMPONENT,
            aggregatedEarlyEntryPointMetadata.stream()
                .map(AggregatedEarlyEntryPointMetadata::earlyEntryPoint)
                // @AggregatedEarlyEntryPointMetadata always references the user module, so convert
                // to the generated public wrapper if needed.
                // TODO(bcorso): Consider converting this to the public module in the processor.
                .map(entryPoint -> PkgPrivateMetadata.publicEarlyEntryPoint(entryPoint, elements))
                .collect(toImmutableSet()));

    return componentDependencies.build();
  }

  /** Returns the component dependencies for the given metadata. */
  public static ComponentDependencies from(
      ImmutableSet<ComponentDescriptor> descriptors,
      ImmutableSet<AggregatedDepsMetadata> aggregatedDepsMetadata,
      ImmutableSet<AggregatedUninstallModulesMetadata> aggregatedUninstallModulesMetadata,
      ImmutableSet<AggregatedEarlyEntryPointMetadata> aggregatedEarlyEntryPointMetadata,
      XProcessingEnv env) {
    return from(
        descriptors,
        aggregatedDepsMetadata,
        aggregatedUninstallModulesMetadata,
        aggregatedEarlyEntryPointMetadata,
        toJavac(env).getElementUtils());
  }
}
