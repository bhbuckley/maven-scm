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
    		CommandLineExecutionRecorder recorder = new CommandLineExecutionRecorder();
    		
    		ClearCaseTagCommand command = new ClearCaseTagCommand();
    		command.setCommandLineExecutor( recorder );
    		command.setLogger( new DefaultLog() );
    		
    		CommandParameters parameters = new CommandParameters();
    		parameters.setString( CommandParameter.TAG_NAME,  "TEST_LABEL_V1.0"  );
    		
            ScmFileSet scmFileSet = new ScmFileSet( getWorkingDirectory(), new File( "test.java" ) );
			command.execute( new ScmProviderRepositoryStub(), scmFileSet, parameters);
			
			List<Commandline> commandLines = recorder.getCommandLines();
			assertEquals("Expected number of commands executed", 2, commandLines.size());
	        assertCommandLine( "cleartool mklbtype -nc TEST_LABEL_V1.0", getWorkingDirectory(), commandLines.get(0) );
	        assertCommandLine( "cleartool mklabel TEST_LABEL_V1.0 test.java", getWorkingDirectory(), commandLines.get(1) );
    	}
    
    /*
     * Implementation of CommandLineExecutor that just records what commands were requested
     */
    class CommandLineExecutionRecorder implements CommandLineExecutor {

    	private List<Commandline> commandLines = new ArrayList<Commandline>();
    	private int exitCode = 0;
    	
		public int executeCommandLine(Commandline cl, StreamConsumer systemOut,
				StreamConsumer systemErr) throws CommandLineException {
			commandLines.add( cl );
			return exitCode;
		}
    	
		public void setExitCode(int exitCode) {
			this.exitCode = exitCode;
		}
		
		public List<Commandline> getCommandLines() {
			return commandLines;
		}
    }
}
