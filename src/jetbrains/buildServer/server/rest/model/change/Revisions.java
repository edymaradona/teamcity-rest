/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.change;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
@SuppressWarnings("PublicField")
@XmlType(name = "revisions")
public class Revisions {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "revision")
  public List<Revision> revisions;

  public Revisions() {
  }

  public Revisions(@NotNull final List<BuildRevision> items, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    revisions = ValueWithDefault.decideDefault(fields.isIncluded("revision", true), new ValueWithDefault.Value<List<Revision>>() {
      @Nullable
      public List<Revision> get() {
        return CollectionsUtil.convertCollection(items, new Converter<Revision, BuildRevision>() {
          public Revision createFrom(@NotNull final BuildRevision source) {
            return new Revision(source, fields.getNestedField("revision", Fields.SHORT, Fields.LONG), beanContext);
          }
        });
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), items.size());
  }
}
