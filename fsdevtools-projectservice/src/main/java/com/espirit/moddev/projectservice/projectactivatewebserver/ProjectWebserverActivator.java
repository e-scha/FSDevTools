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

import static de.espirit.firstspirit.module.WebEnvironment.*;

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
        return performWebServerActivation(connection, parameter);
    }

    private boolean performWebServerActivation(Connection connection, ProjectWebServerActivationParameter parameter) {
        final ModuleAdminAgent moduleAdminAgent = connection.getBroker().requireSpecialist(ModuleAdminAgent.TYPE);
        Project project = connection.getProjectByName(parameter.getProjectName());
        for (WebAppIdentifier scope : parameter.getScopes()) {
            String scopeName = scope.getScope().name();
            if (!shouldActivateWebServer(project, scopeName, parameter.getServerName(), parameter.isForceActivation())) {
                LOGGER.info("Skip activation for scope {}", scopeName);
                continue;
            }
            String oldServerName = project.getActiveWebServer(scopeName);
            setActiveWebServer(parameter.getServerName(), project, scopeName);
            deployWebAppToActiveWebServer(project, moduleAdminAgent, scope);
            undeployWebAppFromInactiveWebServer();
        }
        return true;
    }

    private boolean shouldActivateWebServer(Project project, String scopeName, String serverName, boolean forceActivation) {
        String activeWebServer = project.getActiveWebServer(scopeName);
        if (activeWebServer == null || activeWebServer.isEmpty()) {
            LOGGER.info("Could not find an activated web server for scope {}.", scopeName);
            return true;
        }
        if (Objects.equals(activeWebServer, serverName)) {
            LOGGER.info("'{}' is already the activated web server for scope {}.", scopeName);
            return false;
        }
        if (!forceActivation) {
            LOGGER.info(
                "'{}' already has an activated web server for scope {}. "
                + "Enable 'force activation' flag to overwrite the currently active web server.",
                project.getName(), scopeName);
            return false;
        }
        return true;
    }

    private void setActiveWebServer(String activeWebServer, Project project, String scopeName) throws ExecutionException {
        LOGGER.debug("Try setting {} as active web server.", activeWebServer);
        try {
            project.lock();
            project.setActiveWebServer(scopeName, activeWebServer);
            project.save();
        } catch (LockException e) {
            LOGGER.error("Cannot lock and save project!", e);
            throw new ExecutionException(activeWebServer + " could not be set as active web server for scope '" + scopeName + "'");
        } finally {
            LOGGER.debug("Unlocking project");
            project.unlock();
        }
    }

    private void deployWebAppToActiveWebServer(Project project, ModuleAdminAgent moduleAdminAgent, WebAppIdentifier scope) {
        if (scope.isGlobal()) {
            WebAppId webAppId = scope.createWebAppId(project);
            moduleAdminAgent.deployWebApp(webAppId);
        } else {

        }
    }

    private void undeployWebAppFromInactiveWebServer() {
    }

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
