package org.apache.maven.scm.provider.clearcase.command.tag;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.scm.CommandParameter;
import org.apache.maven.scm.CommandParameters;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.ScmTestCase;
import org.apache.maven.scm.log.DefaultLog;
import org.apache.maven.scm.provider.ScmProviderRepositoryStub;
import org.apache.maven.scm.provider.clearcase.util.CommandLineExecutor;
import org.apache.maven.scm.providers.clearcase.settings.Settings;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * @author <a href="mailto:wim.deblauwe@gmail.com">Wim Deblauwe</a>
 */
public class ClearCaseTagCommandTest
    extends ScmTestCase
{
    public void testCommand()
        throws Exception
    {
        ScmFileSet scmFileSet = new ScmFileSet( getWorkingDirectory(), new File( "test.java" ) );
        Commandline commandLine = ClearCaseTagCommand.createCommandLine( scmFileSet, "TEST_LABEL_V1.0" );
        assertCommandLine( "cleartool mklabel TEST_LABEL_V1.0 test.java", getWorkingDirectory(), commandLine );
    }
    
    public void testLabelCommands() 
    	throws Exception
	{
    	// just record the commands issued
		final List<Commandline> commandLines = new ArrayList<Commandline>();
		CommandLineExecutor recorder = new CommandLineExecutor() {

			public int executeCommandLine(Commandline cl,
					StreamConsumer systemOut, StreamConsumer systemErr)
					throws CommandLineException {
				commandLines.add(cl);
				return 0;
			}
			
		};

		Settings settings = new Settings();
		settings.setLabelToVOBRoot(false);
		settings.setLabelEntireVOB(false);
		
		ClearCaseTagCommand command = new ClearCaseTagCommand();
		command.setCommandLineExecutor( recorder );
		command.setLogger( new DefaultLog() );
		command.setSettings( settings );
		
		CommandParameters parameters = new CommandParameters();
		parameters.setString( CommandParameter.TAG_NAME,  "TEST_LABEL_V1.0"  );
		
        ScmFileSet scmFileSet = new ScmFileSet( getWorkingDirectory(), new File( "test.java" ) );
		command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
		
		assertEquals("Expected number of commands executed", 2, commandLines.size());
        assertCommandLine( "cleartool mklbtype -nc TEST_LABEL_V1.0", getWorkingDirectory(), commandLines.get(0) );
        assertCommandLine( "cleartool mklabel TEST_LABEL_V1.0 test.java", getWorkingDirectory(), commandLines.get(1) );
   	}
    
    /*
     * Test that we label all directories up to the VOB root dir if the Settings is set to do so
     */
    public void testLabelCommandsWhenLabellingToRoot() 
        	throws Exception
	{
    	// record the commands issued and fail if attempting to work in directory below baseDir (3 dirs deep)
		final List<Commandline> mklbtypeCommandLines = new ArrayList<Commandline>();
		final List<Commandline> mklabelCommandLines = new ArrayList<Commandline>();
		CommandLineExecutor recorder = new CommandLineExecutor() {

			public int executeCommandLine(Commandline cl,
					StreamConsumer systemOut, StreamConsumer systemErr)
					throws CommandLineException {
				if ( cl.toString().contains("mklbtype")) {
					mklbtypeCommandLines.add(cl);
				} else if (cl.toString().contains("mklabel") ) {
					mklabelCommandLines.add(cl);
				}
				int exitCode = -1;
				File currDir = cl.getWorkingDirectory();
				while ( currDir.getParentFile() != null ) {
					if ( currDir.getAbsolutePath().equals( getBasedir() ) ) {
						exitCode = 0;
						break;
					}
					currDir = currDir.getParentFile();
				}
				
				return exitCode;
			}
		};

		Settings settings = new Settings();
		settings.setLabelToVOBRoot(true);
		settings.setLabelEntireVOB(false);
		
		ClearCaseTagCommand command = new ClearCaseTagCommand();
		command.setCommandLineExecutor( recorder );
		command.setLogger( new DefaultLog() );
		command.setSettings(settings);
		
		CommandParameters parameters = new CommandParameters();
		parameters.setString( CommandParameter.TAG_NAME,  "TEST_LABEL_V1.0"  );
		
        ScmFileSet scmFileSet = new ScmFileSet( getWorkingDirectory(), new File( "test.java" ) );
		command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
		
		assertEquals("Expected number of commands executed", 1, mklbtypeCommandLines.size());
        assertCommandLine( "cleartool mklbtype -nc TEST_LABEL_V1.0", getWorkingDirectory(), mklbtypeCommandLines.get(0) );

		assertEquals("Expected number of commands executed", 5, mklabelCommandLines.size());
        assertCommandLine( "cleartool mklabel TEST_LABEL_V1.0 test.java", getWorkingDirectory(), mklabelCommandLines.get(0) );

        File parentFile = getWorkingDirectory();
        for ( int i = 1; i < 5; i++) {
            assertCommandLine( "cleartool mklabel TEST_LABEL_V1.0 .", parentFile, mklabelCommandLines.get(i) );
            assertEquals( "Label parent directory", parentFile, mklabelCommandLines.get(i).getWorkingDirectory() );
            parentFile = parentFile.getParentFile();
        }

        assertEquals( "Last directory labelled was root of VOB", new File(getBasedir()), mklabelCommandLines.get(4).getWorkingDirectory() );
   	}
        
    /*
     * Test that we label entire VOB
     */
    public void testLabelCommandsWhenLabellingEntireVOB() 
        	throws Exception
	{
    	// record the commands issued and fail if attempting to work in directory below baseDir (3 dirs deep)
		final List<Commandline> mklbtypeCommandLines = new ArrayList<Commandline>();
		final List<Commandline> mklabelCommandLines = new ArrayList<Commandline>();
		CommandLineExecutor recorder = new CommandLineExecutor() {

			public int executeCommandLine(Commandline cl,
					StreamConsumer systemOut, StreamConsumer systemErr)
					throws CommandLineException {
				if ( cl.toString().contains("mklbtype")) {
					mklbtypeCommandLines.add(cl);
				} else if (cl.toString().contains("mklabel") ) {
					mklabelCommandLines.add(cl);
				}
				int exitCode = -1;
				File currDir = cl.getWorkingDirectory();
				while ( currDir.getParentFile() != null ) {
					if ( currDir.getAbsolutePath().equals( getBasedir() ) ) {
						exitCode = 0;
						break;
					}
					currDir = currDir.getParentFile();
				}
				
				return exitCode;
			}
		};

		Settings settings = new Settings();
		settings.setLabelToVOBRoot(false);
		settings.setLabelEntireVOB(true);
		
		ClearCaseTagCommand command = new ClearCaseTagCommand();
		command.setCommandLineExecutor( recorder );
		command.setLogger( new DefaultLog() );
		command.setSettings(settings);
		
		CommandParameters parameters = new CommandParameters();
		parameters.setString( CommandParameter.TAG_NAME,  "TEST_LABEL_V1.0"  );
		
        ScmFileSet scmFileSet = new ScmFileSet( getWorkingDirectory() );
		command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
		
		assertEquals("Expected number of commands executed", 1, mklbtypeCommandLines.size());
        assertCommandLine( "cleartool mklbtype -nc TEST_LABEL_V1.0", getWorkingDirectory(), mklbtypeCommandLines.get(0) );

        File vobRoot = new File(getBasedir());
        assertCommandLine( "cleartool mklabel -recurse TEST_LABEL_V1.0 .", vobRoot, mklabelCommandLines.get(0) );
        assertEquals( "Label current directory", vobRoot, mklabelCommandLines.get(0).getWorkingDirectory() );

   	}

    /*
     * Should not both label to root and label entire VOB
     */
    public void testErrorWhenSettingsConflict() 
        	throws Exception
	{
		Settings settings = new Settings();
		settings.setLabelToVOBRoot(true);
		settings.setLabelEntireVOB(true);

		ClearCaseTagCommand command = new ClearCaseTagCommand();
		command.setLogger( new DefaultLog() );
		command.setSettings(settings);
		
        ScmFileSet scmFileSet = new ScmFileSet( getWorkingDirectory() );
		CommandParameters parameters = new CommandParameters();
		parameters.setString( CommandParameter.TAG_NAME,  "TEST_LABEL_V1.0"  );
		
		try {
			command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
			fail("Exception should be thrown if both settings are true");
		} catch (ScmException e) {
			assertNotNull("Cause is nested", e.getCause());
			assertEquals("Cannot have both labelEntireVOB=true and labelToVOBRoot=true.", e.getCause().getMessage());
		}
	}
    
    /*
     * Label entire VOB does not make sense if we are labeling a specific fileset
     */
    public void testErrorLabellingSpecificElementsButSettingsRequestLabellingEntireVob() 
        	throws Exception
	{
		Settings settings = new Settings();
		settings.setLabelToVOBRoot(false);
		settings.setLabelEntireVOB(true);

		ClearCaseTagCommand command = new ClearCaseTagCommand();
		command.setLogger( new DefaultLog() );
		command.setSettings(settings);
		
        ScmFileSet scmFileSet = new ScmFileSet( getWorkingDirectory(), new File( "test.java" )  );
		CommandParameters parameters = new CommandParameters();
		parameters.setString( CommandParameter.TAG_NAME,  "TEST_LABEL_V1.0"  );
		
		try {
			command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
			fail("Exception should be thrown if attempt to label specific element");
		} catch (ScmException e) {
			assertNotNull("Cause is nested", e.getCause());
			assertEquals("Cannot label specific files when labelEntireVOB=true.", e.getCause().getMessage());
		}
	}

    /*
     * Test behavior when label fails to be applied
     */
    public void testLabelOnLockedObjects() 
        	throws Exception
	{
    	// Reports error messages when you try to label and exits with failure code
    	final List<String> errorMessages = new ArrayList<String>();
		CommandLineExecutor executor = new CommandLineExecutor() {

			public int executeCommandLine(Commandline cl,
					StreamConsumer systemOut, StreamConsumer systemErr)
					throws CommandLineException {
				if ( cl.toString().contains("mklabel") ) {
					for (String msg : errorMessages ) {
						systemErr.consumeLine(msg);
					}
					return -1;
				} else {
					return 0;
				}
			}
		};
		errorMessages.add("cleartool: Error: Lock on global branch type 'br1' (in VOB\\Admin_vob) prevents operation 'make label'.");
		errorMessages.add("cleartool: Error: Object locked except for users: sue_test.");
		errorMessages.add("cleartool: Error: Trouble applying label to '.\\Folder1\\t1.txt'.");

		Settings settings = new Settings();
		settings.setIgnoreMklabelFailureOnLockedObjects(false);
			
		ClearCaseTagCommand command = new ClearCaseTagCommand();
		command.setCommandLineExecutor( executor );
		command.setLogger( new DefaultLog() );
		command.setSettings(settings);
		
		CommandParameters parameters = new CommandParameters();
		parameters.setString( CommandParameter.TAG_NAME,  "TEST_LABEL_V1.0"  );
		
        ScmFileSet scmFileSet = new ScmFileSet( getWorkingDirectory() );
		ScmResult result = command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
		
		// normal behavior is to fail
		assertFalse("Labelling failed", result.isSuccess());
		
		// set the setting to ignore these errors
		settings.setIgnoreMklabelFailureOnLockedObjects(true);
		result = command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
		
		assertTrue("Labelling succeeded when ignoreMklabelFailureOnLockedObjects = true", result.isSuccess());

		// add an error not related to locked objects
		errorMessages.add("cleartool: Error: Version label of type 'LB1' already on element.");
		result = command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
		
		assertFalse("Labelling succeeded when ignoreMklabelFailureOnLockedObjects = true", result.isSuccess());
	}

}
