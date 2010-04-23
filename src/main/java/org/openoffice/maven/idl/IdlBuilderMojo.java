/*************************************************************************
 *
 * $RCSfile: IdlBuilderMojo.java,v $
 *
 * $Revision: 1.1 $
 *
 * last change: $Author: cedricbosdo $ $Date: 2007/10/08 18:35:15 $
 *
 * The Contents of this file are made available subject to the terms of
 * either of the GNU Lesser General Public License Version 2.1
 *
 * Sun Microsystems Inc., October, 2000
 *
 *
 * GNU Lesser General Public License Version 2.1
 * =============================================
 * Copyright 2000 by Sun Microsystems, Inc.
 * 901 San Antonio Road, Palo Alto, CA 94303, USA
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 2.1, as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 * 
 * The Initial Developer of the Original Code is: Sun Microsystems, Inc..
 *
 * Copyright: 2002 by Sun Microsystems, Inc.
 *
 * All Rights Reserved.
 *
 * Contributor(s): Cedric Bosdonnat
 *
 *
 ************************************************************************/
package org.openoffice.maven.idl;

import java.io.File;
import java.io.FilenameFilter;
import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.openoffice.maven.ConfigurationManager;
import org.openoffice.maven.utils.ErrorReader;
import org.openoffice.maven.utils.VisitableFile;

/**
 * Runs the OOo SDK tools to generate the classes file from the IDL files.
 * 
 * @goal build-idl
 * @phase generate-sources
 * 
 * @author Cedric Bosdonnat
 */
public class IdlBuilderMojo extends AbstractMojo {

    private static final String IDENTIFIER_REGEX = "[_a-zA-Z0-9]+";
    
    /**
     * This is where build results go.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    private File directory;

    /**
     * This is where the resources are located.
     *
     * @parameter expression="${project.resources}"
     * @required
     * @readonly
     */
    private List<Resource> resources;

    /**
     * This is where compiled classes go.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     * @readonly
     */
    private File outputDirectory;

    /**
     * OOo instance to build the extension against.
     * 
     * @parameter
     * @required
     */
    private File ooo;

    /**
     * OOo SDK installation where the build tools are located.
     * 
     * @parameter
     * @required
     */
    private File sdk;

    /**
     * Main method of the idl builder Mojo.
     * 
     * <p>This method runs the following tools:
     *   <ol>
     *     <li><code>idlc</code></li>
     *     <li><code>regmerge</code></li>
     *     <li><code>javamaker</code></li>
     *   </ol>
     * </p>
     * 
     * @throws MojoExecutionException is never thrown by this 
     *             implementation.
     * @throws MojoFailureException is thrown if one of the tools
     *             execution fails.
     */
    public void execute() throws MojoExecutionException, 
                                 MojoFailureException {

        try {
            ConfigurationManager.setOOo(ooo);
            getLog().info("OpenOffice.org used: " + 
                    ooo.getAbsolutePath());

            ConfigurationManager.setSdk(sdk);
            getLog().info("OpenOffice.org SDK used: " + 
                    sdk.getAbsolutePath());

            ConfigurationManager.setOutput(directory);
            ConfigurationManager.setClassesOutput(outputDirectory);
            ConfigurationManager.setResources(resources);
            
            // Check if the IDL folder is present
            File idlDir = ConfigurationManager.getIdlDir();
            if (idlDir == null) {
                throw new MojoFailureException(
                    "No IDL folder found among in the resources");
            }
            
            getLog().info("IDL folder used: " + idlDir.getPath());

            getLog().info("Building IDL files");
            // Build each IDL file
            File idl = ConfigurationManager.getIdlDir();
            VisitableFile idlSources = new VisitableFile(idl.getPath());
            IdlcVisitor idlVisitor = new IdlcVisitor();
            idlSources.accept(idlVisitor);
            
            // Continue only if there were idl files to build
            if (idlVisitor.hasBuildIdlFile()) {

                getLog().info("Merging into types.rdb file");
                // Merge the URD files into a types.rdb file
                VisitableFile urdFiles = new VisitableFile(
                       ConfigurationManager.getUrdDir());
                urdFiles.accept(new RegmergeVisitor());

                getLog().info("Generating classes from the types.rdb file");
                // Run javamaker against the types.rdb file
                generatesClasses();
            } else {
                getLog().warn("No idl file to build");
            }
            
        } catch (Exception e) {
            getLog().error("Error during idl-build", e);
            throw new MojoFailureException("Please check the above errors");
        }
    }

    /**
     * Generates the java classes from the project <code>types.rdb</code>.
     * 
     * @throws Exception if anything wrong happens
     */
    private void generatesClasses() throws Exception {
        
        String typesFile = ConfigurationManager.getTypesFile();
        
        if (!new File(typesFile).exists()) {
            throw new Exception(
                    "No types.rdb file build: check previous errors");
        }

        // Compute the command
        String commandPattern = "javamaker -T{0}.* -nD -Gc -BUCR -O " +
                "\"{1}\" \"{2}\" -X\"{3}\"";

        String classesDir = ConfigurationManager.getClassesOutput().
            getPath();
        String oooTypesFile = ConfigurationManager.getOOoTypesFile();

        // Guess the root module
        String rootModule = guessRootModule();

        String[] args = {
            rootModule, 
            classesDir, 
            typesFile, 
            oooTypesFile
        };
        String command = MessageFormat.format(commandPattern, (Object[])args);

        getLog().info("Running command: " + command);
        
        // Run the javamaker command
        Process process = ConfigurationManager.runTool(command);
        ErrorReader.readErrors(process.getErrorStream());
    }

    /**
     * Guess the root module of the OOo extension API.
     * 
     * <p>The folders containing the IDL files has to follow the
     * same hierarchy than the IDL modules declared in the IDL files.</p>
     * 
     * <p>The root module is the path of IDL folders which are the only
     * children of their parent. A valid IDL folder respects the 
     * regular expression defined by {@link #IDENTIFIER_REGEX}.</p> 
     * 
     * @return the guessed module or an empty string if it can't be guessed.
     */
    private String guessRootModule() {
        
        File idlDir = ConfigurationManager.getIdlDir();
        
        int childCount = 1;
        File currentFile = idlDir;
        
        while (childCount == 1) {
            
            // List only the valid children
            String[] children = currentFile.list(
                    new FilenameFilter() {
                
                        public boolean accept(File pDir, String pName) {
                            return Pattern.matches(IDENTIFIER_REGEX, pName);
                        }

                    });
            
            childCount = children.length;
            if (childCount == 1) {
                currentFile = new File(currentFile, children[0]);
            }
        }
        
        String rootModule = "";
        
        String modulePath = currentFile.getPath().substring(
                idlDir.getPath().length() + 1);
        String fileSep = System.getProperty("file.separator");
        rootModule = modulePath.replaceAll(fileSep, ".");
        
        return rootModule;
    }
}
