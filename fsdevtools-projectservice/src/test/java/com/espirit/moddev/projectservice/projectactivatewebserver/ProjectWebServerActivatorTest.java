package com.espirit.moddev.projectservice.projectactivatewebserver;

import com.espirit.moddev.shared.webapp.WebAppIdentifier;

import de.espirit.firstspirit.access.Connection;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProjectWebServerActivatorTest {
    private ProjectWebServerActivator testling;

    @Before
    public void setUp() {
        testling = new ProjectWebServerActivator();
    }

    @Test
    public void activateWebServer() {
        List<WebAppIdentifier> scopes = new ArrayList<>();
        scopes.add(WebAppIdentifier.WEBEDIT);
        ProjectWebServerActivationParameterBuilder builder = new ProjectWebServerActivationParameterBuilder();
        builder.withServerName("serverName")
            .atProjectName("dummyProjectName")
            .forScopes(scopes)
            .withForceActivation(true);
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.isConnected()).thenReturn(false);

        testling.activateWebServer(connectionMock, builder.build());
    }
}