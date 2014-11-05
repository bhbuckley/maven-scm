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
import org.apache.maven.scm.ScmFileSet;
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
		
		ClearCaseTagCommand command = new ClearCaseTagCommand();
		command.setCommandLineExecutor( recorder );
		command.setLogger( new DefaultLog() );
		command.setSettings( new Settings() );
		
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
		final List<Commandline> commandLines = new ArrayList<Commandline>();
		CommandLineExecutor recorder = new CommandLineExecutor() {

			public int executeCommandLine(Commandline cl,
					StreamConsumer systemOut, StreamConsumer systemErr)
					throws CommandLineException {
				commandLines.add(cl);
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
		
		ClearCaseTagCommand command = new ClearCaseTagCommand();
		command.setCommandLineExecutor( recorder );
		command.setLogger( new DefaultLog() );
		command.setSettings(settings);
		
		CommandParameters parameters = new CommandParameters();
		parameters.setString( CommandParameter.TAG_NAME,  "TEST_LABEL_V1.0"  );
		
        ScmFileSet scmFileSet = new ScmFileSet( getWorkingDirectory(), new File( "test.java" ) );
		command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
		
		assertEquals("Expected number of commands executed", 7, commandLines.size());
        assertCommandLine( "cleartool mklbtype -nc TEST_LABEL_V1.0", getWorkingDirectory(), commandLines.get(0) );
        assertCommandLine( "cleartool mklabel TEST_LABEL_V1.0 test.java", getWorkingDirectory(), commandLines.get(1) );
        assertCommandLine( "cleartool mklabel TEST_LABEL_V1.0 .", getWorkingDirectory(), commandLines.get(2) );
        assertEquals( "Label current directory", getWorkingDirectory(), commandLines.get(2).getWorkingDirectory() );

        File parentFile = getWorkingDirectory().getParentFile();
        for ( int i = 3; i < 7; i++) {
            assertCommandLine( "cleartool mklabel TEST_LABEL_V1.0 .", parentFile, commandLines.get(i) );
            assertEquals( "Label parent directory", parentFile, commandLines.get(i).getWorkingDirectory() );
            parentFile = parentFile.getParentFile();
        }
   	}

}
