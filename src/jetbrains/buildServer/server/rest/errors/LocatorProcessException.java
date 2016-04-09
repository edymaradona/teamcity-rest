/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.errors;

/**
 * @author Yegor.Yarko
 *         Date: 15.08.2010
 */
public class LocatorProcessException extends RuntimeException {
  public LocatorProcessException(final String locator, final int index, final String message) {
    super("Bad locator syntax: " + message + ". Details: locator: '" + locator + "', at position " + index);
  }

  public LocatorProcessException(final String message) {
    super(message);
  }

  public LocatorProcessException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
