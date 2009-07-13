/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.GroupRequest;
import jetbrains.buildServer.server.rest.UserRequest;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.users.SUser;

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

  public RoleAssignment(RoleEntry roleEntry) {
    roleId = roleEntry.getRole().getId();
    scope = getScopeRepresentation(roleEntry.getScope());
  }

  private static String getScopeRepresentation(final RoleScope scope) {
    return scope.isGlobal() ? null : scope.getProjectId();
  }

  public RoleAssignment(RoleEntry roleEntry, SUser user) {
    roleId = roleEntry.getRole().getId();
    scope = getScopeRepresentation(roleEntry.getScope());
    href = UserRequest.getRoleAssignmentHref(roleEntry, user);
  }

  public RoleAssignment(RoleEntry roleEntry, UserGroup group) {
    roleId = roleEntry.getRole().getId();
    scope = getScopeRepresentation(roleEntry.getScope());
    href = GroupRequest.getRoleAssignmentHref(roleEntry, group);
  }
}
