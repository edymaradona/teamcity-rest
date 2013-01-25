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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildLocator;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.build.Branch;
import jetbrains.buildServer.server.rest.model.build.*;
import jetbrains.buildServer.server.rest.model.buildType.*;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.dependency.DependencyFactoryImpl;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/*
 * User: Yegor Yarko
 * Date: 22.03.2009
 */

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404
*/

@Path(BuildTypeRequest.API_BUILD_TYPES_URL)
public class BuildTypeRequest {
  @Context private DataProvider myDataProvider;
  @Context private ApiUrlBuilder myApiUrlBuilder;
  @Context private ServiceLocator myServiceLocator;
  @Context private BeanFactory myFactory;

  public static final String API_BUILD_TYPES_URL = Constants.API_URL + "/buildTypes";

  public static String getBuildTypeHref(@NotNull final BuildTypeOrTemplate buildType) {
    return buildType.isBuildType() ? getBuildTypeHref(buildType.getBuildType()) : getBuildTypeHref(buildType.getTemplate());
  }

  public static String getBuildTypeHref(@NotNull SBuildType buildType) {
    return API_BUILD_TYPES_URL + "/id:" + buildType.getBuildTypeId();
  }

  public static String getBuildTypeHref(@NotNull final BuildTypeTemplate template) {
    return API_BUILD_TYPES_URL + "/id:(" + DataProvider.TEMPLATE_ID_PREFIX + template.getId() + ")";
  }


  public static String getBuildsHref(final SBuildType buildType) {
    return getBuildTypeHref(buildType) + "/builds/";
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesXML() {
    return BuildTypes.createFromBuildTypes(myDataProvider.getServer().getProjectManager().getAllBuildTypes(), myDataProvider,
                                           myApiUrlBuilder);
  }

  /**
   * Serves build configuration or templates according to the locator.
   */
  @GET
  @Path("/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildTypeXML(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new BuildType(buildType, myDataProvider, myApiUrlBuilder);
  }

  @DELETE
  @Path("/{btLocator}")
  public void deleteBuildType(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SProject project = buildType.getProject();
    if (buildType.isBuildType()) {
      project.removeBuildType(buildType.getId());
    } else {
      project.removeBuildTypeTemplate(buildType.getId());
    }
    project.persist();
  }

  @GET
  @Path("/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeField(@PathParam("btLocator") String buildTypeLocator, @PathParam("field") String fieldName) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return buildType.getFieldValue(fieldName);
  }

  @PUT
  @Path("/{btLocator}/{field}")
  @Consumes("text/plain")
  public void setBuildTypeField(@PathParam("btLocator") String buildTypeLocator, @PathParam("field") String fieldName, String newValue) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    buildType.setFieldValue(fieldName, newValue, myDataProvider);
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/buildTags")
  @Produces({"application/xml", "application/json"})
  public Tags serveBuildTypeBuildsTags(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new Tags(buildType.getTags());
  }

  @GET
  @Path("/{btLocator}/parameters")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildTypeParameters(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);

    return new Properties(buildType.get().getParameters());
  }

  @PUT
  @Path("/{btLocator}/parameters")
  @Consumes({"application/xml", "application/json"})
  public void changeBuildTypeParameters(@PathParam("btLocator") String buildTypeLocator, Properties properties) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    BuildTypeUtil.removeAllParameters(buildType.get());
    for (Property p : properties.properties) {
      BuildTypeUtil.changeParameter(p.name, p.value, buildType.get(), myServiceLocator);
    }
    buildType.get().persist();
  }

  @DELETE
  @Path("/{btLocator}/parameters")
  public void deleteAllBuildTypeParameters(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    BuildTypeUtil.removeAllParameters(buildType.get());
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public String serveBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("name") String parameterName) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return BuildTypeUtil.getParameter(parameterName, buildType.get());
  }

  @PUT
  @Path("/{btLocator}/parameters/{name}")
  @Consumes("text/plain")
  public void putBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("name") String parameterName,
                                    String newValue) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    BuildTypeUtil.changeParameter(parameterName, newValue, buildType.get(), myServiceLocator);
    buildType.get().persist();
  }

  @DELETE
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public void deleteBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator,
                                       @PathParam("name") String parameterName) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    BuildTypeUtil.deleteParameter(parameterName, buildType.get());
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/settings")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildTypeSettings(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new Properties(BuildTypeUtil.getSettingsParameters(buildType));
  }

  @GET
  @Path("/{btLocator}/settings/{name}")
  @Produces("text/plain")
  public String serveBuildTypeSettings(@PathParam("btLocator") String buildTypeLocator, @PathParam("name") String parameterName) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Setting parameter name cannot be empty.");
    }

    Map<String, String> parameters = BuildTypeUtil.getSettingsParameters(buildType);
    if (parameters.containsKey(parameterName)) {
      return parameters.get(parameterName);
    }
    throw new NotFoundException("No setting parameter with name '" + parameterName + "' is found.");
  }

  @PUT
  @Path("/{btLocator}/settings/{name}")
  @Produces("text/plain")
  public void putBuildTypeSetting(@PathParam("btLocator") String buildTypeLocator,
                                  @PathParam("name") String parameterName,
                                  String newValue) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Settings parameter name cannot be empty.");
    }

    if (!BuildTypeUtil.getSettingsParameters(buildType).containsKey(parameterName)) {
      throw new BadRequestException("Setting parameter with name '" + parameterName + "' is not known.");
    }

    try {
      BuildTypeUtil.setSettingsParameter(buildType, parameterName, newValue);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
        "Could not set setting parameter with name '" + parameterName + "' to value '" + newValue + "'. Error: " + e.getMessage());
    }
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/template")
  @Produces({"application/xml", "application/json"})
  public BuildTypeRef serveBuildTypeTemplate(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    final BuildTypeTemplate template = buildType.getTemplate();
    if (template == null) {
      throw new NotFoundException("No template associated."); //todo: how to report it duly?
    }
    return new BuildTypeRef(template, myDataProvider, myApiUrlBuilder);
  }

  @PUT
  @Path("/{btLocator}/template")
  @Consumes("text/plain")
  public void getTemplateAssociation(@PathParam("btLocator") String buildTypeLocator, String templateLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    BuildTypeTemplate template = myDataProvider.getBuildTemplate(null, templateLocator);
    buildType.attachToTemplate(template, false);
    buildType.persist();
  }
//todo: allow also to post back the XML from GET request (http://devnet.jetbrains.net/message/5466528#5466528)

  @DELETE
  @Path("/{btLocator}/template")
  public void deleteTemplateAssociation(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    buildType.detachFromTemplate();
    buildType.persist();
  }


  @GET
  @Path("/{btLocator}/vcs-root-entries")
  @Produces({"application/xml", "application/json"})
  public VcsRootEntries getVcsRootEntries(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new VcsRootEntries(buildType.get().getVcsRootEntries(), myApiUrlBuilder);
  }

  @POST
  @Path("/{btLocator}/vcs-root-entries")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public VcsRootEntry addVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, VcsRootEntry description) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SVcsRoot vcsRoot = BuildTypeUtil.getVcsRoot(description, myDataProvider);

    try {
      buildType.get().addVcsRoot(vcsRoot);
    } catch (InvalidVcsRootScopeException e) {
      throw new BadRequestException(
        "Could not attach VCS root with id '" + vcsRoot.getId() + "' because of scope issues. Error: " + e.getMessage());
    }
    if (!StringUtil.isEmpty(description.checkoutRules)) {
      buildType.get().setCheckoutRules(vcsRoot, new CheckoutRules(description.checkoutRules));
    }
    buildType.get().persist();

    return new VcsRootEntry(vcsRoot, buildType.get().getCheckoutRules(vcsRoot), myApiUrlBuilder);
  }

  @GET
  @Path("/{btLocator}/vcs-root-entries/{id}")
  @Produces({"application/xml", "application/json"})
  public VcsRootEntry getVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, @PathParam("id") String vcsRootLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);

    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("No VCS root with id '" + vcsRoot.getId() + "' is attached to the build type.");
    }
    return new VcsRootEntry(vcsRoot, buildType.get().getCheckoutRules(vcsRoot), myApiUrlBuilder);
  }

  @DELETE
  @Path("/{btLocator}/vcs-root-entries/{id}")
  public void deleteVcsRootEntry(@PathParam("btLocator") String buildTypeLocator, @PathParam("id") String vcsRootLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);
    if (!buildType.get().containsVcsRoot(vcsRoot.getId())) {
      throw new NotFoundException("No VCS root with id '" + vcsRoot.getId() + "' is attached to the build type.");
    }
    buildType.get().removeVcsRoot(vcsRoot);
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/steps")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesStep getSteps(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesStep(buildType.get());
  }

  @POST
  @Path("/{btLocator}/steps")
  @Produces({"application/xml", "application/json"})
  public PropEntityStep addStep(@PathParam("btLocator") String buildTypeLocator, PropEntityStep stepDescription) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SBuildRunnerDescriptor newRunner =
      stepDescription.addStep(buildType.get(), myServiceLocator.getSingletonService(BuildRunnerDescriptorFactory.class));
    buildType.get().persist();
    return new PropEntityStep(newRunner, buildType.get());
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}")
  @Produces({"application/xml", "application/json"})
  public PropEntityStep getStep(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildRunnerDescriptor step = buildType.get().findBuildRunnerById(stepId);
    if (step == null) {
      throw new NotFoundException("No step with id '" + stepId + "' is found.");
    }
    return new PropEntityStep(step, buildType.get());
  }

  @DELETE
  @Path("/{btLocator}/steps/{stepId}")
  public void deleteStep(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildRunnerDescriptor step = buildType.get().findBuildRunnerById(stepId);
    if (step == null) {
      throw new NotFoundException("No step with id '" + stepId + "' is found.");
    }
    buildType.get().removeBuildRunner(stepId);
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  public String getStepParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                                 @PathParam("parameterName") String parameterName) {
    SBuildRunnerDescriptor step = getBuildTypeStep(myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator).get(), stepId);
    return getParameterValue(step, parameterName);
  }

  private static String getParameterValue(final ParametersDescriptor parametersHolder, final String parameterName) {
    Map<String, String> stepParameters = parametersHolder.getParameters();
    if (!stepParameters.containsKey(parameterName)) {
      throw new NotFoundException("No parameter with name '" + parameterName + "' is found in the step parameters.");
    }
    return stepParameters.get(parameterName);
  }

  private SBuildRunnerDescriptor getBuildTypeStep(final BuildTypeSettings buildType, final String stepId) {
    SBuildRunnerDescriptor step = buildType.findBuildRunnerById(stepId);
    if (step == null) {
      throw new NotFoundException("No step with id '" + stepId + "' is found in the build configuration.");
    }
    return step;
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}/parameters/{parameterName}")
  @Consumes({"text/plain"})
  public void addStepParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                               @PathParam("parameterName") String parameterName, String newValue) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildRunnerDescriptor step = getBuildTypeStep(buildType.get(), stepId);
    Map<String, String> parameters = new HashMap<String, String>(step.getParameters());
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parameters.put(parameterName, newValue);
    buildType.get().updateBuildRunner(step.getId(), step.getName(), step.getType(), parameters);
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/steps/{stepId}/{fieldName}")
  @Produces({"text/plain"})
  public String getStepSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                               @PathParam("fieldName") String name) {
    final BuildTypeSettings buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final SBuildRunnerDescriptor step = getBuildTypeStep(buildType, stepId);
    return PropEntityStep.getSetting(buildType, step, name);
  }

  @PUT
  @Path("/{btLocator}/steps/{stepId}/{fieldName}")
  @Consumes({"text/plain"})
  public void changeStepSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("stepId") String stepId,
                                @PathParam("fieldName") String name, String newValue) {
    final BuildTypeSettings buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final SBuildRunnerDescriptor step = getBuildTypeStep(buildType, stepId);
    PropEntityStep.setSetting(buildType, step, name, newValue);
    buildType.persist();
  }


  @GET
  @Path("/{btLocator}/features")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesFeature getFeatures(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesFeature(buildType.get());
  }

  @POST
  @Path("/{btLocator}/features")
  @Produces({"application/xml", "application/json"})
  public PropEntityFeature addFeature(@PathParam("btLocator") String buildTypeLocator, PropEntityFeature featureDescription) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SBuildFeatureDescriptor newFeature =
      featureDescription.addFeature(buildType.get(), myServiceLocator.getSingletonService(BuildFeatureDescriptorFactory.class));
    buildType.get().persist();
    return new PropEntityFeature(newFeature, buildType.get());
  }

  @GET
  @Path("/{btLocator}/features/{featureId}")
  @Produces({"application/xml", "application/json"})
  public PropEntityFeature getFeature(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    return new PropEntityFeature(feature, buildType.get());
  }

  @DELETE
  @Path("/{btLocator}/features/{featureId}")
  public void deleteFeature(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String id) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), id);
    buildType.get().removeBuildFeature(feature.getId());
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  @Produces({"text/plain"})
  public String getFeatureParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                    @PathParam("parameterName") String parameterName) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    return feature.getParameters().get(parameterName);
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/parameters/{parameterName}")
  public void addFeatureParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                  @PathParam("parameterName") String parameterName, String newValue) {

    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType.get(), featureId);
    Map<String, String> parameters = new HashMap<String, String>();
    parameters.putAll(feature.getParameters());
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parameters.put(parameterName, newValue);
    buildType.get().updateBuildFeature(feature.getId(), feature.getType(), parameters);
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/features/{featureId}/{name}")
  @Produces({"text/plain"})
  public String getFeatureSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                  @PathParam("name") String name) {
    final BuildTypeSettings buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType, featureId);
    return PropEntityStep.getSetting(buildType, feature, name);
  }

  @PUT
  @Path("/{btLocator}/features/{featureId}/{name}")
  @Consumes({"text/plain"})
  public void changeFeatureSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("featureId") String featureId,
                                   @PathParam("name") String name, String newValue) {
    final BuildTypeSettings buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final SBuildFeatureDescriptor feature = BuildTypeUtil.getBuildTypeFeature(buildType, featureId);
    PropEntityStep.setSetting(buildType, feature, name, newValue);
    buildType.persist();
  }


  @GET
  @Path("/{btLocator}/artifact-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesArtifactDep getArtifactDeps(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesArtifactDep(buildType.get());
  }

  @POST
  @Path("/{btLocator}/artifact-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntityArtifactDep addArtifactDep(@PathParam("btLocator") String buildTypeLocator, PropEntityArtifactDep description) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);

    final List<SArtifactDependency> dependencies = buildType.get().getArtifactDependencies();
    dependencies.add(description.createDependency(myServiceLocator.getSingletonService(ArtifactDependencyFactory.class)));
    int orderNum = dependencies.size() - 1;
    buildType.get().setArtifactDependencies(dependencies);
    buildType.get().persist();
    //todo: might not be a good way to get just added dependency
    return new PropEntityArtifactDep(buildType.get().getArtifactDependencies().get(orderNum), orderNum);
  }

  @GET
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntityArtifactDep getArtifactDep(@PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("artifactDepLocator") String artifactDepLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SArtifactDependency artifactDependency = DataProvider.getArtifactDep(buildType.get(), artifactDepLocator);
    return new PropEntityArtifactDep(artifactDependency, buildType.get());
  }

  @DELETE
  @Path("/{btLocator}/artifact-dependencies/{artifactDepLocator}")
  public void deleteArtifactDep(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("artifactDepLocator") String artifactDepLocator) {
    final BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final SArtifactDependency artifactDependency = DataProvider.getArtifactDep(buildType.get(), artifactDepLocator);
    final List<SArtifactDependency> dependencies = buildType.get().getArtifactDependencies();
    if (!dependencies.remove(artifactDependency)) {
      throw new NotFoundException("Specified artifact dependency is not found in the build type.");
    }
    buildType.get().setArtifactDependencies(dependencies);
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/snapshot-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesSnapshotDep getSnapshotDeps(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesSnapshotDep(buildType.get());
  }

  /**
   * Creates new snapshot dependency. 'id' attribute is ignored in the submitted descriptor.
   * Reports error if new dependency cannot be created (e.g. another dependency on the specified build configuration already exists).
   */
  @POST
  @Path("/{btLocator}/snapshot-dependencies")
  @Produces({"application/xml", "application/json"})
  public PropEntitySnapshotDep addSnapshotDep(@PathParam("btLocator") String buildTypeLocator, PropEntitySnapshotDep description) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);

    Dependency createdDependency = description.addSnapshotDependency(buildType.get(),
                                                                     myServiceLocator.getSingletonService(DependencyFactoryImpl.class));
    buildType.get().persist();
    return new PropEntitySnapshotDep(createdDependency);
  }

  @GET
  @Path("/{btLocator}/snapshot-dependencies/{snapshotDepLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntitySnapshotDep getSnapshotDep(@PathParam("btLocator") String buildTypeLocator,
                                              @PathParam("snapshotDepLocator") String snapshotDepLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final Dependency dependency = DataProvider.getSnapshotDep(buildType.get(), snapshotDepLocator);
    return new PropEntitySnapshotDep(dependency);
  }

  @DELETE
  @Path("/{btLocator}/snapshot-dependencies/{snapshotDepLocator}")
  public void deleteSnapshotDep(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("snapshotDepLocator") String snapshotDepLocator) {
    final BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final Dependency dependency = DataProvider.getSnapshotDep(buildType.get(), snapshotDepLocator);
    buildType.get().removeDependency(dependency);
    buildType.get().persist();
  }


  @GET
  @Path("/{btLocator}/triggers")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesTrigger getTriggers(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesTrigger(buildType.get());
  }

  /**
   * Creates new trigger. 'id' attribute is ignored in the submitted descriptor.
   * Reports error if new trigger cannot be created (e.g. only single trigger of the type is allowed for a build configuration).
   */
  @POST
  @Path("/{btLocator}/triggers")
  @Produces({"application/xml", "application/json"})
  public PropEntityTrigger addTrigger(@PathParam("btLocator") String buildTypeLocator, PropEntityTrigger description) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);

    final BuildTriggerDescriptor justAdded = description.addTrigger(buildType.get(), myServiceLocator
      .getSingletonService(BuildTriggerDescriptorFactory.class));

    buildType.get().persist();

    return new PropEntityTrigger(justAdded, buildType.get());
  }

  @GET
  @Path("/{btLocator}/triggers/{triggerLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntityTrigger getTrigger(@PathParam("btLocator") String buildTypeLocator,
                                      @PathParam("triggerLocator") String triggerLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType.get(), triggerLocator);
    return new PropEntityTrigger(trigger, buildType.get());
  }

  @DELETE
  @Path("/{btLocator}/triggers/{triggerLocator}")
  public void deleteTrigger(@PathParam("btLocator") String buildTypeLocator, @PathParam("triggerLocator") String triggerLocator) {
    final BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType.get(), triggerLocator);
    if (!buildType.get().removeBuildTrigger(trigger)) {
      throw new OperationException("Build trigger removal failed");
    }
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/triggers/{triggerLocator}/{fieldName}")
  @Produces({"text/plain"})
  public String getTriggerSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("triggerLocator") String triggerLocator,
                                  @PathParam("fieldName") String name) {
    final BuildTypeSettings buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType, triggerLocator);
    return PropEntityStep.getSetting(buildType, trigger, name);
  }

  @PUT
  @Path("/{btLocator}/triggers/{triggerLocator}/{fieldName}")
  @Consumes({"text/plain"})
  public void changeTriggerSetting(@PathParam("btLocator") String buildTypeLocator, @PathParam("triggerLocator") String triggerLocator,
                                   @PathParam("fieldName") String name, String newValue) {
    final BuildTypeSettings buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator).get();
    final BuildTriggerDescriptor trigger = DataProvider.getTrigger(buildType, triggerLocator);
    PropEntityStep.setSetting(buildType, trigger, name, newValue);
    buildType.persist();
  }


  @GET
  @Path("/{btLocator}/agent-requirements")
  @Produces({"application/xml", "application/json"})
  public PropEntitiesAgentRequirement getAgentRequirements(@PathParam("btLocator") String buildTypeLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    return new PropEntitiesAgentRequirement(buildType.get());
  }

  /**
   * Creates new agent requirement. 'id' attribute is ignored in the submitted descriptor.
   * Reports error if new requirement cannot be created (e.g. another requirement is present for the parameter).
   */
  @POST
  @Path("/{btLocator}/agent-requirements")
  @Produces({"application/xml", "application/json"})
  public PropEntityAgentRequirement addAgentRequirement(@PathParam("btLocator") String buildTypeLocator,
                                                        PropEntityAgentRequirement description) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);

    final PropEntityAgentRequirement result = description.addRequirement(buildType);
    buildType.get().persist();
    return result;
  }

  @GET
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}")
  @Produces({"application/xml", "application/json"})
  public PropEntityAgentRequirement getAgentRequirement(@PathParam("btLocator") String buildTypeLocator,
                                                        @PathParam("agentRequirementLocator") String agentRequirementLocator) {
    BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final Requirement requirement = DataProvider.getAgentRequirement(buildType.get(), agentRequirementLocator);
    return new PropEntityAgentRequirement(requirement);
  }

  @DELETE
  @Path("/{btLocator}/agent-requirements/{agentRequirementLocator}")
  public void deleteAgentRequirement(@PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("agentRequirementLocator") String agentRequirementLocator) {
    final BuildTypeOrTemplate buildType = myDataProvider.getBuildTypeOrTemplate(null, buildTypeLocator);
    final Requirement requirement = DataProvider.getAgentRequirement(buildType.get(), agentRequirementLocator);
    buildType.get().removeRequirement(requirement.getPropertyName());
    buildType.get().persist();
  }

  @GET
  @Path("/{btLocator}/investigations")
  @Produces({"application/xml", "application/json"})
  public Investigations getInvestigations(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new Investigations(buildType, myDataProvider, myApiUrlBuilder);
  }

  /**
   * Serves builds matching supplied condition.
   *
   * @param locator           Build locator to filter builds
   * @param buildTypeLocator  Deprecated, use "locator" parameter instead
   * @param status            Deprecated, use "locator" parameter instead
   * @param userLocator       Deprecated, use "locator" parameter instead
   * @param includePersonal   Deprecated, use "locator" parameter instead
   * @param includeCanceled   Deprecated, use "locator" parameter instead
   * @param onlyPinned        Deprecated, use "locator" parameter instead
   * @param tags              Deprecated, use "locator" parameter instead
   * @param agentName         Deprecated, use "locator" parameter instead
   * @param sinceBuildLocator Deprecated, use "locator" parameter instead
   * @param sinceDate         Deprecated, use "locator" parameter instead
   * @param start             Deprecated, use "locator" parameter instead
   * @param count             Deprecated, use "locator" parameter instead, defaults to 100
   * @return
   */
  @GET
  @Path("/{btLocator}/builds")
  @Produces({"application/xml", "application/json"})
  public Builds serveBuilds(@PathParam("btLocator") String buildTypeLocator,
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
                            @QueryParam("locator") BuildLocator locator,
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);

    return myDataProvider.getBuildsForRequest(buildType,
                                              status, userLocator, includePersonal, includeCanceled, onlyPinned, tags, agentName,
                                              sinceBuildLocator, sinceDate, start, count, locator, uriInfo, request, myApiUrlBuilder);
  }

  @GET
  @Path("/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);
    return new Build(build, myDataProvider, myApiUrlBuilder, myServiceLocator, myFactory);
  }


  @GET
  @Path("/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildField(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("buildLocator") String buildLocator,
                                @PathParam("field") String field) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }

  @GET
  @Path("/{btLocator}/branches")
  @Produces({"application/xml", "application/json"})
  public Branches serveBranches(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    //todo: support branches filters
    return new Branches(CollectionsUtil
                          .convertCollection(((BuildTypeImpl)buildType).getBranches(BranchesPolicy.ACTIVE_HISTORY_AND_ACTIVE_VCS_BRANCHES, false),
                                             new Converter<jetbrains.buildServer.server.rest.model.build.Branch, BranchEx>() {
                                               public Branch createFrom(@NotNull final BranchEx source) {
                                                 return new Branch(source);
                                               }
                                             }));
  }
}
