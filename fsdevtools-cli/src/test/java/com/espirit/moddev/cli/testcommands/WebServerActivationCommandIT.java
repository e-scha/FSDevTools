package com.espirit.moddev.cli.testcommands;

import com.espirit.moddev.IntegrationTest;
import com.espirit.moddev.cli.commands.project.WebServerActivationCommand;
import com.espirit.moddev.cli.results.SimpleResult;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.espirit.moddev.IntegrationTest.PROJECT_NAME;

@Category(IntegrationTest.class)
public class WebServerActivationCommandIT extends AbstractIntegrationTest {
    @Test
    public void parameterLessCommandDoesntExport() throws Exception {
        WebServerActivationCommand command = new WebServerActivationCommand();
        command.setProject(PROJECT_NAME);
        initContextWithDefaultConfiguration(command);

        SimpleResult result = command.call();

        Assert.assertTrue("Exporting with an empty identifier list is permitted", result.isError());
        Assert.assertTrue(result.getError() instanceof IllegalArgumentException);
    }
}
