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

package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 20/09/2018
 */
@SuppressWarnings({"PublicField", "WeakerAccess"})
@XmlRootElement(name = "relatedEntity")
public class RelatedEntity {
  @XmlElement(name = "build")
  public Build build;

  @SuppressWarnings("unused")
  public RelatedEntity() {
  }

  public RelatedEntity(@NotNull final Entity entity, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    if (entity.build != null) {
      build = ValueWithDefault.decideDefault(fields.isIncluded("build"), () -> new Build(entity.build, fields.getNestedField("build"), beanContext));
    }
  }

  public static class Entity {
    @Nullable private final BuildPromotion build;

    public Entity(@NotNull final Object build) {
      if (build instanceof BuildPromotion) {
        this.build = (BuildPromotion)build;
      }
      else {
        throw new BadRequestException("Unsupported entity type \"" + build.getClass().getName() + "\"");
      }
    }
  }
}