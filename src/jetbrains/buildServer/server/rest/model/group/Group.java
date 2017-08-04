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

package jetbrains.buildServer.server.rest.model.group;

import com.intellij.openapi.util.text.StringUtil;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroupManager;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.server.rest.model.user.Users;
import jetbrains.buildServer.server.rest.request.GroupRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "group")
@XmlType(name = "group")
public class Group {
  @XmlAttribute
  public String key;
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String href;

  @XmlAttribute(name = "description")
  public String description;

  @XmlElement(name = "parent-groups")
  public Groups parentGroups;

  @XmlElement(name = "child-groups")
  public Groups childGroups;

  @XmlElement(name = "users")
  public Users users;

  @XmlElement(name = "roles")
  public RoleAssignments roleAssignments;

  @XmlElement(name = "properties")
  public Properties properties;

  public Group() {
  }

  public Group(@NotNull final SUserGroup userGroup, @NotNull final Fields fields, @NotNull final BeanContext context) {
    this.key = ValueWithDefault.decideDefault(fields.isIncluded("key"), userGroup.getKey());
    this.name = ValueWithDefault.decideDefault(fields.isIncluded("name"), userGroup.getName());
    this.href = ValueWithDefault.decideDefault(fields.isIncluded("href"), context.getApiUrlBuilder().getHref(userGroup));
    this.description = ValueWithDefault.decideDefault(fields.isIncluded("description"), StringUtil.isEmpty(userGroup.getDescription()) ? null : userGroup.getDescription());
    final ApiUrlBuilder apiUrlBuilder = context.getContextService(ApiUrlBuilder.class);
    parentGroups = ValueWithDefault.decideDefault(fields.isIncluded("parent-groups", false), new ValueWithDefault.Value<Groups>() {
      public Groups get() {
        return new Groups(userGroup.getParentGroups(), fields.getNestedField("parent-groups", Fields.NONE, Fields.LONG), context);
      }
    });
    childGroups = ValueWithDefault.decideDefault(fields.isIncluded("child-groups", false), new ValueWithDefault.Value<Groups>() {
      public Groups get() {
        return new Groups(userGroup.getDirectSubgroups(), fields.getNestedField("child-groups", Fields.NONE, Fields.LONG), context);
      }
    });
    users = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("users", false), new ValueWithDefault.Value<Users>() {
      public Users get() {
        //improvement: it is better to force the group to the current one (and support several ANDed groups in the userFinder)
        final PagedSearchResult<SUser> items = context.getSingletonService(UserFinder.class).getItems(Locator.merge(fields.getLocator(), UserFinder.getLocatorByGroup(userGroup)));
        return new Users(items.myEntries, fields.getNestedField("users", Fields.NONE, Fields.LONG), context);
      }
    });
    roleAssignments = ValueWithDefault.decideDefault(fields.isIncluded("roles", false), new ValueWithDefault.Value<RoleAssignments>() {
      public RoleAssignments get() {
        return new RoleAssignments(userGroup.getRoles(), userGroup, context);
      }
    });
    properties = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("properties", false), new ValueWithDefault.Value<Properties>() {
      public Properties get() {
        return new Properties(getProperties(userGroup), GroupRequest.getPropertiesHref(userGroup), fields.getNestedField("properties"), context);
      }
    });
  }

  @NotNull
  public SUserGroup getFromPosted(final ServiceLocator serviceLocator) {
    if (key == null) {
      throw new BadRequestException("No 'key' attribute is supplied for the posted group.");
    }
    final SUserGroup userGroupByKey = serviceLocator.getSingletonService(UserGroupManager.class).findUserGroupByKey(key);
    if (userGroupByKey == null) {
      throw new NotFoundException("No group is found by key '" + key + "'");
    }
    return userGroupByKey;
  }

  public static Map<String, String> getProperties(final SUserGroup user) {
    Map<String, String> convertedProperties = new HashMap<String, String>();
    for (Map.Entry<PropertyKey, String> prop : user.getProperties().entrySet()) {
      convertedProperties.put(prop.getKey().getKey(), prop.getValue());
    }
    return convertedProperties;
  }
}
