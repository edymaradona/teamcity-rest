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

package jetbrains.buildServer.server.rest.model.issue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Yegor.Yarko
 *         Date: 21.07.2009
 */
@XmlRootElement(name = "issues")
public class Issues {
  @XmlElement(name = "issue")
  public List<Issue> issues;

  public Issues() {
  }

  public Issues(Collection<jetbrains.buildServer.issueTracker.Issue> buildIssues) {
    issues = new ArrayList<Issue>(buildIssues.size());
    for (jetbrains.buildServer.issueTracker.Issue buildIssue : buildIssues) {
      issues.add(new Issue(buildIssue));
    }
  }
}