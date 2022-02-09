/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.swift;

import com.facebook.buck.apple.common.AppleCompilerTargetTriple;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.modern.BuildCellRelativePathFactory;
import com.facebook.buck.rules.modern.Buildable;
import com.facebook.buck.rules.modern.ModernBuildRule;
import com.facebook.buck.rules.modern.OutputPath;
import com.facebook.buck.rules.modern.OutputPathResolver;
import com.facebook.buck.step.Step;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A class to compile .swiftinterface files from SDK targets into .swiftmodule files. These are used
 * as input to SwiftCompile actions when using explicit modules.
 */
public class SwiftInterfaceCompile extends ModernBuildRule<SwiftInterfaceCompile.Impl> {

  public SwiftInterfaceCompile(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      AppleCompilerTargetTriple targetTriple,
      Tool swiftc,
      ImmutableList<Arg> swiftArgs,
      boolean withDownwardApi,
      String moduleName,
      SourcePath sdkPath,
      Path swiftInterfacePath,
      ImmutableSet<SourcePath> swiftmoduleDeps) {
    super(
        buildTarget,
        projectFilesystem,
        ruleFinder,
        new Impl(
            targetTriple,
            swiftc,
            swiftArgs,
            withDownwardApi,
            moduleName,
            sdkPath,
            swiftInterfacePath.toString(),
            swiftmoduleDeps));
  }

  @Nullable
  @Override
  public SourcePath getSourcePathToOutput() {
    return getSourcePath(getBuildable().output);
  }

  /** Inner class to implement logic for .swiftinterface compilation. */
  static class Impl implements Buildable {
    @AddToRuleKey private final String targetTriple;
    @AddToRuleKey private final Tool swiftc;
    @AddToRuleKey private final ImmutableList<Arg> swiftArgs;
    @AddToRuleKey private final boolean withDownwardApi;
    @AddToRuleKey private final String moduleName;
    @AddToRuleKey private final SourcePath sdkPath;
    @AddToRuleKey private final String swiftInterfacePath;
    @AddToRuleKey private final ImmutableSet<SourcePath> swiftmoduleDeps;
    @AddToRuleKey private final OutputPath output;

    Impl(
        AppleCompilerTargetTriple targetTriple,
        Tool swiftc,
        ImmutableList<Arg> swiftArgs,
        boolean withDownwardApi,
        String moduleName,
        SourcePath sdkPath,
        String swiftInterfacePath,
        ImmutableSet<SourcePath> swiftmoduleDeps) {
      this.targetTriple = targetTriple.getUnversionedTriple();
      this.swiftc = swiftc;
      this.swiftArgs = swiftArgs;
      this.withDownwardApi = withDownwardApi;
      this.moduleName = moduleName;
      this.sdkPath = sdkPath;
      this.swiftInterfacePath = swiftInterfacePath;
      this.swiftmoduleDeps = swiftmoduleDeps;
      this.output = new OutputPath(moduleName + ".swiftmodule");
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext buildContext,
        ProjectFilesystem filesystem,
        OutputPathResolver outputPathResolver,
        BuildCellRelativePathFactory buildCellPathFactory) {
      SourcePathResolverAdapter resolver = buildContext.getSourcePathResolver();

      ImmutableList.Builder<String> argsBuilder = ImmutableList.builder();
      argsBuilder.add(
          "-frontend",
          "-compile-module-from-interface",
          "-disable-implicit-swift-modules",
          "-target",
          targetTriple,
          "-serialize-parseable-module-interface-dependency-hashes",
          "-disable-modules-validate-system-headers",
          "-suppress-warnings");
      argsBuilder.addAll(Arg.stringify(swiftArgs, resolver));

      if (moduleName.equals("Swift") || moduleName.equals("SwiftOnoneSupport")) {
        argsBuilder.add("-parse-stdlib");
      }

      Path sdkRelPath = resolver.getIdeallyRelativePath(sdkPath);
      argsBuilder.add(sdkRelPath.resolve(swiftInterfacePath).toString());
      for (SourcePath dep : swiftmoduleDeps) {
        argsBuilder.add("-swift-module-file", resolver.getIdeallyRelativePath(dep).toString());
      }

      argsBuilder.add("-o", outputPathResolver.resolvePath(output).getPath().toString());

      return ImmutableList.of(
          new SwiftCompileStep(
              filesystem.getRootPath(),
              ImmutableMap.of(),
              swiftc.getCommandPrefix(resolver),
              argsBuilder.build(),
              filesystem,
              Optional.empty(),
              withDownwardApi,
              false));
    }
  }
}
