/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geode.management.internal.cli.functions;

import org.apache.commons.lang.StringUtils;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.query.QueryInvalidException;
import org.apache.geode.cache.query.internal.CompiledValue;
import org.apache.geode.cache.query.internal.QCompiler;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.commands.DataCommands;
import org.apache.geode.management.internal.cli.domain.DataCommandRequest;
import org.apache.geode.management.internal.cli.domain.DataCommandResult;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.multistep.CLIMultiStepHelper;
import org.apache.geode.management.internal.cli.remote.CommandExecutionContext;
import org.apache.logging.log4j.Logger;
import org.apache.shiro.subject.Subject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SelectExecStep extends CLIMultiStepHelper.RemoteStep {
  private static final Logger logger = LogService.getLogger();

  private static final long serialVersionUID = 1L;

  public SelectExecStep(Object[] arguments) {
    super(DataCommandFunction.SELECT_STEP_EXEC, arguments);
  }

  @Override
  public Result exec() {
    String remainingQuery = (String) commandArguments[0];
    boolean interactive = (Boolean) commandArguments[2];
    DataCommandResult result = _select(remainingQuery);
    int endCount = 0;
    DataCommandFunction.cachedResult = result;
    if (interactive) {
      endCount = DataCommandFunction.getPageSize();
    } else {
      if (result.getSelectResult() != null) {
        endCount = result.getSelectResult().size();
      }
    }
    if (interactive) {
      return result.pageResult(0, endCount, DataCommandFunction.SELECT_STEP_DISPLAY);
    } else {
      return CLIMultiStepHelper.createBannerResult(new String[] {}, new Object[] {},
          DataCommandFunction.SELECT_STEP_END);
    }
  }

  public DataCommandResult _select(String query) {
    InternalCache cache = (InternalCache) CacheFactory.getAnyInstance();
    DataCommandResult dataResult;

    if (StringUtils.isEmpty(query)) {
      dataResult = DataCommandResult.createSelectInfoResult(null, null, -1, null,
          CliStrings.QUERY__MSG__QUERY_EMPTY, false);
      return dataResult;
    }

    Object array[] = DataCommands.replaceGfshEnvVar(query, CommandExecutionContext.getShellEnv());
    query = (String) array[1];
    query = addLimit(query);

    @SuppressWarnings("deprecation")
    QCompiler compiler = new QCompiler();
    Set<String> regionsInQuery;
    try {
      CompiledValue compiledQuery = compiler.compileQuery(query);
      Set<String> regions = new HashSet<>();
      compiledQuery.getRegionsInQuery(regions, null);

      // authorize data read on these regions
      for (String region : regions) {
        cache.getSecurityService().authorizeRegionRead(region);
      }

      regionsInQuery = Collections.unmodifiableSet(regions);
      if (regionsInQuery.size() > 0) {
        Set<DistributedMember> members =
            DataCommands.getQueryRegionsAssociatedMembers(regionsInQuery, cache, false);
        if (members != null && members.size() > 0) {
          DataCommandFunction function = new DataCommandFunction();
          DataCommandRequest request = new DataCommandRequest();
          request.setCommand(CliStrings.QUERY);
          request.setQuery(query);
          Subject subject = cache.getSecurityService().getSubject();
          if (subject != null) {
            request.setPrincipal(subject.getPrincipal());
          }
          dataResult = DataCommands.callFunctionForRegion(request, function, members);
          dataResult.setInputQuery(query);
          return dataResult;
        } else {
          return DataCommandResult.createSelectInfoResult(null, null, -1, null, CliStrings.format(
              CliStrings.QUERY__MSG__REGIONS_NOT_FOUND, regionsInQuery.toString()), false);
        }
      } else {
        return DataCommandResult.createSelectInfoResult(null, null, -1, null,
            CliStrings.format(CliStrings.QUERY__MSG__INVALID_QUERY,
                "Region mentioned in query probably missing /"),
            false);
      }
    } catch (QueryInvalidException qe) {
      logger.error("{} Failed Error {}", query, qe.getMessage(), qe);
      return DataCommandResult.createSelectInfoResult(null, null, -1, null,
          CliStrings.format(CliStrings.QUERY__MSG__INVALID_QUERY, qe.getMessage()), false);
    }
  }

  private String addLimit(String query) {
    if (StringUtils.containsIgnoreCase(query, " limit")
        || StringUtils.containsIgnoreCase(query, " count(")) {
      return query;
    }
    return query + " limit " + DataCommandFunction.getFetchSize();
  }
}
