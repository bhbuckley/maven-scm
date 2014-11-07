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
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.ScmTagParameters;
import org.apache.maven.scm.command.tag.AbstractTagCommand;
import org.apache.maven.scm.command.tag.TagScmResult;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.provider.clearcase.command.ClearCaseCommand;
import org.apache.maven.scm.provider.clearcase.util.CommandLineExecutor;
import org.apache.maven.scm.providers.clearcase.settings.Settings;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * @author <a href="mailto:wim.deblauwe@gmail.com">Wim Deblauwe</a>
 * @author Olivier Lamy
 *
 */
public class ClearCaseTagCommand
    extends AbstractTagCommand
    implements ClearCaseCommand
{
    private Settings settings = null;
    private CommandLineExecutor commandLineExecutor = null;
    
    protected ScmResult executeTagCommand( ScmProviderRepository scmProviderRepository, ScmFileSet fileSet, String tag,
                                           String message )
        throws ScmException
    {
        return executeTagCommand( scmProviderRepository, fileSet, tag, new ScmTagParameters( message ) );
    }
    
    /** {@inheritDoc} */
    protected ScmResult executeTagCommand( ScmProviderRepository scmProviderRepository, ScmFileSet fileSet, String tag,
                                           ScmTagParameters scmTagParameters )
        throws ScmException
    {
    	if ( isLabelEntireVOB() && isLabelToVOBRoot() ) {
            throw new ScmException( "Cannot have both labelEntireVOB=true and labelToVOBRoot=true." );
    	}
    	
    	if ( isLabelEntireVOB() && ! fileSet.getFileList().isEmpty() ) {
    		throw new ScmException( "Cannot label specific files when labelEntireVOB=true." );
    	}
    	
        if ( getLogger().isDebugEnabled() )
        {
            getLogger().debug( "executing tag command..." );
        }
        
        StringBuffer executedCommands = new StringBuffer();

        ClearCaseTagConsumer consumer = new ClearCaseTagConsumer( getLogger() );

        CommandLineUtils.StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();

        int exitCode;

        try
        {
            if ( getLogger().isDebugEnabled() )
            {
                getLogger().debug( "Creating label: " + tag );
            }
            Commandline newLabelCommandLine = createNewLabelCommandLine( fileSet, tag );
            if ( getLogger().isDebugEnabled() )
            {
                getLogger().debug(
                                   "Executing: " + newLabelCommandLine.getWorkingDirectory().getAbsolutePath()
                                       + ">>" + newLabelCommandLine.toString() );
            }
            exitCode = commandLineExecutor.executeCommandLine( newLabelCommandLine,
                                                            new CommandLineUtils.StringStreamConsumer(), stderr );

            if ( exitCode == 0 )
            {
            	if ( isLabelEntireVOB() ) {
            		fileSet = new ScmFileSet( findVOBRoot(fileSet.getBasedir()) );
            	}
                Commandline cl = createCommandLine( fileSet, tag );
                executedCommands.append( cl.toString() );
                getLogger().debug( "Executing: " + cl.getWorkingDirectory().getAbsolutePath() + ">>" + cl.toString() );
                exitCode = commandLineExecutor.executeCommandLine( cl, consumer, stderr );
                
                if ( exitCode != 0 && isIgnoreMklabelFailureOnLockedObjects()) 
                {
                	// TODO: is this really the best strategy, or is it better to review the use of an empty fileset and -recurse
                	// and perhaps use -find ... -exec cleartool pattern instead
                	String[] output = stderr.getOutput().split(System.getProperty( "line.separator" ));
                	
                	int otherErrCtr = 0;
                	for ( int i=0; i<output.length; i++) {
                		// TODO: use ClearCaseUtil.getLocalizedResource to look for localized messages
                		Pattern p = Pattern.compile("(.*Error: Lock on .* prevents operation 'make label'.)|(.*Error: Object locked except for.*)|(.*Error: Trouble applying label to.*)");
                		if ( ! p.matcher(output[i]).matches() )
                		{
                			otherErrCtr++;
                		}
                	}
                	
                	if ( otherErrCtr == 0 )
                	{
                		exitCode = 0;
                	}
                }
                
                if ( exitCode == 0 && isLabelToVOBRoot() )
                {
                    getLogger().debug( "Labelling to VOB Root from : " + cl.getWorkingDirectory().getAbsolutePath() );
                	
                    File currDir = cl.getWorkingDirectory();
                    File vobRootDir = findVOBRoot(currDir);
                    
                    while ( currDir.getParentFile() != null && ! currDir.equals(vobRootDir.getParentFile()) ) {
                    	ScmFileSet fs = new ScmFileSet( currDir, new File(".") );
                        Commandline dirCl = createCommandLine( fs, tag );
                        getLogger().debug(
                                "Executing: " + dirCl.getWorkingDirectory().getAbsolutePath()
                                    + ">>" + dirCl.toString() );

                        commandLineExecutor.executeCommandLine( dirCl, consumer, stderr );
                    	currDir = currDir.getParentFile();
                    }
                }
            }
        }
        catch ( CommandLineException ex )
        {
            throw new ScmException( "Error while executing clearcase command.", ex );
        }

        if ( exitCode != 0 )
        {
            return new TagScmResult( executedCommands.toString(), "The cleartool command failed.", stderr.getOutput(), false );
        }

        return new TagScmResult( executedCommands.toString(), consumer.getTaggedFiles() );
    }
    
    private File findVOBRoot(File workingDir) throws CommandLineException {
    	File currDir = workingDir;
        int dirLabelExitCode = 0;
        // recursively ls each parent directory until we have a failure (presumably from not being in the VOB)
        // root of the VOB will be considered to be the last directory that the ls returned exit code 0 in
        // TODO: look for more elegant approach
        while ( currDir.getParentFile() != null && dirLabelExitCode == 0) {
            Commandline command = new Commandline();
            command.setWorkingDirectory( currDir.getParentFile() );

            command.setExecutable( "cleartool" );
            command.createArg().setValue( "ls ." );

            dirLabelExitCode = commandLineExecutor.executeCommandLine( command, new CommandLineUtils.StringStreamConsumer(), new CommandLineUtils.StringStreamConsumer() );
        	if ( dirLabelExitCode == 0 ) 
        	{
            	currDir = currDir.getParentFile();
        	} 
        }
        return currDir;
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public static Commandline createCommandLine( ScmFileSet scmFileSet, String tag )
    {
        Commandline command = new Commandline();

        File workingDirectory = scmFileSet.getBasedir();

        command.setWorkingDirectory( workingDirectory.getAbsolutePath() );

        command.setExecutable( "cleartool" );

        command.createArg().setValue( "mklabel" );
        List<File> files = scmFileSet.getFileList();
        if ( files.isEmpty() )
        {
            command.createArg().setValue( "-recurse" );
        }
        command.createArg().setValue( tag );

        if ( files.size() > 0 )
        {
            for ( File file : files )
            {
                command.createArg().setValue( file.getName() );
            }
        }
        else
        {
            command.createArg().setValue( "." );
        }

        return command;
    }

    private static Commandline createNewLabelCommandLine( ScmFileSet scmFileSet, String tag )
    {
        Commandline command = new Commandline();

        File workingDirectory = scmFileSet.getBasedir();

        command.setWorkingDirectory( workingDirectory.getAbsolutePath() );

        command.setExecutable( "cleartool" );

        command.createArg().setValue( "mklbtype" );
        command.createArg().setValue( "-nc" );
        command.createArg().setValue( tag );

        return command;
    }

    /**
     * @return the value of the setting property 'labelToVOBRoot'
     */
    protected boolean isLabelToVOBRoot()
    {
        return settings.isLabelToVOBRoot();
    }
    
    /**
     * @return the value of the setting property 'labelEntireVOB'
     */
    protected boolean isLabelEntireVOB()
    {
        return settings.isLabelEntireVOB();
    }

    /**
     * @return the value of the setting property 'ignoreMklabelFailureOnLockedObjects'
     */
    protected boolean isIgnoreMklabelFailureOnLockedObjects()
    {
        return settings.isIgnoreMklabelFailureOnLockedObjects();
    }    

    public void setSettings( Settings settings )
    {
        this.settings = settings;
    }
    
    public void setCommandLineExecutor( CommandLineExecutor commandLineExecutor )
    {
        this.commandLineExecutor = commandLineExecutor;
    }
}
