/*
 *
 * *********************************************************************
 * fsdevtools
 * %%
 * Copyright (C) 2019 e-Spirit AG
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * *********************************************************************
 *
 */

package com.espirit.moddev.projectservice.projectactivatewebserver;

import com.espirit.moddev.shared.webapp.WebAppIdentifier;

import de.espirit.firstspirit.access.Connection;
import de.espirit.firstspirit.access.project.Project;
import de.espirit.firstspirit.access.script.ExecutionException;
import de.espirit.firstspirit.access.store.LockException;
import de.espirit.firstspirit.agency.ModuleAdminAgent;
import de.espirit.firstspirit.agency.WebAppId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Class that can activate a web server for given FirstSpirit project.
 */
public class ProjectWebserverActivator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectWebserverActivator.class);

    /**
     * Empty constructor to avoid implicit constructor
     */
    public ProjectWebserverActivator() {
        // Nothing to do here
    }

    /**
     * Activates a web server for a given project and scope. Project, scope and further activation details are given in a set of parameters. Uses the
     * given connection to obtain all necessary managers. See {@link ProjectWebServerActivationParameter} for parameter details.
     *
     * @param connection the connection that is used to access the FirstSpirit server
     * @param parameter  the parameters for the server activation
     * @return true if the project was imported successfully, false otherwise
     */
    public boolean activateWebServer(Connection connection, ProjectWebServerActivationParameter parameter) {
        if (!arePreconditionsFulfilled(connection, parameter)) {
            LOGGER.error("Preconditions for server activation are not fulfilled!");
            return false;
        }
        final ModuleAdminAgent moduleAdminAgent = connection.getBroker().requireSpecialist(ModuleAdminAgent.TYPE);
        Project project = connection.getProjectByName(parameter.getProjectName());
        for (WebAppIdentifier scope : parameter.getScopes()) {
            String scopeName = scope.getScope().name();
            if (!shouldActivateWebServer(project, scopeName, parameter.getServerName(), parameter.isForceActivation())) {
                LOGGER.info("Skip activation for scope '{}'", scopeName);
                continue;
            }
            migrateActiveWebServer(parameter.getServerName(), moduleAdminAgent, project, scope, scopeName);
        }
        return true;
    }

    /**
     * Undeploys a scope from the current web server, sets the new active web server and then deploys the scope
     * on the new active web server.
     * @param scopeName the name of a scope (aka web app name)
     * @param activeServerName should be the name of the new active web server. Should not be empty.
     * @param project given project
     * @param moduleAdminAgent a moduleAdminAgent derived from a connection
     * @param scope web app to be undeployed / deployed
     */
    private void migrateActiveWebServer(String activeServerName, ModuleAdminAgent moduleAdminAgent, Project project,
                                        WebAppIdentifier scope, String scopeName) {
        final String oldActiveServerName = project.getActiveWebServer(scopeName);
        final boolean
            successfullyUndeployedScope =
            performFunctionToActiveWebServer(project, moduleAdminAgent::undeployWebApp, scope, "Scope could not be undeployed from server");
        if (successfullyUndeployedScope) {
            recoverDeploymentForScope(scopeName, oldActiveServerName, project, moduleAdminAgent, scope);
            return;
        }
        try {
            setActiveWebServer(activeServerName, project, scopeName);
        } catch (ExecutionException e) {
            recoverDeploymentForScope(scopeName, oldActiveServerName, project, moduleAdminAgent, scope);
            return;
        }
        final boolean
            successfullyDeployedScope =
            performFunctionToActiveWebServer(project, moduleAdminAgent::deployWebApp, scope, "Scope could not be deployed on server");
        if (successfullyDeployedScope) {
            recoverDeploymentForScope(scopeName, oldActiveServerName, project, moduleAdminAgent, scope);
        }
    }

    /**
     * Performs the same operations as the migrateActiveWebServer method but without fail-safe.
     * This method is handy, if you give the old active server name into the parameters, this method will then
     * try to recreate the state of the scope's active web server configuration before the migration process took place.
     * There is no warranty of the correct execution of the recovery, so the user is advised to check the state of the
     * configuration afterwards.
     *
     * @param scopeName the name of a scope (aka web app name)
     * @param activeServerName should be the name of the original (pre manipulation) active web server. May be empty.
     * @param project given project
     * @param moduleAdminAgent a moduleAdminAgent derived from a connection
     * @param scope web app to be undeployed / deployed
     */
    private void recoverDeploymentForScope(String scopeName, String activeServerName, Project project,
                                           ModuleAdminAgent moduleAdminAgent, WebAppIdentifier scope) {
        LOGGER.warn("The migration from an old web server to a new web server failed. Try to recover state!");
        performFunctionToActiveWebServer(project, moduleAdminAgent::undeployWebApp, scope, "Scope could not be undeployed from server");
        setActiveWebServer(activeServerName, project, scopeName);
        performFunctionToActiveWebServer(project, moduleAdminAgent::deployWebApp, scope, "Scope could not be deployed to server");
        LOGGER.warn("Recovery process has finished! Please check the project web app configuration state for correctness.");
    }

    /**
     * Checks if the given web server should be activated.
     * @param project given project
     * @param scopeName the name of a scope (aka web app name)
     * @param serverName the name of an installed web server (aka web app name)
     * @param forceActivation a flag as indicator if an already activated (none empty) web server should be replaced
     * @return false if the new active web server is already the current web server or there is an active web server but the force flag is not set
     */
    private boolean shouldActivateWebServer(Project project, String scopeName, String serverName, boolean forceActivation) {
        String activeWebServer = project.getActiveWebServer(scopeName);
        if (activeWebServer == null || activeWebServer.isEmpty()) {
            LOGGER.info("Could not find an activated web server for scope '{}'.", scopeName);
            return true;
        }
        if (Objects.equals(activeWebServer, serverName)) {
            LOGGER.info("'{}' is already the activated web server for scope '{}'.", scopeName);
            return false;
        }
        if (!forceActivation) {
            LOGGER.info(
                "'{}' already has an activated web server for scope '{}'. "
                + "Enable 'force activation' flag to overwrite the currently active web server.",
                project.getName(), scopeName);
            return false;
        }
        return true;
    }

    /**
     * Sets the activate web server from a project's scope to a given server name.
     * After this method, the project is unlocked!
     * @param activeWebServer the given server name
     * @param project given project
     * @param scopeName the name of a scope (aka web app name)
     * @throws ExecutionException if the active web server could not be set.
     */
    private void setActiveWebServer(String activeWebServer, Project project, String scopeName) throws ExecutionException {
        LOGGER.debug("Try to set '{}' as active web server.", activeWebServer);
        try {
            project.lock();
            project.setActiveWebServer(scopeName, activeWebServer);
            project.save();
        } catch (LockException e) {
            LOGGER.error("Cannot lock and save project!", e);
            throw new ExecutionException(activeWebServer + " could not be set as active web server for scope '" + scopeName + "'.");
        } finally {
            LOGGER.debug("Unlocking project");
            project.unlock();
        }
    }

    /**
     * deploys/undeploys the scope's corresponding web app to/from the scope's corresponding active web server.
     * @param project used to create the web app id
     * @param function deploy or undeploy function (e.g. {@see de.espirit.firstspirit.agency.ModuleAdminAgent#deployWebApp(WebAppId webAppId)}
     * @param scope web app to be deployed
     * @param errorMsg message to be shown, if function fails to success
     * @return success indicator
     */
    private boolean performFunctionToActiveWebServer(Project project, Function<WebAppId, Boolean> function, WebAppIdentifier scope, String errorMsg) {
        try {
            WebAppId webAppId = scope.createWebAppId(project);
            final boolean successIndicator = function.apply(webAppId);
            if (!successIndicator) {
                LOGGER.error(errorMsg);
                return false;
            }
        } catch (SecurityException e) {
            if (scope.isGlobal()) {
                LOGGER.error("You need to have server admin rights.", e);
            } else {
                LOGGER.error("You need to have project admin rights for project '{}'", project.getName(), e);
            }
            return false;
        }
        return true;
    }

    /**
     * Checks if necessary preconditions for further operations are fulfilled
     * @param connection the connection for the FirstSpirit
     * @param parameters the parameters used for the activation
     * @return only true if every condition is met
     */
    private boolean arePreconditionsFulfilled(Connection connection, ProjectWebServerActivationParameter parameters) {
        if (connection == null || !connection.isConnected()) {
            LOGGER.error("Please provide a connected connection");
            return false;
        }
        final Project[] projects = connection.getProjects();
        if (projects == null || projects.length < 1) {
            LOGGER.error("Could not find any projects on the server.");
            return false;
        }
        if (connection.getProjectByName(parameters.getProjectName()) == null) {
            LOGGER.error("Could not find project with name '" + parameters.getProjectName() + "' on the server.");
            return false;
        }
        if (parameters.getScopes().contains(null)) {
            LOGGER.error("Found null scope in scopes. All scopes must not be null.");
            return false;
        }
        return true;
    }
}
