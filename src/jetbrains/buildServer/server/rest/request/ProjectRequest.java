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

package jetbrains.buildServer.server.rest.request;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildFinder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.CopyOptionsDescription;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.buildType.NewBuildTypeDescription;
import jetbrains.buildServer.server.rest.model.project.NewProjectDescription;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/*
 * User: Yegor Yarko
 * Date: 11.04.2009
 */
@Path(ProjectRequest.API_PROJECTS_URL)
public class ProjectRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private BuildFinder myBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanFactory myFactory;

  public static final String API_PROJECTS_URL = Constants.API_URL + "/projects";

  public static String getProjectHref(SProject project) {
    return API_PROJECTS_URL + "/id:" + project.getExternalId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Projects serveProjects() {
    return new Projects(myDataProvider.getServer().getProjectManager().getProjects(), myApiUrlBuilder);
  }

  @POST
  @Consumes({"text/plain"})
  @Produces({"application/xml", "application/json"})
  public Project createEmptyProject(String name) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Project name cannot be empty.");
    }
    final SProject project = myDataProvider.getServer().getProjectManager().createProject(name);
    project.persist();
    return new Project(project, myDataProvider, myApiUrlBuilder);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Project createProject(NewProjectDescription descriptor) {
    if (StringUtil.isEmpty(descriptor.name)) {
      throw new BadRequestException("Project name cannot be empty.");
    }
    SProject resultingProject;
    if (StringUtil.isEmpty(descriptor.sourceProjectLocator)) {
      if (!StringUtil.isEmpty(descriptor.id)) {
        resultingProject = myDataProvider.getServer().getProjectManager().createProject(descriptor.id, descriptor.name, myDataProvider.getServer().getProjectManager().getRootProject());
      } else {
        resultingProject = myDataProvider.getServer().getProjectManager().createProject(descriptor.name);
      }
    } else {
      SProject sourceProject = myProjectFinder.getProject(descriptor.sourceProjectLocator
      );
      if (!StringUtil.isEmpty(descriptor.id)) {
        // todo (TeamCity) open API see http://youtrack.jetbrains.com/issue/TW-25556
        throw new BadRequestException("Sorry, setting project id on copying is not supported. Create project with name only and then change the id.");
      }else{
        resultingProject = myDataProvider.getServer().getProjectManager().createProject(sourceProject, descriptor.name, getCopyOptions(descriptor));
      }
      //todo: (TeamCity) open API how to change external id?
      if (!StringUtil.isEmpty(descriptor.id)) {
        resultingProject.setExternalId(descriptor.id);
      }
    }
    resultingProject.persist();
    return new Project(resultingProject, myDataProvider, myApiUrlBuilder);
  }

  @GET
  @Path("/{projectLocator}")
  @Produces({"application/xml", "application/json"})
  public Project serveProject(@PathParam("projectLocator") String projectLocator) {
    return new Project(myProjectFinder.getProject(projectLocator), myDataProvider, myApiUrlBuilder);
  }

  @DELETE
  @Path("/{projectLocator}")
  public void deleteProject(@PathParam("projectLocator") String projectLocator) {
    final SProject project = myProjectFinder.getProject(projectLocator);
    myDataProvider.getServer().getProjectManager().removeProject(project.getProjectId());
  }

  @GET
  @Path("/{projectLocator}/{field}")
  @Produces("text/plain")
  public String serveProjectField(@PathParam("projectLocator") String projectLocator, @PathParam("field") String fieldName) {
    return Project.getFieldValue(myProjectFinder.getProject(projectLocator), fieldName);
  }

  @PUT
  @Path("/{projectLocator}/{field}")
  @Consumes("text/plain")
  public void setProjectFiled(@PathParam("projectLocator") String projectLocator, @PathParam("field") String fieldName, String newValue) {
    final SProject project = myProjectFinder.getProject(projectLocator);
    Project.setFieldValue(project, fieldName, newValue, myDataProvider);
    project.persist();
  }

  @GET
  @Path("/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesInProject(@PathParam("projectLocator") String projectLocator) {
    SProject project = myProjectFinder.getProject(projectLocator);
    return BuildTypes.createFromBuildTypes(project.getOwnBuildTypes(), myDataProvider, myApiUrlBuilder);
  }

  @POST
  @Path("/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  @Consumes({"text/plain"})
  public BuildType createEmptyBuildType(@PathParam("projectLocator") String projectLocator, String name) {
    SProject project = myProjectFinder.getProject(projectLocator);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Build type name cannot be empty.");
    }
    final SBuildType buildType = project.createBuildType(name);
    project.persist();
    return new BuildType(buildType, myDataProvider, myApiUrlBuilder);
  }

  /**
   * Creates a new build configuration by copying existing one.
   * @param projectLocator
   * @param descriptor reference to the build configuration to copy and copy options. e.g. <newBuildTypeDescription name='Conf Name' sourceBuildTypeLocator='id:bt42' copyAllAssociatedSettings='true' shareVCSRoots='false'/>
   * @return the build configuration created
   */
  @POST
  @Path("/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  public BuildType createBuildType(@PathParam("projectLocator") String projectLocator, NewBuildTypeDescription descriptor) {
    SProject project = myProjectFinder.getProject(projectLocator);
    if (StringUtil.isEmpty(descriptor.name)) {
      throw new BadRequestException("Should specify build type name to create a new one.");
    }
    SBuildType resultingBuildType;
    if (StringUtil.isEmpty(descriptor.sourceBuildTypeLocator)) {
      if (!StringUtil.isEmpty(descriptor.id)){
        resultingBuildType = project.createBuildType(descriptor.id, descriptor.name);
      }else{
        resultingBuildType = project.createBuildType(descriptor.name);
      }
    }else{
      SBuildType sourceBuildType = myBuildTypeFinder.getBuildType(null, descriptor.sourceBuildTypeLocator);
      if (!StringUtil.isEmpty(descriptor.id)){
        resultingBuildType = project.createBuildType(sourceBuildType, descriptor.id, descriptor.name, getCopyOptions(descriptor));
      }else{
        resultingBuildType = project.createBuildType(sourceBuildType, descriptor.name, getCopyOptions(descriptor));
      }
    }
    project.persist();
    return new BuildType(resultingBuildType, myDataProvider, myApiUrlBuilder);
  }

  private CopyOptions getCopyOptions(@NotNull final CopyOptionsDescription description) {
    final CopyOptions result = new CopyOptions();
    if (toBoolean(description.copyAllAssociatedSettings)) {
      //todo: need to use some API to set all necessary options. e.g. see TW-16948, TW-16934
      result.addOption(CopyOptions.Option.COPY_AGENT_POOL_ASSOCIATIONS);
      result.addOption(CopyOptions.Option.COPY_AGENT_RESTRICTIONS);
      result.addOption(CopyOptions.Option.COPY_MUTED_TESTS);
      result.addOption(CopyOptions.Option.COPY_PROJECT_TEMPLATES);
      result.addOption(CopyOptions.Option.COPY_USER_NOTIFICATION_RULES);
      result.addOption(CopyOptions.Option.COPY_USER_ROLES);
    }
    if (toBoolean(description.shareVCSRoots)) {
      result.addOption(CopyOptions.Option.SHARE_VCS_ROOTS);
    }else{
      result.addOption(CopyOptions.Option.COPY_VCS_ROOTS);
    }
    return result;
  }

  private static boolean toBoolean(final Boolean value) {
    return (value == null) ? false: value;
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildType(@PathParam("projectLocator") String projectLocator,
                                  @PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(
      myProjectFinder.getProject(projectLocator), buildTypeLocator
    );
    return new BuildType(buildType, myDataProvider, myApiUrlBuilder);
  }


  @GET
  @Path("/{projectLocator}/templates")
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveTemplatesInProject(@PathParam("projectLocator") String projectLocator) {
    SProject project = myProjectFinder.getProject(projectLocator);
    return BuildTypes.createFromTemplates(project.getOwnBuildTypeTemplates(), myDataProvider, myApiUrlBuilder);
  }

  @POST
  @Path("/{projectLocator}/templates")
  @Produces({"application/xml", "application/json"})
  @Consumes({"text/plain"})
  public BuildType createEmptyBuildTypeTemplate(@PathParam("projectLocator") String projectLocator, String name) {
    SProject project = myProjectFinder.getProject(projectLocator);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Build type template name cannot be empty.");
    }
    final BuildTypeTemplate buildType = project.createBuildTypeTemplate(name);
    project.persist();
    return new BuildType(buildType, myDataProvider, myApiUrlBuilder);
  }

  /**
   * Creates a new build configuration template by copying existing one.
   * @param projectLocator
   * @param descriptor reference to the build configuration template to copy and copy options. e.g. <newBuildTypeDescription name='Conf Name' sourceBuildTypeLocator='id:bt42' copyAllAssociatedSettings='true' shareVCSRoots='false'/>
   * @return the build configuration created
   */
  @POST
  @Path("/{projectLocator}/templates")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  public BuildType createBuildTypeTemplate(@PathParam("projectLocator") String projectLocator, NewBuildTypeDescription descriptor) {
    SProject project = myProjectFinder.getProject(projectLocator);
    if (StringUtil.isEmpty(descriptor.name)) {
      throw new BadRequestException("Should specify build type template name to create a new one.");
    }
    BuildTypeTemplate resultingBuildType;
    if (StringUtil.isEmpty(descriptor.sourceBuildTypeLocator)) {
      if (!StringUtil.isEmpty(descriptor.id)){
        resultingBuildType = project.createBuildTypeTemplate(descriptor.id, descriptor.name);
      }else{
        resultingBuildType = project.createBuildTypeTemplate(descriptor.name);
      }
    }else{
      BuildTypeTemplate sourceTemplate = myBuildTypeFinder.getBuildTemplate(null, descriptor.sourceBuildTypeLocator);
      if (!StringUtil.isEmpty(descriptor.id)){
        resultingBuildType = project.createBuildTypeTemplate(sourceTemplate, descriptor.id, descriptor.name, getCopyOptions(descriptor));
      }else{
        resultingBuildType = project.createBuildTypeTemplate(sourceTemplate, descriptor.name, getCopyOptions(descriptor));
      }
    }
    project.persist();
    return new BuildType(resultingBuildType, myDataProvider, myApiUrlBuilder);
  }


  @GET
  @Path("/{projectLocator}/templates/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildTypeTemplates(@PathParam("projectLocator") String projectLocator,
                                  @PathParam("btLocator") String buildTypeLocator) {
    BuildTypeTemplate buildType = myBuildTypeFinder.getBuildTemplate(
      myProjectFinder.getProject(projectLocator), buildTypeLocator
    );
    return new BuildType(buildType, myDataProvider, myApiUrlBuilder);
  }

  @GET
  @Path("/{projectLocator}/parameters")
  @Produces({"application/xml", "application/json"})
  public Properties serveParameters(@PathParam("projectLocator") String projectLocator) {
    SProject project = myProjectFinder.getProject(projectLocator);
    return new Properties(project.getParameters());
  }

  @PUT
  @Path("/{projectLocator}/parameters")
  @Consumes({"application/xml", "application/json"})
  public void changeAllParameters(@PathParam("projectLocator") String projectLocator, Properties properties) {
    SProject project = myProjectFinder.getProject(projectLocator);
    BuildTypeUtil.removeAllParameters(project);
    for (Property p : properties.properties) {
      BuildTypeUtil.changeParameter(p.name, p.value, project, myServiceLocator);
    }
    project.persist();
  }

  @DELETE
  @Path("/{projectLocator}/parameters")
  public void deleteAllParameters(@PathParam("projectLocator") String projectLocator) {
    SProject project = myProjectFinder.getProject(projectLocator);
    BuildTypeUtil.removeAllParameters(project);
    project.persist();
  }

  @GET
  @Path("/{projectLocator}/parameters/{name}")
  @Produces("text/plain")
  public String serveParameter(@PathParam("projectLocator") String projectLocator, @PathParam("name") String parameterName) {
    SProject project = myProjectFinder.getProject(projectLocator);
    return BuildTypeUtil.getParameter(parameterName, project);
  }

  @PUT
  @Path("/{projectLocator}/parameters/{name}")
  @Consumes("text/plain")
  public void putParameter(@PathParam("projectLocator") String projectLocator,
                                    @PathParam("name") String parameterName,
                                    String newValue) {
    SProject project = myProjectFinder.getProject(projectLocator);
    BuildTypeUtil.changeParameter(parameterName, newValue, project, myServiceLocator);
    project.persist();
  }

  @DELETE
  @Path("/{projectLocator}/parameters/{name}")
  @Produces("text/plain")
  public void deleteParameter(@PathParam("projectLocator") String projectLocator,
                                       @PathParam("name") String parameterName) {
    SProject project = myProjectFinder.getProject(projectLocator);
    BuildTypeUtil.deleteParameter(parameterName, project);
    project.persist();
  }


  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                               @PathParam("btLocator") String buildTypeLocator,
                                               @PathParam("field") String fieldName) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(
      myProjectFinder.getProject(projectLocator), buildTypeLocator);

    return buildType.getFieldValue(fieldName);
  }

  //todo: separate methods to serve running builds

  /**
   * Serves builds matching supplied condition.
   * @param locator Build locator to filter builds
   * @param buildTypeLocator Deprecated, use "locator" parameter instead
   * @param status   Deprecated, use "locator" parameter instead
   * @param userLocator   Deprecated, use "locator" parameter instead
   * @param includePersonal   Deprecated, use "locator" parameter instead
   * @param includeCanceled   Deprecated, use "locator" parameter instead
   * @param onlyPinned   Deprecated, use "locator" parameter instead
   * @param tags   Deprecated, use "locator" parameter instead
   * @param agentName   Deprecated, use "locator" parameter instead
   * @param sinceBuildLocator   Deprecated, use "locator" parameter instead
   * @param sinceDate   Deprecated, use "locator" parameter instead
   * @param start   Deprecated, use "locator" parameter instead
   * @param count   Deprecated, use "locator" parameter instead, defaults to 100
   * @return
   */
  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds")
  @Produces({"application/xml", "application/json"})
  public Builds serveBuilds(@PathParam("projectLocator") String projectLocator,
                            @PathParam("btLocator") String buildTypeLocator,
                            @QueryParam("status") String status,
                            @QueryParam("triggeredByUser") String userLocator,
                            @QueryParam("includePersonal") boolean includePersonal,
                            @QueryParam("includeCanceled") boolean includeCanceled,
                            @QueryParam("onlyPinned") boolean onlyPinned,
                            @QueryParam("tag") List<String> tags,
                            @QueryParam("agentName") String agentName,
                            @QueryParam("sinceBuild") String sinceBuildLocator,
                            @QueryParam("sinceDate") String sinceDate,
                            @QueryParam("start") Long start,
                            @QueryParam("count") Integer count,
                            @QueryParam("locator") String locator,
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getProject(projectLocator
    ),
                                                          buildTypeLocator);
    return myBuildFinder.getBuildsForRequest(buildType, status, userLocator, includePersonal, includeCanceled, onlyPinned, tags, agentName,
                                           sinceBuildLocator, sinceDate, start, count, locator, "locator", uriInfo, request, myApiUrlBuilder
    );
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("projectLocator") String projectLocator,
                                     @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getProject(projectLocator
    ),
                                                          buildTypeLocator);
    SBuild build = myBuildFinder.getBuild(buildType, buildLocator);

    return new Build(build, myDataProvider, myApiUrlBuilder, myServiceLocator, myFactory);
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                           @PathParam("btLocator") String buildTypeLocator,
                                           @PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getProject(projectLocator
    ),
                                                          buildTypeLocator);
    SBuild build = myBuildFinder.getBuild(buildType, buildLocator);

    return Build.getFieldValue(build, field);
  }
}
