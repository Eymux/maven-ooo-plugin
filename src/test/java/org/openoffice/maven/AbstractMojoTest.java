package org.openoffice.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

public abstract class AbstractMojoTest extends AbstractMojoTestCase {

    protected AbstractMojo mojo;
    protected final File testPom = new File(getBasedir(), "src/main/resources/archetype-resources/pom.xml");
    protected final File outputDirectory = new File(getTargetDir(), "ooo");

    /**
     * Set up the mojo.
     * 
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
        AbstractTest.setUpEnvironment();
    }
    
    protected void setUpMojo() throws IllegalAccessException {
        setVariableValueToObject(mojo, "ooo", Environment.getOfficeHome());
        setVariableValueToObject(mojo, "sdk", Environment.getOoSdkHome());
        setUpResources();
        setUpTargetDir();
    }

    private void setUpTargetDir() throws IllegalAccessException {
        File buildDir = this.getTargetDir();
        setVariableValueToObject(mojo, "directory", buildDir);
        setVariableValueToObject(mojo, "outputDirectory", outputDirectory);
    }
    
    /**
     * Gets the target dir.
     *
     * @return the target dir
     */
    protected File getTargetDir() {
        return new File(getBasedir(), "target");
    }

    private void setUpResources() throws IllegalAccessException {
        List<Resource> resources = new ArrayList<Resource>();
        Resource rsc = new Resource();
        rsc.setDirectory("src/test/resources");
        resources.add(rsc);
        setVariableValueToObject(mojo, "resources", resources);
    }

}