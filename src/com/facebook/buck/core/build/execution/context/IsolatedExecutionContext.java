/*
 * Copyright (c) Facebook, Inc. and its affiliates.
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

package com.facebook.buck.core.build.execution.context;

import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.core.util.immutables.BuckStyleValueWithBuilder;
import com.facebook.buck.event.BuckEvent;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.ThrowableConsoleEvent;
import com.facebook.buck.util.Ansi;
import com.facebook.buck.util.ClassLoaderCache;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.Verbosity;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import org.immutables.value.Value;

/** The context exposed for executing {@code IsolatedStep}s */
@BuckStyleValueWithBuilder
public abstract class IsolatedExecutionContext implements Closeable {

  public abstract Console getConsole();

  public abstract BuckEventBus getBuckEventBus();

  public abstract Platform getPlatform();

  public abstract ImmutableMap<String, String> getEnvironment();

  public abstract ProcessExecutor getProcessExecutor();

  @Value.Default
  public ClassLoaderCache getClassLoaderCache() {
    return new ClassLoaderCache();
  }

  @Value.Derived
  public Verbosity getVerbosity() {
    return getConsole().getVerbosity();
  }

  @Value.Derived
  public PrintStream getStdErr() {
    return getConsole().getStdErr();
  }

  @Value.Derived
  public PrintStream getStdOut() {
    return getConsole().getStdErr();
  }

  @Value.Derived
  public BuildId getBuildId() {
    return getBuckEventBus().getBuildId();
  }

  @Value.Derived
  public Ansi getAnsi() {
    return getConsole().getAnsi();
  }

  public void logError(Throwable error, String msg, Object... formatArgs) {
    getBuckEventBus().post(ThrowableConsoleEvent.create(error, msg, formatArgs));
  }

  public void postEvent(BuckEvent event) {
    getBuckEventBus().post(event);
  }

  @Override
  public void close() throws IOException {
    // Using a Closer makes it easy to ensure that exceptions from one of the closeables don't
    // cancel the others.
    try (Closer closer = Closer.create()) {
      registerCloseables(closer);
    }
  }

  protected void registerCloseables(Closer closer) {
    closer.register(getClassLoaderCache()::close);
  }
}
