/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jetbrains.buildServer.server.rest.data.build.TagFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 20.08.2014
 */
public class BuildPromotionFinder extends AbstractFinder<BuildPromotion> {
  //DIMENSION_ID - id of a build or id of build promotion which will get associated build with the id
  public static final String PROMOTION_ID = BuildFinder.PROMOTION_ID;
  public static final String BUILD_TYPE = "buildType";
  public static final String PROJECT = "project"; //todo: BuildFinder treats "project" as "affectedProject" thus this behavior is differet from BuildFinder
  private static final String AFFECTED_PROJECT = "affectedProject";
  public static final String AGENT = "agent";
  public static final String AGENT_NAME = "agentName";
  public static final String PERSONAL = "personal";
  public static final String USER = "user";
  protected static final String BRANCH = "branch";
  protected static final String PROPERTY = "property";

  public static final String STATE = "state";
  public static final String STATE_QUEUED = "queued";
  public static final String STATE_RUNNING = "running";
  public static final String STATE_FINISHED = "finished";
  protected static final String STATE_ANY = "any";

  protected static final String NUMBER = "number";
  protected static final String STATUS = "status";
  protected static final String CANCELED = "canceled";
  protected static final String PINNED = "pinned";
  protected static final String RUNNING = "running";
  protected static final String SNAPSHOT_DEP = "snapshotDependency";
  protected static final String COMPATIBLE_AGENTS_COUNT = "compatibleAgentsCount";
  protected static final String TAGS = "tags";
  protected static final String TAG = "tag";
  protected static final String COMPATIBLE_AGENT = "compatibleAgent";
  protected static final String SINCE_BUILD = "sinceBuild";
  protected static final String SINCE_DATE = "sinceDate";
  protected static final String UNTIL_BUILD = "untilBuild";
  protected static final String UNTIL_DATE = "untilDate";
  public static final String BY_PROMOTION = "byPromotion";  //used in BuildFinder

  private final BuildPromotionManager myBuildPromotionManager;
  private final BuildQueue myBuildQueue;
  private final BuildsManager myBuildsManager;
  private final BuildHistory myBuildHistory;
  private final ProjectFinder myProjectFinder;
  private final BuildTypeFinder myBuildTypeFinder;
  private final UserFinder myUserFinder;
  private final AgentFinder myAgentFinder;

  @NotNull
  public static String getLocator(@NotNull final BuildPromotion buildPromotion) {
    final Long associatedBuildId = buildPromotion.getAssociatedBuildId();
    if (associatedBuildId == null) {
      return Locator.getStringLocator(DIMENSION_ID, String.valueOf(buildPromotion.getId())); //assume this is a queued build, so buildId==promotionId
    }
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(associatedBuildId));
  }

  public BuildPromotionFinder(final BuildPromotionManager buildPromotionManager,
                              final BuildQueue buildQueue,
                              final BuildsManager buildsManager,
                              final BuildHistory buildHistory,
                              final ProjectFinder projectFinder,
                              final BuildTypeFinder buildTypeFinder,
                              final UserFinder userFinder,
                              final AgentFinder agentFinder) {
    super(new String[]{DIMENSION_ID, PROMOTION_ID, PROJECT, AFFECTED_PROJECT, BUILD_TYPE, BRANCH, AGENT, USER, PERSONAL, STATE, TAG, PROPERTY, COMPATIBLE_AGENT,
      NUMBER, STATUS, CANCELED, PINNED, DIMENSION_LOOKUP_LIMIT,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, PagerData.START, PagerData.COUNT});
    myBuildPromotionManager = buildPromotionManager;
    myBuildQueue = buildQueue;
    myBuildsManager = buildsManager;
    myBuildHistory = buildHistory;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myUserFinder = userFinder;
    myAgentFinder = agentFinder;
  }

  @NotNull
  @Override
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator result = super.createLocator(locatorText, locatorDefaults);
    result.addHiddenDimensions(AGENT_NAME, RUNNING, COMPATIBLE_AGENTS_COUNT, SNAPSHOT_DEP, TAGS, SINCE_BUILD, SINCE_DATE, UNTIL_BUILD, UNTIL_DATE,
                               STATE_RUNNING //compatibility with pre-9.1
    );
    result.addIgnoreUnusedDimensions(BY_PROMOTION);
    return result;
  }

  @NotNull
  @Override
  public ItemHolder<BuildPromotion> getAllItems() {
    final ArrayList<BuildPromotion> result = new ArrayList<BuildPromotion>();
    result.addAll(CollectionsUtil.convertCollection(myBuildQueue.getItems(), new Converter<BuildPromotion, SQueuedBuild>() {
      public BuildPromotion createFrom(@NotNull final SQueuedBuild source) {
        return source.getBuildPromotion();
      }
    }));
    result.addAll(CollectionsUtil.convertCollection(myBuildsManager.getRunningBuilds(), new Converter<BuildPromotion, SRunningBuild>() {
      public BuildPromotion createFrom(@NotNull final SRunningBuild source) {
        return source.getBuildPromotion();
      }
    }));
    result.addAll(CollectionsUtil.convertCollection(myBuildHistory.getEntries(true), new Converter<BuildPromotion, SFinishedBuild>() {
      public BuildPromotion createFrom(@NotNull final SFinishedBuild source) {
        return source.getBuildPromotion();
      }
    }));
    return getItemHolder(result);
  }

  @Nullable
  @Override
  protected BuildPromotion findSingleItem(@NotNull final Locator locator) {
    //see also getBuildId method
    if (locator.isSingleValue()) {
     // try build id first for compatibility reasons
      @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
      final SBuild build = myBuildsManager.findBuildInstanceById(singleValueAsLong);
      if (build != null){
        return build.getBuildPromotion();
      }
      // assume it's promotion id
      return BuildFinder.getBuildPromotion(singleValueAsLong, myBuildPromotionManager);
    }

    Long promotionId = locator.getSingleDimensionValueAsLong(PROMOTION_ID);
    if (promotionId == null){
      promotionId = locator.getSingleDimensionValueAsLong("promotionId"); //support TeamCity 8.0 dimension
    }
    if (promotionId != null) {
      return BuildFinder.getBuildPromotion(promotionId, myBuildPromotionManager);
    }

    final Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      final BuildPromotion buildPromotion = BuildFinder.getBuildPromotion(id, myBuildPromotionManager);
      if (!buildIdDiffersFromPromotionId(buildPromotion)){
        return buildPromotion;
      }
      final SBuild build = myBuildsManager.findBuildInstanceById(id);
      if (build != null){
        return build.getBuildPromotion();
      }
    }

    final String number = locator.getSingleDimensionValue(NUMBER);
    if (number != null) {
      final SBuildType buildType = myBuildTypeFinder.getBuildType(null, locator.getSingleDimensionValue(BUILD_TYPE), false);

      SBuild build = myBuildsManager.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + number + "' in build configuration with id '" + buildType.getExternalId() + "'.");
      }
      return build.getBuildPromotion();
    }

    return null;
  }

  public static void ensureCanView(@NotNull final BuildPromotion buildPromotion) {
    //checking permissions to view - workaround for TW-45544
    try {
      buildPromotion.getBuildType();
    } catch (AccessDeniedException e) {
      //concealing the message which contains build configuration id: You do not have enough permissions to access build type with id: XXX
      throw new AccessDeniedException(e.getAuthorityHolder(), "Not enough permissions to access build with id: " + buildPromotion.getId());
    }
  }

  /**
   * Utility method to get id from the locator even if there is no such build
   * Should match findSingleItem method logic
   */
  @Nullable
  private Long getBuildId(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      return locator.getSingleValueAsLong();
    }
    return locator.getSingleDimensionValueAsLong(DIMENSION_ID);
  }

  @NotNull
  @Override
  protected AbstractFilter<BuildPromotion> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    if (countFromFilter == null) {
      //limiting to 100 builds by default
      countFromFilter = 100L;
    }
    final MultiCheckerFilter<BuildPromotion> result =
      new MultiCheckerFilter<BuildPromotion>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter.intValue(),
                                             locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT));

    //checking permissions to view - workaround for TW-45544
    result.add(new FilterConditionChecker<BuildPromotion>() {
      public boolean isIncluded(@NotNull final BuildPromotion item) {
        try {
          ensureCanView(item);
          return true;
        } catch (AccessDeniedException e) {
          return false; //excluding from the lists as secure wrappers usually do
        }
      }
    });

    final String stateDimension = locator.getSingleDimensionValue(STATE);
    Locator stateLocator;
    if (stateDimension != null) {
      stateLocator = createStateLocator(stateDimension);
    } else {
      final String stateRunningDimension = locator.getSingleDimensionValue(STATE_RUNNING); //compatibility with pre-9.1
      if (stateRunningDimension != null) {
        stateLocator = createStateLocator(Locator.getStringLocator(STATE_RUNNING, stateRunningDimension));
      } else {
        stateLocator = createStateLocator(STATE_FINISHED); // default to only finished builds
      }
    }
    if (!isStateIncluded(stateLocator, STATE_QUEUED)) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return item.getQueuedBuild() == null;
        }
      });
    }

    if (!isStateIncluded(stateLocator, STATE_RUNNING)) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild associatedBuild = item.getAssociatedBuild();
          return associatedBuild == null || associatedBuild.isFinished();
        }
      });
    }

    if (!isStateIncluded(stateLocator, STATE_FINISHED)) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild associatedBuild = item.getAssociatedBuild();
          return associatedBuild == null || !associatedBuild.isFinished();
        }
      });
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    SProject project = null;
    if (projectLocator != null) {
      project = myProjectFinder.getProject(projectLocator);
      final SProject internalProject = project;
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuildType buildType = item.getBuildType();
          return buildType != null && internalProject.equals(buildType.getProject());
        }
      });
    }

    final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    SProject affectedProject = null;
    if (affectedProjectLocator != null) {
      affectedProject = myProjectFinder.getProject(affectedProjectLocator);
      final SProject internalProject = affectedProject;
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuildType buildType = item.getBuildType();
          return buildType != null && ProjectFinder.isSameOrParent(internalProject, buildType.getProject());
        }
      });
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator != null) {
      final SBuildType buildType = myBuildTypeFinder.getBuildType(affectedProject, buildTypeLocator, false);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return buildType.equals(item.getParentBuildType());
        }
      });
    }

    final String agentLocator = locator.getSingleDimensionValue(AGENT);
    if (agentLocator != null) {
      final List<SBuildAgent> agents = myAgentFinder.getItems(agentLocator).myEntries;
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild build = item.getAssociatedBuild();
          if (build != null) {
            return agents.contains(build.getAgent());
          }

          final SQueuedBuild queuedBuild = item.getQueuedBuild(); //for queued build using compatible agents
          if (queuedBuild != null) {
            return !CollectionsUtil.intersect(queuedBuild.getCompatibleAgents(), agents).isEmpty();
          }
          return false;
        }
      });
    }

    final String branchLocatorValue = locator.getSingleDimensionValue(BRANCH);
    // should filter by branch even if not specified in the locator
    final BranchMatcher branchMatcher;
    try {
      branchMatcher = new BranchMatcher(branchLocatorValue);
    } catch (LocatorProcessException e) {
      throw new LocatorProcessException("Invalid sub-locator '" + BRANCH + "': " + e.getMessage());
    }
    if (!branchMatcher.matchesAnyBranch()) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return branchMatcher.matches(item);
        }
      });
    }

    //compatibility support
    final String tags = locator.getSingleDimensionValue(TAGS);
    if (tags != null) {
      final List<String> tagsList = Arrays.asList(tags.split(","));
      if (tagsList.size() > 0) {
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return item.getTags().containsAll(tagsList);
          }
        });
      }
    }

    final String tag = locator.getSingleDimensionValue(TAG);
    if (tag != null) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return new TagFinder(myUserFinder, item).getItems(tag, TagFinder.getDefaultLocator()).myEntries.size() > 0;
        }
      });
    }

    final String compatibleAagentLocator = locator.getSingleDimensionValue(COMPATIBLE_AGENT); //experimental, only for queued builds
    if (compatibleAagentLocator != null) {
      final SBuildAgent agent = myAgentFinder.getItem(compatibleAagentLocator);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SQueuedBuild queuedBuild = item.getQueuedBuild();
          if (queuedBuild != null) {
            return queuedBuild.getCompatibleAgents().contains(agent);
          }
          return false;
        }
      });
    }

    final Long compatibleAgentsCount = locator.getSingleDimensionValueAsLong(COMPATIBLE_AGENTS_COUNT); //experimental, only for queued builds
    if (compatibleAgentsCount != null) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SQueuedBuild queuedBuild = item.getQueuedBuild();
          if (queuedBuild != null) {
            return compatibleAgentsCount.equals(Integer.valueOf(queuedBuild.getCompatibleAgents().size()).longValue());
          }
          return false;
        }
      });
    }

    final Boolean personal = locator.getSingleDimensionValueAsBoolean(PERSONAL, false);
    if (personal != null) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return FilterUtil.isIncludedByBooleanFilter(personal, item.isPersonal());
        }
      });
    }

    final String userDimension = locator.getSingleDimensionValue(USER);
    if (userDimension != null) {
      final SUser user = myUserFinder.getUser(userDimension);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          SUser actualUser = null;
          final SBuild build = item.getAssociatedBuild();
          if (build != null) {
            actualUser = build.getTriggeredBy().getUser();
          }
          final SQueuedBuild queuedBuild = item.getQueuedBuild();
          if (queuedBuild != null) {
            actualUser = queuedBuild.getTriggeredBy().getUser();
          }
          return actualUser != null && user.getId() == actualUser.getId();
        }
      });
    }

    final String property = locator.getSingleDimensionValue(PROPERTY);
    if (property != null) {
      final ParameterCondition parameterCondition = ParameterCondition.create(property);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return parameterCondition.matches(((BuildPromotionEx)item).getParametersProvider()); //TeamCity open API issue
        }
      });
    }

    final MultiCheckerFilter<SBuild> buildFilter = getBuildFilter(locator);
    if (buildFilter.getSubFiltersCount() > 0) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild build = item.getAssociatedBuild();
          if (build == null) {
            return false;
          }
          return buildFilter.isIncluded(build);
        }
      });
    }

    return result;
  }

  @NotNull
  private MultiCheckerFilter<SBuild> getBuildFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SBuild> result = new MultiCheckerFilter<SBuild>(null, null, null);

    final String buildNumber = locator.getSingleDimensionValue(NUMBER);
    if (buildNumber != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return buildNumber.equals(item.getBuildNumber());
        }
      });
    }

    final String status = locator.getSingleDimensionValue(STATUS);
    if (status != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return status.equalsIgnoreCase(item.getStatusDescriptor().getStatus().getText());
        }
      });
    }

    final Boolean canceled = locator.getSingleDimensionValueAsBoolean(CANCELED, false);
    if (canceled != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(canceled, item.getCanceledInfo() != null);
        }
      });
    }

    //for compatibility, use "state:running" instead
    final Boolean running = locator.getSingleDimensionValueAsBoolean(RUNNING);
    ;
    if (running != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(running, !item.isFinished());
        }
      });
    }

    final Boolean pinned = locator.getSingleDimensionValueAsBoolean(PINNED);
    if (pinned != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(pinned, item.isPinned());
        }
      });
    }

    //compatibility, use "agent" locator instead
    final String agentName = locator.getSingleDimensionValue(AGENT_NAME);
    if (agentName != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return agentName.equals(item.getAgent().getName());
        }
      });
    }

    //todo: filter on gettings builds; more options (all times); also for buildPromotion
    final String sinceBuild = locator.getSingleDimensionValue(SINCE_BUILD);
    final Date sinceDate = DataProvider.parseDate(locator.getSingleDimensionValue(SINCE_DATE));
    if (sinceBuild != null || sinceDate != null) {
      final RangeLimit rangeLimit = new RangeLimit(getBuildId(sinceBuild), sinceDate);
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return rangeLimit.before(item);
        }
      });
    }

    final String untilBuild = locator.getSingleDimensionValue(UNTIL_BUILD);
    final Date untilDate = DataProvider.parseDate(locator.getSingleDimensionValue(UNTIL_DATE));
    if (untilBuild != null || untilDate != null) {
      final RangeLimit rangeLimit = new RangeLimit(getBuildId(untilBuild), untilDate);
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return !rangeLimit.before(item);
        }
      });
    }

    return result;
  }

  @Nullable
  private Long getBuildId(@Nullable final String buildLocator) {
    if (buildLocator == null) {
      return null;
    }
    final Long buildId = getBuildId(new Locator(buildLocator));
    if (buildId != null) {
      return buildId;
    }
    return getItem(buildLocator).getId();
  }

  public class RangeLimit {
    @Nullable private final Long myBuildId;
    @Nullable private final Date myStartDate;

    public RangeLimit(@Nullable final Long buildId, @Nullable final Date startDate) {
      myBuildId = buildId;
      myStartDate = startDate;
    }

    public boolean before(@NotNull SBuild build) {
      if (myBuildId != null) {
        return myBuildId < build.getBuildId();
      }
      if (myStartDate != null) {
        return myStartDate.before(build.getStartDate());
      }
      return false;
    }
  }

  private boolean isTagsMatchLocator(final List<String> buildTags, final Locator tagsLocator) {
    if (!"extended".equals(tagsLocator.getSingleDimensionValue("format"))) {
      throw new BadRequestException("Only 'extended' value is supported for 'format' dimension of 'tag' dimension");
    }
    final Boolean present = tagsLocator.getSingleDimensionValueAsBoolean("present", true);
    final String patternString = tagsLocator.getSingleDimensionValue("regexp");
    if (present == null) {
      return true;
    }
    Boolean tagsMatchPattern = null;
    if (patternString != null) {
      if (StringUtil.isEmpty(patternString)) {
        throw new BadRequestException("'regexp' sub-dimension should not be empty for 'tag' dimension");
      }
      try {
        tagsMatchPattern = tagsMatchPattern(buildTags, patternString);
      } catch (PatternSyntaxException e) {
        throw new BadRequestException(
          "Bad syntax for Java regular expression in 'regexp' sub-dimension of 'tag' dimension: " + e.getMessage(), e);
      }
    }
    if (tagsMatchPattern == null) {
      if ((present && buildTags.size() != 0) || (!present && (buildTags.size() == 0))) {
        return true;
      }
    } else {
      if (present && tagsMatchPattern) {
        return true;
      } else if (!present && !tagsMatchPattern) {
        return true;
      }
    }
    return false;
  }

  private Boolean tagsMatchPattern(@NotNull final List<String> tags, @NotNull final String patternString) throws PatternSyntaxException {
    final Pattern pattern = Pattern.compile(patternString);
    boolean atLestOneMatches = false;
    for (String tag : tags) {
      atLestOneMatches = atLestOneMatches || pattern.matcher(tag).matches();
    }
    return atLestOneMatches;
  }


  @Override
  protected ItemHolder<BuildPromotion> getPrefilteredItems(@NotNull Locator locator) {
    final String snapshotDepDimension = locator.getSingleDimensionValue(SNAPSHOT_DEP);
    if (snapshotDepDimension != null) {
      ArrayList<BuildPromotion> result = new ArrayList<BuildPromotion>();

      Locator snapshotDepLocator = new Locator(snapshotDepDimension, "to", "from", "recursive");  //todo: also use the locator in Build's nodes
      Boolean recursive = snapshotDepLocator.getSingleDimensionValueAsBoolean("recursive", false);
      if (recursive == null) recursive = false;

      final String toBuildDimension = snapshotDepLocator.getSingleDimensionValue("to");
      if (toBuildDimension != null) {
        final List<BuildPromotion> toBuilds = getItems(toBuildDimension).myEntries;
        if (recursive) {
          for (BuildPromotion toBuild : toBuilds) {
            result.addAll(toBuild.getAllDependencies());
          }
        } else {
          final Set<BuildPromotion> alldependencyBuilds = new TreeSet<BuildPromotion>();
          for (BuildPromotion toBuild : toBuilds) {
            alldependencyBuilds.addAll(CollectionsUtil.convertCollection(toBuild.getDependencies(), new Converter<BuildPromotion, BuildDependency>() {
              public BuildPromotion createFrom(@NotNull final BuildDependency source) {
                return source.getDependOn();
              }
            }));
          }
          result.addAll(alldependencyBuilds); //todo: sort
        }
      }

      final String fromBuildDimension = snapshotDepLocator.getSingleDimensionValue("from");
      if (fromBuildDimension != null) {
        final Collection<BuildPromotion> allDependingOn = getAllDependOn(getItems(fromBuildDimension).myEntries, recursive); //todo: sort!
        if (result.isEmpty()) {
          result.addAll(allDependingOn);
        } else {
          result = new ArrayList<BuildPromotion>(CollectionsUtil.intersect(result, allDependingOn));
        }
      }

      snapshotDepLocator.checkLocatorFullyProcessed();
      return getItemHolder(result);
    }

    final ArrayList<BuildPromotion> result = new ArrayList<BuildPromotion>();

    final String stateDimension = locator.getSingleDimensionValue(STATE);
    Locator stateLocator;
    if (stateDimension != null) {
      stateLocator = createStateLocator(stateDimension);
    } else {
      final String stateRunningDimension = locator.getSingleDimensionValue(STATE_RUNNING); //compatibility with pre-9.1
      if (stateRunningDimension != null) {
        stateLocator = createStateLocator(Locator.getStringLocator(STATE_RUNNING, stateRunningDimension));
      } else {
        stateLocator = createStateLocator(STATE_FINISHED); // defult to only finished builds
      }
    }

    if (isStateIncluded(stateLocator, STATE_QUEUED)) {
      result.addAll(CollectionsUtil.convertCollection(myBuildQueue.getItems(), new Converter<BuildPromotion, SQueuedBuild>() {
        public BuildPromotion createFrom(@NotNull final SQueuedBuild source) {
          return source.getBuildPromotion();
        }
      }));
    }

    if (isStateIncluded(stateLocator, STATE_RUNNING)) {
      result.addAll(CollectionsUtil.convertCollection(myBuildsManager.getRunningBuilds(), new Converter<BuildPromotion, SRunningBuild>() {
        public BuildPromotion createFrom(@NotNull final SRunningBuild source) {
          return source.getBuildPromotion();
        }
      }));
    }

    ItemHolder<BuildPromotion> finishedBuilds = null;
    if (isStateIncluded(stateLocator, STATE_FINISHED)) {
      @Nullable SBuildType buildType = null;
      final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeLocator != null) {
        final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
        SProject affectedProject = null;
        if (affectedProjectLocator != null) {
          affectedProject = myProjectFinder.getProject(affectedProjectLocator);
        }
        buildType = myBuildTypeFinder.getBuildType(affectedProject, buildTypeLocator, false);
      }

      if (buildType != null) {
        SUser user = null;
        boolean includePersonalIfUserNotSpecified = false;
        boolean includeCanceled = false;

        final Boolean personal = locator.getSingleDimensionValueAsBoolean(PERSONAL);
        if ((personal == null && locator.getSingleDimensionValue(PERSONAL) != null) ||
            (personal != null && personal)) {
          includePersonalIfUserNotSpecified = true;

          final String userDimension = locator.getSingleDimensionValue(USER);
          if (userDimension != null) {
            user = myUserFinder.getUser(userDimension);
          }
        }

        final Boolean canceled = locator.getSingleDimensionValueAsBoolean(CANCELED);
        if ((canceled == null && locator.getSingleDimensionValue(CANCELED) != null) ||
            (canceled != null && canceled)) {
          includeCanceled = true;
        }


        final SBuildType buildTypeFinal = buildType;
        final SUser userFinal = user;
        final boolean includePersonalIfUserNotSpecifiedFinal = includePersonalIfUserNotSpecified;
        final boolean includeCanceledFinal = includeCanceled;

    //todo: try to use the API:
    //BuildQueryOptions options = new BuildQueryOptions()
    //  .setBuildTypeId(buildType.getBuildTypeId())
    //  .setMatchAllBranches(matchAllBranches)
    //  .setBranch(branch)
    //  .setIncludePersonal(false, null)
    //  .setIncludeRunning(true)
    //  .setOrderByChanges(true);
    //myBuildsManager.processBuilds(options, new ItemProcessor<SBuild>() {
    //  public boolean processItem(SBuild item) {
    //    if (!item.getBuildPromotion().isChangesDetached()) {
    //      builds.add(item);
    //    }
    //    return true;
    //  }
    //});

        finishedBuilds = new ItemHolder<BuildPromotion>() {
          public boolean process(@NotNull final ItemProcessor<BuildPromotion> processor) {
            myBuildHistory.processEntries(buildTypeFinal.getInternalId(), userFinal, includePersonalIfUserNotSpecifiedFinal, includeCanceledFinal, false,
                                          new ItemProcessor<SFinishedBuild>() {
                                            public boolean processItem(final SFinishedBuild item) {
                                              return processor.processItem(item.getBuildPromotion());
                                            }
                                          });
            return false;
          }
        };
      } else {
        finishedBuilds = new ItemHolder<BuildPromotion>() {
          public boolean process(@NotNull final ItemProcessor<BuildPromotion> processor) {
            myBuildHistory.processEntries(new ItemProcessor<SFinishedBuild>() {
              public boolean processItem(final SFinishedBuild item) {
                return processor.processItem(item.getBuildPromotion());
              }
            });
            return false;
          }
        };
      }
    }

    stateLocator.checkLocatorFullyProcessed();

    final ItemHolder<BuildPromotion> finishedBuildsFinal = finishedBuilds;
    return new ItemHolder<BuildPromotion>() {
      public boolean process(@NotNull final ItemProcessor<BuildPromotion> processor) {
        if (new CollectionItemHolder<BuildPromotion>(result).process(processor)) {
          if (finishedBuildsFinal != null) {
            return finishedBuildsFinal.process(processor);
          }
          return true;
        }
        return false;
      }
    };
  }

  private Collection<BuildPromotion> getAllDependOn(final List<BuildPromotion> items, boolean recursive) {
    final Set<BuildPromotion> processed = new TreeSet<BuildPromotion>();
    final List<BuildPromotion> toProcess = new ArrayList<BuildPromotion>();
    for (BuildPromotion item : items) {
      toProcess.addAll(getDependingPromotions(item));
    }
    while (!toProcess.isEmpty()) {
      final List<BuildPromotion> currentBatch = new ArrayList<BuildPromotion>(toProcess);
      toProcess.clear();
      for (BuildPromotion item : currentBatch) {
        if (!processed.contains(item)) {
          processed.add(item);
          if (recursive) {
            toProcess.addAll(getDependingPromotions(item));
          }
        }
      }
    }
    return processed;
  }

  @NotNull
  private List<BuildPromotion> getDependingPromotions(@NotNull final BuildPromotion fromBuild) {
    return CollectionsUtil.convertCollection(fromBuild.getDependedOnMe(), new Converter<BuildPromotion, BuildDependency>() {
      public BuildPromotion createFrom(@NotNull final BuildDependency source) {
        return source.getDependent();
      }
    });
  }

  @NotNull
  private Locator createStateLocator(@NotNull final String stateDimension) {
    final Locator locator = new Locator(stateDimension, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, STATE_QUEUED, STATE_RUNNING, STATE_FINISHED);
    if (locator.isSingleValue()) {
      //check single value validity
      if (!stateDimension.equals(STATE_QUEUED) &&
          !stateDimension.equals(STATE_RUNNING) &&
          !stateDimension.equals(STATE_FINISHED) &&
          !stateDimension.equals(STATE_ANY)) {
        throw new BadRequestException("Unsupported value of '" + STATE + "' dimension: '" + stateDimension + "'. Should be one of the build states or '" + STATE_ANY + "'");
      }
    }

    return locator;
  }

  private boolean isStateIncluded(@NotNull final Locator stateLocator, @NotNull final String state) {
    final String singleValue = stateLocator.getSingleValue();
    if (singleValue != null && (STATE_ANY.equals(singleValue) || state.equals(singleValue))) {
      return true;
    }
    //noinspection RedundantIfStatement
    if (!stateLocator.isSingleValue() && FilterUtil.isIncludedByBooleanFilter(stateLocator.getSingleDimensionValueAsBoolean(state), true)) {
      return true;
    }
    return false;
  }

  public static boolean buildIdDiffersFromPromotionId(@NotNull final BuildPromotion buildPromotion) {
    return buildPromotion.getAssociatedBuildId() != null && buildPromotion.getId() != buildPromotion.getAssociatedBuildId();
  }
}
