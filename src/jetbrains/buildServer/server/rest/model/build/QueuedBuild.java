/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeRef;
import jetbrains.buildServer.server.rest.model.change.Revisions;
import jetbrains.buildServer.server.rest.model.user.UserRef;
import jetbrains.buildServer.server.rest.request.BuildQueueRequest;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * User: Yegor Yarko
 */
@XmlRootElement(name = "queuedBuild")
@XmlType(name = "queuedBuild", propOrder = {"id", "href", "webUrl", "branchName", "defaultBranch", "unspecifiedBranch", "personal", "history", "buildType",
  "queuedDate", "compatibleAgents", "comment", "personalBuildUser", "properties",
  "revisions", "triggered"})
public class QueuedBuild {
  @NotNull
  protected SQueuedBuild myBuild;
  @NotNull
  private DataProvider myDataProvider;
  private ApiUrlBuilder myApiUrlBuilder;
  @Autowired private BeanFactory myFactory;

  private ServiceLocator myServiceLocator;

  public QueuedBuild() {
  }

  public QueuedBuild(@NotNull final SQueuedBuild build,
                     @NotNull final DataProvider dataProvider,
                     final ApiUrlBuilder apiUrlBuilder,
                     @NotNull final ServiceLocator serviceLocator, final BeanFactory factory) {
    myBuild = build;
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
    myServiceLocator = serviceLocator;
    factory.autowire(this);
  }

  @XmlAttribute
  public String getId() {
    return myBuild.getItemId();
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myBuild);
  }

  @XmlAttribute
  public boolean isHistory() {
    return myBuild.getBuildPromotion().isOutOfChangesSequence();
  }

  @XmlAttribute
  public String getBranchName() {
    Branch branch = myBuild.getBuildPromotion().getBranch();
    if (branch == null) {
      return null;
    }
    return branch.getDisplayName();
  }

  @XmlAttribute
  public Boolean getDefaultBranch() {
    Branch branch = myBuild.getBuildPromotion().getBranch();
    if (branch == null) {
      return null;
    }
    return branch.isDefaultBranch() ? Boolean.TRUE : null;
  }

  @XmlAttribute
  public Boolean getUnspecifiedBranch() {
    Branch branch = myBuild.getBuildPromotion().getBranch();
    if (branch == null) {
      return null;
    }
    return Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName()) ? Boolean.TRUE : null;
  }

  @XmlAttribute
  public boolean isPersonal() {
    return myBuild.getBuildPromotion().isPersonal();
  }

  @XmlAttribute
  public String getWebUrl() {
    return myServiceLocator.getSingletonService(WebLinks.class).getQueuedBuildUrl(myBuild);
  }

  @XmlElement(name = "compatibleAgents")
  public Href getCompatibleAgents() { //TODO: IMPLEMENT!
    return new Href(BuildQueueRequest.getCompatibleAgentsHref(myBuild), myApiUrlBuilder);
  }

  @XmlElement(name = "buildType")
  public BuildTypeRef getBuildType() {
    return new BuildTypeRef(myBuild.getBuildType(), myDataProvider, myApiUrlBuilder);
  }

  @XmlElement
  public String getQueuedDate() {
    return Util.formatTime(myBuild.getWhenQueued());
  }

/* TODO: implement
  @XmlElement
  public String getStartEstimateDate() {
    return Util.formatTime(myBuild.getBuildEstimates().);
  }
*/

  @XmlElement(defaultValue = "")
  public Comment getComment() {
    final jetbrains.buildServer.serverSide.comments.Comment comment = myBuild.getBuildPromotion().getBuildComment();
    if (comment != null) {
      return new Comment(comment, myApiUrlBuilder);
    }
    return null;
  }

  @XmlElement
  public Properties getProperties() {
    return new Properties(myBuild.getBuildPromotion().getParameters());
  }

  /* todo: add these. requires refactoring of Builds list
  @XmlElement(name = "snapshot-dependencies")
  public Builds getBuildDependencies() {
    return new Builds(Build.getBuilds(myBuild.getBuildPromotion().getDependencies()), myDataProvider, null, myApiUrlBuilder);
  }
  */

/* TODO: ignore?
  @XmlElement(name = "artifact-dependencies")
  public Builds getBuildArtifactDependencies() {
    final Map<jetbrains.buildServer.Build,List<ArtifactInfo>> artifacts = myBuild.getBuildPromotion().getArtifactDependencies();
    List<SBuild> builds = new ArrayList<SBuild>(artifacts.size());
    for (jetbrains.buildServer.Build sourceBuild : artifacts.keySet()) {
      //todo: TeamCity API: cast to SBuild?
      builds.add((SBuild)sourceBuild);
    }
    Collections.sort(builds, new BuildDependenciesComparator());
    return new Builds(builds, myDataProvider, null, myApiUrlBuilder);
  }
*/

  @XmlElement(name = "revisions")
  public Revisions getRevisions() {
    return new Revisions(myBuild.getBuildPromotion().getRevisions(), myApiUrlBuilder);
  }

/* TODO: need to support promotionId in changes request
  @XmlElement(name = "changes")
  public ChangesRef getChanges() {
    return new ChangesRef(myBuild, myApiUrlBuilder);
  }
*/

  @XmlElement(name = "triggered")
  public TriggeredBy getTriggered() {
    final jetbrains.buildServer.serverSide.TriggeredBy triggeredBy = myBuild.getTriggeredBy();
    return triggeredBy != null ? new TriggeredBy(triggeredBy, myDataProvider, myApiUrlBuilder) : null;
  }

  @XmlElement(name = "user")
  public UserRef getPersonalBuildUser() {
    final SUser owner = myBuild.getBuildPromotion().getOwner();
    return owner == null ? null : new UserRef(owner, myApiUrlBuilder);
  }

  @Nullable
  public static String getFieldValue(@NotNull final SQueuedBuild build, @Nullable final String field) {
    if ("id".equals(field)) {
      return (new Long(build.getItemId())).toString();
    } else if ("queuedDate".equals(field)) {
      return Util.formatTime(build.getWhenQueued());
    } else if ("startEstimateDate".equals(field)) {
      final BuildEstimates buildEstimates = build.getBuildEstimates();
      if (buildEstimates == null) {
        return null;
      } else {
        final TimeInterval timeInterval = buildEstimates.getTimeInterval();
        if (timeInterval == null) {
          return null;
        }
        final TimePoint endPoint = timeInterval.getEndPoint();
        if (endPoint == null) {
          return null;
        }
        return Util.formatTime(endPoint.getAbsoluteTime());
      }
    } else if ("buildTypeId".equals(field)) {
      return (build.getBuildType().getExternalId());
    } else if ("buildTypeInternalId".equals(field)) {
      return (build.getBuildTypeId());
    } else if ("branchName".equals(field)) {
      Branch branch = build.getBuildPromotion().getBranch();
      return branch == null ? "" : branch.getDisplayName();
    } else if ("branch".equals(field)) {
      Branch branch = build.getBuildPromotion().getBranch();
      return branch == null ? "" : branch.getName();
    } else if ("defaultBranch".equals(field)) {
      Branch branch = build.getBuildPromotion().getBranch();
      return branch == null ? "" : String.valueOf(branch.isDefaultBranch());
    } else if ("unspecifiedBranch".equals(field)) {
      Branch branch = build.getBuildPromotion().getBranch();
      return branch == null ? "" : String.valueOf(Branch.UNSPECIFIED_BRANCH_NAME.equals(branch.getName()));
    } else if ("promotionId".equals(field)) { //Experimental support only, this is not exposed in any other way
      return (String.valueOf(build.getBuildPromotion().getId()));
    } else if ("modificationId".equals(field)) { //Experimental support only, this is not exposed in any other way
      return (String.valueOf(build.getBuildPromotion().getLastModificationId()));
    } else if ("commentText".equals(field)) { //Experimental support only
      final jetbrains.buildServer.serverSide.comments.Comment buildComment = build.getBuildPromotion().getBuildComment();
      return buildComment != null ? buildComment.getComment() : null;
    }
    throw new NotFoundException("Field '" + field + "' is not supported. Supported are: id, queuedDate, startEstimateDate, buildTypeId, branchName.");
  }
}