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

package jetbrains.buildServer.server.rest;

import com.sun.jersey.spi.resource.Singleton;
import java.util.Collection;
import javax.ws.rs.*;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.users.SUser;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404 error
*/

@Path("/httpAuth/api/users")
@Singleton
public class UserRequest {
  private final DataProvider myDataProvider;
  private DataUpdater myDataUpdater;

  public UserRequest(DataProvider myDataProvider, DataUpdater dataUpdater) {
    this.myDataProvider = myDataProvider;
    myDataUpdater = dataUpdater;
  }

  public static String getUserHref(final jetbrains.buildServer.users.User user) {
    //todo: investigate why "DOMAIN username" does not work as query parameter
//    this.href = "/httpAuth/api/users/" + user.getUsername();
    return "/httpAuth/api/users/id:" + user.getId();
  }

  public static String getRoleAssignmentHref(final RoleEntry roleEntry, final SUser user) {
    final RoleScope roleScope = roleEntry.getScope();
    return getUserHref(user) + "/roles/" + roleEntry.getRole().getId() +
           (roleScope.isGlobal() ? "/" + roleScope.getProjectId() : "");
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Users serveUsers() {
    return new Users(myDataProvider.getAllUsers());
  }

  @GET
  @Path("/{userLocator}")
  @Produces({"application/xml", "application/json"})
  public User serveUser(@PathParam("userLocator") String userLocator) {
    return new User(myDataProvider.getUser(userLocator));
  }

  //TODO
  //@PUT
  @POST
  @Path("/{userLocator}")
  @Consumes({"application/xml", "application/json"})
  public void updateUser(@PathParam("userLocator") String userLocator, UserData userData) {
    SUser user = myDataProvider.getUser(userLocator);
    myDataUpdater.modify(user, userData);
  }

  @GET
  @Path("/{userLocator}/roles")
  @Produces({"application/xml", "application/json"})
  public RoleAssignments listRoles(@PathParam("userLocator") String userLocator) {
    SUser user = myDataProvider.getUser(userLocator);
    return new RoleAssignments(user.getRoles(), user);
  }


  //TODO
  //@PUT
  @POST
  @Path("/{userLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  public void addRole(@PathParam("userLocator") String userLocator, RoleAssignment roleAssignment) {
    SUser user = myDataProvider.getUser(userLocator);
    user.addRole(myDataProvider.getScope(roleAssignment.scope), myDataProvider.getRoleById(roleAssignment.roleId));
  }

  @GET
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  //TODO
  public RoleAssignment listRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                                 @PathParam("scope") String scopeValue) {
    SUser user = myDataProvider.getUser(userLocator);
    return new RoleAssignment(getUserRoleEntry(user, roleId, scopeValue), user);
  }

  private RoleEntry getUserRoleEntry(final SUser user, final String roleId, final String scopeValue) {
    if (roleId == null) {
      throw new BadRequestException("Expected roleId is not specified");
    }
    final RoleScope roleScope = myDataProvider.getScope(scopeValue);
    final Collection<RoleEntry> roles = user.getRoles();
    for (RoleEntry roleEntry : roles) {
      if (roleScope.equals(roleEntry.getScope()) && roleId.equals(roleEntry.getRole().getId())) {
        return roleEntry;
      }
    }
    throw new NotFoundException("User " + user + " does not have role with id: " + roleId + " and scope " + scopeValue);
  }

  //TODO
  //@DELETE
  @POST
  @Path("/{userLocator}/roles/{roleId}/{scope}/delete")
  //TODO
  public void deleteRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                         @PathParam("scope") String scopeValue) {
    SUser user = myDataProvider.getUser(userLocator);
    user.removeRole(myDataProvider.getScope(scopeValue), myDataProvider.getRoleById(roleId));
  }


  @POST
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  public void addRoleSimple(@PathParam("userLocator") String userLocator,
                            @PathParam("roleId") String roleId,
                            @PathParam("scope") String scopeValue) {
    SUser user = myDataProvider.getUser(userLocator);
    user.addRole(myDataProvider.getScope(scopeValue), myDataProvider.getRoleById(roleId));
  }
}
