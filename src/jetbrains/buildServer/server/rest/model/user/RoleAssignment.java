/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.user;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.identifiers.EntityId;
import jetbrains.buildServer.serverSide.identifiers.ProjectIdentifiersManager;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */

@XmlRootElement(name = "role")
public class RoleAssignment {
  @XmlAttribute
  public String roleId;
  @XmlAttribute
  public String scope;
  @XmlAttribute
  public String href;

  public RoleAssignment() {
  }

  public RoleAssignment(RoleEntry roleEntry, SUser user, @NotNull final BeanContext context) {
    roleId = roleEntry.getRole().getId();
    final String scopeParam = getScopeProject(roleEntry.getScope(), context);
    scope = getScopeRepresentation(scopeParam);
    href = context.getContextService(ApiUrlBuilder.class).getHref(roleEntry, user, scopeParam);
  }

  public RoleAssignment(RoleEntry roleEntry, UserGroup group, @NotNull final BeanContext context) {
    roleId = roleEntry.getRole().getId();
    final String scopeParam = getScopeProject(roleEntry.getScope(), context);
    scope = getScopeRepresentation(scopeParam);
    href = context.getContextService(ApiUrlBuilder.class).getHref(roleEntry, group, scopeParam);
  }

  public static String getScopeRepresentation(@Nullable final String scopeParam) {
    if (scopeParam == null) {
      return "g";
    }
    return "p:" + scopeParam;
  }

  // See also jetbrains.buildServer.server.rest.data.UserFinder.RoleEntryDatas.getScope()
  @NotNull
  public static RoleScope getScope(@NotNull String scopeData, @NotNull final BeanContext context) {
    if ("g".equalsIgnoreCase(scopeData)) {
      return RoleScope.globalScope();
    }

    if (!scopeData.startsWith("p:")) {
      throw new NotFoundException("Cannot find scope by '" + scopeData + "' Valid formats are: 'g' or 'p:<projectId>'.");
    }
    final String projectString = scopeData.substring(2);
    final EntityId<String> internalId = context.getSingletonService(ProjectIdentifiersManager.class).findEntityIdByExternalId(projectString);
    if (internalId == null){
      //throw new InvalidStateException("Could not find project internal id by external id '" + projectString + "'.");
      //support locator here just in case
      final SProject project = context.getSingletonService(ProjectFinder.class).getItem(projectString);
      return RoleScope.projectScope(project.getProjectId());
    }
    return RoleScope.projectScope(internalId.getInternalId());
  }

  @Nullable
  private String getScopeProject(@NotNull final RoleScope scope, @NotNull final BeanContext context) {
    if (scope.isGlobal()){
      return null;
    }
    final EntityId<String> externalId = context.getSingletonService(ProjectIdentifiersManager.class).findEntityIdByInternalId(scope.getProjectId());
    if (externalId == null){
      throw new InvalidStateException("Could not find project external id by internal id '" + scope.getProjectId() + "'.");
    }
    return externalId.getExternalId();
  }
}
