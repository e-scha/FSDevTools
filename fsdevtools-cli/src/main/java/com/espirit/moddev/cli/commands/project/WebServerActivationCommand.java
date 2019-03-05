package com.espirit.moddev.cli.commands.project;

import com.espirit.moddev.cli.ConnectionBuilder;
import com.espirit.moddev.cli.commands.SimpleCommand;
import com.espirit.moddev.cli.results.SimpleResult;
import com.espirit.moddev.projectservice.projectactivatewebserver.ProjectWebServerActivationParameter;
import com.espirit.moddev.projectservice.projectactivatewebserver.ProjectWebServerActivator;
import com.espirit.moddev.shared.webapp.WebAppIdentifierParser;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import com.github.rvesse.airline.annotations.help.Examples;
import com.github.rvesse.airline.annotations.restrictions.Required;

import de.espirit.common.VisibleForTesting;
import de.espirit.firstspirit.access.Connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(name = "activatewebserver", groupNames = {"project"}, description = "Activates a web server for a number of project's web scopes")
@Examples(
    examples = {"fs-cli project activatewebserver -wpn \"existingProjectName\" -was \"WEBEDIT\" -wsn \"FirstSpirit Jetty\" -fwa"},
    descriptions = {"Undeploys web app scope 'WEBEDIT' from an old web server, activates web server 'FirstSpirit Jetty' and deploys the given web app scope 'WEBEDIT' to the new web server."})

public class WebServerActivationCommand extends SimpleCommand<SimpleResult> {
    protected static final Logger LOGGER = LoggerFactory.getLogger(com.espirit.moddev.cli.commands.project.WebServerActivationCommand.class);

    @Option(type = OptionType.COMMAND, name = {"-wpn", "--webServerActivationProjectName"}, description = "The name of the project for which the web server activation will be performed.")
    @Required
    private String projectName;
    @Option(type = OptionType.COMMAND, name = {"-wsn", "--webServerName"}, description = "The name of the web server which should be activated.")
    @Required
    private String webServerName;
    @Option(type = OptionType.COMMAND, name = {"-was", "--webAppScopes"}, description = "Define a set of webapp scopes of the given project - comma-separated values from the FirstSpirit WebScope enum."
                                                                                        + " The FS WebScope enum contains the following keys:\n"
                                                                                        + "'GLOBAL'\n"
                                                                                        + "'PREVIEW'\n"
                                                                                        + "'STAGING'\n"
                                                                                        + "'WEBEDIT'\n"
                                                                                        + " For global webapps, use 'global(WebAppId)'.")
    @Required
    private String webAppScopes;
    @Option(type = OptionType.COMMAND, name = {"-fwa", "--forceWebServerActivation"}, description = "Whether to force web server activation if there already is an active web server. Default is false.")
    private boolean forceWebServerActivation;

    @Override
    public SimpleResult call() {
        SimpleResult failure = getQuickFail();
        if (failure != null) {
            return failure;
        }
        try(final Connection connection = create()) {
            connection.connect();
            boolean activated = new ProjectWebServerActivator().activateWebServer(connection, getParameters());
            return new SimpleResult<>(activated ? true : new IllegalStateException("Web server activation failed."));

        } catch (final Exception e) {
            return new SimpleResult<>(e);
        }
    }

    private SimpleResult getQuickFail() {
        if (projectName == null || projectName.isEmpty()) {
            return new SimpleResult<>(new IllegalArgumentException("Missing parameter for project name"));
        }
        if (webServerName == null || webServerName.isEmpty()) {
            return new SimpleResult<>(new IllegalArgumentException("Missing parameter for web server name"));
        }
        if (webAppScopes == null || webAppScopes.isEmpty()) {
            return new SimpleResult<>(new IllegalArgumentException("Missing parameter for web app scopes"));
        }
        return null;
    }

    private ProjectWebServerActivationParameter getParameters() {
        return ProjectWebServerActivationParameter.builder()
            .withForceActivation(forceWebServerActivation)
            .atProjectName(projectName)
            .withServerName(webServerName)
            .forScopes(new WebAppIdentifierParser().extractWebScopes(webAppScopes))
            .build();
    }

    protected Connection create() {
        return ConnectionBuilder.with(this).build();
    }

    @Override
    public boolean needsContext() {
        return false;
    }
}
